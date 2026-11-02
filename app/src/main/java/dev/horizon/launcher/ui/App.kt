package dev.horizon.launcher.ui

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import dev.horizon.launcher.data.AccountsInfo
import dev.horizon.launcher.data.Core
import dev.horizon.launcher.data.InstalledVersion
import dev.horizon.launcher.data.InstanceInfo
import dev.horizon.launcher.data.ModInfo
import dev.horizon.launcher.data.PlatformInfo
import dev.horizon.launcher.data.ServerInfo
import dev.horizon.launcher.data.SettingsInfo
import dev.horizon.launcher.data.SignInInfo
import dev.horizon.launcher.data.TelemetryInfo
import dev.horizon.launcher.data.TouchControlsInfo
import dev.horizon.launcher.data.WorldInfo
import dev.horizon.launcher.data.ReleasesState
import dev.horizon.launcher.importMinecraftFolder
import dev.horizon.launcher.game.HorizonGameActivity
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale
import java.util.UUID

enum class Screen(val label: String, val icon: ImageVector) {
    Home("Home", Icons.Outlined.Home),
    Mods("Mods", Icons.Outlined.Extension),
    Servers("Servers", Icons.Outlined.Dns),
    Versions("Versions", Icons.Outlined.Inventory2),
    Settings("Settings", Icons.Outlined.Settings),
}

fun screenOf(value: String?): Screen = when (value?.lowercase()) {
    "mods" -> Screen.Mods
    "servers" -> Screen.Servers
    "versions" -> Screen.Versions
    "settings" -> Screen.Settings
    else -> Screen.Home
}

private fun copyPickedFile(context: android.content.Context, uri: Uri): File {
    val displayName = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        ?: uri.lastPathSegment?.substringAfterLast('/')
        ?: "imported-pack"
    val leaf = displayName.substringAfterLast('/').substringAfterLast('\\')
        .replace(Regex("[^A-Za-z0-9._-]"), "_")
        .let { name ->
            val dot = name.lastIndexOf('.')
            if (dot > 0) name.substring(0, dot) + name.substring(dot).lowercase(Locale.ROOT) else name
        }
    require(leaf.isNotBlank() && leaf != "." && leaf != "..") { "The selected file has an invalid name" }
    val folder = File(context.cacheDir, "game-imports/${UUID.randomUUID()}").apply { mkdirs() }
    val target = File(folder, leaf)
    val input = context.contentResolver.openInputStream(uri) ?: error("Could not open the selected file")
    input.use { source -> target.outputStream().use { sink ->
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val count = source.read(buffer)
            if (count < 0) break
            total += count
            require(total <= 512L * 1024 * 1024) { "Selected pack is larger than 512 MiB" }
            sink.write(buffer, 0, count)
        }
    } }
    return target
}

@Composable
fun HorizonApp(initialScreen: String, homeDir: String, loadError: String?) {
    val context = LocalContext.current
    var screen by remember { mutableStateOf(screenOf(initialScreen)) }
    var accent by remember { mutableStateOf(Accent.Violet) }
    var releases by remember { mutableStateOf<ReleasesState>(ReleasesState.Loading) }
    var platform by remember { mutableStateOf<PlatformInfo?>(null) }
    var instances by remember { mutableStateOf<List<InstanceInfo>>(emptyList()) }
    var accounts by remember { mutableStateOf<AccountsInfo?>(null) }
    var settings by remember { mutableStateOf<SettingsInfo?>(null) }
    var touchControls by remember { mutableStateOf<TouchControlsInfo?>(null) }
    var installed by remember { mutableStateOf<List<InstalledVersion>>(emptyList()) }
    var worlds by remember { mutableStateOf<List<WorldInfo>>(emptyList()) }
    var servers by remember { mutableStateOf<List<ServerInfo>>(emptyList()) }
    var mods by remember { mutableStateOf<List<ModInfo>>(emptyList()) }
    var shaderPacks by remember { mutableStateOf<List<String>>(emptyList()) }
    var telemetry by remember { mutableStateOf<TelemetryInfo?>(null) }
    var signIn by remember { mutableStateOf<SignInInfo?>(null) }
    var busy by remember { mutableStateOf<String?>(null) }
    var gameReady by remember { mutableStateOf(false) }
    var notes by remember { mutableStateOf(initialScreen.lowercase() == "notes") }
    var accountSheet by remember { mutableStateOf(false) }
    var newInstance by remember { mutableStateOf(false) }
    var addServer by remember { mutableStateOf(false) }
    var reload by remember { mutableStateOf(0) }

    HorizonTheme(accent) {
        val c = LocalHorizon.current
        val snackbar = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()
        val say: (String) -> Unit = { message -> scope.launch { snackbar.showSnackbar(message) } }

        suspend fun refreshLocal() {
            runCatching { Core.accounts() }.onSuccess { accounts = it }
            runCatching { Core.settings() }.onSuccess { settings = it }
            runCatching { Core.touchControls() }.onSuccess { touchControls = it }
            runCatching { Core.instances() }.onSuccess { instances = it }
            runCatching { Core.installed() }.onSuccess { installed = it }
            runCatching { Core.worlds() }.onSuccess { worlds = it }
            runCatching { Core.servers() }.onSuccess { servers = it }
            runCatching { Core.mods() }.onSuccess { mods = it }
            shaderPacks = File(homeDir, "game/shaderpacks").listFiles()
                ?.filter { it.isFile && it.extension.lowercase(Locale.ROOT) in setOf("zip", "hshader") }
                ?.map { it.name }
                ?.sortedBy(String::lowercase)
                ?: emptyList()
            runCatching { Core.telemetry() }.onSuccess { telemetry = it }
            runCatching { Core.signInInfo() }.onSuccess { signIn = it }
            runCatching { Core.gameReady() }.onSuccess { gameReady = it }
        }

        fun operate(label: String, block: suspend () -> String?) {
            if (busy != null) return
            scope.launch {
                busy = label
                try {
                    block()?.let(say)
                } catch (e: Exception) {
                    say(e.message ?: "Something went wrong")
                } finally {
                    busy = null
                }
                refreshLocal()
            }
        }

        val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
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
        var contentImportKind by remember { mutableStateOf("mod") }
        val contentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            if (uris.isNotEmpty()) scope.launch {
                val importingShaders = contentImportKind == "shader"
                busy = if (importingShaders) "Importing shader pack" else "Importing Horizon mod"
                try {
                    val imported = mutableListOf<String>()
                    for (uri in uris) {
                        val file = copyPickedFile(context, uri)
                        try {
                            imported += if (importingShaders) Core.importShader(file.absolutePath)
                            else Core.importMod(file.absolutePath)
                        } finally { file.delete() }
                    }
                    say(if (importingShaders) "Imported ${imported.joinToString()}. Choose it in the game's Shader Packs settings." else "Imported ${imported.joinToString()}. Restart the game to load the mod.")
                } catch (e: Exception) {
                    say(e.message ?: "Could not import selected files")
                } finally {
                    busy = null
                    refreshLocal()
                }
            }
        }
        LaunchedEffect(settings?.accent) {
            val savedAccent = settings?.accent ?: return@LaunchedEffect
            accent = Accent.entries.firstOrNull { it.name.equals(savedAccent, ignoreCase = true) } ?: Accent.Violet
        }

        LaunchedEffect(reload) {
            if (loadError != null) releases = ReleasesState.Failed(loadError)
            runCatching { Core.platform() }.onSuccess { platform = it }
            refreshLocal()
            if (loadError == null) {
                releases = runCatching { Core.releases() }
                    .fold(
                        onSuccess = { ReleasesState.Ready(it) },
                        onFailure = { ReleasesState.Failed(it.message ?: "Could not read the releases") },
                    )
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(c.sky))
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(340.dp)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(c.accent.copy(alpha = 0.22f), Color.Transparent),
                        )
                    )
            )
            Scaffold(
                containerColor = Color.Transparent,
                contentColor = c.ink,
                snackbarHost = { SnackbarHost(snackbar) },
                topBar = {
                    HorizonTopBar(
                        accountLabel = accounts?.player?.label ?: "Offline",
                        onAccount = { accountSheet = true },
                        onNotes = { notes = true },
                    )
                },
                bottomBar = {
                    HorizonBottomBar(screen) { screen = it }
                },
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
            ) { padding ->
                Box(Modifier.fillMaxSize().padding(padding)) {
                    AnimatedContent(
                        targetState = screen,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "screen",
                    ) { destination ->
                        when (destination) {
                            Screen.Home -> HomeScreen(
                                instances = instances,
                                accounts = accounts,
                                settings = settings,
                                installed = installed,
                                worlds = worlds,
                                mods = mods,
                                busy = busy,
                                gameAvailable = true,
                                gameReady = gameReady,
                                onInstall = {
                                    if (busy == null) scope.launch {
                                        busy = "Checking Minecraft version"
                                        val poll = scope.launch {
                                            while (true) {
                                                runCatching { Core.installProgress() }.onSuccess { p ->
                                                    val total = p.optLong("total")
                                                    val done = p.optLong("done")
                                                    val stage = p.optString("stage", "Installing Minecraft 26.3")
                                                    busy = if (total > 0) "$stage · ${done * 100 / total}%" else stage
                                                }
                                                kotlinx.coroutines.delay(650)
                                            }
                                        }
                                        try {
                                            Core.setImportCache(context.cacheDir.absolutePath)
                                            Core.installGame(context.cacheDir.absolutePath)
                                            gameReady = Core.gameReady()
                                            say("Minecraft 26.3 is ready to play offline as ${accounts?.player?.label ?: "Offline player"}.")
                                        } catch (e: Exception) { say(e.message ?: "Minecraft installation failed") }
                                        finally { poll.cancel(); busy = null; refreshLocal() }
                                    }
                                },
                                onChooseFolder = { folderPicker.launch(null) },
                                onPlay = {
                                    runCatching {
                                        context.startActivity(Intent(context, HorizonGameActivity::class.java))
                                    }.onFailure { say("Could not open Horizon game: ${it.message}") }
                                },
                                onNewInstance = { newInstance = true },
                                onSelectInstance = { id ->
                                    operate("Switching instance") {
                                        Core.selectInstance(id)
                                        null
                                    }
                                },
                                onDeleteInstance = { id ->
                                    operate("Deleting instance") {
                                        Core.deleteInstance(id)
                                        null
                                    }
                                },
                                onVersions = { screen = Screen.Versions },
                                onMods = { screen = Screen.Mods },
                                onAccounts = { accountSheet = true },
                            )
                            Screen.Mods -> ModsScreen(
                                mods = mods,
                                shaders = shaderPacks,
                                shaderFolder = File(homeDir, "game/shaderpacks").absolutePath,
                                onImportMod = { contentImportKind = "mod"; contentPicker.launch(arrayOf("*/*")) },
                                onImportShader = { contentImportKind = "shader"; contentPicker.launch(arrayOf("*/*")) },
                                onRemoveMod = { id -> operate("Removing mod") { Core.removeMod(id); "Removed $id. Restart the game to apply." } },
                                onRemoveShader = { name -> operate("Removing shader pack") { Core.removeShader(name); "Removed $name" } },
                            )
                            Screen.Servers -> ServersScreen(
                                servers = servers,
                                busy = busy,
                                gameAvailable = platform?.asset != null,
                                onAdd = { addServer = true },
                                onRemove = { index ->
                                    operate("Removing server") {
                                        Core.removeServer(index)
                                        null
                                    }
                                },
                                onJoin = { server ->
                                    if (platform?.asset == null) {
                                        say("Joining will be available when the Android game runtime is published.")
                                    } else {
                                        operate("Joining ${server.name}") {
                                            val result = Core.launch(server.ip)
                                            when {
                                                result.started -> "Joining ${server.name} (${server.ip})"
                                                else -> "The core did not start a game process."
                                            }
                                        }
                                    }
                                },
                                onMessage = say,
                            )
                            Screen.Versions -> VersionsScreen(
                                releases = releases,
                                installed = installed,
                                busy = busy,
                                onInstall = { tag ->
                                    operate("Installing $tag") {
                                        Core.installVersion(tag)
                                        "Installed $tag"
                                    }
                                },
                                onRemove = { tag ->
                                    operate("Removing $tag") {
                                        Core.removeVersion(tag)
                                        "Removed $tag"
                                    }
                                },
                                onRetry = {
                                    releases = ReleasesState.Loading
                                    reload++
                                },
                            )
                            Screen.Settings -> SettingsScreen(
                                accounts = accounts,
                                settings = settings,
                                touchControls = touchControls,
                                signIn = signIn,
                                telemetry = telemetry,
                                accent = accent,
                                onAccent = { accent = it },
                                platform = platform,
                                releases = releases,
                                homeDir = homeDir,
                                busy = busy,
                                onSave = { patch ->
                                    operate("Saving settings") {
                                        Core.saveSettings(patch)
                                        null
                                    }
                                },
                                onSaveTouchControls = { patch ->
                                    operate("Saving touch controls") {
                                        touchControls = Core.saveTouchControls(patch)
                                        "Touch control profile saved"
                                    }
                                },
                                onAccounts = { accountSheet = true },
                                onTelemetry = { on ->
                                    operate("Updating telemetry") {
                                        Core.setTelemetry(on)
                                        null
                                    }
                                },
                                onMessage = say,
                                onGameFilesChanged = { gameReady = runCatching { Core.gameReady() }.getOrDefault(false) },
                            )
                        }
                    }

                    if (busy != null) {
                        Row(
                            Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 8.dp)
                                .clip(pixelShape(4.dp))
                                .background(c.card.copy(alpha = 0.94f))
                                .border(1.dp, c.accentHi.copy(alpha = 0.4f), pixelShape(4.dp))
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            StatusDot(c.accentHi)
                            Spacer(Modifier.width(8.dp))
                            Text(busy ?: "", color = c.inkSoft, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }

        if (notes) {
            NotesOverlay(onClose = { notes = false })
        }

        if (accountSheet) {
            AccountsSheet(
                accounts = accounts,
                signIn = signIn,
                onAddOffline = { name ->
                    operate("Adding offline account") {
                        Core.addOffline(name)
                        "Added $name"
                    }
                },
                onPlayOffline = { name ->
                    operate("Playing as $name") {
                        Core.playOffline(name)
                        "Playing as $name"
                    }
                },
                onRemoveOffline = { name ->
                    operate("Removing $name") {
                        Core.removeOffline(name)
                        "Removed $name"
                    }
                },
                onGameName = {
                    operate("Using the game's own name") {
                        Core.playAsGameName()
                        null
                    }
                },
                onDismiss = { accountSheet = false },
            )
        }

        if (newInstance) {
            NewInstanceDialog(
                onCreate = { name ->
                    newInstance = false
                    operate("Creating instance") {
                        Core.createInstance(name)
                        "Created $name"
                    }
                },
                onDismiss = { newInstance = false },
            )
        }

        if (addServer) {
            AddServerDialog(
                onAdd = { name, address ->
                    addServer = false
                    operate("Saving server") {
                        Core.addServer(name, address)
                        "Saved ${name.ifBlank { address }}"
                    }
                },
                onDismiss = { addServer = false },
            )
        }
    }
}

@Composable
private fun HorizonTopBar(accountLabel: String, onAccount: () -> Unit, onNotes: () -> Unit) {
    val c = LocalHorizon.current
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(34.dp)
                .clip(pixelShape(4.dp))
                .background(Brush.verticalGradient(listOf(c.accentHi, c.accent))),
            contentAlignment = Alignment.Center,
        ) {
            Text("H", color = Color.White, fontFamily = Pixel, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "HORIZON",
                color = c.ink,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = Pixel,
                    fontSize = 21.sp,
                    letterSpacing = 1.5.sp,
                ),
            )
            Text(
                "ANDROID PREVIEW",
                color = c.accentHi,
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.2.sp),
            )
        }
        Row(
            Modifier
                .clip(pixelShape(4.dp))
                .background(c.card.copy(alpha = 0.8f))
                .clickable(onClick = onNotes)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Notes", color = c.inkSoft, style = MaterialTheme.typography.labelMedium)
        }
        Spacer(Modifier.width(8.dp))
        Row(
            Modifier
                .clip(pixelShape(4.dp))
                .background(c.card.copy(alpha = 0.8f))
                .clickable(onClick = onAccount)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Person, contentDescription = null, tint = c.accentHi, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(accountLabel, color = c.inkSoft, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}

@Composable
private fun HorizonBottomBar(current: Screen, onSelect: (Screen) -> Unit) {
    val c = LocalHorizon.current
    NavigationBar(
        containerColor = c.side.copy(alpha = 0.94f),
        contentColor = c.ink,
        tonalElevation = 0.dp,
    ) {
        Screen.entries.forEach { destination ->
            NavigationBarItem(
                selected = destination == current,
                onClick = { onSelect(destination) },
                icon = {
                    Icon(
                        destination.icon,
                        contentDescription = destination.label,
                        modifier = Modifier.size(22.dp),
                    )
                },
                label = {
                    Text(
                        destination.label,
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.4.sp),
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = c.ink,
                    selectedTextColor = c.ink,
                    indicatorColor = c.accent.copy(alpha = 0.26f),
                    unselectedIconColor = c.muted,
                    unselectedTextColor = c.muted,
                ),
            )
        }
    }
}
