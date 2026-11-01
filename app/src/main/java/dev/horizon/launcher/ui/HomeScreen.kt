package dev.horizon.launcher.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.horizon.launcher.data.AccountsInfo
import dev.horizon.launcher.data.InstalledVersion
import dev.horizon.launcher.data.InstanceInfo
import dev.horizon.launcher.data.ModInfo
import dev.horizon.launcher.data.WorldInfo

@Composable
fun HomeScreen(
    instances: List<InstanceInfo>,
    accounts: AccountsInfo?,
    settings: dev.horizon.launcher.data.SettingsInfo?,
    installed: List<InstalledVersion>,
    worlds: List<WorldInfo>,
    mods: List<ModInfo>,
    busy: String?,
    gameAvailable: Boolean,
    gameReady: Boolean,
    onInstall: () -> Unit,
    onChooseFolder: () -> Unit,
    onPlay: () -> Unit,
    onNewInstance: () -> Unit,
    onSelectInstance: (String) -> Unit,
    onDeleteInstance: (String) -> Unit,
    onVersions: () -> Unit,
    onMods: () -> Unit,
    onAccounts: () -> Unit,
) {
    val c = LocalHorizon.current
    val instance = instances.firstOrNull { it.id == settings?.selected }
        ?: instances.firstOrNull { it.id == "horizon" }
        ?: instances.firstOrNull()
    var picker by remember { mutableStateOf(false) }
    val player = accounts?.player?.label ?: "Offline player"

    LazyColumn(
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Plate(
                color = c.card.copy(alpha = 0.72f),
                borderColor = c.accentHi.copy(alpha = 0.3f),
                contentPadding = PaddingValues(20.dp),
            ) {
                SectionLabel("SELECTED INSTANCE")
                Spacer(Modifier.height(5.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        instance?.name ?: "Horizon",
                        color = c.ink,
                        style = MaterialTheme.typography.displayMedium,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text("Change", color = c.accentHi, style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.clickable { picker = true }.padding(8.dp))
                }
                Spacer(Modifier.height(4.dp))
                Text("Playing as $player", color = c.inkSoft, style = MaterialTheme.typography.bodyMedium)
                Text("Manage account", color = c.accentHi, style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.clickable(onClick = onAccounts).padding(vertical = 6.dp))
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = c.stroke)
                Spacer(Modifier.height(10.dp))
                Text(
                    when {
                        !gameAvailable -> "The Android game runtime is unavailable in this build."
                        !gameReady -> "Install Minecraft 26.3 from Mojang or select your existing game files."
                        else -> "Horizon 26.3 is ready · ${installed.size} build(s) installed."
                    },
                    color = c.muted,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(14.dp))
                if (gameAvailable) {
                    PrimaryButton(
                        text = when {
                            busy != null -> busy
                            gameReady -> "Play"
                            else -> "Download Minecraft 26.3"
                        },
                        modifier = Modifier.fillMaxWidth(),
                        icon = Icons.Filled.PlayArrow,
                        onClick = { if (busy == null) { if (gameReady) onPlay() else onInstall() } },
                    )
                    if (!gameReady) {
                        Text(
                            "Choose existing Minecraft files",
                            color = c.accentHi,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.clickable(enabled = busy == null, onClick = onChooseFolder)
                                .padding(top = 14.dp, bottom = 2.dp),
                        )
                    }
                }
            }
        }
        item {
            Plate(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                SectionLabel("THIS INSTANCE")
                Spacer(Modifier.height(9.dp))
                Row(Modifier.fillMaxWidth()) {
                    Stat("Worlds", worlds.size.toString(), Modifier.weight(1f))
                    Stat("Horizon mods", mods.size.toString(), Modifier.weight(1f))
                    Stat("Last played", instance?.lastPlayed?.let(::relativeTime) ?: "Never", Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = c.stroke)
                InfoRow(Icons.Outlined.Extension, "Manage mods & shaders", "Import compatible game content", "Open", onClick = onMods)
                InfoRow(Icons.Outlined.Inventory2, "Manage versions", "${installed.size} installed build(s)", "Open", onClick = onVersions)
            }
        }
        if (worlds.isNotEmpty()) {
            item { SectionLabel("RECENT WORLDS") }
            items(worlds.take(3), key = { it.folder }) { world ->
                Plate(contentPadding = PaddingValues(horizontal = 14.dp, vertical = 3.dp)) {
                    InfoRow(
                        icon = Icons.Filled.Place,
                        title = world.name,
                        subtitle = "${world.mode} · ${formatSize(world.size)}",
                        trailing = world.version,
                    )
                }
            }
        }
    }

    if (picker) {
        InstancePickerSheet(
            instances = instances,
            selected = settings?.selected,
            onDismiss = { picker = false },
            onSelect = { picker = false; onSelectInstance(it) },
            onDelete = onDeleteInstance,
            onCreate = { picker = false; onNewInstance() },
        )
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    val c = LocalHorizon.current
    Column(modifier.padding(end = 5.dp)) {
        Text(label.uppercase(), color = c.muted, style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.8.sp))
        Spacer(Modifier.height(4.dp))
        Text(value, color = c.ink, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InstancePickerSheet(
    instances: List<InstanceInfo>,
    selected: String?,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
    onDelete: (String) -> Unit,
    onCreate: () -> Unit,
) {
    val c = LocalHorizon.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.side,
        contentColor = c.ink,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
            SectionLabel("Instances")
            Spacer(Modifier.height(10.dp))
            Text("Each instance keeps its own worlds, mods and settings.", color = c.muted, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            androidx.compose.foundation.lazy.LazyColumn(
                contentPadding = PaddingValues(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(instances, key = { it.id }) { instance ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(instance.name, color = c.ink, style = MaterialTheme.typography.titleMedium)
                            Text(instance.home, color = c.muted, style = MaterialTheme.typography.bodySmall,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (instance.id == selected) Tag("SELECTED", c.success)
                        else SoftButton("Use") { onSelect(instance.id) }
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.Outlined.Delete, contentDescription = "Delete instance", tint = c.muted,
                            modifier = Modifier.clickable { onDelete(instance.id) }.padding(8.dp))
                    }
                    HorizontalDivider(color = c.stroke)
                }
                item {
                    Spacer(Modifier.height(10.dp))
                    PrimaryButton("New instance", Modifier.fillMaxWidth(), Icons.Outlined.Add, onCreate)
                }
            }
        }
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1L shl 30 -> "%.1f GB".format(bytes / (1L shl 30).toDouble())
    bytes >= 1L shl 20 -> "%.1f MB".format(bytes / (1L shl 20).toDouble())
    bytes >= 1L shl 10 -> "%.1f KB".format(bytes / (1L shl 10).toDouble())
    else -> "$bytes B"
}

private fun relativeTime(epochSeconds: Long): String {
    val delta = System.currentTimeMillis() / 1000 - epochSeconds
    return when {
        delta < 60 -> "Just now"
        delta < 3600 -> "${delta / 60}m ago"
        delta < 86400 -> "${delta / 3600}h ago"
        else -> "${delta / 86400}d ago"
    }
}
