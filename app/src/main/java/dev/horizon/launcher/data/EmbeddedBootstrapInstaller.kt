package dev.horizon.launcher.data

import android.content.Context
import android.system.Os
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream

/**
 * Extracts a pinned Horizon-prefix Termux bootstrap into files/usr.
 *
 * The archive must be built for dev.horizon.launcher. This class only installs files; it does not
 * claim that the runtime is executable until the separate Android linker smoke test has passed.
 */
object EmbeddedBootstrapInstaller {
    private const val MAX_ENTRIES = 500_000
    private const val MAX_UNPACKED_BYTES = 20L * 1024L * 1024L * 1024L
    private const val SYMLINK_SEPARATOR = '←'

    data class Result(
        val prefix: File,
        val unpackedBytes: Long,
        val symlinkCount: Int,
        val sha256: String
    )

    fun install(context: Context, archive: File, expectedSha256: String): Result {
        require(archive.isFile && archive.length() > 0L) { "Toolchain archive is missing or empty." }
        require(expectedSha256.matches(Regex("^[0-9a-fA-F]{64}$"))) { "Invalid toolchain checksum." }

        val digest = sha256(archive)
        require(digest.equals(expectedSha256, ignoreCase = true)) {
            "Toolchain checksum mismatch. The downloaded archive was not installed."
        }

        val files = context.filesDir
        val prefix = File(files, "usr")
        val staging = File(files, "usr-staging")
        val backup = File(files, "usr-backup")
        require(files.isDirectory || files.mkdirs()) { "Cannot access Horizon's private files directory." }

        staging.deleteRecursively()
        backup.deleteRecursively()
        require(staging.mkdirs()) { "Cannot create the toolchain staging directory." }

        var totalBytes = 0L
        var entryCount = 0
        val extracted = HashSet<String>()
        val symlinks = ArrayList<Pair<String, String>>()
        var sawSymlinkTable = false
        var prefixActivated = false

        try {
            ZipInputStream(BufferedInputStream(FileInputStream(archive))).use { zip ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val entry = zip.nextEntry ?: break
                    entryCount++
                    require(entryCount <= MAX_ENTRIES) { "Toolchain archive contains too many entries." }

                    if (entry.name == "SYMLINKS.txt" || entry.name == "./SYMLINKS.txt") {
                        require(!sawSymlinkTable) { "Duplicate symlink table in toolchain archive." }
                        sawSymlinkTable = true
                        val bytes = zip.readBytesBounded(8 * 1024 * 1024)
                        val text = bytes.toString(Charsets.UTF_8)
                        text.lineSequence().filter { it.isNotBlank() }.forEach { line ->
                            val separator = line.indexOf(SYMLINK_SEPARATOR)
                            require(separator > 0 && line.indexOf(SYMLINK_SEPARATOR, separator + 1) == -1) {
                                "Malformed symlink record in toolchain archive."
                            }
                            val target = line.substring(0, separator)
                            val link = safeRelative(line.substring(separator + 1))
                            validateSymlinkTarget(context, staging, link, target)
                            require(extracted.add(link)) { "Duplicate toolchain path: $link" }
                            symlinks += target to link
                        }
                        zip.closeEntry()
                        continue
                    }

                    val relative = safeArchivePath(entry.name)
                    if (relative.isEmpty()) {
                        zip.closeEntry()
                        continue
                    }
                    require(extracted.add(relative)) { "Duplicate toolchain path: $relative" }

                    val output = File(staging, relative)
                    ensureInside(staging, output)
                    if (entry.isDirectory || entry.name.endsWith("/")) {
                        require(output.isDirectory || output.mkdirs()) { "Cannot create toolchain directory: $relative" }
                    } else {
                        require(output.parentFile?.let { it.isDirectory || it.mkdirs() } == true) {
                            "Cannot create parent directory for $relative"
                        }
                        FileOutputStream(output).use { stream ->
                            while (true) {
                                val count = zip.read(buffer)
                                if (count < 0) break
                                totalBytes += count
                                require(totalBytes <= MAX_UNPACKED_BYTES) { "Toolchain archive exceeds the unpacked size limit." }
                                stream.write(buffer, 0, count)
                            }
                        }
                        if (isExecutablePath(relative)) {
                            Os.chmod(output.absolutePath, 0x1C0) // 0700
                        }
                    }
                    zip.closeEntry()
                }
            }

            require(sawSymlinkTable && symlinks.isNotEmpty()) {
                "Toolchain archive has no valid SYMLINKS.txt table."
            }
            require(listOf("bin/bash", "bin/cargo", "bin/rustc", "bin/clang", "bin/git")
                .all { it in extracted }) {
                "Toolchain archive is missing required compiler/runtime files."
            }

            symlinks.forEach { (target, relativeLink) ->
                val link = File(staging, relativeLink)
                ensureInside(staging, link)
                require(!link.exists() && !java.nio.file.Files.isSymbolicLink(link.toPath())) {
                    "Symlink destination already exists: $relativeLink"
                }
                link.parentFile?.let { require(it.isDirectory || it.mkdirs()) }
                Os.symlink(target, link.absolutePath)
            }

            require(!prefix.exists() || prefix.renameTo(backup)) {
                "Cannot move the previous toolchain prefix out of the way."
            }
            if (!staging.renameTo(prefix)) {
                if (backup.exists()) backup.renameTo(prefix)
                error("Cannot activate the extracted Horizon toolchain prefix.")
            }
            prefixActivated = true

            val required = listOf("bin/bash", "bin/cargo", "bin/rustc", "bin/clang", "bin/git")
            require(required.all { File(prefix, it).exists() }) {
                "Toolchain files were extracted, but required runtime links do not resolve."
            }
            backup.deleteRecursively()
            return Result(prefix, totalBytes, symlinks.size, digest)
        } catch (error: Exception) {
            staging.deleteRecursively()
            if (prefixActivated) prefix.deleteRecursively()
            if (backup.exists()) backup.renameTo(prefix)
            throw error
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(128 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun safeArchivePath(raw: String): String {
        require(!raw.startsWith("/") && !raw.contains('\\') && !raw.contains('\u0000')) {
            "Unsafe absolute or malformed path in toolchain archive."
        }
        return safeRelative(raw.trimEnd('/').removePrefix("./"))
    }

    private fun safeRelative(raw: String): String {
        val normalized = raw.removePrefix("./")
        require(normalized.isNotBlank() && !normalized.startsWith("/") &&
            !normalized.contains('\\') && !normalized.contains('\u0000')) {
            "Unsafe path in toolchain archive."
        }
        val parts = normalized.split('/')
        require(parts.none { it.isBlank() || it == "." || it == ".." }) {
            "Path traversal detected in toolchain archive."
        }
        return parts.joinToString("/")
    }

    private fun ensureInside(root: File, candidate: File) {
        val rootPath = root.canonicalFile.toPath()
        val candidatePath = candidate.canonicalFile.toPath()
        require(candidatePath.startsWith(rootPath)) { "Toolchain archive path escapes its staging directory." }
    }

    private fun validateSymlinkTarget(context: Context, staging: File, link: String, target: String) {
        require(target.isNotBlank() && !target.contains('\u0000')) { "Empty or malformed symlink target." }
        if (target.startsWith("/")) {
            val expectedPrefix = File(context.filesDir, "usr").absolutePath
            val legacyPrefix = "/data/data/${context.packageName}/files/usr"
            require(target == expectedPrefix || target.startsWith(expectedPrefix + "/") ||
                target == legacyPrefix || target.startsWith(legacyPrefix + "/") ||
                target.startsWith("/system/") || target.startsWith("/apex/")) {
                "Symlink points outside Horizon's runtime: $link"
            }
        } else {
            val linkFile = File(staging, link)
            val resolved = File(linkFile.parentFile, target).canonicalFile.toPath()
            require(resolved.startsWith(staging.canonicalFile.toPath())) {
                "Symlink escapes Horizon's runtime: $link"
            }
        }
    }

    private fun isExecutablePath(path: String): Boolean =
        path.startsWith("bin/") ||
            path == "libexec" || path.startsWith("libexec/") ||
            path == "lib/apt/apt-helper" ||
            path.startsWith("lib/apt/methods/")

    private fun InputStream.readBytesBounded(limit: Int): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0
        while (true) {
            val count = read(buffer)
            if (count < 0) break
            total += count
            require(total <= limit) { "Toolchain symlink table is too large." }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }
}
