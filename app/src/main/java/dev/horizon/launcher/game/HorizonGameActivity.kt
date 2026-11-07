package dev.horizon.launcher.game

import android.app.Activity
import android.app.NativeActivity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences
import android.content.Intent
import android.os.Bundle
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.movtery.layer_controller.ControlBoxLayout
import com.movtery.layer_controller.data.HideLayerWhen
import com.movtery.layer_controller.event.ClickEvent
import com.movtery.layer_controller.event.EventHandler
import com.movtery.layer_controller.layout.ControlLayout
import com.movtery.layer_controller.layout.loadLayoutFromFile
import com.movtery.layer_controller.observable.ObservableControlLayout
import com.movtery.zalithlauncher.path.PathManager
import com.movtery.zalithlauncher.setting.AllSettings
import dev.horizon.launcher.game.controls.ControlJson
import dev.horizon.launcher.game.controls.LayoutProfiles
import dev.horizon.launcher.game.controls.LookTuner
import java.io.File
import kotlinx.coroutines.delay
/** Native game surface with Zalith's open-source control canvas above it. */
class HorizonGameActivity : NativeActivity(), LifecycleOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry by lazy { LifecycleRegistry(this) }
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    private val savedStateController by lazy { SavedStateRegistryController.create(this) }
    private val activePadAxes = mutableSetOf<String>()
    private var layoutImportCallback: ((ControlLayout) -> Unit)? = null
    private var pendingExport: ControlLayout? = null
    private var zalithOverlay: ComposeView? = null
    private var activeLayout by mutableStateOf<ObservableControlLayout?>(null)
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    private val currentLayoutFile: File get() = File(File(filesDir, "launcher"), "touch-layout.json")

    override fun onCreate(savedInstanceState: Bundle?) {
        // NativeActivity loads the game inside super.onCreate: tell the boot stub which one.
        dev.horizon.launcher.data.Updater.gamePath(filesDir)?.let {
            runCatching { android.system.Os.setenv("HORIZON_GAME_LIB", it, true) }
        }
        // Prevent the legacy Rust-drawn touch HUD from competing with Zalith LayerController.
        runCatching { android.system.Os.setenv("HORIZON_ANDROID_ZALITH_CONTROLS", "1", true) }
        dev.horizon.launcher.data.Updater.armGuard(filesDir)
        super.onCreate(savedInstanceState)
        runCatching {
            if (android.os.Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 7414)
            startForegroundService(Intent(this, GameKeepAliveService::class.java))
        }
        savedStateController.performAttach()
        savedStateController.performRestore(savedInstanceState)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        // WindowRecomposer resolves its owners from the window decor view tree.
        window.decorView.setViewTreeLifecycleOwner(this)
        window.decorView.setViewTreeSavedStateRegistryOwner(this)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // Sustained-performance mode keeps clocks steady instead of boosting then throttling (MediaTek/Mali SoCs overheat fast).
        runCatching {
            val power = getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
            if (android.os.Build.VERSION.SDK_INT >= 24 && power.isSustainedPerformanceModeSupported) window.setSustainedPerformanceMode(true)
        }
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)

        // Zalith's Compose control canvas is layered over the native game surface.
        gyro = GyroLook(this)
        PathManager.initialize(filesDir)
        AllSettings.initialize(this)
        activeLayout = loadLayout()
        attachZalithControls()
        window.decorView.post {
            val prefs = getSharedPreferences("zalith-touch-settings", MODE_PRIVATE)
            runCatching { GameInput.nativeSetImeAllowed(prefs.getBoolean("auto_keyboard", true)) }
        }
    }

    /**
     * Render the actual Zalith LayerController layout above the native game surface.
     * The LayerController consumes configured buttons/joysticks; unconsumed pointers are
     * forwarded through Horizon's existing native look/menu bridge.
     */
    private fun attachZalithControls() {
        val prefs = getSharedPreferences("zalith-touch-settings", MODE_PRIVATE)
        val overlay = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@HorizonGameActivity)
            setViewTreeSavedStateRegistryOwner(this@HorizonGameActivity)
            setContent {
                var cursorGrabbing by remember { mutableStateOf(runCatching { GameInput.nativeCursorGrabbing() }.getOrDefault(true)) }
                LaunchedEffect(Unit) {
                    while (true) {
                        cursorGrabbing = runCatching { GameInput.nativeCursorGrabbing() }.getOrDefault(cursorGrabbing)
                        delay(120)
                    }
                }
                val handler = remember {
                    EventHandler { event, pressed ->
                        when (event.type) {
                            ClickEvent.Type.Key -> runCatching { GameInput.nativeSendControl(event.key, pressed) }
                            ClickEvent.Type.SendText -> if (pressed) runCatching {
                                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Horizon control text", event.key))
                                GameInput.nativeSendControl("GLFW_KEY_LEFT_CONTROL", true)
                                GameInput.nativeSendControl("GLFW_KEY_V", true)
                                GameInput.nativeSendControl("GLFW_KEY_V", false)
                                GameInput.nativeSendControl("GLFW_KEY_LEFT_CONTROL", false)
                            }
                            else -> Unit
                        }
                    }
                }
                val passthrough = Modifier.fillMaxSize().horizonTouchForwarder(cursorGrabbing, prefs)
                MaterialTheme(colorScheme = androidx.compose.material3.darkColorScheme(
                    primary = Color(0xFF8B9CFF),
                    surface = Color(0xFF171A24),
                    background = Color.Transparent
                )) {
                    Box(Modifier.fillMaxSize()) {
                        val layout = activeLayout
                        if (prefs.getBoolean("touch_enabled", true) && layout != null) {
                            ControlBoxLayout(
                                modifier = Modifier.fillMaxSize(),
                                observedLayout = layout,
                                eventHandler = handler,
                                isCursorGrabbing = cursorGrabbing,
                                checkOccupiedPointers = { false },
                                opacity = { prefs.getFloat("touch_opacity", 0.92f).coerceIn(0.3f, 1f) },
                                markPointerAsMoveOnly = {},
                                onOccupiedPointer = {},
                                onReleasePointer = {},
                                hideLayerWhen = HideLayerWhen.None,
                                isDark = true
                            ) {
                                Box(passthrough)
                            }
                        } else {
                            // Disabling the touch layer must not disable camera look or native menu input.
                            Box(passthrough)
                        }
                        Surface(
                            modifier = Modifier.align(Alignment.TopStart).padding(12.dp).clickable {
                                startActivity(Intent(this@HorizonGameActivity, TouchControlEditorActivity::class.java))
                            },
                            color = Color(0xAA151A2A),
                            contentColor = Color.White,
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Text("EDIT CONTROLS", modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), color = Color.White)
                        }
                    }
                }
            }
        }
        zalithOverlay = overlay
        (window.decorView as? ViewGroup)?.addView(
            overlay,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
    }

    private var gyro: GyroLook? = null

    /** Compose's Zalith layer consumes configured controls and forwards remaining touch deltas via GameInput. */
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean = super.dispatchTouchEvent(ev)

    override fun onStart() { super.onStart(); lifecycleRegistry.currentState = Lifecycle.State.STARTED }
    override fun onResume() { super.onResume(); lifecycleRegistry.currentState = Lifecycle.State.RESUMED; activeLayout = runCatching { loadLayout() }.getOrNull() ?: activeLayout; gyro?.start() }
    override fun onPause() { gyro?.stop(); runCatching { GameInput.nativeLookActive(false) }; lifecycleRegistry.currentState = Lifecycle.State.STARTED; super.onPause() }
    override fun onStop() { lifecycleRegistry.currentState = Lifecycle.State.CREATED; super.onStop() }
    override fun onDestroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        zalithOverlay?.let { (window.decorView as? ViewGroup)?.removeView(it) }
        zalithOverlay = null
        runCatching { GameInput.nativeLookActive(false) }
        super.onDestroy()
        // The game lives in its own process (:game); the native engine cannot start twice in one process.
        stopService(Intent(this, GameKeepAliveService::class.java))
        android.os.Process.killProcess(android.os.Process.myPid())
    }
    override fun onSaveInstanceState(outState: Bundle) {
        savedStateController.performSave(outState)
        super.onSaveInstanceState(outState)
    }

    // ---------------------------------------------------------------------------------------
    // Layout files: current layout, import, export, share, paste
    // ---------------------------------------------------------------------------------------

    private fun saveCurrentLayout(packed: ControlLayout) {
        runCatching {
            val file = currentLayoutFile
            file.parentFile?.mkdirs()
            // Write to a temp file first so a crash mid-save cannot corrupt the layout.
            val temp = File(file.parentFile, "${file.name}.tmp")
            temp.writeText(ControlJson.encode(packed))
            if (!temp.renameTo(file)) { temp.copyTo(file, overwrite = true); temp.delete() }
        }.onFailure { toast("Could not save control layout: ${it.message}") }
    }

    @Suppress("DEPRECATION")
    fun chooseTouchLayout(onImported: (ControlLayout) -> Unit) {
        layoutImportCallback = onImported
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
        }
        startActivityForResult(intent, REQUEST_IMPORT_LAYOUT)
    }

    @Suppress("DEPRECATION")
    fun exportTouchLayout(layout: ControlLayout) {
        pendingExport = layout
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(Intent.EXTRA_TITLE, "horizon-layout.json")
        }
        startActivityForResult(intent, REQUEST_EXPORT_LAYOUT)
    }

    /** Shares the layout JSON as text (no FileProvider needed); the receiver can paste it back in. */
    fun shareTouchLayout(layout: ControlLayout) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Horizon control layout")
            putExtra(Intent.EXTRA_TEXT, ControlJson.encode(layout))
        }
        startActivity(Intent.createChooser(send, "Share control layout"))
    }

    fun copyTouchLayout(layout: ControlLayout) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Horizon control layout", ControlJson.encode(layout)))
        toast("Layout copied to clipboard")
    }

    fun pasteTouchLayout(onImported: (ControlLayout) -> Unit) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString()
        if (text.isNullOrBlank()) { toast("Clipboard is empty"); return }
        runCatching { ControlJson.decode(text) }
            .onSuccess(onImported)
            .onFailure { toast("Clipboard does not contain a control layout: ${it.message}") }
    }

    fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()

    @Suppress("DEPRECATION")
    @Deprecated("NativeActivity uses the platform activity-result callback")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        when (requestCode) {
            REQUEST_IMPORT_LAYOUT -> {
                if (resultCode != Activity.RESULT_OK || uri == null) { layoutImportCallback = null; return }
                runCatching {
                    val target = File(File(filesDir, "launcher"), "imported-touch-layout.json")
                    target.parentFile?.mkdirs()
                    contentResolver.openInputStream(uri).use { input ->
                        requireNotNull(input) { "Could not open selected layout" }.use { source -> target.outputStream().use(source::copyTo) }
                    }
                    loadLayoutFromFile(target)
                }.onSuccess { imported -> layoutImportCallback?.invoke(imported) }
                    .onFailure { toast("Could not import control layout: ${it.message}") }
                layoutImportCallback = null
            }
            REQUEST_EXPORT_LAYOUT -> {
                val layout = pendingExport
                pendingExport = null
                if (resultCode != Activity.RESULT_OK || uri == null || layout == null) return
                runCatching { LayoutProfiles(this).exportTo(uri, layout) }
                    .onSuccess { toast("Layout exported") }
                    .onFailure { toast("Could not export control layout: ${it.message}") }
            }
        }
    }

    // ---------------------------------------------------------------------------------------
    // Hardware gamepad
    // ---------------------------------------------------------------------------------------

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val isPad = event.source and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD ||
            event.source and InputDevice.SOURCE_DPAD == InputDevice.SOURCE_DPAD
        if (!isPad) return super.dispatchKeyEvent(event)
        val key = when (event.keyCode) {
            KeyEvent.KEYCODE_BUTTON_A -> "button_a"
            KeyEvent.KEYCODE_BUTTON_B -> "button_b"
            KeyEvent.KEYCODE_BUTTON_X -> "button_x"
            KeyEvent.KEYCODE_BUTTON_Y -> "button_y"
            KeyEvent.KEYCODE_BUTTON_L1 -> "left_bumper"
            KeyEvent.KEYCODE_BUTTON_R1 -> "right_bumper"
            KeyEvent.KEYCODE_BUTTON_L2 -> "left_trigger"
            KeyEvent.KEYCODE_BUTTON_R2 -> "right_trigger"
            KeyEvent.KEYCODE_BUTTON_THUMBL -> "left_stick"
            KeyEvent.KEYCODE_BUTTON_THUMBR -> "right_stick"
            KeyEvent.KEYCODE_BUTTON_START -> "button_start"
            KeyEvent.KEYCODE_BUTTON_SELECT -> "button_select"
            KeyEvent.KEYCODE_DPAD_UP -> "dpad_up"
            KeyEvent.KEYCODE_DPAD_DOWN -> "dpad_down"
            KeyEvent.KEYCODE_DPAD_LEFT -> "dpad_left"
            KeyEvent.KEYCODE_DPAD_RIGHT -> "dpad_right"
            else -> return super.dispatchKeyEvent(event)
        }
        val binding = getSharedPreferences("zalith-touch-settings", MODE_PRIVATE).getString("pad_$key", PAD_DEFAULTS[key] ?: "")
        if (!binding.isNullOrBlank()) GameInput.nativeSendControl(binding, event.action != KeyEvent.ACTION_UP)
        return true
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        val joystick = event.source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK
        if (!joystick || event.action != MotionEvent.ACTION_MOVE) return super.dispatchGenericMotionEvent(event)
        val prefs = getSharedPreferences("zalith-touch-settings", MODE_PRIVATE)
        val deadzone = prefs.getFloat("pad_deadzone", 0.22f)
        val triggerThreshold = prefs.getFloat("pad_trigger_threshold", 0.22f)
        val sensitivity = prefs.getFloat("pad_camera_sensitivity", 1.4f)
        val invertY = prefs.getBoolean("pad_invert_y", false)
        val swapSticks = prefs.getBoolean("pad_swap_sticks", false)
        fun axis(axis: Int): Float = event.getAxisValue(axis)
        val leftX = axis(MotionEvent.AXIS_X); val leftY = axis(MotionEvent.AXIS_Y)
        val rightX = axis(MotionEvent.AXIS_Z).takeIf { it != 0f } ?: axis(MotionEvent.AXIS_RX)
        val rightY = axis(MotionEvent.AXIS_RZ).takeIf { it != 0f } ?: axis(MotionEvent.AXIS_RY)
        // "Swap sticks" gives left-handed / "legacy" layouts: right stick moves, left stick looks.
        val lx = if (swapSticks) rightX else leftX
        val ly = if (swapSticks) rightY else leftY
        val rx = if (swapSticks) leftX else rightX
        val ry = (if (swapSticks) leftY else rightY) * if (invertY) -1f else 1f
        setPadAxis("GLFW_KEY_A", lx < -deadzone); setPadAxis("GLFW_KEY_D", lx > deadzone)
        setPadAxis("GLFW_KEY_W", ly < -deadzone); setPadAxis("GLFW_KEY_S", ly > deadzone)
        val lt = axis(MotionEvent.AXIS_LTRIGGER).takeIf { it > 0f } ?: axis(MotionEvent.AXIS_BRAKE)
        val rt = axis(MotionEvent.AXIS_RTRIGGER).takeIf { it > 0f } ?: axis(MotionEvent.AXIS_GAS)
        setPadAxis(prefs.getString("pad_left_trigger", "GLFW_MOUSE_BUTTON_LEFT") ?: "", lt > triggerThreshold)
        setPadAxis(prefs.getString("pad_right_trigger", "GLFW_MOUSE_BUTTON_RIGHT") ?: "", rt > triggerThreshold)
        if (kotlin.math.abs(rx) > deadzone || kotlin.math.abs(ry) > deadzone) {
            GameInput.nativeLook(rx * sensitivity * 24f, ry * sensitivity * 24f)
        }
        return true
    }

    private fun setPadAxis(key: String, pressed: Boolean) {
        if (key.isBlank()) return
        val wasPressed = key in activePadAxes
        if (pressed != wasPressed) {
            if (pressed) activePadAxes += key else activePadAxes -= key
            GameInput.nativeSendControl(key, pressed)
        }
    }

    // ---------------------------------------------------------------------------------------
    // Default / saved layout
    // ---------------------------------------------------------------------------------------

    private fun loadLayout(): ObservableControlLayout {
        val file = currentLayoutFile
        val prefs = getSharedPreferences("zalith-touch-settings", MODE_PRIVATE)
        if (!prefs.getBoolean("default_layout_v4", false)) {
            prefs.edit().putBoolean("default_layout_v4", true).apply()
            // Preserve a valid layout already created by the editor or an older Horizon build.
            val existing = runCatching { if (file.isFile) loadLayoutFromFile(file) else null }.getOrNull()
            if (existing != null) {
                runCatching { LayoutProfiles(this).save("Previous layout", existing) }
                return ObservableControlLayout(existing)
            }
            val fresh = defaultLayout()
            saveCurrentLayout(fresh)
            LayoutProfiles(this).activeName = ""
            return ObservableControlLayout(fresh)
        }
        return runCatching { if (file.isFile) ObservableControlLayout(loadLayoutFromFile(file)) else ObservableControlLayout(defaultLayout()) }
            .getOrElse { ObservableControlLayout(defaultLayout()) }
    }

    /**
     * Default touch layout modelled on the Horizon reference screenshot: square glass joystick on the
     * left, sword / use / jump / sprint / sneak on the right, a row of small icon buttons at the top
     * centre, and a "…" button that reveals extra keys. A separate menu layer keeps Back / Inventory /
     * Shift / Drop on screen whenever a GUI (inventory, pause, chat) has the cursor.
     */
    internal fun defaultLayout(): ControlLayout = TouchLayoutDefaults.create()

    companion object {
        private const val REQUEST_IMPORT_LAYOUT = 7412
        private const val REQUEST_EXPORT_LAYOUT = 7413

        /** Default hardware-gamepad bindings; every slot can be rebound in the quick panel. */
        val PAD_DEFAULTS = mapOf(
            "button_a" to "GLFW_KEY_SPACE", "button_b" to "GLFW_KEY_LEFT_SHIFT",
            "button_x" to "GLFW_KEY_E", "button_y" to "GLFW_KEY_Q",
            "left_bumper" to "GLFW_KEY_LEFT_CONTROL", "right_bumper" to "GLFW_MOUSE_BUTTON_RIGHT",
            "left_trigger" to "GLFW_MOUSE_BUTTON_LEFT", "right_trigger" to "GLFW_MOUSE_BUTTON_RIGHT",
            "left_stick" to "GLFW_KEY_LEFT_SHIFT", "right_stick" to "GLFW_MOUSE_BUTTON_MIDDLE",
            "button_start" to "GLFW_KEY_ESCAPE", "button_select" to "GLFW_KEY_TAB",
            "dpad_up" to "GLFW_KEY_W", "dpad_down" to "GLFW_KEY_S",
            "dpad_left" to "GLFW_KEY_A", "dpad_right" to "GLFW_KEY_D"
        )
    }
}


/**
 * Passes unclaimed drags through to the native game camera. Zalith's own controls
 * receive their pointer events first; this modifier is hosted only in the unused
 * portion of the overlay.
 */
private fun Modifier.horizonTouchForwarder(
    cursorGrabbing: Boolean,
    preferences: SharedPreferences
): Modifier = pointerInput(cursorGrabbing, preferences.getFloat("look_sensitivity", 1.0f)) {
    val sensitivity = preferences.getFloat("look_sensitivity", 1.0f).coerceIn(0.1f, 4.0f)
    detectDragGestures(
        onDragStart = { if (cursorGrabbing) GameInput.nativeLookActive(true) },
        onDragEnd = { GameInput.nativeLookActive(false) },
        onDragCancel = { GameInput.nativeLookActive(false) }
    ) { change, dragAmount ->
        if (cursorGrabbing) {
            GameInput.nativeLook(dragAmount.x * sensitivity, dragAmount.y * sensitivity)
            change.consume()
        }
    }
}
