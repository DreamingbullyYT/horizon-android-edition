package dev.horizon.launcher.game

import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.graphics.PixelFormat
import android.view.Gravity
import android.app.NativeActivity
import android.widget.FrameLayout
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import com.movtery.layer_controller.ControlBoxLayout
import com.movtery.layer_controller.data.HideLayerWhen
import com.movtery.layer_controller.event.ClickEvent
import com.movtery.layer_controller.event.EventHandler
import com.movtery.layer_controller.layout.ControlLayout
import com.movtery.layer_controller.observable.ObservableControlLayout
import com.movtery.layer_controller.observable.ObservableControlLayer
import com.movtery.layer_controller.layout.loadLayoutFromFile
import com.movtery.layer_controller.layout.createNewLayer
import com.movtery.layer_controller.data.JoystickData
import com.movtery.layer_controller.data.NormalData
import com.movtery.layer_controller.data.ButtonPosition
import com.movtery.layer_controller.data.DefaultSize
import com.movtery.layer_controller.data.VisibilityType
import com.movtery.layer_controller.data.lang.createTranslatable
import java.io.File
import java.util.UUID
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Canvas
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import android.app.Activity
import android.media.AudioManager
import android.app.ActivityManager
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.content.Intent
import android.view.inputmethod.InputMethodManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableStateMapOf
import com.movtery.layer_controller.ControlEditorLayer
import com.movtery.layer_controller.observable.ObservableWidget
import com.movtery.layer_controller.observable.ObservableNormalData
import com.movtery.layer_controller.observable.ObservableJoystickData
import com.movtery.layer_controller.utils.snap.SnapMode
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Native game surface with Zalith's open-source control canvas above it. */
class HorizonGameActivity : NativeActivity(), LifecycleOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry by lazy { LifecycleRegistry(this) }
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    private val savedStateController by lazy { SavedStateRegistryController.create(this) }
    private val activePadAxes = mutableSetOf<String>()
    private var layoutImportCallback: ((ControlLayout) -> Unit)? = null
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    override fun onCreate(savedInstanceState: Bundle?) {
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

        val overlay = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@HorizonGameActivity)
            Class.forName("androidx.savedstate.ViewTreeSavedStateRegistryOwner")
                .getMethod("set", View::class.java, SavedStateRegistryOwner::class.java)
                .invoke(null, this, this@HorizonGameActivity)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent { GameControls(loadLayout(),
                setKeepAwake = { enabled ->
                    if (enabled) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                },
                setMaxRefresh = { enabled ->
                    val attributes = window.attributes
                    attributes.preferredRefreshRate = if (enabled) windowManager.defaultDisplay.refreshRate else 0f
                    window.attributes = attributes
                }
            ) { packed ->
                val file = File(File(filesDir, "launcher"), "touch-layout.json")
                file.parentFile?.mkdirs()
                file.writeText(Json { encodeDefaults = true; prettyPrint = true }.encodeToString(ControlLayout.serializer(), packed))
            } }
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

    fun chooseTouchLayout(onImported: (ControlLayout) -> Unit) {
        layoutImportCallback = onImported
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
        }
        startActivityForResult(intent, 7412)
    }

    @Deprecated("NativeActivity uses the platform activity-result callback")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 7412 || resultCode != Activity.RESULT_OK || data?.data == null) return
        runCatching {
            val target = File(File(filesDir, "launcher"), "imported-touch-layout.json")
            target.parentFile?.mkdirs()
            contentResolver.openInputStream(data.data!!).use { input ->
                requireNotNull(input) { "Could not open selected layout" }.use { source -> target.outputStream().use(source::copyTo) }
            }
            loadLayoutFromFile(target)
        }.onSuccess { imported -> layoutImportCallback?.invoke(imported) }
            .onFailure { android.widget.Toast.makeText(this, "Could not import control layout: ${it.message}", android.widget.Toast.LENGTH_LONG).show() }
        layoutImportCallback = null
    }

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
            KeyEvent.KEYCODE_DPAD_UP -> "dpad_up"
            KeyEvent.KEYCODE_DPAD_DOWN -> "dpad_down"
            KeyEvent.KEYCODE_DPAD_LEFT -> "dpad_left"
            KeyEvent.KEYCODE_DPAD_RIGHT -> "dpad_right"
            else -> return super.dispatchKeyEvent(event)
        }
        val fallback = when (key) {
            "button_a" -> "GLFW_KEY_SPACE"; "button_b" -> "GLFW_KEY_LEFT_SHIFT"
            "button_x" -> "GLFW_KEY_E"; "button_y" -> "GLFW_KEY_Q"
            "left_bumper" -> "GLFW_KEY_LEFT_CONTROL"; "right_bumper" -> "GLFW_MOUSE_BUTTON_RIGHT"
            "left_trigger" -> "GLFW_MOUSE_BUTTON_LEFT"; "right_trigger" -> "GLFW_MOUSE_BUTTON_RIGHT"
            "left_stick" -> "GLFW_KEY_LEFT_SHIFT"; "right_stick" -> "GLFW_MOUSE_BUTTON_MIDDLE"
            "dpad_up" -> "GLFW_KEY_W"; "dpad_down" -> "GLFW_KEY_S"
            "dpad_left" -> "GLFW_KEY_A"; else -> "GLFW_KEY_D"
        }
        val binding = getSharedPreferences("zalith-touch-settings", MODE_PRIVATE).getString("pad_$key", fallback)
        if (!binding.isNullOrBlank()) GameInput.nativeSendControl(binding, event.action != KeyEvent.ACTION_UP)
        return true
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        val joystick = event.source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK
        if (!joystick || event.action != MotionEvent.ACTION_MOVE) return super.dispatchGenericMotionEvent(event)
        val prefs = getSharedPreferences("zalith-touch-settings", MODE_PRIVATE)
        val deadzone = prefs.getFloat("pad_deadzone", 0.22f)
        val sensitivity = prefs.getFloat("pad_camera_sensitivity", 1.4f)
        fun axis(axis: Int): Float = event.getAxisValue(axis)
        val lx = axis(MotionEvent.AXIS_X); val ly = axis(MotionEvent.AXIS_Y)
        setPadAxis("GLFW_KEY_A", lx < -deadzone); setPadAxis("GLFW_KEY_D", lx > deadzone)
        setPadAxis("GLFW_KEY_W", ly < -deadzone); setPadAxis("GLFW_KEY_S", ly > deadzone)
        val rx = axis(MotionEvent.AXIS_Z).takeIf { it != 0f } ?: axis(MotionEvent.AXIS_RX)
        val ry = axis(MotionEvent.AXIS_RZ).takeIf { it != 0f } ?: axis(MotionEvent.AXIS_RY)
        val lt = axis(MotionEvent.AXIS_LTRIGGER).takeIf { it > 0f } ?: axis(MotionEvent.AXIS_BRAKE)
        val rt = axis(MotionEvent.AXIS_RTRIGGER).takeIf { it > 0f } ?: axis(MotionEvent.AXIS_GAS)
        setPadAxis(prefs.getString("pad_left_trigger", "GLFW_MOUSE_BUTTON_LEFT") ?: "GLFW_MOUSE_BUTTON_LEFT", lt > deadzone)
        setPadAxis(prefs.getString("pad_right_trigger", "GLFW_MOUSE_BUTTON_RIGHT") ?: "GLFW_MOUSE_BUTTON_RIGHT", rt > deadzone)
        if (kotlin.math.abs(rx) > deadzone || kotlin.math.abs(ry) > deadzone) {
            GameInput.nativeLook(rx * sensitivity * 24f, ry * sensitivity * 24f)
        }
        return true
    }

    private fun setPadAxis(key: String, pressed: Boolean) {
        val wasPressed = key in activePadAxes
        if (pressed != wasPressed) {
            if (pressed) activePadAxes += key else activePadAxes -= key
            GameInput.nativeSendControl(key, pressed)
        }
    }

    private fun loadLayout(): ObservableControlLayout {
        val file = File(File(filesDir, "launcher"), "touch-layout.json")
        return runCatching { if (file.isFile) ObservableControlLayout(loadLayoutFromFile(file)) else ObservableControlLayout(defaultLayout()) }
            .getOrElse { ObservableControlLayout(defaultLayout()) }
    }

    private fun defaultLayout(): ControlLayout {
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
}

@Composable
private fun GameControls(layout: ObservableControlLayout, setKeepAwake: (Boolean) -> Unit, setMaxRefresh: (Boolean) -> Unit, save: (ControlLayout) -> Unit) {
    var grabbing by remember { mutableStateOf(true) }
    var editing by remember { mutableStateOf(false) }
    var quickSettings by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("zalith-touch-settings", android.content.Context.MODE_PRIVATE) }
    var opacity by remember { mutableFloatStateOf(prefs.getFloat("opacity", 0.88f)) }
    var lookSensitivity by remember { mutableFloatStateOf(prefs.getFloat("look_sensitivity", 1f)) }
    var invertLookX by remember { mutableStateOf(prefs.getBoolean("invert_look_x", false)) }
    var invertLookY by remember { mutableStateOf(prefs.getBoolean("invert_look_y", false)) }
    var swipeLook by remember { mutableStateOf(prefs.getBoolean("swipe_look", true)) }
    var gyroEnabled by remember { mutableStateOf(prefs.getBoolean("gyro_enabled", false)) }
    var gyroSensitivity by remember { mutableFloatStateOf(prefs.getFloat("gyro_sensitivity", 1f)) }
    var tapAction by remember { mutableIntStateOf(prefs.getInt("gesture_tap_action", 0)) }
    var longPressAction by remember { mutableIntStateOf(prefs.getInt("gesture_long_action", 0)) }
    var longPressDelay by remember { mutableIntStateOf(prefs.getInt("gesture_long_delay", 500)) }
    var storedHud by remember { mutableStateOf(prefs.getBoolean("hud_visible", true)) }
    var quickTab by remember { mutableIntStateOf(0) }
    val quickScroll = remember(quickTab) { ScrollState(0) }
    var keepAwake by remember { mutableStateOf(prefs.getBoolean("keep_awake", true)) }
    var brightness by remember { mutableFloatStateOf(prefs.getFloat("screen_brightness", -1f)) }
    var showFps by remember { mutableStateOf(prefs.getBoolean("show_fps", false)) }
    var showMemory by remember { mutableStateOf(prefs.getBoolean("show_memory", false)) }
    var maxRefresh by remember { mutableStateOf(prefs.getBoolean("max_refresh", false)) }
    var fpsMode by remember { mutableIntStateOf(prefs.getInt("fps_mode", 0)) }
    var quickButtonOpacity by remember { mutableFloatStateOf(prefs.getFloat("quick_button_opacity", 0.88f)) }
    var autoKeyboard by remember { mutableStateOf(prefs.getBoolean("auto_keyboard", true)) }
    var currentFps by remember { mutableIntStateOf(0) }
    var currentMemoryMb by remember { mutableIntStateOf(0) }
    val fpsHistory = remember { mutableStateListOf<Float>() }
    val padSlots = listOf("button_a", "button_b", "button_x", "button_y", "left_bumper", "right_bumper", "left_trigger", "right_trigger", "left_stick", "right_stick", "dpad_up", "dpad_down", "dpad_left", "dpad_right")
    val padChoices = listOf("GLFW_KEY_SPACE", "GLFW_KEY_LEFT_SHIFT", "GLFW_KEY_LEFT_CONTROL", "GLFW_KEY_E", "GLFW_KEY_Q", "GLFW_KEY_F", "GLFW_KEY_W", "GLFW_KEY_A", "GLFW_KEY_S", "GLFW_KEY_D", "GLFW_KEY_1", "GLFW_KEY_2", "GLFW_KEY_ESCAPE", "GLFW_MOUSE_BUTTON_LEFT", "GLFW_MOUSE_BUTTON_RIGHT", "GLFW_MOUSE_BUTTON_MIDDLE")
    val padDefaults = mapOf("button_a" to "GLFW_KEY_SPACE", "button_b" to "GLFW_KEY_LEFT_SHIFT", "button_x" to "GLFW_KEY_E", "button_y" to "GLFW_KEY_Q", "left_bumper" to "GLFW_KEY_LEFT_CONTROL", "right_bumper" to "GLFW_MOUSE_BUTTON_RIGHT", "left_trigger" to "GLFW_MOUSE_BUTTON_LEFT", "right_trigger" to "GLFW_MOUSE_BUTTON_RIGHT", "left_stick" to "GLFW_KEY_LEFT_SHIFT", "right_stick" to "GLFW_MOUSE_BUTTON_MIDDLE", "dpad_up" to "GLFW_KEY_W", "dpad_down" to "GLFW_KEY_S", "dpad_left" to "GLFW_KEY_A", "dpad_right" to "GLFW_KEY_D")
    val padMappings = remember { mutableStateMapOf<String, String>().apply { padSlots.forEach { slot -> this[slot] = prefs.getString("pad_$slot", padDefaults[slot]) ?: "" } } }
    var padDeadzone by remember { mutableFloatStateOf(prefs.getFloat("pad_deadzone", 0.22f)) }
    var padCameraSensitivity by remember { mutableFloatStateOf(prefs.getFloat("pad_camera_sensitivity", 1.4f)) }
    var selected by remember { mutableStateOf<ObservableWidget?>(null) }
    var activeLayout by remember { mutableStateOf(layout) }
    var selectedLayer by remember(activeLayout) { mutableStateOf(activeLayout.layers.value.firstOrNull()) }
    val occupied = remember { mutableSetOf<Long>() }
    LaunchedEffect(Unit) {
        while (true) {
            grabbing = runCatching { GameInput.nativeCursorGrabbing() }.getOrDefault(true)
            kotlinx.coroutines.delay(150)
        }
    }
    LaunchedEffect(showFps, showMemory) {
        val manager = context.getSystemService(android.content.Context.ACTIVITY_SERVICE) as ActivityManager
        while (showFps || showMemory) {
            if (showFps) {
                currentFps = runCatching { GameInput.nativeGameFps() }.getOrDefault(0)
                fpsHistory.add(currentFps.toFloat())
                while (fpsHistory.size > 30) fpsHistory.removeAt(0)
            }
            if (showMemory) currentMemoryMb = runCatching {
                manager.getProcessMemoryInfo(intArrayOf(android.os.Process.myPid())).firstOrNull()?.totalPss?.div(1024) ?: 0
            }.getOrDefault(0)
            kotlinx.coroutines.delay(1000)
        }
    }
    LaunchedEffect(keepAwake) { setKeepAwake(keepAwake) }
    LaunchedEffect(maxRefresh) { setMaxRefresh(maxRefresh) }
    LaunchedEffect(brightness) {
        (context as? Activity)?.window?.let { w ->
            val attributes = w.attributes
            attributes.screenBrightness = if (brightness < 0f) WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE else brightness
            w.attributes = attributes
        }
    }
    LaunchedEffect(autoKeyboard) { GameInput.nativeSetImeAllowed(autoKeyboard) }
    LaunchedEffect(gyroEnabled) { GameInput.nativeLookActive(gyroEnabled) }
    DisposableEffect(gyroEnabled, gyroSensitivity, invertLookX, invertLookY) {
        val manager = context.getSystemService(android.content.Context.SENSOR_SERVICE) as SensorManager
        val sensor = manager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        var lastTimestamp = 0L
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (!gyroEnabled || lastTimestamp == 0L) { lastTimestamp = event.timestamp; return }
                val dt = ((event.timestamp - lastTimestamp) / 1_000_000_000f).coerceIn(0f, 0.05f)
                lastTimestamp = event.timestamp
                val rotation = (context as? Activity)?.windowManager?.defaultDisplay?.rotation ?: Surface.ROTATION_0
                val (x, y) = when (rotation) {
                    Surface.ROTATION_90 -> event.values[1] to -event.values[0]
                    Surface.ROTATION_180 -> -event.values[0] to -event.values[1]
                    Surface.ROTATION_270 -> -event.values[1] to event.values[0]
                    else -> event.values[0] to event.values[1]
                }
                GameInput.nativeLookActive(true)
                GameInput.nativeLook(y * dt * gyroSensitivity * 900f * if (invertLookX) -1f else 1f,
                    x * dt * gyroSensitivity * 900f * if (invertLookY) -1f else 1f)
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        if (gyroEnabled && sensor != null) manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        onDispose { manager.unregisterListener(listener) }
    }
    val handler = remember {
        EventHandler { event, pressed ->
            if (event.type == ClickEvent.Type.Key) GameInput.nativeSendControl(event.key, pressed)
        }
    }
    MaterialTheme {
    Box(
        Modifier.fillMaxSize().pointerInput(Unit) {
            awaitPointerEventScope {
                val positions = mutableMapOf<Long, androidx.compose.ui.geometry.Offset>()
                val starts = mutableMapOf<Long, androidx.compose.ui.geometry.Offset>()
                val downAt = mutableMapOf<Long, Long>()
                val lookPointers = mutableSetOf<Long>()
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Final)
                    event.changes.forEach { change ->
                        val id = change.id.value
                        if (change.pressed && !change.isConsumed && id !in occupied) {
                            val prior = positions.put(id, change.position)
                            if (prior == null) {
                                starts[id] = change.position
                                downAt[id] = android.os.SystemClock.elapsedRealtime()
                            }
                            if (grabbing && swipeLook) {
                                if (prior == null) {
                                    lookPointers += id
                                    GameInput.nativeLookActive(true)
                                }
                                else GameInput.nativeLook(
                                    (change.position.x - prior.x) * lookSensitivity * if (invertLookX) -1f else 1f,
                                    (change.position.y - prior.y) * lookSensitivity * if (invertLookY) -1f else 1f
                                )
                            } else {
                                GameInput.nativeMenuTouch(id, change.position.x, change.position.y, if (prior == null) 0 else 1)
                            }
                        } else if (!change.pressed && positions.remove(id) != null) {
                            occupied.remove(id)
                            val start = starts.remove(id)
                            val duration = android.os.SystemClock.elapsedRealtime() - (downAt.remove(id) ?: 0L)
                            if (lookPointers.remove(id)) {
                                GameInput.nativeLookActive(lookPointers.isNotEmpty())
                                val moved = start?.let { (it - change.position).getDistance() > 24f } ?: true
                                val action = if (duration >= longPressDelay) longPressAction else tapAction
                                if (!moved && action in 1..2) {
                                    val button = if (action == 1) "GLFW_MOUSE_BUTTON_LEFT" else "GLFW_MOUSE_BUTTON_RIGHT"
                                    GameInput.nativeSendControl(button, true)
                                    GameInput.nativeSendControl(button, false)
                                }
                            } else {
                                GameInput.nativeMenuTouch(id, change.position.x, change.position.y, 2)
                            }
                        }
                    }
                }
            }
        }
    ) {
        if (editing) {
            ControlEditorLayer(
                observedLayout = activeLayout,
                selectedWidget = selected,
                onButtonTap = { data, layer -> selected = data; selectedLayer = layer },
                onBackgroundClick = { selected = null; selectedLayer = null },
                floatingButtons = {}, enableSnap = true, snapInAllLayers = true,
                snapMode = SnapMode.FullScreen, isDark = true
            )
            Row(
                Modifier.align(Alignment.TopCenter).padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = { selectedLayer?.let { layer ->
                    val p = ButtonPosition(5000, 5000)
                    layer.addNormalButton(NormalData(createTranslatable("Key"), UUID.randomUUID().toString(), p,
                        DefaultSize, visibilityType = VisibilityType.IN_GAME,
                        _clickEvents = listOf(ClickEvent(ClickEvent.Type.Key, "GLFW_KEY_E")),
                        isSwipple = false, isPenetrable = false, isToggleable = false))
                } }) { Text("+ Button") }
                Button(onClick = { selectedLayer?.let { layer -> layer.addJoystickButton(JoystickData(
                    uuid = UUID.randomUUID().toString(), position = ButtonPosition(5000, 5000), sizePercentage = 2200
                )) } }) { Text("+ Stick") }
                Button(onClick = { selected?.let { widget -> selectedLayer?.let { layer ->
                    when (widget) {
                        is ObservableNormalData -> layer.removeNormalButton(widget.uuid)
                        is ObservableJoystickData -> layer.removeJoystickButton(widget.uuid)
                    }
                } }; selected = null; selectedLayer = null }) { Text("Delete") }
                Button(onClick = { save(activeLayout.pack()); editing = false; selected = null; selectedLayer = null }) { Text("Save / Done") }
            }
        } else {
            if (storedHud) ControlBoxLayout(
                modifier = Modifier.fillMaxSize(), observedLayout = activeLayout, eventHandler = handler,
                isCursorGrabbing = grabbing,
                checkOccupiedPointers = { it.value in occupied },
                onOccupiedPointer = { occupied += it.value },
                onReleasePointer = { occupied -= it.value },
                opacity = { opacity }, hideLayerWhen = HideLayerWhen.None,
                isDark = true
            ) {}
            Button(onClick = { quickSettings = !quickSettings }, modifier = Modifier.align(Alignment.TopEnd).padding(12.dp).graphicsLayer { alpha = quickButtonOpacity }) {
                Text("⚙")
            }
            if (showFps || showMemory) {
                Column(Modifier.align(Alignment.TopStart).padding(12.dp)) {
                    if (showFps && fpsMode == 0) Text("$currentFps FPS", color = androidx.compose.ui.graphics.Color.White)
                    if (showFps && fpsMode == 1) Canvas(Modifier.width(120.dp).height(42.dp)) {
                        if (fpsHistory.size > 1) {
                            val maxFps = (fpsHistory.maxOrNull() ?: 1f).coerceAtLeast(1f)
                            val points = fpsHistory.mapIndexed { i, value ->
                                androidx.compose.ui.geometry.Offset(size.width * i / (fpsHistory.size - 1), size.height * (1f - value / maxFps))
                            }
                            points.zipWithNext().forEach { (a, b) -> drawLine(androidx.compose.ui.graphics.Color.Green, a, b, strokeWidth = 3f) }
                        }
                    }
                    if (showMemory) Text("${currentMemoryMb} MB", color = androidx.compose.ui.graphics.Color.White)
                }
            }
            if (quickSettings) {
                Surface(
                    modifier = Modifier.align(Alignment.TopEnd).padding(top = 72.dp, end = 12.dp)
                        .width(340.dp).fillMaxHeight(0.82f),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                    shape = RoundedCornerShape(16.dp), tonalElevation = 8.dp
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Horizon quick settings", style = MaterialTheme.typography.titleMedium)
                        val tabs = listOf("Controls", "Mouse", "Gamepad", "Gestures", "Gyro", "System")
                        ScrollableTabRow(selectedTabIndex = quickTab, edgePadding = 0.dp) {
                            tabs.forEachIndexed { i, label ->
                                Tab(selected = quickTab == i, onClick = { quickTab = i }, text = { Text(label) })
                            }
                        }
                        Column(Modifier.weight(1f).verticalScroll(quickScroll), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            when (quickTab) {
                                0 -> {
                                    Text("On-screen controls", style = MaterialTheme.typography.titleSmall)
                                    SettingSwitch("Show touch HUD", storedHud) {
                                        storedHud = it; prefs.edit().putBoolean("hud_visible", it).apply()
                                    }
                                    Text("Control opacity · ${(opacity * 100).toInt()}%")
                                    Slider(value = opacity, onValueChange = {
                                        opacity = it; prefs.edit().putFloat("opacity", it).apply()
                                    }, valueRange = 0.2f..1f)
                                    Button(onClick = { quickSettings = false; editing = true }) { Text("Customize controls") }
                                    Button(onClick = {
                                        (context as? HorizonGameActivity)?.chooseTouchLayout { imported ->
                                            activeLayout = ObservableControlLayout(imported)
                                            save(imported)
                                            selected = null
                                            selectedLayer = activeLayout.layers.value.firstOrNull()
                                        }
                                    }) { Text("Choose / replace layout") }
                                    SettingSwitch("Auto-show keyboard in text fields", autoKeyboard) {
                                        autoKeyboard = it; prefs.edit().putBoolean("auto_keyboard", it).apply()
                                    }
                                    Button(onClick = {
                                        (context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                                            .showInputMethodPicker()
                                    }) { Text("Choose Android keyboard") }
                                    Text("Your layout is saved on this device.", style = MaterialTheme.typography.bodySmall)
                                    Text("Quick button opacity · ${(quickButtonOpacity * 100).toInt()}%")
                                    Slider(value = quickButtonOpacity, onValueChange = {
                                        quickButtonOpacity = it; prefs.edit().putFloat("quick_button_opacity", it).apply()
                                    }, valueRange = 0.25f..1f)
                                }
                                1 -> {
                                    Text("Touch mouse / camera", style = MaterialTheme.typography.titleSmall)
                                    SettingSwitch("Swipe to look", swipeLook) {
                                        swipeLook = it; prefs.edit().putBoolean("swipe_look", it).apply()
                                    }
                                    Text("Look sensitivity · ${"%.1f".format(lookSensitivity)}×")
                                    Slider(value = lookSensitivity, onValueChange = {
                                        lookSensitivity = it; prefs.edit().putFloat("look_sensitivity", it).apply()
                                    }, valueRange = 0.25f..3f)
                                    SettingSwitch("Invert horizontal look", invertLookX) {
                                        invertLookX = it; prefs.edit().putBoolean("invert_look_x", it).apply()
                                    }
                                    SettingSwitch("Invert vertical look", invertLookY) {
                                        invertLookY = it; prefs.edit().putBoolean("invert_look_y", it).apply()
                                    }
                                    Text("Drag empty screen space while the game captures the pointer. Touch controls stay clickable.", style = MaterialTheme.typography.bodySmall)
                                }
                                2 -> {
                                    Text("Controller input", style = MaterialTheme.typography.titleSmall)
                                    val pads = remember {
                                        buildList {
                                            for (deviceId in InputDevice.getDeviceIds()) {
                                                val device = InputDevice.getDevice(deviceId) ?: continue
                                                val gamepad = device.sources.and(InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
                                                val joystick = device.sources.and(InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
                                                if (!device.isVirtual && (gamepad || joystick)) add(device.name)
                                            }
                                        }
                                    }
                                    Text(if (pads.isEmpty()) "No game controller detected. Connect a Bluetooth or USB controller." else "Connected: ${pads.joinToString()}")
                                    Text("Tap a binding to cycle through supported keyboard and mouse outputs. Changes save immediately.", style = MaterialTheme.typography.bodySmall)
                                    padSlots.forEach { slot ->
                                        val title = slot.replace('_', ' ').replaceFirstChar { it.uppercase() }
                                        val binding = padMappings[slot] ?: ""
                                        Button(onClick = {
                                            val choices = padChoices + ""
                                            val next = (choices.indexOf(binding) + 1) % choices.size
                                            val value = choices[next]
                                            padMappings[slot] = value
                                            prefs.edit().putString("pad_$slot", value).apply()
                                        }) { Text("$title → ${binding.ifBlank { "None" }}") }
                                    }
                                    Text("Stick deadzone · ${(padDeadzone * 100).toInt()}%")
                                    Slider(value = padDeadzone, onValueChange = { padDeadzone = it }, onValueChangeFinished = {
                                        prefs.edit().putFloat("pad_deadzone", padDeadzone).apply()
                                    }, valueRange = 0.05f..0.6f)
                                    Text("Camera sensitivity · ${"%.1f".format(padCameraSensitivity)}×")
                                    Slider(value = padCameraSensitivity, onValueChange = { padCameraSensitivity = it }, onValueChangeFinished = {
                                        prefs.edit().putFloat("pad_camera_sensitivity", padCameraSensitivity).apply()
                                    }, valueRange = 0.2f..4f)
                                }
                                3 -> {
                                    Text("Touch gestures and scrolling", style = MaterialTheme.typography.titleSmall)
                                    Button(onClick = { tapAction = (tapAction + 1) % 3; prefs.edit().putInt("gesture_tap_action", tapAction).apply() }) {
                                        Text("Tap: ${listOf("None", "Left click", "Right click")[tapAction]}")
                                    }
                                    Button(onClick = { longPressAction = (longPressAction + 1) % 3; prefs.edit().putInt("gesture_long_action", longPressAction).apply() }) {
                                        Text("Long press: ${listOf("None", "Left click", "Right click")[longPressAction]}")
                                    }
                                    Text("Long press threshold · ${longPressDelay} ms")
                                    Slider(value = longPressDelay.toFloat(), onValueChange = { longPressDelay = it.toInt() },
                                        onValueChangeFinished = { prefs.edit().putInt("gesture_long_delay", longPressDelay).apply() },
                                        valueRange = 250f..1200f)
                                    Text("When the game is not capturing the pointer, taps and drags outside the HUD are sent to the game for menus.", style = MaterialTheme.typography.bodySmall)
                                    Text("Menu swipes become native scroll-wheel events in Horizon's Rust input bridge. Touch controls consume their own pointer so they do not click through.", style = MaterialTheme.typography.bodySmall)
                                }
                                4 -> {
                                    val manager = context.getSystemService(android.content.Context.SENSOR_SERVICE) as SensorManager
                                    val gyroAvailable = manager.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null
                                    Text("Gyroscope camera", style = MaterialTheme.typography.titleSmall)
                                    SettingSwitch("Enable gyro look", gyroEnabled && gyroAvailable) {
                                        gyroEnabled = it; prefs.edit().putBoolean("gyro_enabled", it).apply()
                                    }
                                    Text(if (gyroAvailable) "Move the phone to look while the game has captured the pointer." else "This device has no gyroscope sensor.", style = MaterialTheme.typography.bodySmall)
                                    Text("Gyro sensitivity · ${"%.1f".format(gyroSensitivity)}×")
                                    Slider(value = gyroSensitivity, onValueChange = {
                                        gyroSensitivity = it; prefs.edit().putFloat("gyro_sensitivity", it).apply()
                                    }, valueRange = 0.2f..3f, enabled = gyroAvailable)
                                }
                                else -> {
                                    Text("Device and session", style = MaterialTheme.typography.titleSmall)
                                    val audio = context.getSystemService(android.content.Context.AUDIO_SERVICE) as AudioManager
                                    var volume by remember { mutableFloatStateOf(audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()) }
                                    Text("Media volume · ${volume.toInt()}/${audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)}")
                                    Slider(value = volume, onValueChange = { volume = it }, onValueChangeFinished = {
                                        audio.setStreamVolume(AudioManager.STREAM_MUSIC, volume.toInt(), 0)
                                    }, valueRange = 0f..audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat())
                                    SettingSwitch("Keep screen awake", keepAwake) {
                                        keepAwake = it; prefs.edit().putBoolean("keep_awake", it).apply(); setKeepAwake(it)
                                    }
                                    Text("Screen brightness · ${if (brightness < 0f) "system" else "${(brightness * 100).toInt()}%"}")
                                    Slider(value = if (brightness < 0f) 0.5f else brightness, onValueChange = { value ->
                                        brightness = value
                                        (context as? Activity)?.window?.let { w ->
                                            val a = w.attributes; a.screenBrightness = value; w.attributes = a
                                        }
                                        prefs.edit().putFloat("screen_brightness", value).apply()
                                    }, valueRange = 0.05f..1f)
                                    Button(onClick = {
                                        brightness = -1f
                                        (context as? Activity)?.window?.let { w ->
                                            val a = w.attributes; a.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE; w.attributes = a
                                        }
                                        prefs.edit().putFloat("screen_brightness", -1f).apply()
                                    }) { Text("Use system brightness") }
                                    SettingSwitch("Show FPS", showFps) {
                                        showFps = it; prefs.edit().putBoolean("show_fps", it).apply()
                                    }
                                    SettingSwitch("Show process memory", showMemory) {
                                        showMemory = it; prefs.edit().putBoolean("show_memory", it).apply()
                                    }
                                    Button(onClick = { fpsMode = (fpsMode + 1) % 2; prefs.edit().putInt("fps_mode", fpsMode).apply() }) {
                                        Text("FPS display: ${if (fpsMode == 0) "Number" else "Chart"}")
                                    }
                                    SettingSwitch("Request maximum refresh rate", maxRefresh) {
                                        maxRefresh = it; prefs.edit().putBoolean("max_refresh", it).apply(); setMaxRefresh(it)
                                    }
                                    Button(onClick = { (context as? Activity)?.finish() }) { Text("Close game") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}
