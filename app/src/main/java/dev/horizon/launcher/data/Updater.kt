package dev.horizon.launcher.data

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import dev.horizon.launcher.core.Native
import dev.horizon.launcher.core.NativeLoader
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.security.MessageDigest

/**
 * In-app updates of the Horizon runtime (launcher core + game library + engine data), modelled on
 * the PC launcher: the newest build is the signed GitHub release `nightly`, older ones are kept as
 * `nightly-<date>-<commit>`. The Rust core (core/src/update.rs) lists them and downloads a build only
 * after its SHA256SUMS passed the Ed25519 check; this object keeps the versions on disk, switches
 * between them and rolls back a build that does not start.
 *
 * filesDir/updates/<version>/   libhorizon_core.so, libhorizon_android_game.so, engine-data.zip,
 *                               android-manifest.json
 * filesDir/updates/current      the active version's name (missing: the runtime bundled in the APK)
 * filesDir/updates/hold         the newest version the player chose not to follow (rolled back by hand)
 * filesDir/updates/bad          versions that failed to start
 * filesDir/updates/pending      written while a start with an update has not lived 20 s yet
 *
 * A failed or unverified download never touches the active version.
 */
object Updater {
    /** Bump (also android/ABI_LEVEL) when the Kotlin <-> Rust JNI contract changes. */
    const val ABI_LEVEL = 2
    private const val CORE = "libhorizon_core.so"
    private const val GAME = "libhorizon_android_game.so"
    private const val DATA = "engine-data.zip"
    private const val MANIFEST = "android-manifest.json"
    private const val KEEP = 3

    /** The 30-minute background check (like the PC launcher's), on by default. */
    fun autoEnabled(context: Context): Boolean =
        context.getSharedPreferences("horizon-updates", Context.MODE_PRIVATE).getBoolean("auto", true)

    fun setAuto(context: Context, on: Boolean) {
        context.getSharedPreferences("horizon-updates", Context.MODE_PRIVATE).edit().putBoolean("auto", on).apply()
    }

    data class Version(val name: String, val build: Long, val active: Boolean)

    private fun root(files: File) = File(files, "updates").apply { mkdirs() }
    private fun marker(files: File) = File(root(files), "pending")
    private fun read(files: File, name: String): String? =
        runCatching { File(root(files), name).readText().trim() }.getOrNull()?.takeIf { it.isNotEmpty() }
    private fun write(files: File, name: String, value: String?) {
        val f = File(root(files), name)
        if (value == null) f.delete() else f.writeText(value)
    }
    private fun bad(files: File): Set<String> = read(files, "bad")?.lines()?.toSet() ?: emptySet()

    private fun complete(dir: File) = File(dir, CORE).isFile && File(dir, GAME).isFile && File(dir, DATA).isFile

    private fun buildOf(dir: File): Long =
        runCatching { JSONObject(File(dir, MANIFEST).readText()).optLong("build", 0) }.getOrDefault(0L)

    private fun apkBuild(context: Context): Long {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        @Suppress("DEPRECATION")
        return if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
    }

    private fun current(files: File): File? {
        val name = read(files, "current") ?: return null
        return File(root(files), name).takeIf { complete(it) }
    }

    /** The active update's version, or null when the runtime bundled in the APK is used. */
    fun activeVersion(files: File): String? = current(files)?.name

    /** Installed updates, newest first. */
    fun versions(files: File): List<Version> {
        val active = activeVersion(files)
        return root(files).listFiles { f -> f.isDirectory && !f.name.endsWith(".partial") && complete(f) }
            .orEmpty()
            .map { Version(it.name, buildOf(it), it.name == active) }
            .sortedByDescending { it.build }
    }

    /** Active update's build number, or 0 (kept for the settings screen). */
    fun installedBuild(files: File): Long = current(files)?.let { buildOf(it) } ?: 0L

    /** Makes `version` the one the next start uses; null selects the runtime bundled in the APK. */
    fun activate(files: File, version: String?, byHand: Boolean = false) {
        write(files, "current", version)
        if (byHand) {
            // Remember the newest one we were offered, so a rollback by hand is not undone by the next check.
            val newest = versions(files).firstOrNull()?.name
            write(files, "hold", if (version != newest) newest else null)
        }
    }

    /** Call first in MainActivity: rolls back a failed update and points the loader at the active one. */
    fun prepare(files: File, context: Context? = null) {
        if (marker(files).exists()) {
            // The previous start with an update never got going: that build is bad, go back to the one before.
            val failed = read(files, "pending") ?: activeVersion(files)
            marker(files).delete()
            if (failed != null) {
                write(files, "bad", (bad(files) + failed).joinToString("\n"))
                val fallback = versions(files).firstOrNull { it.name != failed && it.name !in bad(files) }
                write(files, "current", fallback?.name)
            }
        }
        // A newer APK obsoletes updates older than itself.
        if (context != null) {
            current(files)?.let { if (buildOf(it) <= apkBuild(context)) write(files, "current", null) }
            // Local builds carry the core of the app they were made under: a newer APK retires them.
            root(files).listFiles { f -> f.isDirectory && isLocal(f.name) && buildOf(f) <= apkBuild(context) }
                ?.forEach { it.deleteRecursively() }
        }
        NativeLoader.corePath = current(files)?.let { File(it, CORE).absolutePath }
    }

    fun dataPath(files: File): File? = current(files)?.let { File(it, DATA) }?.takeIf { it.isFile }
    fun gamePath(files: File): String? = current(files)?.let { File(it, GAME).absolutePath }

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

    /** Marks "starting with an update"; cleared once the process has lived 20 s. */
    fun armGuard(files: File) {
        val active = activeVersion(files) ?: return
        marker(files).writeText(active)
        Handler(Looper.getMainLooper()).postDelayed({ marker(files).delete() }, 20_000)
    }

    sealed class Result {
        data class UpToDate(val build: Long) : Result()
        data class Installed(val build: Long, val upstream: String) : Result()
        data class Failed(val message: String) : Result()
    }

    /** What the newest published Nightly is, as the app sees it. */
    data class Newest(val tag: String, val version: String)

    /** Blocking. The current Nightly (release tag `nightly`), or null when none is published. */
    fun newest(): Newest? {
        val raw = JSONObject(Native.nativeUpdateList())
        if (raw.has("error")) error(raw.getString("error"))
        val list = raw.getJSONArray("releases")
        for (i in 0 until list.length()) {
            val r = list.getJSONObject(i)
            if (r.optBoolean("current")) return Newest(r.getString("tag"), r.getString("version"))
        }
        return null
    }

    /** Blocking: all published Nightlies as (tag, version), newest first. Used to offer older ones. */
    fun published(): List<Newest> {
        val raw = JSONObject(Native.nativeUpdateList())
        if (raw.has("error")) error(raw.getString("error"))
        val list = raw.getJSONArray("releases")
        return (0 until list.length()).map { list.getJSONObject(it).let { r -> Newest(r.getString("tag"), r.getString("version")) } }
    }

    /**
     * Blocking: downloads (and verifies) `tag` as a new installed version and makes it the active one,
     * unless the player rolled back by hand past it. The active version is never touched when
     * anything fails.
     */
    fun install(context: Context, tag: String, version: String, activateIt: Boolean = true): Result {
        return try {
            val files = context.filesDir
            val staging = File(root(files), "$version.partial").apply { deleteRecursively(); mkdirs() }
            val manifest = JSONObject(Native.nativeUpdateDownload(tag, "manifest", staging.absolutePath).also {
                if (it.trimStart().startsWith("{") && JSONObject(it).has("error")) error(JSONObject(it).getString("error"))
            })
            if (manifest.optInt("abi_level", -1) != ABI_LEVEL) {
                staging.deleteRecursively()
                return Result.Failed("This build needs a newer app. Use “Install app update”.")
            }
            val out = Native.nativeUpdateDownload(tag, "runtime", staging.absolutePath)
            if (out.trimStart().startsWith("{") && JSONObject(out).has("error")) {
                staging.deleteRecursively()
                return Result.Failed(JSONObject(out).getString("error"))
            }
            if (!complete(staging)) {
                staging.deleteRecursively()
                return Result.Failed("The download is incomplete")
            }
            val finalDir = File(root(files), version)
            finalDir.deleteRecursively()
            if (!staging.renameTo(finalDir)) return Result.Failed("Could not store the update")
            val build = manifest.optLong("build", 0)
            if (build <= apkBuild(context)) {
                // Not newer than the app itself: nothing to run.
                finalDir.deleteRecursively()
                return Result.UpToDate(apkBuild(context))
            }
            val held = read(files, "hold") == version
            val onLocal = isLocal(activeVersion(files))
            if (activateIt && !held && !onLocal && version !in bad(files)) write(files, "current", version)
            prune(files)
            Result.Installed(build, manifest.optString("upstream"))
        } catch (t: Throwable) {
            Result.Failed(t.message ?: t.javaClass.simpleName)
        }
    }

    /** Name prefix of builds made on the phone (Termux, android/termux/build-with-mods.sh). */
    const val LOCAL = "local-"

    fun isLocal(name: String?) = name?.startsWith(LOCAL) == true

    sealed class Imported {
        data class Ok(val name: String, val sha: String) : Imported()
        data class Bad(val message: String) : Imported()
    }

    /**
     * Blocking: stores a game library built on the phone as its own version and makes it the active one.
     * It is NOT signed (nothing vouches for it but the player), so the UI asks first. The core library
     * and engine data are the ones in use now (the active update's, else the APK's). The build number is
     * the APK's + 1, so a newer APK retires it (its core would be older than the app).
     */
    fun importLocal(context: Context, source: InputStream): Imported {
        val files = context.filesDir
        val staging = File(root(files), "local.partial").apply { deleteRecursively(); mkdirs() }
        return try {
            val game = File(staging, GAME)
            val digest = MessageDigest.getInstance("SHA-256")
            source.use { input ->
                game.outputStream().use { out ->
                    val buf = ByteArray(65536)
                    while (true) { val n = input.read(buf); if (n < 0) break; out.write(buf, 0, n); digest.update(buf, 0, n) }
                }
            }
            val head = ByteArray(20)
            game.inputStream().use { it.read(head) }
            val elf = head[0] == 0x7f.toByte() && head[1] == 'E'.code.toByte() && head[2] == 'L'.code.toByte() && head[3] == 'F'.code.toByte()
            val aarch64 = (head[18].toInt() and 0xff) == 183 && head[19].toInt() == 0
            if (!elf || !aarch64) return Imported.Bad("This is not a 64-bit ARM (aarch64) library.")
            if (game.length() < 1_000_000) return Imported.Bad("The file is too small to be the game library.")
            val sha = digest.digest().joinToString("") { "%02x".format(it) }

            val active = current(files)
            val core = active?.let { File(it, CORE) } ?: File(context.applicationInfo.nativeLibraryDir, CORE)
            if (!core.isFile) return Imported.Bad("The launcher core library was not found.")
            core.copyTo(File(staging, CORE), overwrite = true)
            val data = dataPath(files)
            (data?.inputStream() ?: context.assets.open(DATA)).use { src ->
                File(staging, DATA).outputStream().use { src.copyTo(it) }
            }
            val build = apkBuild(context) + 1
            val name = "$LOCAL${sha.take(8)}"
            File(staging, MANIFEST).writeText(
                JSONObject().put("abi_level", ABI_LEVEL).put("build", build).put("version", name)
                    .put("local", true).put("game_sha256", sha).toString()
            )
            val finalDir = File(root(files), name)
            finalDir.deleteRecursively()
            if (!staging.renameTo(finalDir)) return Imported.Bad("Could not store the build.")
            write(files, "current", name)
            // The newest published Nightly must not replace a build the player made on purpose.
            write(files, "hold", versions(files).firstOrNull { !isLocal(it.name) }?.name)
            prune(files)
            Imported.Ok(name, sha)
        } catch (t: Throwable) {
            staging.deleteRecursively()
            Imported.Bad(t.message ?: t.javaClass.simpleName)
        }
    }

    /** Removes an installed version (a local build, say). If it was the active one, the APK's runtime is used. */
    fun remove(files: File, name: String) {
        if (activeVersion(files) == name) write(files, "current", null)
        File(root(files), name).deleteRecursively()
    }

    /** Keeps the active version and the newest few; the rest go. */
    private fun prune(files: File) {
        val active = activeVersion(files)
        versions(files).filter { !isLocal(it.name) }.drop(KEEP).filter { it.name != active }.forEach { File(root(files), it.name).deleteRecursively() }
        root(files).listFiles { f -> f.name.endsWith(".partial") }?.forEach { it.deleteRecursively() }
    }

    /** Blocking: checks for the newest Nightly and installs it unless it already is. */
    fun checkAndInstall(context: Context): Result {
        return try {
            val files = context.filesDir
            val newest = newest() ?: return Result.UpToDate(maxOf(apkBuild(context), installedBuild(files)))
            val have = versions(files).any { it.name == newest.version }
            val held = read(files, "hold") == newest.version
            if (have || held || newest.version in bad(files)) {
                return Result.UpToDate(maxOf(apkBuild(context), installedBuild(files)))
            }
            install(context, newest.tag, newest.version)
        } catch (t: Throwable) {
            Result.Failed(t.message ?: t.javaClass.simpleName)
        }
    }
}
