package dev.horizon.launcher

import android.net.Uri
import android.app.AlertDialog
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.horizon.launcher.ui.HorizonLauncherUi
import androidx.activity.result.contract.ActivityResultContracts
import dev.horizon.launcher.core.Native
import dev.horizon.launcher.data.Core
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Temporary launcher shell with no UI toolkit: just enough buttons to install, restore, export and play
 * while the real launcher UI is rebuilt piece by piece. All behaviour lives in [GameSetup].
 */
class MainActivity : ComponentActivity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var statusText by mutableStateOf("")
    private val buttons = mutableListOf<Button>()
    private var busy = false
    private var initError: String? = null

    private val folderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) run("Importing Minecraft folder") { GameSetup.importFolder(this, uri, this::show).let { "Imported $it files." } }
    }
    private val backupPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) run("Restoring backup") { GameSetup.restoreBackup(this, uri, this::show).let { "Restored $it Minecraft resource set(s)." } }
    }
    private val hnmodPicker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) confirmAndCompileHnmods(uris)
    }
    private val compiledBuildPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) confirmAndImportBuild(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dev.horizon.launcher.data.Updater.prepare(filesDir, this)

        val home = File(filesDir, "launcher").apply { mkdirs() }
        val gameHome = File(home, "game").apply { mkdirs() }
        val resourceError = runCatching { dev.horizon.launcher.data.Updater.syncEngineData(this, gameHome) }.exceptionOrNull()?.message
        initError = Native.loadError ?: runCatching { Native.nativeInit(home.absolutePath) }.exceptionOrNull()?.message ?: resourceError
        dev.horizon.launcher.data.Updater.armGuard(filesDir)

        setContent {
            HorizonLauncherUi(
                status = statusText,
                minecraftReady = runCatching { GameSetup.ready() }.getOrDefault(false),
                toolchainReady = dev.horizon.launcher.data.EmbeddedToolchain.status(this).installed,
                onPlay = { GameSetup.play(this).onFailure { show("Could not open the game: " + it.message) } },
                onInstall = { run("Installing Minecraft") { GameSetup.install(this, this::show); "Minecraft is ready." } },
                onImportFolder = { folderPicker.launch(null) },
                onRestoreBackup = { backupPicker.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) },
                onCompileHnmod = { hnmodPicker.launch(arrayOf("application/octet-stream", "application/zip", "*/*")) },
                onImportBuild = { compiledBuildPicker.launch(arrayOf("application/octet-stream", "*/*")) },
                onExportBackup = { run("Exporting backup") { "Saved to " + GameSetup.exportBackup(this, this::show) } },
                onShowBuildLog = { showLiveHnmodLog() }
            )
        }
        refreshStatus()
        startUpdateMonitor()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun show(text: String) { statusText = text }

    private fun confirmAndCompileHnmods(uris: List<Uri>) {
        AlertDialog.Builder(this)
            .setTitle("Compile native mod source?")
            .setMessage(
                "HNMod files contain Rust source. Their build scripts and procedural macros can execute code " +
                    "with Horizon's app permissions during compilation. The local compiler is not sandboxed. " +
                    "Only continue if you trust every selected mod. Like the PC launcher, Horizon will select the newest pinned engine revision; if selected mods target different revisions, this confirmation also authorizes trying that mixed set (which may fail to apply or compile). " +
                    "Horizon manages its own private compiler runtime; no separate Termux app, external command permission, or shared-storage setup is needed."
            )
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Continue") { _, _ ->
                run("Starting HNMod compiler") {
                    dev.horizon.launcher.data.EmbeddedToolchain.start(this, uris, onProgress = { message ->
                        runOnUiThread { show(message) }
                    }, onLogLine = { message ->
                        // The complete line is persisted by HnmodBuildLog; status stays concise.
                        if (message.startsWith("==") || message.startsWith("Compiling") || message.startsWith("ERROR:")) {
                            runOnUiThread { show(message) }
                        }
                    })
                }
            }
            .show()
    }

    private fun confirmAndImportBuild(uri: Uri) {
        AlertDialog.Builder(this)
            .setTitle("Trust this native build?")
            .setMessage(
                "A native mod is compiled into the game library and runs with the game's full access. " +
                    "Only import builds made from mods you trust. The library becomes active on the next game launch."
            )
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Import and activate") { _, _ ->
                run("Importing native build") {
                    val result = withContext(Dispatchers.IO) {
                        contentResolver.openInputStream(uri)?.use {
                            dev.horizon.launcher.data.Updater.importLocal(this@MainActivity, it)
                        } ?: error("Could not open the selected library.")
                    }
                    when (result) {
                        is dev.horizon.launcher.data.Updater.Imported.Ok ->
                            "Build ${result.name} imported and activated. Restart the game to use it."
                        is dev.horizon.launcher.data.Updater.Imported.Bad -> error(result.message)
                    }
                }
            }
            .show()
    }


    /** Opens a live, scrollable view of the complete HNMod build log. */
    private fun showLiveHnmodLog() {
        val logFile = dev.horizon.launcher.data.HnmodBuildLog.file(this)
        val logView = TextView(this).apply {
            textSize = 11f
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(20, 16, 20, 16)
            text = if (logFile.isFile) logFile.readText() else "No HNMod build log yet. Start a compilation first."
        }
        val scroll = ScrollView(this).apply {
            addView(logView)
            setPadding(0, 0, 0, 0)
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("HNMod — live build log")
            .setView(scroll)
            .setPositiveButton("Close", null)
            .create()
        dialog.setOnShowListener {
            scroll.post { scroll.fullScroll(ScrollView.FOCUS_DOWN) }
            scope.launch {
                val content = StringBuilder()
                var offset = 0L
                while (dialog.isShowing) {
                    if (logFile.isFile) {
                        val length = logFile.length()
                        if (length < offset) {
                            offset = 0L
                            content.setLength(0)
                        }
                        if (length > offset) {
                            val count = (length - offset).coerceAtMost(1024L * 1024L).toInt()
                            val bytes = ByteArray(count)
                            val read = runCatching {
                                java.io.RandomAccessFile(logFile, "r").use { input ->
                                    input.seek(offset)
                                    input.read(bytes)
                                }
                            }.getOrDefault(-1)
                            if (read > 0) {
                                val wasAtBottom = scroll.getChildAt(0).let { child ->
                                    child.bottom - (scroll.scrollY + scroll.height) <= 48
                                }
                                content.append(String(bytes, 0, read, Charsets.UTF_8))
                                offset += read
                                logView.text = content.toString()
                                if (wasAtBottom) scroll.post { scroll.fullScroll(ScrollView.FOCUS_DOWN) }
                            }
                        }
                    }
                    delay(500)
                }
            }
        }
        dialog.show()
    }

    private fun refreshStatus() {
        scope.launch {
            val ready = GameSetup.ready()
            val toolchain = dev.horizon.launcher.data.EmbeddedToolchain.status(this@MainActivity)
            val problem = initError?.let { "\nCore problem: $it" } ?: ""
            statusText = "Core ${Core.coreVersion()} · Minecraft ${if (ready) "installed" else "not installed"} · HNMod runtime ${if (toolchain.installed) "ready" else "not installed"}$problem"
        }
    }

    /**
     * Keep the signed Android Nightly updater alive independently of the UI implementation.
     * The previous Compose LaunchedEffect did this at startup and every 30 minutes; moving the
     * launcher shell must not silently remove that lifecycle hook.
     */
    private fun startUpdateMonitor() {
        if (initError != null) return
        scope.launch {
            var announcedAppTag = ""
            while (true) {
                if (dev.horizon.launcher.data.Updater.autoEnabled(applicationContext)) {
                    val result = withContext(Dispatchers.IO) {
                        dev.horizon.launcher.data.Updater.checkAndInstall(applicationContext)
                    }
                    when (result) {
                        is dev.horizon.launcher.data.Updater.Result.Installed ->
                            show("A new Horizon Nightly runtime was downloaded. It will be used on the next game launch.")
                        is dev.horizon.launcher.data.Updater.Result.Failed ->
                            show("Nightly check failed: ${result.message}")
                        is dev.horizon.launcher.data.Updater.Result.UpToDate -> Unit
                    }

                    val appOffer = runCatching {
                        withContext(Dispatchers.IO) {
                            dev.horizon.launcher.data.AppUpdater.check(applicationContext)
                        }
                    }.getOrNull()
                    if (appOffer != null && announcedAppTag != appOffer.tag) {
                        announcedAppTag = appOffer.tag
                        show("A new Horizon app build is available: ${appOffer.tag}.")
                    }
                }
                delay(30 * 60 * 1000L)
            }
        }
    }

    /** Runs one setup action with the buttons locked and the result (or the error) shown as status. */
    private fun run(label: String, action: suspend () -> String) {
        if (busy) return
        busy = true
        buttons.forEach { it.isEnabled = false }
        show("$label…")
        scope.launch {
            val message = try { action() } catch (e: Exception) { e.message ?: "$label failed" }
            busy = false
            buttons.forEach { it.isEnabled = true }
            // Keep the final result visible. refreshStatus() launched asynchronously here could
            // overwrite a compiler/download error immediately after the dialog closes.
            statusText = "$label\n$message"
        }
    }
}
