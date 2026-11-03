package dev.horizon.launcher.data

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import dev.horizon.launcher.core.NativeLoader
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * In-app updates for the native libraries (launcher core + game runtime).
 *
 * CI publishes them to the rolling GitHub release `libs` with a manifest.json (sha256 per file).
 * Downloaded files live in filesDir/updates/<build>/ and are used instead of the APK's copies. A
 * start that does not survive 20 seconds (see armGuard) drops the update, so a bad one can only
 * cost one failed launch.
 */
object Updater {
    /** Bump (also android/ABI_LEVEL) when the Kotlin <-> Rust JNI contract changes. */
    const val ABI_LEVEL = 1
    private const val ABI = "arm64-v8a"
    private const val BASE = "https://github.com/DreamingbullyYT/horizon-android-edition/releases/download/libs"
    private const val CORE = "libhorizon_core.so"
    private const val GAME = "libhorizon_android_game.so"
    private const val DATA = "engine-data.zip"

    private fun root(files: File) = File(files, "updates").apply { mkdirs() }
    private fun marker(files: File) = File(root(files), "pending")
    private fun current(files: File): File? {
        val name = runCatching { File(root(files), "current").readText().trim() }.getOrNull() ?: return null
        return File(root(files), name).takeIf { File(it, CORE).isFile && File(it, GAME).isFile }
    }

    /** Installed update's build number, or 0. */
    fun installedBuild(files: File): Long = current(files)?.name?.toLongOrNull() ?: 0L

    /** Call first in MainActivity: rolls back a failed update and points the loader at the new core. */
    fun prepare(files: File) {
        if (marker(files).exists()) {
            // The previous start with an update never got going.
            File(root(files), "current").delete()
            marker(files).delete()
        }
        NativeLoader.corePath = current(files)?.let { File(it, CORE).absolutePath }
    }

    fun dataPath(files: File): File? = current(files)?.let { File(it, DATA) }?.takeIf { it.isFile }

    /**
     * Makes launcher/game/engine-data.zip the one this app should use (the updated one, else the
     * APK's). When it changed since the game was installed, the installer is asked to redo the
     * tables of the installed game (Install in the launcher; nothing downloads from Mojang).
     */
    fun syncEngineData(context: Context, gameHome: File) {
        val target = File(gameHome, DATA)
        val stamp = File(gameHome, ".engine-data.sha")
        val updated = dataPath(context.filesDir)
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(65536)
        val input = updated?.inputStream() ?: context.assets.open(DATA)
        input.use { while (true) { val n = it.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) } }
        val sum = digest.digest().joinToString("") { "%02x".format(it) }
        val old = runCatching { stamp.readText().trim() }.getOrNull()
        if (old == sum && target.isFile) return
        (updated?.inputStream() ?: context.assets.open(DATA)).use { src ->
            File(gameHome, "$DATA.partial").also { part ->
                part.outputStream().use { src.copyTo(it) }
                part.renameTo(target)
            }
        }
        // A different table set than the installed game was prepared with: ask the installer to
        // redo just the tables (no Mojang download). Not "ready" until it has.
        if (old != null) {
            File(gameHome, ".local/horizon-client").listFiles()?.forEach {
                File(it, "android-downloads-v2.json").delete()
            }
            File(gameHome, ".engine-refresh").writeText("1")
        }
        stamp.writeText(sum)
    }

    fun gamePath(files: File): String? = current(files)?.let { File(it, GAME).absolutePath }

    /** Marks "starting with an update"; cleared once the process has lived 20 s. */
    fun armGuard(files: File) {
        if (current(files) == null) return
        marker(files).writeText("1")
        Handler(Looper.getMainLooper()).postDelayed({ marker(files).delete() }, 20_000)
    }

    sealed class Result {
        data class UpToDate(val build: Long) : Result()
        data class Installed(val build: Long, val upstream: String) : Result()
        data class Failed(val message: String) : Result()
    }

    /** Blocking: call from Dispatchers.IO. Takes effect at the next app start. */
    fun checkAndInstall(context: Context): Result {
        return try {
        val files = context.filesDir
        val manifest = JSONObject(httpText("$BASE/manifest.json"))
        if (manifest.optInt("abi_level", -1) != ABI_LEVEL) {
            return Result.Failed("This update needs a newer app. Use “Install app update” below.")
        }
        val build = manifest.getLong("build")
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        @Suppress("DEPRECATION")
        val apkBuild = if (Build.VERSION.SDK_INT >= 28) packageInfo.longVersionCode else packageInfo.versionCode.toLong()
        val have = maxOf(apkBuild, installedBuild(files))
        if (build <= have) return Result.UpToDate(have)
        val entry = manifest.getJSONObject(ABI)
        val target = File(root(files), "$build.partial").apply { deleteRecursively(); mkdirs() }
        for ((key, name) in listOf("core" to CORE, "game" to GAME, "data" to DATA)) {
            if (key == "data" && !entry.has("data")) continue
            val item = entry.getJSONObject(key)
            val out = File(target, name)
            download("$BASE/${item.getString("file")}", out)
            val sum = sha256(out)
            if (!sum.equals(item.getString("sha256"), ignoreCase = true)) {
                target.deleteRecursively()
                return Result.Failed("Checksum mismatch for $name")
            }
        }
        val finalDir = File(root(files), build.toString())
        finalDir.deleteRecursively()
        if (!target.renameTo(finalDir)) return Result.Failed("Could not store the update")
        File(root(files), "current").writeText(build.toString())
        // keep only the newest installed update
        root(files).listFiles()?.filter { it.isDirectory && it.name != build.toString() }?.forEach { it.deleteRecursively() }
        Result.Installed(build, manifest.optString("upstream"))
        } catch (t: Throwable) {
            Result.Failed(t.message ?: t.javaClass.simpleName)
        }
    }

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 60_000
            instanceFollowRedirects = true
        }

    private fun httpText(url: String): String {
        val c = open(url)
        if (c.responseCode != 200) error("HTTP ${c.responseCode} for ${url.substringAfterLast('/')}")
        return c.inputStream.use { it.readBytes().decodeToString() }
    }

    private fun download(url: String, out: File) {
        val c = open(url)
        if (c.responseCode != 200) error("HTTP ${c.responseCode} for ${url.substringAfterLast('/')}")
        c.inputStream.use { input -> out.outputStream().use { input.copyTo(it) } }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(65536)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
