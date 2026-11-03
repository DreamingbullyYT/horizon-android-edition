package dev.horizon.launcher.data

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.os.Build
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Updates the whole app (APK) from the rolling `libs` release. CI publishes the APK next to the
 * native libraries and lists it in manifest.json under "apk" (file + sha256). The download is
 * verified, then handed to Android's PackageInstaller, which asks the player to confirm. Android
 * only installs it over the current app when both are signed with the same key (CI signs every
 * build with the project's fixed key).
 */
object AppUpdater {
    private const val BASE = "https://github.com/DreamingbullyYT/horizon-android-edition/releases/download/libs"
    private const val ACTION = "dev.horizon.launcher.APP_UPDATE_RESULT"

    data class Offer(val build: Long, val file: String, val sha256: String, val upstream: String)

    private fun currentBuild(context: Context): Long {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        @Suppress("DEPRECATION")
        return if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
    }

    private fun open(url: String) = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 20_000
        readTimeout = 60_000
        instanceFollowRedirects = true
    }

    /** Blocking. Returns the newer APK on offer, or null when this app is current (or none is published). */
    fun check(context: Context): Offer? {
        val c = open("$BASE/manifest.json")
        if (c.responseCode != 200) error("HTTP ${c.responseCode} for manifest.json")
        val manifest = JSONObject(c.inputStream.use { it.readBytes().decodeToString() })
        val apk = manifest.optJSONObject("apk") ?: return null
        val build = manifest.optLong("build", 0)
        if (build <= currentBuild(context)) return null
        return Offer(build, apk.getString("file"), apk.getString("sha256"), manifest.optString("upstream"))
    }

    /** Blocking: downloads and verifies the APK. [onProgress] gets 0..1. */
    fun download(context: Context, offer: Offer, onProgress: (Float) -> Unit): File {
        val dir = File(context.cacheDir, "app-update").apply { deleteRecursively(); mkdirs() }
        val out = File(dir, "update.apk")
        val c = open("$BASE/${offer.file}")
        if (c.responseCode != 200) error("HTTP ${c.responseCode} for ${offer.file}")
        val total = c.contentLengthLong
        val digest = MessageDigest.getInstance("SHA-256")
        c.inputStream.use { input ->
            out.outputStream().use { sink ->
                val buffer = ByteArray(65536)
                var done = 0L
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    sink.write(buffer, 0, n)
                    digest.update(buffer, 0, n)
                    done += n
                    if (total > 0) onProgress(done.toFloat() / total)
                }
            }
        }
        val sum = digest.digest().joinToString("") { "%02x".format(it) }
        if (!sum.equals(offer.sha256, ignoreCase = true)) {
            out.delete()
            error("The downloaded update is corrupted (checksum mismatch)")
        }
        return out
    }

    /** True when Android lets this app install packages; otherwise the caller opens the settings page. */
    fun canInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < 26 || context.packageManager.canRequestPackageInstalls()

    fun settingsIntent(context: Context): Intent =
        Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, android.net.Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Hands the APK to the system installer; Android shows its own confirmation. */
    fun install(context: Context, apk: File) {
        val app = context.applicationContext
        val installer = app.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        val id = installer.createSession(params)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)) {
                    PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                        @Suppress("DEPRECATION")
                        val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                        confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)?.let { c.startActivity(it) }
                    }
                    else -> runCatching { app.unregisterReceiver(this) }
                }
            }
        }
        ContextCompat.registerReceiver(app, receiver, IntentFilter(ACTION), ContextCompat.RECEIVER_NOT_EXPORTED)
        installer.openSession(id).use { session ->
            apk.inputStream().use { input ->
                session.openWrite("horizon.apk", 0, apk.length()).use { sink ->
                    input.copyTo(sink)
                    session.fsync(sink)
                }
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
            val pending = PendingIntent.getBroadcast(app, id, Intent(ACTION).setPackage(app.packageName), flags)
            session.commit(pending.intentSender)
        }
    }
}
