package dev.horizon.launcher.data

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Runs the native HNMod build in Termux's supported RUN_COMMAND environment.
 *
 * Termux packages are built for Termux's own prefix. We must not unpack them into Horizon's
 * private directory and pretend they are relocatable.
 */
object TermuxCompiler {
    private const val TERMUX_PACKAGE = "com.termux"
    private const val TERMUX_PREFIX = "/data/data/com.termux/files/usr"
    private const val TERMUX_HOME = "/data/data/com.termux/files/home"
    private const val REPOSITORY = "https://github.com/DreamingbullyYT/horizon-android-edition.git"
    private const val TOOLS_REF = "feature/hnmod-core"

    suspend fun start(activity: Activity, selected: List<Uri>): String {
        require(selected.isNotEmpty()) { "Select at least one .hnmod file." }
        require(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "In-launcher HNMod compilation currently requires Android 10 or newer."
        }

        val installed = runCatching {
            activity.packageManager.getPackageInfo(TERMUX_PACKAGE, 0)
        }.isSuccess
        if (!installed) {
            activity.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/termux/termux-app/releases/latest"))
            )
            return "Install Termux from its official releases, open it once, then return and retry."
        }

        if (activity.checkSelfPermission("com.termux.permission.RUN_COMMAND") != PackageManager.PERMISSION_GRANTED) {
            activity.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${activity.packageName}"))
            )
            return "Grant Horizon the “Run commands in Termux environment” permission, then retry."
        }

        val paths = withContext(Dispatchers.IO) {
            selected.map { copyToDownloads(activity, it) }
        }
        // copyToDownloads only emits a sanitized filename under a fixed path, so these arguments
        // contain no shell metacharacters or whitespace.
        val modArguments = paths.joinToString(" ")
        val command = """
            set -e
            echo '[Horizon] Preparing Termux shared storage…'
            if [ ! -d "${'$'}HOME/storage/downloads" ]; then termux-setup-storage; fi
            echo '[Horizon] Installing/checking compiler dependencies…'
            pkg update -y
            pkg install -y rust clang git rsync binutils python cmake make pkg-config coreutils
            REPO="${'$'}HOME/.horizon-launcher/hnmod-tools"
            mkdir -p "${'$'}HOME/.horizon-launcher"
            if [ ! -d "${'$'}REPO/.git" ]; then
              git clone --depth 1 --filter=blob:none --sparse --branch $TOOLS_REF $REPOSITORY "${'$'}REPO"
            else
              git -C "${'$'}REPO" fetch --depth 1 origin $TOOLS_REF
              git -C "${'$'}REPO" reset --hard FETCH_HEAD
            fi
            git -C "${'$'}REPO" sparse-checkout set android/UPSTREAM_PIN android/android.patch hnmod-core android/termux
            echo '[Horizon] Starting the HNMod build…'
            bash "${'$'}REPO/android/termux/build-with-mods.sh" $modArguments
        """.trimIndent()

        val intent = Intent().apply {
            setClassName(TERMUX_PACKAGE, "com.termux.app.RunCommandService")
            action = "com.termux.RUN_COMMAND"
            putExtra("com.termux.RUN_COMMAND_PATH", "$TERMUX_PREFIX/bin/bash")
            putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-lc", command))
            putExtra("com.termux.RUN_COMMAND_WORKDIR", TERMUX_HOME)
            putExtra("com.termux.RUN_COMMAND_BACKGROUND", false)
            putExtra("com.termux.RUN_COMMAND_COMMAND_LABEL", "Horizon HNMod compiler")
            putExtra(
                "com.termux.RUN_COMMAND_COMMAND_DESCRIPTION",
                "Checks or installs the Rust/Clang toolchain, validates the selected HNMod files and compiles the Android game library."
            )
            putExtra(
                "com.termux.RUN_COMMAND_COMMAND_HELP",
                "Termux must have allow-external-apps=true in ~/.termux/termux.properties. " +
                    "The first run asks for shared-storage access. Native mod build scripts and procedural macros execute during compilation; only compile mods you trust."
            )
        }
        activity.startService(intent)
        return "Build started in Termux. Keep its terminal session open; when it finishes, return here and import the compiled game library."
    }

    private fun copyToDownloads(activity: Activity, source: Uri): String {
        val resolver = activity.contentResolver
        val original = resolver.query(source, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
            ?: "native-mod.hnmod"
        require(original.endsWith(".hnmod", ignoreCase = true)) {
            "Only .hnmod archives can be compiled (selected: $original)."
        }
        val base = original.substringAfterLast('/').substringBeforeLast('.')
            .replace(Regex("[^A-Za-z0-9._-]"), "_").take(64).ifBlank { "native-mod" }
        val fileName = "$base-${System.currentTimeMillis()}.hnmod"
        val relative = "Horizon/HNMods"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/zip")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$relative")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val destination = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("Could not create the shared HNMod input file.")
        try {
            val input = resolver.openInputStream(source) ?: error("Could not read the selected HNMod file.")
            input.use { src ->
                val output = resolver.openOutputStream(destination) ?: error("Could not write the HNMod file to Downloads.")
                output.use { src.copyTo(it) }
            }
            resolver.update(destination, ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }, null, null)
        } catch (t: Throwable) {
            resolver.delete(destination, null, null)
            throw t
        }
        return "/storage/emulated/0/Download/$relative/$fileName"
    }
}
