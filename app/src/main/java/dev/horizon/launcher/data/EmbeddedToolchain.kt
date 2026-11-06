package dev.horizon.launcher.data

import android.app.Activity
import android.net.Uri
import android.os.Build
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Horizon-owned toolchain entry point.
 *
 * This deliberately does not call Termux RUN_COMMAND, inspect another app, or request shared
 * storage. The runtime is installed in Horizon's private files directory. Until the pinned,
 * app-prefix-compatible bootstrap and Android W^X execution bridge are shipped, fail closed
 * instead of silently falling back to an external Termux installation.
 */
object EmbeddedToolchain {
    private const val PREFIX_DIR = "usr"
    private const val STATE_DIR = "horizon-toolchain"
    private const val MARKER = ".horizon-runtime-ready"

    data class RuntimeStatus(
        val supportedAbi: Boolean,
        val installed: Boolean,
        val detail: String
    )

    suspend fun installBootstrap(
        context: android.content.Context,
        archive: File,
        expectedSha256: String
    ): EmbeddedBootstrapInstaller.Result = withContext(Dispatchers.IO) {
        val state = File(context.filesDir, STATE_DIR)
        val marker = File(state, MARKER)
        marker.delete()
        val result = EmbeddedBootstrapInstaller.install(context, archive, expectedSha256)
        try {
            val secondStage = File(
                result.prefix,
                "etc/termux/termux-bootstrap/second-stage/termux-bootstrap-second-stage.sh"
            )
            val stageResult = EmbeddedProcessRunner.runScript(context, result.prefix, secondStage)
            check(stageResult.exitCode == 0) {
                "Toolchain bootstrap configuration failed (exit ${stageResult.exitCode}). Log: ${stageResult.logFile.absolutePath}"
            }

            val smoke = EmbeddedProcessRunner.runShellCommand(
                context,
                result.prefix,
                "set -e; test \"\$(uname -m)\" = aarch64; cargo --version; clang --version; git --version; readelf --version",
                "toolchain-smoke-test"
            )
            check(smoke.exitCode == 0) {
                "Embedded compiler smoke test failed (exit ${smoke.exitCode}). Log: ${smoke.logFile.absolutePath}"
            }

            require(state.isDirectory || state.mkdirs()) { "Cannot create Horizon toolchain state directory." }
            marker.writeText("schema=1\nsha256=${result.sha256}\nabi=arm64-v8a\n")
            result
        } catch (error: Exception) {
            marker.delete()
            throw error
        }
    }

    fun status(activity: Activity): RuntimeStatus {
        val supported = Build.SUPPORTED_ABIS.any { it == "arm64-v8a" }
        val prefix = File(activity.filesDir, PREFIX_DIR)
        val state = File(activity.filesDir, STATE_DIR)
        val marker = File(state, MARKER)
        val installed = supported &&
            marker.isFile && marker.length() > 0L &&
            File(prefix, "bin/bash").isFile &&
            File(prefix, "bin/cargo").isFile &&
            File(prefix, "bin/rustc").isFile &&
            File(prefix, "bin/clang").exists()
        val detail = when {
            !supported -> "The embedded compiler currently targets ARM64 (arm64-v8a) only."
            installed -> "Embedded toolchain runtime ready; HNMod build adapter is still pending."
            else -> "The embedded compiler runtime is not bundled in this build yet. No external Termux app is required or used."
        }
        return RuntimeStatus(supported, installed, detail)
    }

    suspend fun start(activity: Activity, selected: List<Uri>): String = withContext(Dispatchers.IO) {
        require(selected.isNotEmpty()) { "Select at least one .hnmod file." }
        val invalid = selected.firstOrNull { uri ->
            val name = activity.contentResolver.query(
                uri,
                arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
                null, null, null
            )?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
            name?.endsWith(".hnmod", ignoreCase = true) != true
        }
        require(invalid == null) { "Only .hnmod archives can be compiled." }

        val status = status(activity)
        check(status.supportedAbi) { status.detail }
        check(status.installed) {
            status.detail + " The app-side runtime installer is being prepared; compilation is safely disabled until its checksum-verified bootstrap payload and Android executable bridge are included."
        }
        error("The embedded toolchain runtime is present, but the HNMod build adapter is not enabled in this build.")
    }
}
