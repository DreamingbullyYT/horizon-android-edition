#!/usr/bin/env python3
"""Apply context-checked edits to App.kt, HorizonGameControls.kt, Updater.kt and CI.

The rest of this patch is provided as complete replacement files. Run from the
repository root after extracting the ZIP there:
    python tools/apply_horizon_patch.py
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def replace_once(path: str, old: str, new: str) -> None:
    file_path = ROOT / path
    if not file_path.is_file():
        raise SystemExit(f"Missing expected source file: {path}")
    source = file_path.read_text(encoding="utf-8")
    matches = source.count(old)
    if matches != 1:
        raise SystemExit(
            f"Refusing to edit {path}: expected one exact match, found {matches}. "
            "Your source differs from the version this patch targets."
        )
    file_path.write_text(source.replace(old, new, 1), encoding="utf-8")
    print(f"Updated {path}")


# App.kt: route legacy 'servers' launch intents into the combined Servers & Builds page.
replace_once(
    "app/src/main/java/dev/horizon/launcher/ui/App.kt",
    'import dev.horizon.launcher.importMinecraftFolder\n',
    'import dev.horizon.launcher.MinecraftBackup\nimport dev.horizon.launcher.importMinecraftFolder\n',
)
replace_once(
    "app/src/main/java/dev/horizon/launcher/ui/App.kt",
    '    "servers" -> Screen.Servers\n',
    '    "servers" -> Screen.Versions\n',
)
replace_once(
    "app/src/main/java/dev/horizon/launcher/ui/App.kt",
    '''        val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
            if (uri != null) scope.launch {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                busy = "Reading Minecraft folder"
                Core.setImportCache(context.cacheDir.absolutePath)
                val count = importMinecraftFolder(context, uri)
                say("Imported $count files into the verified download cache. Completing installation…")
                busy = "Preparing Minecraft 26.3"
                Core.installGame(context.cacheDir.absolutePath)
                gameReady = Core.gameReady()
            }.onFailure { say(it.message ?: "Could not import this Minecraft folder") }
                .also { busy = null; refreshLocal() }
            }
        }
''',
    "",
)
replace_once(
    "app/src/main/java/dev/horizon/launcher/ui/App.kt",
    '                                onChooseFolder = { folderPicker.launch(null) },\n',
    '''                                onImportFolder = { treeUri, documentId ->
                                    if (busy == null) scope.launch {
                                        busy = "Reading Minecraft folder"
                                        try {
                                            Core.setImportCache(context.cacheDir.absolutePath)
                                            val count = importMinecraftFolder(context, treeUri, documentId)
                                            say("Imported $count files into the verified download cache. Completing installation…")
                                            busy = "Preparing Minecraft 26.3"
                                            Core.installGame(context.cacheDir.absolutePath)
                                            gameReady = Core.gameReady()
                                            say("Minecraft 26.3 is ready to play.")
                                        } catch (error: Exception) {
                                            say(error.message ?: "Could not import this Minecraft folder")
                                        } finally {
                                            busy = null
                                            refreshLocal()
                                        }
                                    }
                                },
                                onImportBackup = { uri ->
                                    if (busy == null) scope.launch {
                                        busy = "Restoring Minecraft backup"
                                        try {
                                            val count = MinecraftBackup.restore(context, uri) { progress -> busy = progress }
                                            gameReady = Core.gameReady()
                                            if (!gameReady) {
                                                busy = "Finishing Minecraft setup"
                                                Core.setImportCache(context.cacheDir.absolutePath)
                                                Core.installGame(context.cacheDir.absolutePath)
                                                gameReady = Core.gameReady()
                                            }
                                            say("Restored $count Minecraft resource set(s).")
                                        } catch (error: Exception) {
                                            say(error.message ?: "Could not restore this Minecraft ZIP")
                                        } finally {
                                            busy = null
                                            refreshLocal()
                                        }
                                    }
                                },
''',
)
replace_once(
    "app/src/main/java/dev/horizon/launcher/ui/App.kt",
    '''                            Screen.Versions -> VersionsScreen(
                                releases = releases,
                                installed = installed,
                                busy = busy,
                                onInstall = { tag ->
''',
    '''                            Screen.Versions -> VersionsScreen(
                                servers = servers,
                                gameAvailable = platform?.asset != null,
                                releases = releases,
                                installed = installed,
                                busy = busy,
                                onAddServer = { addServer = true },
                                onRemoveServer = { index ->
                                    operate("Removing server") {
                                        Core.removeServer(index)
                                        null
                                    }
                                },
                                onJoinServer = { server ->
                                    if (platform?.asset == null) {
                                        say("Joining will be available when the Android game runtime is published.")
                                    } else {
                                        operate("Joining ${server.name}") {
                                            val result = Core.launch(server.ip)
                                            if (result.started) "Joining ${server.name} (${server.ip})"
                                            else "The core did not start a game process."
                                        }
                                    }
                                },
                                onInstall = { tag ->
''',
)
replace_once(
    "app/src/main/java/dev/horizon/launcher/ui/App.kt",
    '        Screen.entries.forEach { destination ->\n',
    '        Screen.entries.filterNot { it == Screen.Servers }.forEach { destination ->\n',
)

# HorizonGameControls.kt: default Zalith-style tap/place and hold/break behaviour.
replace_once(
    "app/src/main/java/dev/horizon/launcher/game/HorizonGameControls.kt",
    'import java.util.UUID\n',
    'import kotlinx.coroutines.launch\nimport java.util.UUID\n',
)
replace_once(
    "app/src/main/java/dev/horizon/launcher/game/HorizonGameControls.kt",
    '''    var tapAction by remember { mutableIntStateOf(prefs.getInt("gesture_tap_action", 0)) }
    var longPressAction by remember { mutableIntStateOf(prefs.getInt("gesture_long_action", 0)) }
    var longPressDelay by remember { mutableIntStateOf(prefs.getInt("gesture_long_delay", 500)) }
''',
    '''    var gesturesEnabled by remember { mutableStateOf(prefs.getBoolean("touch_gestures_enabled", true)) }
    var tapAction by remember { mutableIntStateOf(prefs.getInt("gesture_tap_action", 2).coerceIn(0, 2)) }
    var longPressAction by remember { mutableIntStateOf(prefs.getInt("gesture_long_action", 1).coerceIn(0, 2)) }
    var longPressDelay by remember { mutableIntStateOf(prefs.getInt("gesture_long_delay", 300).coerceIn(100, 1000)) }
''',
)
replace_once(
    "app/src/main/java/dev/horizon/launcher/game/HorizonGameControls.kt",
    '''        Modifier.fillMaxSize().pointerInput(Unit) {
            awaitPointerEventScope {
                val positions = mutableMapOf<Long, Offset>()
                val starts = mutableMapOf<Long, Offset>()
                val downAt = mutableMapOf<Long, Long>()
                val lookPointers = mutableSetOf<Long>()
                val gesturePointers = mutableSetOf<Long>()
                val ignored = mutableSetOf<Long>()
                val hotbarPointers = mutableSetOf<Long>()
''',
    '''        Modifier.fillMaxSize().pointerInput(Unit) {
            val pointerScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.currentCoroutineContext())
            awaitPointerEventScope {
                val positions = mutableMapOf<Long, Offset>()
                val starts = mutableMapOf<Long, Offset>()
                val downAt = mutableMapOf<Long, Long>()
                val lookPointers = mutableSetOf<Long>()
                val ignored = mutableSetOf<Long>()
                val hotbarPointers = mutableSetOf<Long>()
                val longPressJobs = mutableMapOf<Long, kotlinx.coroutines.Job>()
                val heldButtons = mutableMapOf<Long, String>()
''',
)
replace_once(
    "app/src/main/java/dev/horizon/launcher/game/HorizonGameControls.kt",
    '''                    if (editing) {
                        // The editor owns touch input; never steer the camera while dragging controls.
                        positions.clear(); starts.clear(); downAt.clear(); lookPointers.clear(); gesturePointers.clear(); ignored.clear(); tuner.reset()
                        continue
                    }
''',
    '''                    if (editing) {
                        // Release any synthetic mouse button before the editor takes over input.
                        longPressJobs.values.forEach { it.cancel() }
                        longPressJobs.clear()
                        heldButtons.values.forEach { GameInput.nativeSendControl(it, false) }
                        if (swipeLook) GameInput.nativeLookActive(false)
                        heldButtons.clear()
                        positions.clear(); starts.clear(); downAt.clear(); lookPointers.clear(); ignored.clear(); tuner.reset()
                        continue
                    }
''',
)
replace_once(
    "app/src/main/java/dev/horizon/launcher/game/HorizonGameControls.kt",
    '''                            if (grabbing && swipeLook) {
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
''',
    '''                            if (grabbing) {
                                if (prior == null) {
                                    gesturePointers += id
                                    if (change.position.x >= size.width * areaStart) {
                                        lookPointers += id
                                        tuner.reset()
                                        if (swipeLook) GameInput.nativeLookActive(true)
                                    }
                                    if (gesturesEnabled && longPressAction in 1..2) {
                                        longPressJobs[id] = pointerScope.launch {
                                            kotlinx.coroutines.delay(longPressDelay.toLong())
                                            if (id in gesturePointers && gesturesEnabled && longPressAction in 1..2) {
                                                val button = if (longPressAction == 1) "GLFW_MOUSE_BUTTON_LEFT" else "GLFW_MOUSE_BUTTON_RIGHT"
                                                GameInput.nativeSendControl(button, true)
                                                heldButtons[id] = button
                                            }
                                        }
                                    }
                                } else if (id in gesturePointers) {
                                    val start = starts[id]
                                    if (start != null && (start - change.position).getDistance() > 24f) {
                                        longPressJobs.remove(id)?.cancel()
                                        heldButtons.remove(id)?.let { GameInput.nativeSendControl(it, false) }
                                    }
                                    if (id in lookPointers && swipeLook) {
                                        val dx = (change.position.x - prior.x) * lookSensitivity * lookSensX * if (invertLookX) -1f else 1f
                                        val dy = (change.position.y - prior.y) * lookSensitivity * lookSensY * if (invertLookY) -1f else 1f
                                        tuner.apply(dx, dy, lookAccel, lookSmoothing)
                                        GameInput.nativeLook(tuner.outX, tuner.outY)
                                    }
                                }
                            } else {
                                GameInput.nativeMenuTouch(id, change.position.x, change.position.y, if (prior == null) 0 else 1)
                            }
                        } else if (!change.pressed && positions.remove(id) != null) {
                            hotbarPointers.remove(id)
                            occupied.remove(id)
                            val start = starts.remove(id)
                            val duration = SystemClock.elapsedRealtime() - (downAt.remove(id) ?: 0L)
                            if (gesturePointers.remove(id)) {
                                if (lookPointers.remove(id) && swipeLook) GameInput.nativeLookActive(lookPointers.isNotEmpty())
                                longPressJobs.remove(id)?.cancel()
                                val heldButton = heldButtons.remove(id)
                                val moved = start?.let { (it - change.position).getDistance() > 24f } ?: true
                                if (heldButton != null) {
                                    GameInput.nativeSendControl(heldButton, false)
                                } else if (!moved && gesturesEnabled) {
                                    val action = if (duration >= longPressDelay) longPressAction else tapAction
                                    if (action in 1..2) {
                                        val button = if (action == 1) "GLFW_MOUSE_BUTTON_LEFT" else "GLFW_MOUSE_BUTTON_RIGHT"
                                        GameInput.nativeSendControl(button, true)
                                        GameInput.nativeSendControl(button, false)
                                    }
                                }
                            } else if (!ignored.remove(id)) {
                                GameInput.nativeMenuTouch(id, change.position.x, change.position.y, 2)
                            }
''',
)
replace_once(
    "app/src/main/java/dev/horizon/launcher/game/HorizonGameControls.kt",
    '''                                3 -> {
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
''',
    '''                                3 -> {
                                    Text("Touch gestures", style = MaterialTheme.typography.titleSmall)
                                    ToggleRow("Enable tap and long-press actions", gesturesEnabled) {
                                        gesturesEnabled = it
                                        prefs.edit().putBoolean("touch_gestures_enabled", it).apply()
                                    }
                                    Text("Short tap action", style = MaterialTheme.typography.bodySmall)
                                    ChipChoice(listOf(0, 1, 2), tapAction, { listOf("None", "Break / Attack", "Place / Use")[it] }) {
                                        tapAction = it
                                        prefs.edit().putInt("gesture_tap_action", it).apply()
                                    }
                                    Text("Long-press action", style = MaterialTheme.typography.bodySmall)
                                    ChipChoice(listOf(0, 1, 2), longPressAction, { listOf("None", "Break / Attack", "Place / Use")[it] }) {
                                        longPressAction = it
                                        prefs.edit().putInt("gesture_long_action", it).apply()
                                    }
                                    Text("Long-press delay · ${longPressDelay} ms (default 300 ms)")
                                    Slider(
                                        value = longPressDelay.toFloat(),
                                        onValueChange = { longPressDelay = it.toInt() },
                                        onValueChangeFinished = { prefs.edit().putInt("gesture_long_delay", longPressDelay).apply() },
                                        valueRange = 100f..1000f,
                                        steps = 17,
                                    )
                                    Text("Gestures are active only while the game captures the pointer. A stationary short tap sends the tap action; reaching the long-press delay holds the selected mouse button until you lift your finger. Menus continue to receive normal touches.", style = MaterialTheme.typography.bodySmall)
                                }
''',
)


# android.yml: pass the optional Microsoft OAuth client ID secret into the Gradle tasks.
replace_once(
    ".github/workflows/android.yml",
    '    timeout-minutes: 90\n',
    '    timeout-minutes: 90\n    env:\n      HORIZON_MSA_CLIENT_ID: ${{ secrets.HORIZON_MSA_CLIENT_ID }}\n',
)

# Updater.kt: keep the in-app updater compatible with the app's API 26 minimum.
replace_once(
    "app/src/main/java/dev/horizon/launcher/data/Updater.kt",
    'import android.content.Context\n',
    'import android.content.Context\nimport android.os.Build\n',
)
replace_once(
    "app/src/main/java/dev/horizon/launcher/data/Updater.kt",
    '        val apkBuild = context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode\n',
    "        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)\n        @Suppress(\"DEPRECATION\")\n        val apkBuild = if (Build.VERSION.SDK_INT >= 28) packageInfo.longVersionCode else packageInfo.versionCode.toLong()\n",
)

print("Patch complete. Review the changed files and run the Android build.")
