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
 * storage. The pinned bootstrap is installed under Horizon's private files/usr prefix and must
 * pass the Android linker, shell, Cargo and Clang smoke tests before HNMod builds are enabled.
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
                "set -e; test \"\$(uname -m)\" = aarch64; cargo --version; rustc --version; clang --version; git --version; rsync --version; python3 --version; cmake --version; make --version; pkg-config --version; readelf --version",
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
            installed -> "Embedded toolchain runtime ready."
            else -> "The embedded compiler runtime is not installed yet. No external Termux app is required or used."
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
            status.detail + " Install the Horizon embedded toolchain before compiling HNMods."
        }
        when (val imported = HnmodBuildAdapter.compile(activity, File(activity.filesDir, PREFIX_DIR), selected)) {
            is Updater.Imported.Ok ->
                "Build ${imported.name} (${imported.sha.take(12)}) imported and activated. Restart the game to use it."
            is Updater.Imported.Bad -> error(imported.message)
        }
    }
}
