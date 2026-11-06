package dev.horizon.launcher.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream

/**
 * Materializes pinned HNMod build inputs from APK assets, copies selected archives into private
 * storage, runs the Horizon build pipeline in the embedded prefix, and imports the AArch64 result.
 */
object HnmodBuildAdapter {
    private const val MAX_MOD_ARCHIVE_BYTES = 64L * 1024L * 1024L
    private const val MAX_TOTAL_ARCHIVE_BYTES = 256L * 1024L * 1024L

    private val projectAssets = listOf(
        "android/UPSTREAM_PIN",
        "android/android.patch",
        "android/termux/build-with-mods.sh",
        "hnmod-core/Cargo.toml",
        "hnmod-core/README.md",
        "hnmod-core/src/lib.rs",
        "hnmod-core/src/bin/hnmod.rs",
        "hnmod-core/THIRD_PARTY_LICENSES/starling-MIT.txt"
    )

    fun compile(context: Context, prefix: File, selected: List<Uri>): Updater.Imported {
        require(selected.isNotEmpty()) { "Select at least one .hnmod archive." }
        materializeProject(context)

        val state = File(context.filesDir, "horizon-toolchain")
        val inputDir = File(state, "input-mods")
        inputDir.deleteRecursively()
        require(inputDir.isDirectory || inputDir.mkdirs()) { "Cannot create private HNMod input directory." }

        try {
            var totalBytes = 0L
            val archives = selected.mapIndexed { index, uri ->
                val fallback = "mod-$index.hnmod"
                val displayName = context.contentResolver.query(
                    uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }?.takeIf { it.isNotBlank() } ?: fallback

                require(displayName.endsWith(".hnmod", ignoreCase = true)) {
                    "Only .hnmod archives can be compiled: $displayName"
                }
                val stem = displayName.substringBeforeLast('.', displayName)
                val safeName = stem.replace(Regex("[^A-Za-z0-9._-]"), "_") + ".hnmod"
                val destination = File(inputDir, "${index.toString().padStart(3, '0')}_$safeName")
                val source = context.contentResolver.openInputStream(uri)
                    ?: error("Could not open selected HNMod archive: $displayName")

                source.use { input ->
                    FileOutputStream(destination).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var fileBytes = 0L
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            fileBytes += count
                            totalBytes += count
                            require(fileBytes <= MAX_MOD_ARCHIVE_BYTES) {
                                "$displayName exceeds the 64 MiB compressed archive limit."
                            }
                            require(totalBytes <= MAX_TOTAL_ARCHIVE_BYTES) {
                                "Selected HNMod archives exceed the 256 MiB total limit."
                            }
                            output.write(buffer, 0, count)
                        }
                    }
                }
                require(destination.length() > 0L) { "Selected HNMod archive is empty: $displayName" }
                destination
            }

            val buildName = "mods-${System.currentTimeMillis()}"
            val projectRoot = File(context.filesDir, "horizon-project")
            val script = File(projectRoot, "android/termux/build-with-mods.sh")
            val args = listOf("--name", buildName, "--jobs", "3") + archives.map { it.absolutePath }
            val build = EmbeddedProcessRunner.runScript(context, prefix, script, args)
            check(build.exitCode == 0) {
                "HNMod compilation failed (exit ${build.exitCode}). Full log: ${build.logFile.absolutePath}\n${build.output.takeLast(8000)}"
            }

            val home = File(state, "home")
            val library = File(home, "Horizon-builds/$buildName/libhorizon_android_game.so")
            require(library.isFile && library.length() > 1_000_000L) {
                "The build finished without producing a valid game library. Log: ${build.logFile.absolutePath}"
            }
            return Updater.importLocal(context, library.inputStream())
        } finally {
            inputDir.deleteRecursively()
        }
    }

    private fun materializeProject(context: Context) {
        val root = File(context.filesDir, "horizon-project")
        require(root.isDirectory || root.mkdirs()) { "Cannot create private Horizon build workspace." }
        projectAssets.forEach { relative ->
            val destination = File(root, relative)
            require(destination.parentFile?.let { it.isDirectory || it.mkdirs() } == true) {
                "Cannot create build workspace directory for $relative"
            }
            context.assets.open("hnmod/$relative").use { source ->
                destination.outputStream().use { output -> source.copyTo(output) }
            }
        }
    }
}
