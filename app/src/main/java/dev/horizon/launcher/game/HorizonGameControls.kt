package dev.horizon.launcher.game

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioManager
import android.os.SystemClock
import android.view.InputDevice
import android.view.Surface
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.movtery.layer_controller.ControlBoxLayout
import com.movtery.layer_controller.ControlEditorLayer
import com.movtery.layer_controller.data.ButtonPosition
import com.movtery.layer_controller.data.DefaultSize
import com.movtery.layer_controller.data.HideLayerWhen
import com.movtery.layer_controller.data.JoystickData
import com.movtery.layer_controller.data.NormalData
import com.movtery.layer_controller.data.TextData
import com.movtery.layer_controller.data.VisibilityType
import com.movtery.layer_controller.data.cloneNew
import com.movtery.layer_controller.data.lang.createTranslatable
import com.movtery.layer_controller.event.ClickEvent
import com.movtery.layer_controller.event.EventHandler
import com.movtery.layer_controller.layout.ControlLayout
import com.movtery.layer_controller.observable.ObservableControlLayout
import com.movtery.layer_controller.observable.ObservableJoystickData
import com.movtery.layer_controller.observable.ObservableNormalData
import com.movtery.layer_controller.observable.ObservableTextData
import com.movtery.layer_controller.observable.ObservableWidget
import com.movtery.layer_controller.observable.cloneNormal
import com.movtery.layer_controller.utils.snap.SnapMode
import dev.horizon.launcher.game.controls.ChipChoice
import dev.horizon.launcher.game.controls.ControlHistory
import dev.horizon.launcher.game.controls.Haptics
import dev.horizon.launcher.game.controls.KeyCatalog
import dev.horizon.launcher.game.controls.KeyPickerDialog
import dev.horizon.launcher.game.controls.LabeledSlider
import dev.horizon.launcher.game.controls.LayerPanel
import dev.horizon.launcher.game.controls.LayoutProfiles
import dev.horizon.launcher.game.controls.LookTuner
import dev.horizon.launcher.game.controls.ProfilesPanel
import dev.horizon.launcher.game.controls.StylesPanel
import dev.horizon.launcher.game.controls.ToggleRow
import dev.horizon.launcher.game.controls.WidgetInspector
import java.util.UUID

private val EDITOR_TABS = listOf("Control", "Layers", "Styles", "Layouts")
private val QUICK_TABS = listOf("Controls", "Mouse", "Gamepad", "Gestures", "Gyro", "System")

@Composable
internal fun HorizonGameControls(
    layout: ObservableControlLayout,
    profiles: LayoutProfiles,
    defaultLayout: () -> ControlLayout,
    setKeepAwake: (Boolean) -> Unit,
    setMaxRefresh: (Boolean) -> Unit,
    save: (ControlLayout) -> Unit
) {
    val context = LocalContext.current
    val activity = context as? HorizonGameActivity
    val view = LocalView.current
    val prefs = remember { context.getSharedPreferences("zalith-touch-settings", Context.MODE_PRIVATE) }

    var grabbing by remember { mutableStateOf(true) }
    var editing by remember { mutableStateOf(false) }
    var quickSettings by remember { mutableStateOf(false) }

    // ---- touch HUD / look settings -------------------------------------------------------
    var opacity by remember { mutableFloatStateOf(prefs.getFloat("opacity", 0.88f)) }
    var lookSensitivity by remember { mutableFloatStateOf(prefs.getFloat("look_sensitivity", 1f)) }
    var lookSensX by remember { mutableFloatStateOf(prefs.getFloat("look_sens_x", 1f)) }
    var lookSensY by remember { mutableFloatStateOf(prefs.getFloat("look_sens_y", 1f)) }
    var lookAccel by remember { mutableFloatStateOf(prefs.getFloat("look_accel", 0f)) }
    var lookSmoothing by remember { mutableFloatStateOf(prefs.getFloat("look_smoothing", 0f)) }
    var lookArea by remember { mutableIntStateOf(prefs.getInt("look_area", 0)) }
    var invertLookX by remember { mutableStateOf(prefs.getBoolean("invert_look_x", false)) }
    var invertLookY by remember { mutableStateOf(prefs.getBoolean("invert_look_y", false)) }
    var swipeLook by remember { mutableStateOf(prefs.getBoolean("swipe_look", true)) }
    var gyroEnabled by remember { mutableStateOf(prefs.getBoolean("gyro_enabled", false)) }
    var gyroSensitivity by remember { mutableFloatStateOf(prefs.getFloat("gyro_sensitivity", 1f)) }
    var tapAction by remember { mutableIntStateOf(prefs.getInt("gesture_tap_action", 0)) }
    var longPressAction by remember { mutableIntStateOf(prefs.getInt("gesture_long_action", 0)) }
    var longPressDelay by remember { mutableIntStateOf(prefs.getInt("gesture_long_delay", 500)) }
    var storedHud by remember { mutableStateOf(prefs.getBoolean("hud_visible", true)) }
    var hapticLevel by remember { mutableIntStateOf(prefs.getInt("haptic_level", 0)) }
    var autoHideForPad by remember { mutableStateOf(prefs.getBoolean("hide_hud_for_gamepad", false)) }
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
    var hotbarTouch by remember { mutableStateOf(prefs.getBoolean("hotbar_touch", true)) }
    var currentFps by remember { mutableIntStateOf(0) }
    var currentMemoryMb by remember { mutableIntStateOf(0) }
    val fpsHistory = remember { mutableStateListOf<Float>() }

    // ---- gamepad ------------------------------------------------------------------------
    val padSlots = remember { HorizonGameActivity.PAD_DEFAULTS.keys.toList() }
    val padMappings = remember {
        mutableStateMapOf<String, String>().apply {
            padSlots.forEach { slot -> this[slot] = prefs.getString("pad_$slot", HorizonGameActivity.PAD_DEFAULTS[slot]) ?: "" }
        }
    }
    var padPick by remember { mutableStateOf<String?>(null) }
    var padDeadzone by remember { mutableFloatStateOf(prefs.getFloat("pad_deadzone", 0.22f)) }
    var padTriggerThreshold by remember { mutableFloatStateOf(prefs.getFloat("pad_trigger_threshold", 0.22f)) }
    var padCameraSensitivity by remember { mutableFloatStateOf(prefs.getFloat("pad_camera_sensitivity", 1.4f)) }
    var padInvertY by remember { mutableStateOf(prefs.getBoolean("pad_invert_y", false)) }
    var padSwapSticks by remember { mutableStateOf(prefs.getBoolean("pad_swap_sticks", false)) }
    var padConnected by remember { mutableStateOf(false) }

    // ---- layout editor state ------------------------------------------------------------
    var activeLayout by remember { mutableStateOf(layout) }
    var selected by remember { mutableStateOf<ObservableWidget?>(null) }
    var selectedLayer by remember(activeLayout) { mutableStateOf(activeLayout.layers.value.firstOrNull()) }
    var panelOpen by remember { mutableStateOf(true) }
    var editTab by remember { mutableIntStateOf(0) }
    var snapEnabled by remember { mutableStateOf(prefs.getBoolean("editor_snap", true)) }
    val history = remember { ControlHistory() }
    var editSnapshot by remember { mutableStateOf<ControlLayout?>(null) }
    val occupied = remember { mutableSetOf<Long>() }
    val tuner = remember { LookTuner() }
    val haptics = remember(view) { Haptics(view) }

    fun record() = history.record(activeLayout.pack())
    fun swapLayout(next: ControlLayout) { activeLayout = ObservableControlLayout(next); selected = null }

    fun startEditing() {
        editSnapshot = activeLayout.pack(); history.clear()
        selected = null; panelOpen = true; editTab = 0; quickSettings = false; editing = true
    }
    fun finishEditing(keep: Boolean) {
        if (keep) {
            activeLayout.applyEditorHide()
            save(activeLayout.pack())
            profiles.activeName.takeIf { it.isNotBlank() }?.let { name ->
                // Keep the active saved layout in sync with what the player just edited.
                runCatching { if (name in profiles.names()) profiles.save(name, activeLayout.pack()) }
            }
        } else editSnapshot?.let { swapLayout(it) }
        selected = null; editing = false; editSnapshot = null
    }
    fun addButton() {
        val layer = selectedLayer ?: activeLayout.layers.value.firstOrNull() ?: return
        record()
        layer.addNormalButton(NormalData(
            text = createTranslatable("Key"), uuid = UUID.randomUUID().toString(), position = ButtonPosition(5000, 5000),
            buttonSize = DefaultSize, visibilityType = VisibilityType.IN_GAME,
            _clickEvents = listOf(ClickEvent(ClickEvent.Type.Key, "GLFW_KEY_E")),
            isSwipple = false, isPenetrable = false, isToggleable = false))
        selected = layer.normalButtons.value.lastOrNull(); selectedLayer = layer; editTab = 0; panelOpen = true
    }
    fun addStick() {
        val layer = selectedLayer ?: activeLayout.layers.value.firstOrNull() ?: return
        record()
        layer.addJoystickButton(JoystickData(uuid = UUID.randomUUID().toString(), position = ButtonPosition(5000, 5000),
            sizePercentage = 2200, visibilityType = VisibilityType.IN_GAME))
        selected = layer.joystickButtons.value.lastOrNull(); selectedLayer = layer; editTab = 0; panelOpen = true
    }
    fun addLabel() {
        val layer = selectedLayer ?: activeLayout.layers.value.firstOrNull() ?: return
        record()
        layer.addTextBox(TextData(text = createTranslatable("Label"), uuid = UUID.randomUUID().toString(),
            position = ButtonPosition(5000, 5000), buttonSize = DefaultSize.copy(widthPercentage = 2500, heightPercentage = 1000),
            visibilityType = VisibilityType.IN_GAME))
        selected = layer.textBoxes.value.lastOrNull(); selectedLayer = layer; editTab = 0; panelOpen = true
    }
    fun duplicateSelected() {
        val widget = selected
        val layer = selectedLayer ?: return
        when (widget) {
            is ObservableNormalData -> { val copy = widget.cloneNormal(); layer.addNormalButton(copy); selected = copy }
            is ObservableJoystickData -> { val copy = ObservableJoystickData(widget.packJoystick().cloneNew()); layer.addJoystickButton(copy); selected = copy }
            else -> {}
        }
    }
    fun deleteSelected() {
        val widget = selected
        val layer = selectedLayer ?: return
        when (widget) {
            is ObservableNormalData -> layer.removeNormalButton(widget.uuid)
            is ObservableJoystickData -> layer.removeJoystickButton(widget.uuid)
            is ObservableTextData -> layer.removeTextBox(widget.uuid)
            else -> {}
        }
        selected = null
    }

    // ---- background polling / effects -----------------------------------------------------
    LaunchedEffect(Unit) {
        while (true) {
            grabbing = runCatching { GameInput.nativeCursorGrabbing() }.getOrDefault(true)
            kotlinx.coroutines.delay(150)
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            padConnected = InputDevice.getDeviceIds().any { id ->
                val device = InputDevice.getDevice(id)
                device != null && !device.isVirtual && device.sources and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD
            }
            kotlinx.coroutines.delay(2000)
        }
    }
    LaunchedEffect(hapticLevel) { haptics.level = hapticLevel }
    LaunchedEffect(showFps, showMemory) {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
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
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = manager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        var lastTimestamp = 0L
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (!gyroEnabled || lastTimestamp == 0L) { lastTimestamp = event.timestamp; return }
                val dt = ((event.timestamp - lastTimestamp) / 1_000_000_000f).coerceIn(0f, 0.05f)
                lastTimestamp = event.timestamp
                @Suppress("DEPRECATION")
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

    val handler = remember(haptics) {
        EventHandler { event, pressed ->
            if (event.type == ClickEvent.Type.Key) {
                GameInput.nativeSendControl(event.key, pressed)
                if (pressed) haptics.tick()
            }
        }
    }

    MaterialTheme {
    Box(
        Modifier.fillMaxSize().pointerInput(Unit) {
            awaitPointerEventScope {
                val positions = mutableMapOf<Long, Offset>()
                val starts = mutableMapOf<Long, Offset>()
                val downAt = mutableMapOf<Long, Long>()
                val lookPointers = mutableSetOf<Long>()
                val ignored = mutableSetOf<Long>()
                val hotbarPointers = mutableSetOf<Long>()
                /** Zalith-style hotbar: index 0..8 of the slot under [p], or -1. Geometry comes from the game's HUD. */
                fun hotbarSlotAt(p: Offset): Int {
                    val r = runCatching { GameInput.nativeHotbarRect() }.getOrNull() ?: return -1
                    if (r.size < 4 || r[2] <= 0f) return -1
                    val pad = r[3] * 0.25f
                    if (p.y < r[1] - pad || p.y > r[1] + r[3] + pad) return -1
                    val slot = ((p.x - r[0] - r[2] * 0.05f) / r[2]).toInt()
                    return if (p.x >= r[0] && slot in 0..8) slot else -1
                }
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Final)
                    if (editing) {
                        // The editor owns touch input; never steer the camera while dragging controls.
                        positions.clear(); starts.clear(); downAt.clear(); lookPointers.clear(); ignored.clear(); tuner.reset()
                        continue
                    }
                    val areaStart = when (lookArea) { 1 -> 0.3f; 2 -> 0.5f; else -> 0f }
                    event.changes.forEach { change ->
                        val id = change.id.value
                        if (change.pressed && !change.isConsumed && id !in occupied) {
                            if (id in hotbarPointers) return@forEach
                            if (hotbarTouch && grabbing && id !in positions) {
                                val slot = hotbarSlotAt(change.position)
                                if (slot >= 0) {
                                    hotbarPointers += id; ignored += id; positions[id] = change.position
                                    GameInput.nativeSendControl("GLFW_KEY_${slot + 1}", true)
                                    GameInput.nativeSendControl("GLFW_KEY_${slot + 1}", false)
                                    return@forEach
                                }
                            }
                            val prior = positions.put(id, change.position)
                            if (prior == null) {
                                starts[id] = change.position
                                downAt[id] = SystemClock.elapsedRealtime()
                            }
                            if (grabbing && swipeLook) {
                                if (prior == null) {
                                    if (change.position.x >= size.width * areaStart) {
                                        lookPointers += id
                                        tuner.reset()
                                        GameInput.nativeLookActive(true)
                                    } else ignored += id
                                } else if (id in lookPointers) {
                                    val dx = (change.position.x - prior.x) * lookSensitivity * lookSensX * if (invertLookX) -1f else 1f
                                    val dy = (change.position.y - prior.y) * lookSensitivity * lookSensY * if (invertLookY) -1f else 1f
                                    tuner.apply(dx, dy, lookAccel, lookSmoothing)
                                    GameInput.nativeLook(tuner.outX, tuner.outY)
                                }
                            } else {
                                GameInput.nativeMenuTouch(id, change.position.x, change.position.y, if (prior == null) 0 else 1)
                            }
                        } else if (!change.pressed && positions.remove(id) != null) {
                            hotbarPointers.remove(id)
                            occupied.remove(id)
                            val start = starts.remove(id)
                            val duration = SystemClock.elapsedRealtime() - (downAt.remove(id) ?: 0L)
                            if (lookPointers.remove(id)) {
                                GameInput.nativeLookActive(lookPointers.isNotEmpty())
                                val moved = start?.let { (it - change.position).getDistance() > 24f } ?: true
                                val action = if (duration >= longPressDelay) longPressAction else tapAction
                                if (!moved && action in 1..2) {
                                    val button = if (action == 1) "GLFW_MOUSE_BUTTON_LEFT" else "GLFW_MOUSE_BUTTON_RIGHT"
                                    GameInput.nativeSendControl(button, true)
                                    GameInput.nativeSendControl(button, false)
                                }
                            } else if (!ignored.remove(id)) {
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
                onButtonTap = { data, layer ->
                    if (data !== selected) record()
                    selected = data; selectedLayer = layer; editTab = 0
                },
                onBackgroundClick = { selected = null },
                floatingButtons = {}, enableSnap = snapEnabled, snapInAllLayers = true,
                snapMode = SnapMode.FullScreen, isDark = true
            )

            // Toolbar
            androidx.compose.material3.Surface(
                Modifier.align(Alignment.TopStart).padding(8.dp).fillMaxWidth(if (panelOpen) 0.58f else 0.9f),
                shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f), tonalElevation = 6.dp
            ) {
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = { addButton() }) { Text("+ Button") }
                    Button(onClick = { addStick() }) { Text("+ Stick") }
                    Button(onClick = { addLabel() }) { Text("+ Label") }
                    OutlinedButton(enabled = history.canUndo, onClick = {
                        history.undo(activeLayout.pack())?.let { swapLayout(it) }
                    }) { Text("Undo") }
                    OutlinedButton(enabled = history.canRedo, onClick = {
                        history.redo(activeLayout.pack())?.let { swapLayout(it) }
                    }) { Text("Redo") }
                    FilterChip(selected = snapEnabled, onClick = { snapEnabled = !snapEnabled; prefs.edit().putBoolean("editor_snap", snapEnabled).apply() }, label = { Text("Snap") })
                    FilterChip(selected = panelOpen, onClick = { panelOpen = !panelOpen }, label = { Text("Panel") })
                    OutlinedButton(onClick = { finishEditing(keep = false) }) { Text("Cancel") }
                    Button(onClick = { finishEditing(keep = true) }) { Text("Save / Done") }
                }
            }

            // Side panel
            if (panelOpen) androidx.compose.material3.Surface(
                Modifier.align(Alignment.CenterEnd).width(340.dp).fillMaxHeight().padding(8.dp),
                shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f), tonalElevation = 8.dp
            ) {
                Column(Modifier.fillMaxSize()) {
                    ScrollableTabRow(selectedTabIndex = editTab, edgePadding = 0.dp) {
                        EDITOR_TABS.forEachIndexed { i, label -> Tab(selected = editTab == i, onClick = { editTab = i }, text = { Text(label) }) }
                    }
                    Box(Modifier.weight(1f)) {
                        when (editTab) {
                            0 -> {
                                val widget = selected
                                if (widget == null) Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Nothing selected", style = MaterialTheme.typography.titleMedium)
                                    Text("Tap a button, stick or label on the canvas to edit its keys, size, position and look. Drag to move it, drag a corner to resize.", style = MaterialTheme.typography.bodyMedium)
                                    Text("New controls go on layer: ${selectedLayer?.name?.ifBlank { "(unnamed)" } ?: "—"}", style = MaterialTheme.typography.bodySmall)
                                } else WidgetInspector(
                                    widget = widget, layout = activeLayout, layer = selectedLayer,
                                    recordHistory = { record() }, onDelete = { deleteSelected() }, onDuplicate = { duplicateSelected() },
                                    onClose = { selected = null }, modifier = Modifier.fillMaxSize()
                                )
                            }
                            1 -> LayerPanel(activeLayout, selectedLayer, { selectedLayer = it }, { record() }, Modifier.fillMaxSize())
                            2 -> StylesPanel(activeLayout, { record() }, Modifier.fillMaxSize())
                            else -> ProfilesPanel(
                                profiles = profiles, layout = activeLayout,
                                onLoad = { loaded, _ -> record(); swapLayout(loaded) },
                                onResetToDefault = { record(); swapLayout(defaultLayout()); profiles.activeName = "" },
                                onImport = { activity?.chooseTouchLayout { imported -> record(); swapLayout(imported) } },
                                onExport = { activity?.exportTouchLayout(activeLayout.pack()) },
                                onShare = { activity?.shareTouchLayout(activeLayout.pack()) },
                                onCopy = { activity?.copyTouchLayout(activeLayout.pack()) },
                                onPaste = { activity?.pasteTouchLayout { imported -> record(); swapLayout(imported) } },
                                onError = { message -> activity?.toast(message) },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        } else {
            if (storedHud) ControlBoxLayout(
                modifier = Modifier.fillMaxSize(), observedLayout = activeLayout, eventHandler = handler,
                isCursorGrabbing = grabbing,
                checkOccupiedPointers = { it.value in occupied },
                onOccupiedPointer = { occupied += it.value },
                onReleasePointer = { occupied -= it.value },
                opacity = { opacity },
                hideLayerWhen = if (autoHideForPad && padConnected) HideLayerWhen.WhenGamepad else HideLayerWhen.None,
                isDark = true
            ) {}
            Button(onClick = { quickSettings = !quickSettings }, modifier = Modifier.align(Alignment.TopEnd).padding(12.dp).graphicsLayer { alpha = quickButtonOpacity }) {
                Text("⚙")
            }
            if (showFps || showMemory) {
                Column(Modifier.align(Alignment.TopStart).padding(12.dp)) {
                    if (showFps && fpsMode == 0) Text("$currentFps FPS", color = Color.White)
                    if (showFps && fpsMode == 1) Canvas(Modifier.width(120.dp).height(42.dp)) {
                        if (fpsHistory.size > 1) {
                            val maxFps = (fpsHistory.maxOrNull() ?: 1f).coerceAtLeast(1f)
                            val points = fpsHistory.mapIndexed { i, value ->
                                Offset(size.width * i / (fpsHistory.size - 1), size.height * (1f - value / maxFps))
                            }
                            points.zipWithNext().forEach { (a, b) -> drawLine(Color.Green, a, b, strokeWidth = 3f) }
                        }
                    }
                    if (showMemory) Text("${currentMemoryMb} MB", color = Color.White)
                }
            }
            if (quickSettings) {
                androidx.compose.material3.Surface(
                    modifier = Modifier.align(Alignment.TopEnd).padding(top = 72.dp, end = 12.dp)
                        .width(340.dp).fillMaxHeight(0.82f),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                    shape = RoundedCornerShape(16.dp), tonalElevation = 8.dp
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Horizon quick settings", style = MaterialTheme.typography.titleMedium)
                        ScrollableTabRow(selectedTabIndex = quickTab, edgePadding = 0.dp) {
                            QUICK_TABS.forEachIndexed { i, label -> Tab(selected = quickTab == i, onClick = { quickTab = i }, text = { Text(label) }) }
                        }
                        Column(Modifier.weight(1f).verticalScroll(quickScroll), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            when (quickTab) {
                                0 -> {
                                    Text("On-screen controls", style = MaterialTheme.typography.titleSmall)
                                    ToggleRow("Show touch HUD", storedHud) { storedHud = it; prefs.edit().putBoolean("hud_visible", it).apply() }
                                    LabeledSlider("Control opacity", opacity, 0.2f..1f, { "${(it * 100).toInt()}%" }) {
                                        opacity = it; prefs.edit().putFloat("opacity", it).apply()
                                    }
                                    Button(onClick = { startEditing() }) { Text("Customize controls") }
                                    Text("Haptic feedback", style = MaterialTheme.typography.bodySmall)
                                    ChipChoice(listOf(0, 1, 2, 3), hapticLevel, { Haptics.labels[it] }) {
                                        hapticLevel = it; prefs.edit().putInt("haptic_level", it).apply(); haptics.level = it; haptics.tick()
                                    }
                                    ToggleRow("Hide on-screen controls when a gamepad is connected", autoHideForPad) {
                                        autoHideForPad = it; prefs.edit().putBoolean("hide_hud_for_gamepad", it).apply()
                                    }
                                    Text("Saved layouts", style = MaterialTheme.typography.titleSmall)
                                    val saved = remember(quickSettings) { profiles.names() }
                                    if (saved.isEmpty()) Text("Save layouts from Customize controls → Layouts to switch between them here.", style = MaterialTheme.typography.bodySmall)
                                    else ChipChoice(saved, profiles.activeName.takeIf { it in saved }, { it }) { name ->
                                        runCatching { profiles.load(name) }
                                            .onSuccess { swapLayout(it); save(it); profiles.activeName = name }
                                            .onFailure { activity?.toast("Could not load $name: ${it.message}") }
                                    }
                                    OutlinedButton(onClick = {
                                        activity?.chooseTouchLayout { imported -> swapLayout(imported); save(imported); profiles.activeName = "" }
                                    }) { Text("Import layout file") }
                                    ToggleRow("Tap hotbar slots directly", hotbarTouch) { hotbarTouch = it; prefs.edit().putBoolean("hotbar_touch", it).apply() }
                                    ToggleRow("Auto-show keyboard in text fields", autoKeyboard) { autoKeyboard = it; prefs.edit().putBoolean("auto_keyboard", it).apply() }
                                    OutlinedButton(onClick = {
                                        (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
                                    }) { Text("Choose Android keyboard") }
                                    LabeledSlider("Quick button opacity", quickButtonOpacity, 0.25f..1f, { "${(it * 100).toInt()}%" }) {
                                        quickButtonOpacity = it; prefs.edit().putFloat("quick_button_opacity", it).apply()
                                    }
                                }
                                1 -> {
                                    Text("Touch mouse / camera", style = MaterialTheme.typography.titleSmall)
                                    ToggleRow("Swipe to look", swipeLook) { swipeLook = it; prefs.edit().putBoolean("swipe_look", it).apply() }
                                    LabeledSlider("Look sensitivity", lookSensitivity, 0.25f..3f, { "%.2f×".format(it) }) {
                                        lookSensitivity = it; prefs.edit().putFloat("look_sensitivity", it).apply()
                                    }
                                    LabeledSlider("Horizontal multiplier", lookSensX, 0.25f..3f, { "%.2f×".format(it) }) {
                                        lookSensX = it; prefs.edit().putFloat("look_sens_x", it).apply()
                                    }
                                    LabeledSlider("Vertical multiplier", lookSensY, 0.25f..3f, { "%.2f×".format(it) }) {
                                        lookSensY = it; prefs.edit().putFloat("look_sens_y", it).apply()
                                    }
                                    LabeledSlider("Flick acceleration", lookAccel, 0f..1f, { "${(it * 100).toInt()}%" }) {
                                        lookAccel = it; prefs.edit().putFloat("look_accel", it).apply()
                                    }
                                    LabeledSlider("Smoothing", lookSmoothing, 0f..0.9f, { "${(it * 100).toInt()}%" }) {
                                        lookSmoothing = it; prefs.edit().putFloat("look_smoothing", it).apply()
                                    }
                                    Text("Look area", style = MaterialTheme.typography.bodySmall)
                                    ChipChoice(listOf(0, 1, 2), lookArea, { listOf("Full screen", "Right 70%", "Right half")[it] }) {
                                        lookArea = it; prefs.edit().putInt("look_area", it).apply()
                                    }
                                    ToggleRow("Invert horizontal look", invertLookX) { invertLookX = it; prefs.edit().putBoolean("invert_look_x", it).apply() }
                                    ToggleRow("Invert vertical look", invertLookY) { invertLookY = it; prefs.edit().putBoolean("invert_look_y", it).apply() }
                                    Text("Drag empty screen space while the game captures the pointer. Touch controls stay clickable. Restricting the look area keeps stray thumbs on the left side from moving the camera.", style = MaterialTheme.typography.bodySmall)
                                }
                                2 -> {
                                    Text("Controller input", style = MaterialTheme.typography.titleSmall)
                                    Text(if (padConnected) "A game controller is connected." else "No game controller detected. Connect a Bluetooth or USB controller.")
                                    Text("Tap a slot to choose any key or mouse button. ✕ clears it.", style = MaterialTheme.typography.bodySmall)
                                    padSlots.forEach { slot ->
                                        val title = slot.replace('_', ' ').replaceFirstChar { it.uppercase() }
                                        val binding = padMappings[slot] ?: ""
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Button(onClick = { padPick = slot }, modifier = Modifier.weight(1f)) {
                                                Text("$title → ${if (binding.isBlank()) "None" else KeyCatalog.label(binding)}")
                                            }
                                            OutlinedButton(onClick = { padMappings[slot] = ""; prefs.edit().putString("pad_$slot", "").apply() }) { Text("✕") }
                                        }
                                    }
                                    OutlinedButton(onClick = {
                                        padSlots.forEach { slot ->
                                            val default = HorizonGameActivity.PAD_DEFAULTS[slot] ?: ""
                                            padMappings[slot] = default; prefs.edit().putString("pad_$slot", default).apply()
                                        }
                                    }) { Text("Reset bindings") }
                                    ToggleRow("Swap sticks (right moves, left looks)", padSwapSticks) { padSwapSticks = it; prefs.edit().putBoolean("pad_swap_sticks", it).apply() }
                                    ToggleRow("Invert camera Y", padInvertY) { padInvertY = it; prefs.edit().putBoolean("pad_invert_y", it).apply() }
                                    Text("Stick deadzone · ${(padDeadzone * 100).toInt()}%")
                                    Slider(value = padDeadzone, onValueChange = { padDeadzone = it }, onValueChangeFinished = {
                                        prefs.edit().putFloat("pad_deadzone", padDeadzone).apply()
                                    }, valueRange = 0.05f..0.6f)
                                    Text("Trigger threshold · ${(padTriggerThreshold * 100).toInt()}%")
                                    Slider(value = padTriggerThreshold, onValueChange = { padTriggerThreshold = it }, onValueChangeFinished = {
                                        prefs.edit().putFloat("pad_trigger_threshold", padTriggerThreshold).apply()
                                    }, valueRange = 0.05f..0.9f)
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
                                    val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
                                    val gyroAvailable = manager.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null
                                    Text("Gyroscope camera", style = MaterialTheme.typography.titleSmall)
                                    ToggleRow("Enable gyro look", gyroEnabled && gyroAvailable) { gyroEnabled = it; prefs.edit().putBoolean("gyro_enabled", it).apply() }
                                    Text(if (gyroAvailable) "Move the phone to look while the game has captured the pointer." else "This device has no gyroscope sensor.", style = MaterialTheme.typography.bodySmall)
                                    LabeledSlider("Gyro sensitivity", gyroSensitivity, 0.2f..3f, { "%.1f×".format(it) }, onChange = {
                                        gyroSensitivity = it; prefs.edit().putFloat("gyro_sensitivity", it).apply()
                                    })
                                }
                                else -> {
                                    Text("Device and session", style = MaterialTheme.typography.titleSmall)
                                    val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                                    var volume by remember { mutableFloatStateOf(audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()) }
                                    Text("Media volume · ${volume.toInt()}/${audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)}")
                                    Slider(value = volume, onValueChange = { volume = it }, onValueChangeFinished = {
                                        audio.setStreamVolume(AudioManager.STREAM_MUSIC, volume.toInt(), 0)
                                    }, valueRange = 0f..audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat())
                                    ToggleRow("Keep screen awake", keepAwake) { keepAwake = it; prefs.edit().putBoolean("keep_awake", it).apply(); setKeepAwake(it) }
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
                                    ToggleRow("Show FPS", showFps) { showFps = it; prefs.edit().putBoolean("show_fps", it).apply() }
                                    ToggleRow("Show process memory", showMemory) { showMemory = it; prefs.edit().putBoolean("show_memory", it).apply() }
                                    Button(onClick = { fpsMode = (fpsMode + 1) % 2; prefs.edit().putInt("fps_mode", fpsMode).apply() }) {
                                        Text("FPS display: ${if (fpsMode == 0) "Number" else "Chart"}")
                                    }
                                    ToggleRow("Request maximum refresh rate", maxRefresh) { maxRefresh = it; prefs.edit().putBoolean("max_refresh", it).apply(); setMaxRefresh(it) }
                                    Button(onClick = { (context as? Activity)?.finish() }) { Text("Close game") }
                                }
                            }
                        }
                    }
                }
            }
            padPick?.let { slot ->
                KeyPickerDialog(title = "Bind ${slot.replace('_', ' ')}", onPick = { id ->
                    padMappings[slot] = id; prefs.edit().putString("pad_$slot", id).apply(); padPick = null
                }, onDismiss = { padPick = null })
            }
        }
    }
    }
}
