package dev.horizon.launcher.data

import android.app.Activity
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Horizon-owned toolchain entry point.
 *
 * This deliberately does not call Termux RUN_COMMAND, inspect another app, or request shared
 * storage. The runtime is installed in Horizon's private files directory. Until the pinned,
 * app-prefix-compatible bootstrap and Android W^X execution bridge are shipped, fail closed
 * instead of silently falling back to an external Termux installation.
 */
object TermuxCompiler {
    private const val RUNTIME_DIR = "horizon-toolchain"
    private const val MARKER = ".horizon-runtime-ready"

    data class RuntimeStatus(
        val supportedAbi: Boolean,
        val installed: Boolean,
        val detail: String
    )

    fun status(activity: Activity): RuntimeStatus {
        val supported = Build.SUPPORTED_ABIS.any { it == "arm64-v8a" }
        val root = File(activity.filesDir, RUNTIME_DIR)
        val marker = File(root, MARKER)
        val installed = supported && marker.isFile && marker.length() > 0L
        val detail = when {
            !supported -> "The embedded compiler currently targets ARM64 (arm64-v8a) only."
            installed -> "Embedded compiler runtime detected."
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
            status.detail + " The app-side runtime installer is being prepared; compilation is safely disabled until its signed toolchain payload and Android executable bridge are included."
        }
        error("The embedded toolchain runtime is present, but the HNMod build adapter is not enabled in this build.")
    }
}
