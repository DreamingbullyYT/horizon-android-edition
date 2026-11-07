package dev.horizon.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.horizon.launcher.data.InstanceInfo
import dev.horizon.launcher.data.InstalledVersion
import dev.horizon.launcher.data.ModInfo
import dev.horizon.launcher.data.ServerInfo
import dev.horizon.launcher.data.SettingsInfo
import dev.horizon.launcher.data.WorldInfo
import java.util.Locale

private val Void = Color(0xFF090C0A)
private val Panel = Color(0xFF121813)
private val PanelRaised = Color(0xFF1A241D)
private val Acid = Color(0xFFD9FF58)
private val Flare = Color(0xFFFF845C)
private val Paper = Color(0xFFF1F3E8)
private val Ash = Color(0xFF929C8F)
private val Rule = Color(0xFF2B352D)

private enum class Destination(val label: String) {
    HOME("Home"), LIBRARY("Library"), MODS("Mods"), SERVERS("Servers"), SYSTEM("System")
}

@Composable
fun HorizonLauncherUi(
    status: String,
    minecraftReady: Boolean,
    toolchainReady: Boolean,
    instances: List<InstanceInfo>,
    selectedInstance: InstanceInfo?,
    worlds: List<WorldInfo>,
    servers: List<ServerInfo>,
    mods: List<ModInfo>,
    versions: List<InstalledVersion>,
    settings: SettingsInfo?,
    playerName: String,
    onPlay: () -> Unit,
    onInstall: () -> Unit,
    onImportFolder: () -> Unit,
    onRestoreBackup: () -> Unit,
    onCompileHnmod: () -> Unit,
    onImportBuild: () -> Unit,
    onExportBackup: () -> Unit,
    onShowBuildLog: () -> Unit,
    onRefresh: () -> Unit,
    onCreateInstance: (String) -> Unit,
    onSelectInstance: (String) -> Unit,
    onAddServer: (String, String) -> Unit,
    onRemoveServer: (Int) -> Unit,
    onImportMod: () -> Unit,
    onImportShader: () -> Unit,
    onRemoveMod: (String) -> Unit,
    onSaveSettings: (Boolean, Boolean, Boolean) -> Unit,
    onSetNoAi: (Boolean) -> Unit
) {
    var destination by remember { mutableStateOf(Destination.HOME) }

    Scaffold(
        containerColor = Void,
        bottomBar = {
            HorizonDock(destination = destination, onSelect = { destination = it })
        }
    ) { inset ->
        Box(Modifier.fillMaxSize().padding(inset).background(Void)) {
            when (destination) {
                Destination.HOME -> HomeScreen(
                    status, minecraftReady, toolchainReady, playerName, selectedInstance,
                    instances, worlds, servers.size, mods.size, onPlay, onInstall, onRefresh,
                    onSelectInstance, onCreateInstance, { destination = Destination.LIBRARY }
                )
                Destination.LIBRARY -> LibraryScreen(
                    minecraftReady, instances, selectedInstance, worlds, versions,
                    onInstall, onImportFolder, onRestoreBackup, onExportBackup,
                    onSelectInstance, onCreateInstance
                )
                Destination.MODS -> ModsScreen(
                    mods, toolchainReady, status, onCompileHnmod, onImportBuild,
                    onShowBuildLog, onImportMod, onImportShader, onRemoveMod
                )
                Destination.SERVERS -> ServersScreen(servers, onAddServer, onRemoveServer)
                Destination.SYSTEM -> SystemScreen(
                    status, toolchainReady, settings, onShowBuildLog, onRefresh,
                    onSaveSettings, onSetNoAi
                )
            }
        }
    }
}

@Composable
private fun HorizonDock(destination: Destination, onSelect: (Destination) -> Unit) {
    Surface(color = Color(0xFF0E130F), shadowElevation = 18.dp) {
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding()
                .padding(start = 10.dp, end = 10.dp, top = 9.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Destination.values().forEach { item ->
                val selected = item == destination
                val icon = when (item) {
                    Destination.HOME -> Icons.Default.Home
                    Destination.LIBRARY -> Icons.Default.ViewList
                    Destination.MODS -> Icons.Default.Extension
                    Destination.SERVERS -> Icons.Default.Dns
                    Destination.SYSTEM -> Icons.Default.Tune
                }
                Column(
                    Modifier.clip(RoundedCornerShape(10.dp)).clickable { onSelect(item) }
                        .padding(horizontal = 11.dp, vertical = 5.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(icon, contentDescription = item.label, tint = if (selected) Acid else Ash, modifier = Modifier.size(19.dp))
                    Text(item.label.uppercase(), color = if (selected) Acid else Ash, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.7.sp)
                    Box(Modifier.width(if (selected) 13.dp else 4.dp).height(2.dp).clip(CircleShape).background(if (selected) Acid else Rule))
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(
    status: String,
    ready: Boolean,
    toolchainReady: Boolean,
    playerName: String,
    selected: InstanceInfo?,
    instances: List<InstanceInfo>,
    worlds: List<WorldInfo>,
    serverCount: Int,
    modCount: Int,
    onPlay: () -> Unit,
    onInstall: () -> Unit,
    onRefresh: () -> Unit,
    onSelectInstance: (String) -> Unit,
    onCreateInstance: (String) -> Unit,
    onLibrary: () -> Unit
) {
    var showCreate by remember { mutableStateOf(false) }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 23.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("HORIZON  /  FIELD CONSOLE", color = Acid, fontSize = 9.sp, fontFamily = FontFamily.Monospace, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold)
                    Text("Good to see you.", color = Paper, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(if (ready) Acid else Flare))
                    Text(if (ready) "CORE ONLINE" else "CORE IDLE", color = Ash, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(13.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("YOUR NEXT", color = Ash, fontSize = 9.sp, fontFamily = FontFamily.Monospace, letterSpacing = 2.sp)
                        Text("HORIZON.", color = Paper, fontSize = 43.sp, lineHeight = 43.sp, fontWeight = FontWeight.Black, letterSpacing = (-2).sp)
                        Text(selected?.version ?: if (ready) "INSTANCE READY" else "NO GAME INSTANCE", color = Acid, fontSize = 10.sp, fontFamily = FontFamily.Monospace, letterSpacing = 1.sp)
                    }
                    HorizonOrb(Modifier.size(138.dp))
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(Rule))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(selected?.name ?: "Minecraft Java", color = Paper, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text(if (ready) "GAME FILES DETECTED" else "INSTALL OR IMPORT YOUR GAME", color = Ash, fontSize = 8.sp, fontFamily = FontFamily.Monospace, letterSpacing = 0.7.sp)
                    }
                    Text("01 / 05", color = Ash, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                }
                Button(
                    onClick = if (ready) onPlay else onInstall,
                    modifier = Modifier.fillMaxWidth().height(58.dp),
                    shape = RoundedCornerShape(7.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Acid, contentColor = Void)
                ) {
                    Icon(if (ready) Icons.Default.PlayArrow else Icons.Default.Download, null, modifier = Modifier.size(23.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(if (ready) "ENTER WORLD" else "INSTALL MINECRAFT", fontSize = 13.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
                    Spacer(Modifier.weight(1f))
                    Icon(Icons.Default.ArrowForward, null)
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
                SectionLine("ACTIVE PROFILE", "01")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(playerName.ifBlank { "Local profile" }, color = Paper, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("PLAYER IDENTITY", color = Ash, fontSize = 8.sp, fontFamily = FontFamily.Monospace, letterSpacing = 1.sp)
                    }
                    TextButton(onClick = onRefresh) { Text("SYNC  ↻", color = Acid, fontSize = 10.sp, fontFamily = FontFamily.Monospace) }
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionLine("INSTANCE SELECTOR", "02")
                if (instances.isEmpty()) {
                    QuietLine("No separate instances are registered. The default game data remains available.")
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(instances, key = { it.id }) { instance ->
                            val active = instance.id == selected?.id
                            Surface(
                                modifier = Modifier.widthIn(min = 132.dp).clickable { onSelectInstance(instance.id) },
                                color = if (active) PanelRaised else Panel,
                                shape = RoundedCornerShape(7.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, if (active) Acid.copy(alpha = 0.75f) else Rule)
                            ) {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Text(if (active) "● SELECTED" else "○ INSTANCE", color = if (active) Acid else Ash, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                                    Text(instance.name, color = Paper, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(instance.version ?: "Version not set", color = Ash, fontSize = 9.sp)
                                }
                            }
                        }
                    }
                }
                TextButton(onClick = { showCreate = true }, contentPadding = PaddingValues(0.dp)) {
                    Icon(Icons.Default.Add, null, tint = Acid, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("CREATE INSTANCE", color = Acid, fontSize = 9.sp, fontFamily = FontFamily.Monospace, letterSpacing = 1.sp)
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionLine("WORLD SNAPSHOT", "03")
                if (worlds.isEmpty()) {
                    QuietLine("No saved worlds were returned by the game core.")
                } else {
                    worlds.take(3).forEachIndexed { index, world ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text((index + 1).toString().padStart(2, '0'), color = Acid, fontSize = 10.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.width(31.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(world.name, color = Paper, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                Text(listOfNotNull(world.version, world.mode.takeIf { it.isNotBlank() }).joinToString("  /  "), color = Ash, fontSize = 9.sp)
                            }
                            Text(formatBytes(world.size), color = Ash, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                        }
                        if (index != worlds.take(3).lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(Rule))
                    }
                    TextButton(onClick = onLibrary, contentPadding = PaddingValues(0.dp)) { Text("OPEN FULL LIBRARY  →", color = Acid, fontSize = 9.sp, fontFamily = FontFamily.Monospace) }
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionLine("SYSTEM READOUT", "04")
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Readout("WORLDS", worlds.size.toString(), Modifier.weight(1f))
                    Readout("MODS", modCount.toString(), Modifier.weight(1f))
                    Readout("SERVERS", serverCount.toString(), Modifier.weight(1f))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(ready)
                    Spacer(Modifier.width(7.dp))
                    Text(if (ready) "GAME DATA READY" else "GAME DATA NOT READY", color = Ash, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                    Spacer(Modifier.weight(1f))
                    StatusDot(toolchainReady)
                    Spacer(Modifier.width(7.dp))
                    Text(if (toolchainReady) "HNMOD TOOLCHAIN" else "HNMOD TOOLCHAIN OFFLINE", color = Ash, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                }
                if (status.isNotBlank()) Text(status, color = Ash, fontSize = 9.sp, lineHeight = 13.sp, maxLines = 3)
            }
        }
    }
    if (showCreate) InstanceDialog(
        onDismiss = { showCreate = false },
        onCreate = { name -> showCreate = false; onCreateInstance(name) }
    )
}

@Composable
private fun HorizonOrb(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val c = Offset(size.width * 0.51f, size.height * 0.5f)
        val r = size.minDimension * 0.36f
        drawCircle(Brush.radialGradient(listOf(Color(0xFF34452A), Color(0xFF172219), Void), center = c, radius = r * 1.55f), r * 1.55f, c)
        drawCircle(Acid.copy(alpha = 0.85f), r, c, style = Stroke(width = 1.4.dp.toPx()))
        drawCircle(Acid.copy(alpha = 0.22f), r * 0.72f, c, style = Stroke(width = 1.dp.toPx()))
        drawCircle(Flare.copy(alpha = 0.9f), r * 0.11f, c + Offset(r * 0.55f, -r * 0.2f))
        drawLine(Acid.copy(alpha = 0.7f), Offset(c.x - r * 1.3f, c.y), Offset(c.x + r * 1.3f, c.y), 1.dp.toPx())
        drawLine(Ash.copy(alpha = 0.5f), Offset(c.x, c.y - r * 1.2f), Offset(c.x, c.y + r * 1.2f), 0.8.dp.toPx())
        for (i in 0..4) {
            val y = c.y - r * 0.7f + i * r * 0.35f
            drawLine(Acid.copy(alpha = 0.16f), Offset(c.x - r * 0.8f, y), Offset(c.x + r * 0.8f, y), 0.6.dp.toPx())
        }
        drawArc(Acid, 212f, 112f, false, topLeft = Offset(c.x - r * 1.22f, c.y - r * 1.22f), size = androidx.compose.ui.geometry.Size(r * 2.44f, r * 2.44f), style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
    }
}

@Composable
private fun LibraryScreen(
    ready: Boolean,
    instances: List<InstanceInfo>,
    selected: InstanceInfo?,
    worlds: List<WorldInfo>,
    versions: List<InstalledVersion>,
    onInstall: () -> Unit,
    onImport: () -> Unit,
    onRestore: () -> Unit,
    onExport: () -> Unit,
    onSelect: (String) -> Unit,
    onCreate: (String) -> Unit
) {
    var showCreate by remember { mutableStateOf(false) }
    PageShell("LIBRARY", "Everything you\\'ve built.", "INSTANCES  /  WORLDS  /  VERSIONS") {
        SectionLine("GAME DATA", "01")
        Readout(if (ready) "INSTALLATION" else "GAME DATA", if (ready) "READY" else "NOT FOUND", Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            SmallAction("INSTALL", Icons.Default.Download, onInstall, Modifier.weight(1f))
            SmallAction("IMPORT", Icons.Default.FolderOpen, onImport, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            SmallAction("RESTORE", Icons.Default.Restore, onRestore, Modifier.weight(1f))
            SmallAction("BACKUP", Icons.Default.Archive, onExport, Modifier.weight(1f))
        }
        SectionLine("INSTANCES", "02")
        TextButton(onClick = { showCreate = true }, contentPadding = PaddingValues(0.dp)) {
            Icon(Icons.Default.Add, null, tint = Acid)
            Text("NEW INSTANCE", color = Acid, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        }
        if (instances.isEmpty()) QuietLine("No instances are registered in the native core.")
        instances.forEach { instance ->
            DataRow(
                title = instance.name,
                detail = (instance.version ?: "Version not set") + "  ·  " + formatDuration(instance.playedSecs),
                trailing = if (instance.id == selected?.id) "ACTIVE" else "SELECT",
                active = instance.id == selected?.id,
                onClick = { onSelect(instance.id) }
            )
        }
        SectionLine("SAVED WORLDS", "03")
        if (worlds.isEmpty()) QuietLine("The core returned no saved worlds.")
        worlds.forEach { world ->
            DataRow(world.name, listOfNotNull(world.version, world.mode.takeIf { it.isNotBlank() }).joinToString("  /  "), formatBytes(world.size))
        }
        SectionLine("INSTALLED VERSIONS", "04")
        if (versions.isEmpty()) QuietLine("No separately installed engine versions were reported.")
        versions.forEach { version ->
            DataRow(version.version, version.tag, if (version.prepared) "PREPARED" else "RAW", version.prepared)
        }
    }
    if (showCreate) InstanceDialog(onDismiss = { showCreate = false }, onCreate = { showCreate = false; onCreate(it) })
}

@Composable
private fun ModsScreen(
    mods: List<ModInfo>,
    toolchainReady: Boolean,
    status: String,
    onCompile: () -> Unit,
    onImportBuild: () -> Unit,
    onLog: () -> Unit,
    onImportMod: () -> Unit,
    onImportShader: () -> Unit,
    onRemoveMod: (String) -> Unit
) {
    PageShell("MOD WORKSHOP", "Change the rules.", "NATIVE EXTENSIONS  /  BUILD PIPELINE") {
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Readout("INSTALLED", mods.size.toString(), Modifier.weight(1f))
            Readout("HNMOD", if (toolchainReady) "READY" else "OFFLINE", Modifier.weight(1f))
        }
        SectionLine("BUILD CONSOLE", "01")
        Surface(color = Panel, shape = RoundedCornerShape(7.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Rule)) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(toolchainReady)
                    Spacer(Modifier.width(7.dp))
                    Text(if (toolchainReady) "COMPILER AVAILABLE" else "COMPILER WILL SET UP ON FIRST BUILD", color = if (toolchainReady) Acid else Ash, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                }
                Text("HNMod / Rust pipeline", color = Paper, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("Compile trusted .hnmod source with the embedded Horizon toolchain. Build output is streamed to the live log.", color = Ash, fontSize = 10.sp, lineHeight = 15.sp)
                Button(onClick = onCompile, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(6.dp), colors = ButtonDefaults.buttonColors(containerColor = Acid, contentColor = Void)) {
                    Icon(Icons.Default.Terminal, null)
                    Spacer(Modifier.width(7.dp))
                    Text("COMPILE HNMOD", fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                }
                OutlinedButton(onClick = onLog, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(6.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = Acid)) {
                    Icon(Icons.Default.ReceiptLong, null)
                    Spacer(Modifier.width(7.dp))
                    Text("OPEN LIVE BUILD LOG", fontSize = 10.sp, letterSpacing = 0.8.sp)
                }
                if (status.isNotBlank()) Text(status, color = Ash, fontSize = 9.sp, maxLines = 3)
            }
        }
        SectionLine("NATIVE LIBRARY", "02")
        SmallAction("IMPORT COMPILED .SO", Icons.Default.FileDownload, onImportBuild, Modifier.fillMaxWidth())
        SectionLine("HMOD PACKAGES", "03")
        SmallAction("IMPORT MOD FILE", Icons.Default.Add, onImportMod, Modifier.fillMaxWidth())
        if (mods.isEmpty()) QuietLine("No HMod entries are currently reported by the native core.")
        mods.forEach { mod ->
            DataRow(mod.name, mod.file, if (mod.enabled) "ACTIVE" else "DISABLED", mod.enabled, onClick = { if (mod.id.isNotBlank()) onRemoveMod(mod.id) })
        }
        SectionLine("SHADERS", "04")
        SmallAction("IMPORT SHADER PACK", Icons.Default.WbSunny, onImportShader, Modifier.fillMaxWidth())
        QuietLine("Shader import is connected. The current native bridge does not expose a shader inventory yet.")
    }
}

@Composable
private fun ServersScreen(
    servers: List<ServerInfo>,
    onAdd: (String, String) -> Unit,
    onRemove: (Int) -> Unit
) {
    var showAdd by remember { mutableStateOf(false) }
    PageShell("MULTIPLAYER", "Find your people.", "SAVED SERVER LIST  /  NATIVE CORE") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Readout("SAVED", servers.size.toString(), Modifier.weight(1f))
            Button(onClick = { showAdd = true }, shape = RoundedCornerShape(6.dp), colors = ButtonDefaults.buttonColors(containerColor = Acid, contentColor = Void)) {
                Icon(Icons.Default.Add, null)
                Text("ADD SERVER", fontWeight = FontWeight.Black, fontSize = 10.sp)
            }
        }
        SectionLine("SERVER BOOKMARKS", "01")
        if (servers.isEmpty()) QuietLine("Your native server list is empty. Add a name and address to save a destination.")
        servers.forEachIndexed { index, server ->
            Row(
                Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(7.dp)).padding(13.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(server.name, color = Paper, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(server.ip, color = Acid, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                }
                IconButton(onClick = { onRemove(index) }) { Icon(Icons.Default.DeleteOutline, "Remove server", tint = Flare) }
            }
        }
        QuietLine("Server entries are read from and written to Horizon's native Minecraft server list. Removing one deletes it from that list.")
    }
    if (showAdd) ServerDialog(onDismiss = { showAdd = false }, onAdd = { name, address -> showAdd = false; onAdd(name, address) })
}

@Composable
private fun SystemScreen(
    status: String,
    toolchainReady: Boolean,
    settings: SettingsInfo?,
    onLog: () -> Unit,
    onRefresh: () -> Unit,
    onSaveSettings: (Boolean, Boolean, Boolean) -> Unit,
    onSetNoAi: (Boolean) -> Unit
) {
    var installNew by remember(settings) { mutableStateOf(settings?.installNewVersions ?: true) }
    var pruneOld by remember(settings) { mutableStateOf(settings?.pruneOldVersions ?: true) }
    var skipOpening by remember(settings) { mutableStateOf(settings?.skipOpening ?: false) }
    var noAi by remember(settings) { mutableStateOf(settings?.noAi ?: false) }
    PageShell("SYSTEM", "Make it yours.", "RUNTIME  /  BEHAVIOUR  /  DIAGNOSTICS") {
        SectionLine("ENGINE POLICY", "01")
        SettingLine("Install new engine versions", "Allow the launcher to prepare newer versions.", installNew) { installNew = it; onSaveSettings(it, pruneOld, skipOpening) }
        SettingLine("Prune old versions", "Remove outdated engine data when supported.", pruneOld) { pruneOld = it; onSaveSettings(installNew, it, skipOpening) }
        SettingLine("Skip opening screen", "Use the engine's configured startup behaviour.", skipOpening) { skipOpening = it; onSaveSettings(installNew, pruneOld, it) }
        SettingLine("Disable AI features", "Native launcher preference.", noAi) { noAi = it; onSetNoAi(it) }
        SectionLine("EMBEDDED TOOLCHAIN", "02")
        DataRow("HNMod compiler", "Private app-managed Termux/PRoot runtime", if (toolchainReady) "READY" else "NOT INSTALLED", toolchainReady)
        SmallAction("VIEW HNMOD BUILD LOG", Icons.Default.ReceiptLong, onLog, Modifier.fillMaxWidth())
        SectionLine("DIAGNOSTICS", "03")
        SmallAction("REFRESH NATIVE STATE", Icons.Default.Refresh, onRefresh, Modifier.fillMaxWidth())
        Surface(color = Panel, shape = RoundedCornerShape(7.dp)) {
            Text(status.ifBlank { "No runtime message." }, color = Ash, fontSize = 10.sp, lineHeight = 15.sp, modifier = Modifier.fillMaxWidth().padding(13.dp))
        }
        QuietLine("Graphics backend and performance tuning are not shown here yet because the current Android bridge does not expose those controls.")
    }
}

@Composable
private fun PageShell(eyebrow: String, title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 21.dp, end = 21.dp, top = 24.dp, bottom = 26.dp),
        verticalArrangement = Arrangement.spacedBy(17.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("HORIZON  /  " + eyebrow, color = Acid, fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                Text(title, color = Paper, fontSize = 31.sp, fontWeight = FontWeight.Black, letterSpacing = (-0.7).sp)
                Text(subtitle, color = Ash, fontSize = 8.sp, fontFamily = FontFamily.Monospace, letterSpacing = 1.sp)
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
        }
    }
}

@Composable
private fun SectionLine(label: String, index: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Paper, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, letterSpacing = 1.1.sp)
        Spacer(Modifier.width(9.dp))
        Box(Modifier.weight(1f).height(1.dp).background(Rule))
        Spacer(Modifier.width(8.dp))
        Text(index, color = Acid, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun Readout(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.background(Panel, RoundedCornerShape(6.dp)).padding(horizontal = 12.dp, vertical = 11.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, color = Ash, fontSize = 8.sp, fontFamily = FontFamily.Monospace, letterSpacing = 0.6.sp)
        Text(value, color = Paper, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun StatusDot(on: Boolean) {
    Box(Modifier.size(7.dp).clip(CircleShape).background(if (on) Acid else Flare))
}

@Composable
private fun QuietLine(text: String) {
    Row(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(6.dp)).padding(13.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.padding(top = 4.dp).size(5.dp).clip(CircleShape).background(Flare))
        Spacer(Modifier.width(9.dp))
        Text(text, color = Ash, fontSize = 10.sp, lineHeight = 15.sp)
    }
}

@Composable
private fun DataRow(
    title: String,
    detail: String,
    trailing: String,
    active: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    Row(
        Modifier.fillMaxWidth()
            .background(Panel, RoundedCornerShape(6.dp))
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = Paper, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(detail.ifBlank { "—" }, color = Ash, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Text(trailing, color = if (active) Acid else Ash, fontSize = 8.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun SmallAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 46.dp),
        shape = RoundedCornerShape(6.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Rule),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Acid),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Icon(icon, null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(7.dp))
        Text(label, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.4.sp, maxLines = 1)
    }
}

@Composable
private fun SettingLine(label: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(6.dp)).padding(start = 12.dp, end = 7.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(label, color = Paper, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(detail, color = Ash, fontSize = 9.sp, lineHeight = 13.sp)
        }
        Switch(checked = checked, onCheckedChange = onChange, colors = SwitchDefaults.colors(checkedThumbColor = Void, checkedTrackColor = Acid))
    }
}

@Composable
private fun InstanceDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PanelRaised,
        title = { Text("New instance", color = Paper) },
        text = {
            OutlinedTextField(
                value = name, onValueChange = { name = it }, singleLine = true,
                label = { Text("Instance name") }, placeholder = { Text("My survival setup") }
            )
        },
        confirmButton = { TextButton(onClick = { if (name.isNotBlank()) onCreate(name.trim()) }, enabled = name.isNotBlank()) { Text("CREATE", color = Acid) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("CANCEL", color = Ash) } }
    )
}

@Composable
private fun ServerDialog(onDismiss: () -> Unit, onAdd: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PanelRaised,
        title = { Text("Save server", color = Paper) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text("Server name") })
                OutlinedTextField(value = address, onValueChange = { address = it }, singleLine = true, label = { Text("Address / IP") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
            }
        },
        confirmButton = { TextButton(onClick = { if (name.isNotBlank() && address.isNotBlank()) onAdd(name.trim(), address.trim()) }, enabled = name.isNotBlank() && address.isNotBlank()) { Text("SAVE", color = Acid) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("CANCEL", color = Ash) } }
    )
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return bytes.toString() + " B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb)
    return String.format(Locale.US, "%.1f MB", kb / 1024.0)
}

private fun formatDuration(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    return if (hours > 0) hours.toString() + "h " + minutes + "m" else minutes.toString() + "m played"
}
