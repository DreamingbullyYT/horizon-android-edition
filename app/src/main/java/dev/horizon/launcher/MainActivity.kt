package dev.horizon.launcher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import dev.horizon.launcher.core.Native
import dev.horizon.launcher.ui.HorizonApp
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dev.horizon.launcher.data.Updater.prepare(filesDir, this)
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        val home = File(filesDir, "launcher").apply { mkdirs() }
        val gameHome = File(home, "game").apply { mkdirs() }
        val resourceError = runCatching {
            dev.horizon.launcher.data.Updater.syncEngineData(this, gameHome)
        }.exceptionOrNull()?.message
        val initError = runCatching { Native.nativeInit(home.absolutePath) }
            .exceptionOrNull()
            ?.message ?: resourceError
        val error = Native.loadError ?: initError
        dev.horizon.launcher.data.Updater.armGuard(filesDir)
        val start = intent?.getStringExtra("screen") ?: "home"

        setContent {
            HorizonApp(
                initialScreen = start,
                homeDir = home.absolutePath,
                loadError = error,
            )
        }
    }
}
