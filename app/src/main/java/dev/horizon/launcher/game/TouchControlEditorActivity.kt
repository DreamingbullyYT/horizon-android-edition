package dev.horizon.launcher.game

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.Modifier
import com.movtery.layer_controller.layout.ControlLayout
import com.movtery.layer_controller.layout.loadLayoutFromFile
import com.movtery.zalithlauncher.path.PathManager
import com.movtery.zalithlauncher.setting.AllSettings
import com.movtery.zalithlauncher.ui.screens.main.control_editor.ControlEditor
import com.movtery.zalithlauncher.viewmodel.EditorViewModel
import dev.horizon.launcher.game.controls.ControlJson
import java.io.File

/** Full-screen Zalith ControlEditor hosted by Horizon, with Horizon-owned layout persistence. */
class TouchControlEditorActivity : ComponentActivity() {
    private lateinit var layoutFile: File
    private lateinit var editorModel: EditorViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PathManager.initialize(filesDir)
        AllSettings.initialize(this)
        layoutFile = File(File(filesDir, "launcher"), "touch-layout.json").apply {
            parentFile?.mkdirs()
        }
        val initial = runCatching {
            if (layoutFile.isFile) loadLayoutFromFile(layoutFile) else TouchLayoutDefaults.create()
        }.getOrElse { TouchLayoutDefaults.create() }
        if (!layoutFile.isFile) layoutFile.writeText(ControlJson.encode(initial))

        editorModel = EditorViewModel().apply { initLayout(initial) }
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = androidx.compose.ui.graphics.Color(0xFF8B9CFF),
                    background = androidx.compose.ui.graphics.Color(0xFF11131B),
                    surface = androidx.compose.ui.graphics.Color(0xFF1A1D28)
                )
            ) {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    ControlEditor(
                        viewModel = editorModel,
                        targetFile = layoutFile,
                        exit = {
                            setResult(RESULT_OK)
                            finish()
                        },
                        menuExit = {
                            // Save on exit so a back action never silently discards a layout edit.
                            editorModel.save(layoutFile) {
                                setResult(RESULT_OK)
                                finish()
                            }
                        },
                        onSaveLayout = { packed: ControlLayout ->
                            runCatching { layoutFile.writeText(ControlJson.encode(packed)) }
                                .onFailure { android.widget.Toast.makeText(this@TouchControlEditorActivity, "Could not save controls: ${it.message}", android.widget.Toast.LENGTH_LONG).show() }
                            setResult(RESULT_OK)
                        }
                    )
                }
            }
        }
    }
}
