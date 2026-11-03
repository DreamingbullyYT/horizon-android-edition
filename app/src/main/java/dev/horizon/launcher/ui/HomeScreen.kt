package dev.horizon.launcher.ui

import android.net.Uri
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.SettingsBackupRestore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.horizon.launcher.MinecraftBackup
import dev.horizon.launcher.data.AccountsInfo
import dev.horizon.launcher.data.InstalledVersion
import dev.horizon.launcher.data.InstanceInfo
import dev.horizon.launcher.data.ModInfo
import dev.horizon.launcher.data.SettingsInfo
import dev.horizon.launcher.data.WorldInfo
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    instances: List<InstanceInfo>,
    accounts: AccountsInfo?,
    settings: SettingsInfo?,
    installed: List<InstalledVersion>,
    worlds: List<WorldInfo>,
    mods: List<ModInfo>,
    busy: String?,
    gameAvailable: Boolean,
    gameReady: Boolean,
    onInstall: () -> Unit,
    onImportFolder: (Uri, String) -> Unit,
    onImportBackup: (Uri) -> Unit,
    onPlay: () -> Unit,
    onNewInstance: () -> Unit,
    onSelectInstance: (String) -> Unit,
    onDeleteInstance: (String) -> Unit,
    onVersions: () -> Unit,
    onMods: () -> Unit,
    onAccounts: () -> Unit,
) {
    val c = LocalHorizon.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var localReady by remember(gameReady) { mutableStateOf(gameReady) }
    var backupBusy by remember { mutableStateOf<String?>(null) }
    var backupMessage by remember { mutableStateOf<String?>(null) }
    val browserPrefs = remember { context.getSharedPreferences("minecraft-file-browser", android.content.Context.MODE_PRIVATE) }
    var storageTree by remember {
        mutableStateOf(browserPrefs.getString("tree_uri", null)?.let(Uri::parse))
    }
    var browserOpen by remember { mutableStateOf(false) }

    val storagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            storageTree = uri
            browserPrefs.edit().putString("tree_uri", uri.toString()).apply()
            browserOpen = true
        }
    }

    fun openFileBrowser() {
        if (storageTree == null) storagePicker.launch(null) else browserOpen = true
    }

    fun exportBackup() {
        if (backupBusy != null) return
        scope.launch {
            backupBusy = "Preparing backup"
            runCatching {
                MinecraftBackup.exportToDownloads(context) { backupBusy = it }
            }.onSuccess { location -> backupMessage = "Backup saved to $location" }
                .onFailure { backupMessage = it.message ?: "Could not export Minecraft" }
            backupBusy = null
        }
    }

    val instance = instances.firstOrNull { it.id == settings?.selected }
        ?: instances.firstOrNull { it.id == "horizon" }
        ?: instances.firstOrNull()
    val player = accounts?.player?.label ?: "Offline player"
    val ready = localReady && gameAvailable
    val currentBusy = backupBusy ?: busy

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Plate(
                color = c.card.copy(alpha = 0.82f),
                borderColor = c.accentHi.copy(alpha = 0.38f),
                contentPadding = PaddingValues(20.dp),
            ) {
                SectionLabel("PLAY")
                Spacer(Modifier.height(6.dp))
                Text(
                    "Minecraft 26.3",
                    color = c.ink,
                    style = MaterialTheme.typography.displayMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "${instance?.name ?: "Horizon"} · $player",
                    color = c.inkSoft,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = c.stroke)
                Spacer(Modifier.height(10.dp))
                Text(
                    when {
                        !gameAvailable -> "This build can’t start the game engine. Update the app or reinstall it."
                        !ready -> "Almost there. Download Minecraft once, or bring back the copy you saved earlier."
                        else -> "All set · ${worlds.size} world${if (worlds.size == 1) "" else "s"} · ${mods.size} mod${if (mods.size == 1) "" else "s"}"
                    },
                    color = c.muted,
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(14.dp))
                PrimaryButton(
                    text = when {
                        currentBusy != null -> currentBusy
                        ready -> "Play"
                        else -> "Get Minecraft ready"
                    },
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Filled.PlayArrow,
                    onClick = {
                        if (currentBusy == null) {
                            if (ready) onPlay() else onInstall()
                        }
                    },
                )
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = c.stroke)
                Spacer(Modifier.height(9.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel("MINECRAFT FILES")
                    Spacer(Modifier.weight(1f))
                    Text("Already have the game? Bring it over, or save a copy so you never download it twice.", color = c.muted, style = MaterialTheme.typography.labelSmall)
                }
                Spacer(Modifier.height(7.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    SoftButton(
                        "Import files",
                        modifier = Modifier.weight(1.25f),
                        icon = Icons.Outlined.FolderOpen,
                        onClick = { if (currentBusy == null) openFileBrowser() },
                    )
                    SoftButton(
                        if (backupBusy != null) "Exporting…" else "Save a backup",
                        modifier = Modifier.weight(1f),
                        icon = Icons.Outlined.FileUpload,
                        onClick = { if (currentBusy == null) exportBackup() },
                    )
                }
                if (backupMessage != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(backupMessage!!, color = c.success, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        item {
            Plate(contentPadding = PaddingValues(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Outlined.Inventory2)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        SectionLabel("HORIZON BUILDS")
                        Spacer(Modifier.height(3.dp))
                        Text("Engine and launcher updates", color = c.muted, style = MaterialTheme.typography.bodySmall)
                    }
                    SoftButton("Open") { onVersions() }
                }
            }
        }

        item {
            Plate(contentPadding = PaddingValues(16.dp)) {
                SectionLabel("AT A GLANCE")
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth()) {
                    Stat("Worlds", worlds.size.toString(), Modifier.weight(1f))
                    Stat("Mods", mods.size.toString(), Modifier.weight(1f))
                    Stat("Played", instance?.lastPlayed?.let(::relativeTime) ?: "Never", Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = c.stroke)
                InfoRow(Icons.Outlined.Inventory2, "Engine updates", "Keep Horizon fresh", "Open", onClick = onVersions)
                InfoRow(Icons.Outlined.SettingsBackupRestore, "Mods & shaders", "Find new ones and manage what you have", "Open", onClick = onMods)
            }
        }

        if (worlds.isNotEmpty()) {
            item { SectionLabel("RECENT WORLDS") }
            worlds.take(3).forEach { world ->
                item(key = world.folder) {
                    Plate(contentPadding = PaddingValues(horizontal = 14.dp, vertical = 3.dp)) {
                        InfoRow(
                            icon = Icons.Outlined.Inventory2,
                            title = world.name,
                            subtitle = "${world.mode} · ${formatSize(world.size)}",
                            trailing = world.version ?: "26.3",
                        )
                    }
                }
            }
        }
    }

    val tree = storageTree
    if (browserOpen && tree != null) {
        MinecraftFileBrowserDialog(
            treeUri = tree,
            onDismiss = { browserOpen = false },
            onChangeStorage = {
                browserOpen = false
                storagePicker.launch(null)
            },
            onSelectFolder = { documentId ->
                browserOpen = false
                onImportFolder(tree, documentId)
            },
            onSelectZip = { uri ->
                browserOpen = false
                onImportBackup(uri)
            },
        )
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    val c = LocalHorizon.current
    Column(modifier.padding(end = 6.dp)) {
        Text(label.uppercase(), color = c.muted, style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(3.dp))
        Text(value, color = c.ink, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1L shl 30 -> "%.1f GB".format(bytes / (1L shl 30).toDouble())
    bytes >= 1L shl 20 -> "%.1f MB".format(bytes / (1L shl 20).toDouble())
    bytes >= 1L shl 10 -> "%.1f KB".format(bytes / (1L shl 10).toDouble())
    else -> "$bytes B"
}

private fun relativeTime(epochSeconds: Long): String {
    val delta = (System.currentTimeMillis() / 1000 - epochSeconds).coerceAtLeast(0)
    return when {
        delta < 60 -> "Just now"
        delta < 3600 -> "${delta / 60}m"
        delta < 86400 -> "${delta / 3600}h"
        else -> "${delta / 86400}d"
    }
}
