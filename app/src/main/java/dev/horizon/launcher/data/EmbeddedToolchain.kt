package dev.horizon.launcher.data

import android.app.Activity
import android.net.Uri
import android.os.Build
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** First-run installer and entry point for Horizon's private Termux-compatible HNMod toolchain. */
object EmbeddedToolchain {
    private const val BOOTSTRAP_URL =
        "https://github.com/termux/termux-packages/releases/download/bootstrap-2026.11.01-r1%2Bapt.android-7/bootstrap-aarch64.zip"
    private const val BOOTSTRAP_SHA256 =
        "5a1429b6c68ee3bdc17b5fd6e47ba42933ec924cd7283fe74992574e1d5badae"
    private const val STATE_SCHEMA = "2"

    data class RuntimeStatus(
        val supportedAbi: Boolean,
        val installed: Boolean,
        val detail: String
    )

    fun status(activity: Activity): RuntimeStatus {
        val supported = Build.VERSION.SDK_INT >= 29 &&
            Build.SUPPORTED_ABIS.any { it == "arm64-v8a" }
        val prefix = EmbeddedTermuxPaths.prefix(activity)
        val marker = EmbeddedTermuxPaths.marker(activity)
        val installed = supported &&
            marker.isFile && marker.readText().contains("schema=$STATE_SCHEMA") &&
            existsOrSymlink(File(prefix, "bin/bash")) &&
            existsOrSymlink(File(prefix, "bin/cargo")) &&
            existsOrSymlink(File(prefix, "bin/rustc")) &&
            existsOrSymlink(File(prefix, "bin/clang")) &&
            EmbeddedTermuxPaths.proot(activity).isFile &&
            EmbeddedTermuxPaths.loader(activity).isFile
        val detail = when {
            !supported -> "HNMod local compilation requires ARM64 and Android 10 or newer."
            installed -> "Private Termux-compatible compiler runtime ready."
            else -> "The compiler will be installed automatically on the first HNMod compilation."
        }
        return RuntimeStatus(supported, installed, detail)
    }

    suspend fun start(
        activity: Activity,
        selected: List<Uri>,
        onProgress: (String) -> Unit = {}
    ): String = withContext(Dispatchers.IO) {
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
        val initial = status(activity)
        check(initial.supportedAbi) { initial.detail }

        if (!initial.installed) installRuntime(activity, onProgress)
        onProgress("Preparing HNMod build")
        when (val imported = HnmodBuildAdapter.compile(activity, EmbeddedTermuxPaths.prefix(activity), selected)) {
            is Updater.Imported.Ok ->
                "Build ${imported.name} (${imported.sha.take(12)}) imported and activated. Restart the game to use it."
            is Updater.Imported.Bad -> error(imported.message)
        }
    }

    private suspend fun installRuntime(context: android.content.Context, onProgress: (String) -> Unit) {
        val marker = EmbeddedTermuxPaths.marker(context)
        marker.delete()
        val archive = File(context.cacheDir, "termux-bootstrap-arm64.zip")
        try {
            if (!archive.isFile || sha256(archive) != BOOTSTRAP_SHA256) {
                onProgress("Downloading official Termux ARM64 bootstrap (about 32 MB)…")
                downloadBootstrap(archive, onProgress)
            }
            onProgress("Verifying Termux bootstrap")
            require(sha256(archive) == BOOTSTRAP_SHA256) {
                "Termux bootstrap checksum verification failed. Please retry the installation."
            }

            onProgress("Extracting private Termux environment")
            val prefix = EmbeddedBootstrapInstaller.install(context, archive)
            installProotAssets(context)
            onProgress("Configuring official Termux package repository")
            val setup = EmbeddedProcessRunner.runShellCommand(
                context,
                "mkdir -p \$PREFIX/etc/apt \$PREFIX/var/cache/apt/archives/partial; " +
                    "printf '%s\\n' 'deb https://packages-cf.termux.dev/apt/termux-main stable main' > \$PREFIX/etc/apt/sources.list; " +
                    "apt update -o Acquire::Retries=5 -o Acquire::Queue-Mode=access || " +
                    "apt update -o Acquire::Retries=5 -o Acquire::Queue-Mode=access",
                "termux-apt-update"
            )
            check(setup.exitCode == 0) {
                "Could not update the official Termux package index (exit ${setup.exitCode}). Log: ${setup.logFile.absolutePath}\n${setup.output.takeLast(4000)}"
            }

            onProgress("Installing Rust, Clang and HNMod build dependencies")
            val packages = EmbeddedProcessRunner.runShellCommand(
                context,
                "DEBIAN_FRONTEND=noninteractive apt install -y --fix-missing " +
                    "-o Acquire::Retries=5 -o Acquire::Queue-Mode=access " +
                    "-o Dpkg::Options::=--force-confnew " +
                    "rust clang git rsync binutils python cmake make pkg-config coreutils",
                "termux-install-build-packages"
            )
            check(packages.exitCode == 0) {
                "Could not install HNMod compiler packages (exit ${packages.exitCode}). Log: ${packages.logFile.absolutePath}\n${packages.output.takeLast(5000)}"
            }

            onProgress("Checking Rust and native compiler")
            val smoke = EmbeddedProcessRunner.runShellCommand(
                context,
                "set -e; test \"\$(uname -m)\" = aarch64; cargo --version; rustc --version; clang --version; git --version; rsync --version; python --version; cmake --version; make --version; pkg-config --version; readelf --version",
                "toolchain-smoke-test"
            )
            check(smoke.exitCode == 0) {
                "Compiler verification failed (exit ${smoke.exitCode}). Log: ${smoke.logFile.absolutePath}\n${smoke.output.takeLast(5000)}"
            }
            marker.parentFile?.let { require(it.isDirectory || it.mkdirs()) }
            marker.writeText("schema=$STATE_SCHEMA\nbootstrap=$BOOTSTRAP_SHA256\nabi=arm64-v8a\n")
            onProgress("HNMod compiler installed successfully")
        } catch (error: Exception) {
            marker.delete()
            throw error
        } finally {
            archive.delete()
        }
    }

    private fun installProotAssets(context: android.content.Context) {
        val dir = EmbeddedTermuxPaths.prootDir(context)
        require(dir.isDirectory || dir.mkdirs()) { "Cannot create private PRoot directory." }
        copyAsset(context, "proot/proot", EmbeddedTermuxPaths.proot(context), executable = true)
        copyAsset(context, "proot/loader", EmbeddedTermuxPaths.loader(context), executable = true)
        val libDir = EmbeddedTermuxPaths.prootLib(context)
        require(libDir.isDirectory || libDir.mkdirs()) { "Cannot create PRoot library directory." }
        val libraries = context.assets.list("proot/lib") ?: emptyArray()
        require(libraries.isNotEmpty()) { "This Horizon APK is missing the embedded PRoot libraries." }
        libraries.forEach { name ->
            copyAsset(context, "proot/lib/$name", File(libDir, name), executable = false)
        }
    }

    private fun copyAsset(context: android.content.Context, asset: String, destination: File, executable: Boolean) {
        require(destination.parentFile?.let { it.isDirectory || it.mkdirs() } == true) {
            "Cannot create directory for $asset."
        }
        context.assets.open(asset).use { input ->
            FileOutputStream(destination).use { output -> input.copyTo(output) }
        }
        if (executable) destination.setExecutable(true, false)
    }

    private fun downloadBootstrap(destination: File, onProgress: (String) -> Unit) {
        val connection = (URL(BOOTSTRAP_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            requestMethod = "GET"
        }
        try {
            connection.connect()
            check(connection.responseCode in 200..299) {
                "Termux bootstrap download failed with HTTP ${connection.responseCode}."
            }
            val total = connection.contentLengthLong
            var downloaded = 0L
            val lastPercent = intArrayOf(-1)
            val partial = File(destination.parentFile, destination.name + ".part")
            connection.inputStream.use { input ->
                FileOutputStream(partial).use { output ->
                    val buffer = ByteArray(128 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        downloaded += count
                        if (total > 0L) {
                            val percent = (downloaded * 100 / total).toInt()
                            if (percent != lastPercent[0] && percent % 5 == 0) {
                                lastPercent[0] = percent
                                onProgress("Downloading Termux bootstrap… $percent%")
                            }
                        }
                    }
                }
            }
            require(partial.length() > 0L) { "Termux bootstrap download was empty." }
            if (!partial.renameTo(destination)) {
                destination.delete()
                require(partial.renameTo(destination)) { "Cannot save the downloaded Termux bootstrap." }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(128 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun existsOrSymlink(file: File): Boolean =
        file.exists() || java.nio.file.Files.isSymbolicLink(file.toPath())
}
