package dev.horizon.launcher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.horizon.launcher.core.Native
import dev.horizon.launcher.sdui.SduiHost
import java.io.File

/** The launcher UI loaded from JSON files (assets/ui, overridable from filesDir/ui). Same core init as MainActivity. */
class SduiActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dev.horizon.launcher.data.Updater.prepare(filesDir, this)
        enableEdgeToEdge()
        val home = File(filesDir, "launcher").apply { mkdirs() }
        val gameHome = File(home, "game").apply { mkdirs() }
        runCatching { dev.horizon.launcher.data.Updater.syncEngineData(this, gameHome) }
        runCatching { Native.nativeInit(home.absolutePath) }
        dev.horizon.launcher.data.Updater.armGuard(filesDir)
        setContent { SduiHost(start = intent?.getStringExtra("screen") ?: "home", onExit = { finish() }) }
    }
}
