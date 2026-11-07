package dev.horizon.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Ink = Color(0xFF090D13)
private val Surface = Color(0xFF111923)
private val SurfaceRaised = Color(0xFF182330)
private val Cyan = Color(0xFF62E6D4)
private val Muted = Color(0xFF91A1B2)
private val White = Color(0xFFF3F7FA)

@Composable
fun HorizonLauncherUi(
    status: String,
    minecraftReady: Boolean,
    toolchainReady: Boolean,
    onPlay: () -> Unit,
    onInstall: () -> Unit,
    onImportFolder: () -> Unit,
    onRestoreBackup: () -> Unit,
    onCompileHnmod: () -> Unit,
    onImportBuild: () -> Unit,
    onExportBackup: () -> Unit,
    onShowBuildLog: () -> Unit
) {
    var selected by remember { mutableStateOf(0) }
    val tabs = listOf("Home", "Library", "Mods", "Servers", "Settings")
    Scaffold(
        containerColor = Ink,
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF0D141D), contentColor = Muted) {
                val icons = listOf(Icons.Default.Home, Icons.Default.ViewInAr, Icons.Default.Extension, Icons.Default.Dns, Icons.Default.Settings)
                tabs.forEachIndexed { index, label ->
                    NavigationBarItem(
                        selected = selected == index,
                        onClick = { selected = index },
                        icon = { Icon(icons[index], contentDescription = label) },
                        label = { Text(label, fontSize = 10.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Cyan,
                            selectedTextColor = Cyan,
                            indicatorColor = Color(0xFF193A3B),
                            unselectedIconColor = Muted,
                            unselectedTextColor = Muted
                        )
                    )
                }
            }
        }
    ) { padding ->
        when (selected) {
            0 -> HomeScreen(
                modifier = Modifier.padding(padding),
                status = status,
                minecraftReady = minecraftReady,
                toolchainReady = toolchainReady,
                onPlay = onPlay,
                onInstall = onInstall,
                onLibrary = { selected = 1 },
                onMods = { selected = 2 },
                onServers = { selected = 3 },
                onSettings = { selected = 4 }
            )
            1 -> LibraryScreen(Modifier.padding(padding), minecraftReady, onInstall, onImportFolder, onRestoreBackup, onExportBackup)
            2 -> ModsScreen(Modifier.padding(padding), onCompileHnmod, onImportBuild, onShowBuildLog)
            3 -> PlaceholderScreen(Modifier.padding(padding), "Servers", "Your Minecraft worlds, always one tap away.", Icons.Default.Dns,
                "Saved servers and connection status will appear here once server management is connected.")
            else -> SettingsScreen(Modifier.padding(padding), status, toolchainReady, onShowBuildLog)
        }
    }
}

@Composable
private fun HomeScreen(
    modifier: Modifier,
    status: String,
    minecraftReady: Boolean,
    toolchainReady: Boolean,
    onPlay: () -> Unit,
    onInstall: () -> Unit,
    onLibrary: () -> Unit,
    onMods: () -> Unit,
    onServers: () -> Unit,
    onSettings: () -> Unit
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("HORIZON", color = Cyan, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
                    Text("Your world. Your way.", color = White, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                }
                Box(Modifier.size(44.dp).clip(CircleShape).background(SurfaceRaised), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Person, null, tint = Cyan)
                }
            }
        }
        item { SkinShowcaseCard() }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionHeading("GAME", "READY WHEN YOU ARE")
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFF183B3C), Color(0xFF172332), Color(0xFF111923))))
                        .padding(18.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Minecraft Java", color = White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                                Text(if (minecraftReady) "Installation detected" else "No installation yet", color = Muted, fontSize = 12.sp)
                            }
                            Surface(color = if (minecraftReady) Color(0xFF173D39) else Color(0xFF3A3020), shape = CircleShape) {
                                Text(if (minecraftReady) "READY" else "SETUP", color = if (minecraftReady) Cyan else Color(0xFFFFD18A), fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                            }
                        }
                        Button(
                            onClick = if (minecraftReady) onPlay else onInstall,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(15.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Cyan, contentColor = Ink)
                        ) {
                            Icon(if (minecraftReady) Icons.Default.PlayArrow else Icons.Default.Download, null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (minecraftReady) "PLAY NOW" else "INSTALL MINECRAFT", fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
                        }
                    }
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionHeading("QUICK ACCESS", "PICK UP WHERE YOU LEFT OFF")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QuickTile("Library", Icons.Default.ViewInAr, Modifier.weight(1f), onLibrary)
                    QuickTile("Mods", Icons.Default.Extension, Modifier.weight(1f), onMods)
                    QuickTile("Servers", Icons.Default.Dns, Modifier.weight(1f), onServers)
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeading("SYSTEM", "RUNTIME STATUS")
                Surface(color = Surface, shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatusLine("Minecraft data", if (minecraftReady) "Installed" else "Not installed", minecraftReady)
                        StatusLine("HNMod toolchain", if (toolchainReady) "Ready" else "Not installed", toolchainReady)
                        HorizontalDivider(color = Color(0xFF263341))
                        Text(status.ifBlank { "Horizon runtime initialized." }, color = Muted, fontSize = 11.sp, lineHeight = 16.sp, maxLines = 3)
                        TextButton(onClick = onSettings, contentPadding = PaddingValues(0.dp)) { Text("Open settings  →", color = Cyan) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SkinShowcaseCard() {
    Surface(color = Surface, shape = RoundedCornerShape(24.dp)) {
        Row(
            Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(Color(0xFF172630), Color(0xFF111923))))
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("PLAYER PROFILE", color = Cyan, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                Text("Make it yours.", color = White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                Text("Your Minecraft identity, in one place.", color = Muted, fontSize = 11.sp, lineHeight = 15.sp)
                Text("SKIN VIEWER · COMING NEXT", color = Color(0xFF8DA6B2), fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            }
            PixelPlayer(Modifier.width(104.dp).height(126.dp))
        }
    }
}

@Composable
private fun PixelPlayer(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val unit = size.minDimension / 16f
        fun pixel(x: Int, y: Int, w: Int, h: Int, color: Color) {
            drawRect(color, Offset(x * unit, y * unit), androidx.compose.ui.geometry.Size(w * unit, h * unit))
        }
        // Stylized placeholder only; the real viewer will render the user's skin texture.
        pixel(5, 1, 6, 6, Color(0xFFB9784E))
        pixel(5, 1, 6, 2, Color(0xFF39271F))
        pixel(4, 2, 2, 4, Color(0xFF39271F))
        pixel(10, 2, 2, 3, Color(0xFF39271F))
        pixel(6, 4, 1, 1, Color(0xFF202A38))
        pixel(9, 4, 1, 1, Color(0xFF202A38))
        pixel(7, 6, 2, 1, Color(0xFF87523C))
        pixel(5, 7, 6, 1, Color(0xFFB9784E))
        pixel(4, 8, 8, 5, Color(0xFF4E9D9A))
        pixel(2, 8, 2, 5, Color(0xFFB9784E))
        pixel(12, 8, 2, 5, Color(0xFFB9784E))
        pixel(5, 13, 3, 3, Color(0xFF26374C))
        pixel(8, 13, 3, 3, Color(0xFF26374C))
        pixel(2, 13, 3, 2, Color(0xFF202A38))
        pixel(11, 13, 3, 2, Color(0xFF202A38))
    }
}

@Composable
private fun LibraryScreen(
    modifier: Modifier, ready: Boolean, onInstall: () -> Unit, onImport: () -> Unit,
    onRestore: () -> Unit, onExport: () -> Unit
) {
    PageColumn(modifier, "Library", "Manage your game files and installations.") {
        InfoCard("Minecraft installation", if (ready) "Your game data is available." else "Install Minecraft or import an existing game folder.")
        ActionRow("Install Minecraft", "Download and prepare the game", Icons.Default.Download, onInstall)
        ActionRow("Import game folder", "Bring an existing Minecraft directory", Icons.Default.FolderOpen, onImport)
        ActionRow("Restore backup", "Restore a saved .zip backup", Icons.Default.Restore, onRestore)
        ActionRow("Export backup", "Save your current game data", Icons.Default.Archive, onExport)
    }
}

@Composable
private fun ModsScreen(modifier: Modifier, onCompile: () -> Unit, onImport: () -> Unit, onLog: () -> Unit) {
    PageColumn(modifier, "Mods", "Build and manage your Minecraft extensions.") {
        InfoCard("HMod", "Traditional Horizon mod packages.")
        InfoCard("HNMod", "Native Rust mods compiled with the embedded toolchain.")
        ActionRow("Compile HNMods", "Choose one or more .hnmod files", Icons.Default.Terminal, onCompile)
        ActionRow("Import compiled library", "Activate a compiled native .so", Icons.Default.FileDownload, onImport)
        ActionRow("Build log", "Inspect the live compiler output", Icons.Default.ReceiptLong, onLog)
        InfoCard("HShaders", "Shader pack management is being connected to the new interface.")
    }
}

@Composable
private fun SettingsScreen(modifier: Modifier, status: String, toolchainReady: Boolean, onLog: () -> Unit) {
    PageColumn(modifier, "Settings", "Tune Horizon to your device.") {
        InfoCard("Performance", "Graphics backend selection, memory limits, render distance and FPS profiles will live here.")
        InfoCard("Controls", "The full Zalith touch and keyboard editor is the target control system.")
        InfoCard("Toolchain", if (toolchainReady) "Embedded HNMod compiler is installed." else "Embedded HNMod compiler will install on first use.")
        ActionRow("Open HNMod build log", "Diagnostics and compiler output", Icons.Default.Terminal, onLog)
        InfoCard("Runtime diagnostics", status.ifBlank { "No diagnostic messages." })
    }
}

@Composable
private fun PlaceholderScreen(modifier: Modifier, title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, detail: String) {
    PageColumn(modifier, title, subtitle) {
        Box(Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(24.dp)).background(Surface), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(icon, null, tint = Cyan, modifier = Modifier.size(42.dp))
                Text("Coming into Horizon", color = White, fontWeight = FontWeight.Bold)
                Text(detail, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 24.dp))
            }
        }
    }
}

@Composable
private fun PageColumn(modifier: Modifier, title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("HORIZON", color = Cyan, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                Text(title, color = White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text(subtitle, color = Muted, fontSize = 12.sp)
            }
        }
        item { Column(verticalArrangement = Arrangement.spacedBy(10.dp), content = content) }
    }
}

@Composable
private fun SectionHeading(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, color = White, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp)
        Text(subtitle, color = Muted, fontSize = 9.sp, letterSpacing = 1.sp)
    }
}

@Composable
private fun QuickTile(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Surface(modifier.clickable(onClick = onClick), color = Surface, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(vertical = 16.dp, horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, null, tint = Cyan, modifier = Modifier.size(22.dp))
            Text(label, color = White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun StatusLine(label: String, value: String, good: Boolean) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Muted, fontSize = 11.sp, modifier = Modifier.weight(1f))
        Text(value, color = if (good) Cyan else Color(0xFFFFC978), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun InfoCard(title: String, description: String) {
    Surface(color = Surface, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, color = White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(description, color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
        }
    }
}

@Composable
private fun ActionRow(title: String, description: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick), color = Surface, shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Cyan, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, color = White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(description, color = Muted, fontSize = 10.sp)
            }
            Icon(Icons.Default.ChevronRight, null, tint = Muted)
        }
    }
}
