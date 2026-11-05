package dev.horizon.launcher.sdui

import android.content.Context
import java.io.File

/**
 * Where UI files come from. A file in `filesDir/ui/` replaces the one packaged in `assets/ui/`, so a
 * screen, the theme or a translation can be changed (or downloaded) without a new APK.
 */
object UiStore {
    fun read(context: Context, path: String): String? {
        if (path.contains("..") || path.startsWith("/")) return null
        val override = File(File(context.filesDir, "ui"), path)
        if (override.isFile) return runCatching { override.readText() }.getOrNull()
        return runCatching { context.assets.open("ui/$path").bufferedReader().use { it.readText() } }.getOrNull()
    }

    /** Writes an updated UI file (used by a future updater); returns false for an unsafe path. */
    fun write(context: Context, path: String, text: String): Boolean {
        if (path.contains("..") || path.startsWith("/")) return false
        val target = File(File(context.filesDir, "ui"), path)
        target.parentFile?.mkdirs()
        return runCatching { target.writeText(text) }.isSuccess
    }
}
