package dev.horizon.launcher.game

import android.app.Activity
import android.app.NativeActivity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Bundle
import android.view.Gravity
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import com.movtery.layer_controller.data.ButtonPosition
import com.movtery.layer_controller.data.DefaultSize
import com.movtery.layer_controller.data.JoystickData
import com.movtery.layer_controller.data.NormalData
import com.movtery.layer_controller.data.VisibilityType
import com.movtery.layer_controller.data.lang.createTranslatable
import com.movtery.layer_controller.event.ClickEvent
import com.movtery.layer_controller.layout.ControlLayout
import com.movtery.layer_controller.layout.createNewLayer
import com.movtery.layer_controller.layout.loadLayoutFromFile
import com.movtery.layer_controller.observable.ObservableControlLayer
import com.movtery.layer_controller.observable.ObservableControlLayout
import dev.horizon.launcher.game.controls.ControlJson
import dev.horizon.launcher.game.controls.LayoutProfiles
import java.io.File
import java.util.UUID

/** Native game surface with Zalith's open-source control canvas above it. */
class HorizonGameActivity : NativeActivity(), LifecycleOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry by lazy { LifecycleRegistry(this) }
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    private val savedStateController by lazy { SavedStateRegistryController.create(this) }
    private val activePadAxes = mutableSetOf<String>()
    private var layoutImportCallback: ((ControlLayout) -> Unit)? = null
    private var pendingExport: ControlLayout? = null
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    private val currentLayoutFile: File get() = File(File(filesDir, "launcher"), "touch-layout.json")

    override fun onCreate(savedInstanceState: Bundle?) {
        // NativeActivity loads the game inside super.onCreate: tell the boot stub which one.
        dev.horizon.launcher.data.Updater.gamePath(filesDir)?.let {
            runCatching { android.system.Os.setenv("HORIZON_GAME_LIB", it, true) }
        }
        dev.horizon.launcher.data.Updater.armGuard(filesDir)
        super.onCreate(savedInstanceState)
        savedStateController.performAttach()
        savedStateController.performRestore(savedInstanceState)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)

        val initialLayout = loadLayout()
        val profiles = LayoutProfiles(this)
        val overlay = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@HorizonGameActivity)
            Class.forName("androidx.savedstate.ViewTreeSavedStateRegistryOwner")
                .getMethod("set", View::class.java, SavedStateRegistryOwner::class.java)
                .invoke(null, this, this@HorizonGameActivity)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                HorizonGameControls(
                    layout = initialLayout,
                    profiles = profiles,
                    defaultLayout = { defaultLayout() },
                    setKeepAwake = { enabled ->
                        if (enabled) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    },
                    setMaxRefresh = { enabled ->
                        val attributes = window.attributes
                        attributes.preferredRefreshRate = if (enabled) windowManager.defaultDisplay.refreshRate else 0f
                        window.attributes = attributes
                    },
                    save = { packed -> saveCurrentLayout(packed) }
                )
            }
        }
        window.decorView.post {
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_PANEL,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                token = window.decorView.windowToken
                gravity = Gravity.TOP or Gravity.START
                title = "Horizon touch controls"
            }
            windowManager.addView(overlay, params)
        }
    }

    override fun onStart() { super.onStart(); lifecycleRegistry.currentState = Lifecycle.State.STARTED }
    override fun onResume() { super.onResume(); lifecycleRegistry.currentState = Lifecycle.State.RESUMED }
    override fun onPause() { lifecycleRegistry.currentState = Lifecycle.State.STARTED; super.onPause() }
    override fun onStop() { lifecycleRegistry.currentState = Lifecycle.State.CREATED; super.onStop() }
    override fun onDestroy() { lifecycleRegistry.currentState = Lifecycle.State.DESTROYED; super.onDestroy() }
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
        return runCatching { if (file.isFile) ObservableControlLayout(loadLayoutFromFile(file)) else ObservableControlLayout(defaultLayout()) }
            .getOrElse { ObservableControlLayout(defaultLayout()) }
    }

    internal fun defaultLayout(): ControlLayout {
        val layer = ObservableControlLayer(createNewLayer("Gameplay").copy(visibilityType = VisibilityType.IN_GAME))
        val size = DefaultSize.copy(widthPercentage = 1150, heightPercentage = 1150)
        fun button(label: String, key: String, x: Int, y: Int) = NormalData(
            text = createTranslatable(label), uuid = UUID.randomUUID().toString(),
            position = ButtonPosition(x, y), buttonSize = size,
            visibilityType = VisibilityType.IN_GAME,
            _clickEvents = listOf(ClickEvent(ClickEvent.Type.Key, key)),
            isSwipple = false, isPenetrable = false, isToggleable = false
        )
        layer.apply {
            addJoystickButton(JoystickData(
                uuid = UUID.randomUUID().toString(),
                position = ButtonPosition(1350, 6600),
                sizePercentage = 2500,
                visibilityType = VisibilityType.IN_GAME
            ))
            addNormalButton(button("Jump", "GLFW_KEY_SPACE", 9000, 6100))
            addNormalButton(button("Sneak", "GLFW_KEY_LEFT_SHIFT", 7800, 8000))
            addNormalButton(button("Sprint", "GLFW_KEY_LEFT_CONTROL", 6650, 6500))
            addNormalButton(button("Attack", "GLFW_MOUSE_BUTTON_LEFT", 8650, 8250))
            addNormalButton(button("Use", "GLFW_MOUSE_BUTTON_RIGHT", 9600, 8250))
        }
        return ControlLayout(
            info = ControlLayout.Info(createTranslatable("Horizon touch"), createTranslatable("Horizon"),
                createTranslatable("Zalith LayerController based touch layout"), 1, "1"),
            layers = listOf(layer.pack()), editorVersion = 12
        )
    }

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
