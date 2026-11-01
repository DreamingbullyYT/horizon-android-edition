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
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        val home = File(filesDir, "launcher").apply { mkdirs() }
        val gameHome = File(home, "game").apply { mkdirs() }
        val bundledData = File(gameHome, "engine-data.zip")
        val resourceError = runCatching {
            if (!bundledData.isFile) assets.open("engine-data.zip").use { input -> bundledData.outputStream().use(input::copyTo) }
        }.exceptionOrNull()?.message
        val initError = runCatching { Native.nativeInit(home.absolutePath) }
            .exceptionOrNull()
            ?.message ?: resourceError
        val error = Native.loadError ?: initError
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
