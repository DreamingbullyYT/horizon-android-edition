package dev.horizon.launcher.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import dev.horizon.launcher.MinecraftBackup
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Gamepad
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.layout.onSizeChanged
import dev.horizon.launcher.data.AccountsInfo
import dev.horizon.launcher.data.PlatformInfo
import dev.horizon.launcher.data.ReleasesState
import dev.horizon.launcher.data.SettingsInfo
import dev.horizon.launcher.data.SignInInfo
import dev.horizon.launcher.data.TelemetryInfo
import dev.horizon.launcher.data.TouchControlsInfo
import dev.horizon.launcher.data.CustomTouchButtonInfo
import org.json.JSONObject
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import dev.horizon.launcher.data.Updater

@Composable
fun SettingsScreen(
    accounts: AccountsInfo?,
    settings: SettingsInfo?,
    touchControls: TouchControlsInfo?,
    signIn: SignInInfo?,
    telemetry: TelemetryInfo?,
    accent: Accent,
    onAccent: (Accent) -> Unit,
    platform: PlatformInfo?,
    releases: ReleasesState,
    homeDir: String,
    busy: String?,
    onSave: (JSONObject) -> Unit,
    onSaveTouchControls: (JSONObject) -> Unit,
    onAccounts: () -> Unit,
    onTelemetry: (Boolean) -> Unit,
    onMessage: (String) -> Unit,
    onGameFilesChanged: () -> Unit = {},
) {
    val c = LocalHorizon.current
    val appContext = LocalContext.current
    val updateScope = rememberCoroutineScope()
    var updateStatus by remember { mutableStateOf("Tap to check") }
    var backupStatus by remember { mutableStateOf("") }
    var backupRunning by remember { mutableStateOf(false) }
    val restorePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: android.net.Uri? ->
        if (uri != null && !backupRunning) {
            backupRunning = true
            updateScope.launch {
                runCatching { MinecraftBackup.restore(appContext, uri) { backupStatus = it } }
                    .onSuccess { backupStatus = "Restored"; onGameFilesChanged(); onMessage("Minecraft restored from backup. No download needed.") }
                    .onFailure { backupStatus = "Failed"; onMessage(it.message ?: "Could not restore the backup") }
                backupRunning = false
            }
        }
    }
    val coreVersion = platform?.version ?: "0.2.37"
    val dataDir = platform?.dataDir ?: homeDir
    val builds = (releases as? ReleasesState.Ready)?.items?.size
    val enabled = busy == null

    var autoInstall by remember(settings?.installNewVersions) {
        mutableStateOf(settings?.installNewVersions ?: true)
    }
    var prune by remember(settings?.pruneOldVersions) {
        mutableStateOf(settings?.pruneOldVersions ?: true)
    }
    var showOpening by remember(settings?.skipOpening) {
        mutableStateOf(!(settings?.skipOpening ?: false))
    }
    var noAi by remember(settings?.noAi) { mutableStateOf(settings?.noAi ?: false) }
    var touchEnabled by remember(touchControls?.enabled) { mutableStateOf(touchControls?.enabled ?: true) }
    var leftHanded by remember(touchControls?.leftHanded) { mutableStateOf(touchControls?.leftHanded ?: false) }
    var touchLayout by remember(touchControls?.layout) { mutableStateOf(touchControls?.layout ?: "classic") }
    var touchOpacity by remember(touchControls?.opacity) { mutableStateOf(touchControls?.opacity ?: 0.72f) }
    var touchSize by remember(touchControls?.buttonSize) { mutableStateOf(touchControls?.buttonSize ?: 1.0f) }
    var lookSensitivity by remember(touchControls?.lookSensitivity) { mutableStateOf(touchControls?.lookSensitivity ?: 1.0f) }
    var autoJump by remember(touchControls?.autoJump) { mutableStateOf(touchControls?.autoJump ?: false) }
    var holdToSprint by remember(touchControls?.holdToSprint) { mutableStateOf(touchControls?.holdToSprint ?: false) }
    var haptics by remember(touchControls?.haptics) { mutableStateOf(touchControls?.haptics ?: true) }
    var controlPositions by remember(touchControls?.positions) {
        mutableStateOf<Map<String, Pair<Float, Float>>>(touchControls?.positions ?: emptyMap())
    }
    var controlBindings by remember(touchControls?.bindings) {
        mutableStateOf<Map<String, String>>(touchControls?.bindings ?: emptyMap())
    }
    var customButtons by remember(touchControls?.customButtons) {
        mutableStateOf(touchControls?.customButtons ?: emptyList())
    }
    var outputMenuFor by remember { mutableStateOf<String?>(null) }
    var draggingControl by remember { mutableStateOf<String?>(null) }
    var editorSize by remember { mutableStateOf(IntSize.Zero) }
    val controlDefaults = mapOf("move" to (0.14f to 0.77f), "jump" to (0.90f to 0.73f),
        "sneak" to (0.68f to 0.87f), "sprint" to (0.51f to 0.71f),
        "attack" to (0.78f to 0.88f), "use" to (0.92f to 0.90f))
    val outputs = listOf("Space", "ShiftLeft", "ControlLeft", "AltLeft", "Tab", "Escape", "Enter", "Backspace",
        "ArrowUp", "ArrowDown", "ArrowLeft", "ArrowRight", "Home", "End", "PageUp", "PageDown",
        *('A'..'Z').map { it.toString() }.toTypedArray(), *('0'..'9').map { it.toString() }.toTypedArray(),
        * (1..12).map { "F$it" }.toTypedArray(), "MouseLeft", "MouseRight", "MouseMiddle")
    val allControlDefaults = controlDefaults + customButtons.mapIndexed { index, control ->
        control.id to (0.48f + (index % 4) * 0.08f to 0.48f + (index / 4) * 0.12f)
    }
    fun saveControlLayout() {
        val positions = JSONObject().apply {
            allControlDefaults.forEach { (key, default) ->
                val point = controlPositions[key] ?: default
                put(key, org.json.JSONArray().put(point.first.toDouble()).put(point.second.toDouble()))
            }
        }
        val bindings = JSONObject().apply { controlBindings.forEach { (key, value) -> put(key, value) } }
        val custom = org.json.JSONArray().apply {
            customButtons.forEach { put(JSONObject().put("id", it.id).put("label", it.label).put("output", controlBindings[it.id] ?: it.output)) }
        }
        onSaveTouchControls(JSONObject().put("positions", positions).put("bindings", bindings).put("custom_buttons", custom))
    }


    LazyColumn(
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ScreenHeader(
                kicker = "Launcher",
                title = "Settings",
                subtitle = "Accounts, updates and the launcher itself. Changes are saved by the Rust core.",
            )
        }

        item {
            Plate {
                SectionLabel("Account")
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(accent.primary.copy(alpha = 0.22f))
                            .border(1.dp, accent.hi.copy(alpha = 0.5f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("H", color = c.ink, style = MaterialTheme.typography.titleLarge)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(accounts?.player?.label ?: "Reading account…", color = c.ink, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${accounts?.accounts?.size ?: 0} Microsoft account(s) · ${accounts?.offline?.size ?: 0} offline name(s)",
                            color = c.muted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                SoftButton("Manage accounts", modifier = Modifier.fillMaxWidth(), onClick = onAccounts)
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = c.stroke)
                Spacer(Modifier.height(10.dp))
                Text("Microsoft sign-in", color = c.ink, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    when {
                        signIn == null -> "Checking sign-in configuration…"
                        signIn.configured -> "A Microsoft client ID is configured, but the Android device-code flow is not connected to the UI yet."
                        else -> signIn.message ?: "Microsoft sign-in is not configured in this build."
                    },
                    color = c.muted,
                    style = MaterialTheme.typography.bodySmall,
                )
                if (signIn?.configured == true) {
                    Spacer(Modifier.height(8.dp))
                    Tag("ANDROID FLOW NOT WIRED", c.warn)
                }
                if (signIn?.keychain == false) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        signIn.noKeychain ?: "This Android preview cannot keep account secrets between runs.",
                        color = c.warn,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        item {
            Plate {
                SectionLabel("Touch controls")
                Spacer(Modifier.height(6.dp))
                Text(
                    "Drag controls to place them. Change each output below. In-game, tap SET for quick HUD, auto-jump and look-sensitivity controls. Swipe vertically in menus to scroll.",
                    color = c.muted,
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(10.dp))
                ToggleRow("Enable touch controls", touchEnabled, enabled) {
                    touchEnabled = it
                    onSaveTouchControls(JSONObject().put("enabled", it))
                }
                Spacer(Modifier.height(10.dp))
                    Canvas(
                    Modifier.fillMaxWidth().height(210.dp).background(Color(0xFF171B22)).onSizeChanged { editorSize = it }.pointerInput(controlPositions, allControlDefaults, enabled, editorSize) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                val nearest = allControlDefaults.keys.minByOrNull { key ->
                                    val p = controlPositions[key] ?: allControlDefaults.getValue(key)
                                    val dx = p.first * editorSize.width - offset.x
                                    val dy = p.second * editorSize.height - offset.y
                                    dx * dx + dy * dy
                                }
                                draggingControl = nearest
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                draggingControl?.let { key ->
                                    controlPositions = controlPositions.toMutableMap().apply {
                                        put(key, (change.position.x / editorSize.width.coerceAtLeast(1)).coerceIn(0.04f, 0.96f) to (change.position.y / editorSize.height.coerceAtLeast(1)).coerceIn(0.08f, 0.94f))
                                    }
                                }
                            },
                            onDragEnd = { draggingControl = null; saveControlLayout() },
                            onDragCancel = { draggingControl = null },
                        )
                    },
                ) {
                    drawRoundRect(Color(0xFF303844), topLeft = Offset(size.width * 0.01f, size.height * 0.03f), size = androidx.compose.ui.geometry.Size(size.width * 0.98f, size.height * 0.94f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(12f))
                    allControlDefaults.forEach { (key, default) ->
                        val p = controlPositions[key] ?: default
                        val center = Offset(p.first * size.width, p.second * size.height)
                        drawCircle(Color(0xBB8DB8FF), radius = 22.dp.toPx(), center = center)
                        drawCircle(Color.White, radius = 22.dp.toPx(), center = center, style = Stroke(width = 1.dp.toPx()))
                        drawContext.canvas.nativeCanvas.apply {
                            val paint = android.graphics.Paint().apply { color = android.graphics.Color.WHITE; textSize = 10.dp.toPx(); textAlign = android.graphics.Paint.Align.CENTER; isAntiAlias = true }
                            val label = customButtons.firstOrNull { it.id == key }?.label ?: key.replaceFirstChar { it.uppercase() }
                            drawText(label, center.x, center.y + 3.dp.toPx(), paint)
                        }
                    }
                }
                listOf("jump", "sneak", "sprint", "attack", "use").forEach { control ->
                    val fallback = mapOf("jump" to "Space", "sneak" to "ShiftLeft", "sprint" to "ControlLeft", "attack" to "MouseLeft", "use" to "MouseRight").getValue(control)
                    val current = controlBindings[control] ?: fallback
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        Text(control.replaceFirstChar { it.uppercase() }, color = c.ink, modifier = Modifier.weight(1f))
                        Text(current, color = c.accentHi, style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.clip(pixelShape(4.dp)).background(c.cardHi).clickable(enabled = enabled) {
                                val next = outputs[(outputs.indexOf(current).coerceAtLeast(-1) + 1) % outputs.size]
                                controlBindings = controlBindings.toMutableMap().apply { put(control, next) }
                                saveControlLayout()
                            }.padding(horizontal = 10.dp, vertical = 7.dp))
                    }
                }
                listOf("move_up", "move_down", "move_left", "move_right").forEachIndexed { index, key ->
                    val fallback = listOf("W", "S", "A", "D")[index]
                    val current = controlBindings[key] ?: fallback
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Text("Move ${listOf("up", "down", "left", "right")[index]}", color = c.muted, modifier = Modifier.weight(1f))
                        Text(current, color = c.accentHi, modifier = Modifier.clickable(enabled = enabled) {
                            val next = outputs[(outputs.indexOf(current).coerceAtLeast(-1) + 1) % outputs.size]
                            controlBindings = controlBindings.toMutableMap().apply { put(key, next) }; saveControlLayout()
                        }.padding(7.dp))
                    }
                }
                customButtons.forEach { button ->
                    val current = controlBindings[button.id] ?: button.output
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        Text(button.label, color = c.ink, modifier = Modifier.weight(1f))
                        Box {
                            Text(current, color = c.accentHi, modifier = Modifier
                                .clip(pixelShape(4.dp)).background(c.cardHi)
                                .clickable(enabled = enabled) { outputMenuFor = button.id }
                                .padding(horizontal = 10.dp, vertical = 7.dp))
                            DropdownMenu(expanded = outputMenuFor == button.id, onDismissRequest = { outputMenuFor = null }) {
                                outputs.forEach { output ->
                                    DropdownMenuItem(text = { Text(output) }, onClick = {
                                        controlBindings = controlBindings.toMutableMap().apply { put(button.id, output) }
                                        outputMenuFor = null
                                        saveControlLayout()
                                    })
                                }
                            }
                        }
                        Text("Remove", color = c.warn, modifier = Modifier.clickable(enabled = enabled) {
                            customButtons = customButtons.filterNot { it.id == button.id }
                            controlPositions = controlPositions - button.id
                            controlBindings = controlBindings - button.id
                            saveControlLayout()
                        }.padding(start = 10.dp, top = 8.dp, bottom = 8.dp))
                    }
                }
                SoftButton("Add custom button", modifier = Modifier.fillMaxWidth()) {
                    if (!enabled || customButtons.size >= 24) return@SoftButton
                    val id = (1..24).map { "custom_$it" }.firstOrNull { candidate -> customButtons.none { it.id == candidate } }
                        ?: return@SoftButton
                    customButtons = customButtons + CustomTouchButtonInfo(id, "Key", "E")
                    controlPositions = controlPositions + (id to (0.50f to 0.52f))
                    controlBindings = controlBindings + (id to "E")
                    saveControlLayout()
                }
                HorizontalDivider(color = c.stroke)
                Text("Layout", color = c.ink, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    listOf("classic", "compact", "minimal").forEach { layout ->
                        val selected = touchLayout == layout
                        Text(
                            layout.replaceFirstChar { it.uppercase() },
                            color = if (selected) c.onAccent else c.ink,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier
                                .clip(pixelShape(4.dp))
                                .background(if (selected) c.accent else c.cardHi)
                                .clickable(enabled = enabled) {
                                    touchLayout = layout
                                    onSaveTouchControls(JSONObject().put("layout", layout))
                                }
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                        )
                    }
                }
                SliderSetting("Button size", "${(touchSize * 100).toInt()}%", touchSize, 0.7f..1.5f, enabled,
                    onValueChange = { touchSize = it }, onFinished = { onSaveTouchControls(JSONObject().put("button_size", touchSize.toDouble())) })
                SliderSetting("Button opacity", "${(touchOpacity * 100).toInt()}%", touchOpacity, 0.3f..1.0f, enabled,
                    onValueChange = { touchOpacity = it }, onFinished = { onSaveTouchControls(JSONObject().put("opacity", touchOpacity.toDouble())) })
                SliderSetting("Look sensitivity", "${(lookSensitivity * 100).toInt()}%", lookSensitivity, 0.25f..2.0f, enabled,
                    onValueChange = { lookSensitivity = it }, onFinished = { onSaveTouchControls(JSONObject().put("look_sensitivity", lookSensitivity.toDouble())) })
                HorizontalDivider(color = c.stroke)
                ToggleRow("Left-handed controls", leftHanded, enabled) {
                    leftHanded = it
                    onSaveTouchControls(JSONObject().put("left_handed", it))
                }
                HorizontalDivider(color = c.stroke)
                ToggleRow("Auto-jump", autoJump, enabled) {
                    autoJump = it
                    onSaveTouchControls(JSONObject().put("auto_jump", it))
                }
                HorizontalDivider(color = c.stroke)
                ToggleRow("Hold to sprint", holdToSprint, enabled) {
                    holdToSprint = it
                    onSaveTouchControls(JSONObject().put("hold_to_sprint", it))
                }
                HorizontalDivider(color = c.stroke)
                ToggleRow("Haptic feedback", haptics, enabled) {
                    haptics = it
                    onSaveTouchControls(JSONObject().put("haptics", it))
                }
            }
        }

        item {
            Plate {
                SectionLabel("Updates")
                Spacer(Modifier.height(10.dp))
                ToggleRow("Install new versions automatically", autoInstall, enabled) {
                    autoInstall = it
                    onSave(JSONObject().put("install_new_versions", it))
                }
                HorizontalDivider(color = c.stroke)
                ToggleRow("Delete old versions automatically", prune, enabled) {
                    prune = it
                    onSave(JSONObject().put("prune_old_versions", it))
                }
                HorizontalDivider(color = c.stroke)
                ToggleRow("Show the opening", showOpening, enabled) {
                    showOpening = it
                    onSave(JSONObject().put("skip_opening", !it))
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    if (builds != null) "$builds builds listed by the core" else "Reading builds…",
                    color = c.muted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        item {
            Plate {
                SectionLabel("Appearance")
                Spacer(Modifier.height(10.dp))
                Text(
                    "The Night palette is the appearance currently connected to this Android preview.",
                    color = c.muted,
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(12.dp))
                Text("Accent colour", color = c.ink, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(Accent.entries) { option ->
                        Box(
                            Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(option.primary)
                                .border(
                                    width = if (option == accent) 2.dp else 1.dp,
                                    color = if (option == accent) c.ink else c.stroke,
                                    shape = CircleShape,
                                )
                                .clickable(enabled = enabled) {
                                    onAccent(option)
                                    onSave(JSONObject().put("accent", option.name.lowercase()))
                                },
                        )
                    }
                }
            }
        }

        item {
            Plate {
                SectionLabel("Network")
                Spacer(Modifier.height(8.dp))
                InfoRow(
                    icon = Icons.Outlined.Security,
                    title = "Proxy",
                    subtitle = settings?.proxy?.takeIf { it.isNotBlank() } ?: "No proxy configured",
                    trailing = "Core setting",
                    onClick = { onMessage("Proxy editing will be added with the Android network settings panel.") },
                )
            }
        }

        item {
            Plate {
                SectionLabel("Game")
                Spacer(Modifier.height(8.dp))
                InfoRow(
                    icon = Icons.Outlined.Gamepad,
                    title = "Game arguments",
                    subtitle = settings?.gameArgs?.takeIf { it.isNotBlank() } ?: "No extra arguments",
                    trailing = "Read-only",
                )
                HorizontalDivider(color = c.stroke)
                InfoRow(
                    icon = Icons.Outlined.Tune,
                    title = "Native mods",
                    subtitle = "Built from source by the desktop launcher",
                    trailing = "Desktop",
                    onClick = { onMessage("Native Rust mods need a compiler; that stays on desktop for now.") },
                )
                HorizontalDivider(color = c.stroke)
                InfoRow(
                    icon = Icons.Filled.Refresh,
                    title = "Game build for Android",
                    subtitle = if (platform?.asset == null) "No Android game archive is published yet" else platform.asset,
                    trailing = if (platform?.asset == null) "Missing" else "Available",
                    onClick = {
                        onMessage(
                            if (platform?.asset == null) "The launcher can manage data, but the Android game runtime is not published yet."
                            else "Android game archive: ${platform.asset}",
                        )
                    },
                )
            }
        }

        item {
            Plate {
                SectionLabel("AI")
                Spacer(Modifier.height(10.dp))
                ToggleRow("No AI", noAi, enabled) {
                    noAi = it
                    onSave(JSONObject().put("noAi", it))
                }
                HorizontalDivider(color = c.stroke)
                InfoRow(
                    icon = Icons.Outlined.Dns,
                    title = "Connect an AI agent",
                    subtitle = "horizon-ctl and the MCP server, as on desktop",
                    trailing = "Guide",
                    onClick = { onMessage("Agent control is exposed by the core; the guide is at playhorizon.dev/docs/agents.") },
                )
            }
        }

        item {
            Plate {
                SectionLabel("Data collection")
                Spacer(Modifier.height(10.dp))
                if (telemetry == null) {
                    Text("Reading telemetry preferences…", color = c.muted, style = MaterialTheme.typography.bodySmall)
                } else {
                    ToggleRow("Share anonymous usage", telemetry.any, enabled) { onTelemetry(it) }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "Opt-in only. The native core stores consent and queues reports for upload.",
                    color = c.muted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        item {
            Plate {
                SectionLabel("About")
                Spacer(Modifier.height(8.dp))
                InfoRow(icon = Icons.Outlined.Info, title = "Launcher", subtitle = "Android preview", trailing = "0.1.0-alpha")
                HorizontalDivider(color = c.stroke)
                InfoRow(icon = Icons.Outlined.Info, title = "Rust core", subtitle = "horizon-launcher, native", trailing = coreVersion)
                HorizontalDivider(color = c.stroke)
                InfoRow(
                    icon = Icons.Filled.Refresh,
                    title = "Engine updates",
                    subtitle = "Downloads the newest native libraries; applies after restarting the app",
                    trailing = updateStatus,
                    onClick = {
                        updateStatus = "Checking…"
                        updateScope.launch {
                            val result = withContext(Dispatchers.IO) { Updater.checkAndInstall(appContext) }
                            updateStatus = when (result) {
                                is Updater.Result.UpToDate -> "Up to date (#${result.build})"
                                is Updater.Result.Installed -> "Installed #${result.build}: restart the app"
                                is Updater.Result.Failed -> "Failed: ${result.message}"
                            }
                        }
                    },
                )
                HorizontalDivider(color = c.stroke)
                InfoRow(
                    icon = Icons.Outlined.Info,
                    title = "Platform",
                    subtitle = "JNI bridge to a cdylib",
                    trailing = "${platform?.os ?: "android"} · ${platform?.arch ?: "arm64"}",
                )
                HorizontalDivider(color = c.stroke)
                InfoRow(
                    icon = Icons.Outlined.Info,
                    title = "Data folder",
                    subtitle = dataDir,
                    trailing = "Private",
                )
            }
        }

        item {
            Text(
                "Horizon is an independent native Rust client. This Android preview contains no Minecraft code, assets or data.",
                color = c.muted,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
            )
        }

        item {
            Plate {
                SectionLabel("Minecraft files")
                Spacer(Modifier.height(8.dp))
                InfoRow(
                    icon = Icons.Outlined.FileUpload,
                    title = "Export Minecraft to Downloads",
                    subtitle = "Saves the installed game as ${MinecraftBackup.FILE_NAME} so you never have to download it again",
                    trailing = backupStatus.ifBlank { "Export" },
                    onClick = {
                        if (backupRunning) return@InfoRow
                        if (MinecraftBackup.installedVersions(appContext).isEmpty()) {
                            onMessage("Install Minecraft first, then export it.")
                            return@InfoRow
                        }
                        backupRunning = true
                        backupStatus = "Starting…"
                        updateScope.launch {
                            runCatching { MinecraftBackup.exportToDownloads(appContext) { backupStatus = it } }
                                .onSuccess { backupStatus = "Saved"; onMessage("Saved to $it") }
                                .onFailure { backupStatus = "Failed"; onMessage(it.message ?: "Could not export Minecraft") }
                            backupRunning = false
                        }
                    },
                )
                HorizontalDivider(color = c.stroke)
                InfoRow(
                    icon = Icons.Outlined.FileDownload,
                    title = "Restore Minecraft from backup",
                    subtitle = "Pick ${MinecraftBackup.FILE_NAME} from Downloads after a reinstall",
                    trailing = "Restore",
                    onClick = { if (!backupRunning) restorePicker.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) },
                )
            }
        }
    }
}

@Composable
private fun SliderSetting(
    title: String,
    valueLabel: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    onValueChange: (Float) -> Unit,
    onFinished: () -> Unit,
) {
    val c = LocalHorizon.current
    Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = c.ink, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(valueLabel, color = c.muted, style = MaterialTheme.typography.labelMedium)
        }
        Slider(value = value, onValueChange = onValueChange, valueRange = range, enabled = enabled, onValueChangeFinished = onFinished)
    }
}

@Composable
private fun ToggleRow(
    title: String,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    val c = LocalHorizon.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onChange(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            color = if (enabled) c.ink else c.muted,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = c.onAccent,
                checkedTrackColor = c.accent,
                uncheckedThumbColor = c.muted,
                uncheckedTrackColor = c.cardHi,
                uncheckedBorderColor = c.stroke,
            ),
        )
    }
}
