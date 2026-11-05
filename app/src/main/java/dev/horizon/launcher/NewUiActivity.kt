package dev.horizon.launcher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.horizon.launcher.core.Native
import dev.horizon.launcher.ui2.Shell
import java.io.File

/** The new launcher UI (Compose screens arranged by ui/app.json). Same core start-up as MainActivity. */
class NewUiActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dev.horizon.launcher.data.Updater.prepare(filesDir, this)
        enableEdgeToEdge()
        val home = File(filesDir, "launcher").apply { mkdirs() }
        val gameHome = File(home, "game").apply { mkdirs() }
        runCatching { dev.horizon.launcher.data.Updater.syncEngineData(this, gameHome) }
        runCatching { Native.nativeInit(home.absolutePath) }
        dev.horizon.launcher.data.Updater.armGuard(filesDir)
        setContent { Shell(onExit = { finish() }) }
    }
}
