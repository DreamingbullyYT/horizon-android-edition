package dev.horizon.launcher.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Lan
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.horizon.launcher.data.ServerInfo

@Composable
fun ServersScreen(
    servers: List<ServerInfo>,
    busy: String?,
    gameAvailable: Boolean,
    onAdd: () -> Unit,
    onRemove: (Int) -> Unit,
    onJoin: (ServerInfo) -> Unit,
    onMessage: (String) -> Unit,
) {
    val c = LocalHorizon.current
    LazyColumn(
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ScreenHeader(
                kicker = "Multiplayer",
                title = "Servers",
                subtitle = "Saved servers from the game's servers.dat, plus quick join with your offline name.",
            )
        }
        item {
            Plate(
                color = c.warn.copy(alpha = 0.10f),
                borderColor = c.warn.copy(alpha = 0.35f),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Filled.Warning, tint = c.warn)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Joining games is not available on Android yet",
                        color = c.ink,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "The Android client is not published yet. You can keep and edit your saved server list; joining becomes available with the native game runtime.",
                    color = c.muted,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        item {
            PrimaryButton(
                text = if (busy != null) "Working…" else "Add a server",
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Filled.Add,
                onClick = { if (busy == null) onAdd() },
            )
        }
        if (servers.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Outlined.Dns,
                    title = "No servers yet",
                    body = "Add a server address and it is written to servers.dat by the core. " +
                        "Offline-mode servers accept your offline name.",
                    primary = "Add a server",
                    primaryIcon = Icons.Filled.Add,
                    onPrimary = onAdd,
                )
            }
        } else {
            item {
                Plate(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)) {
                    SectionLabel("${servers.size} saved")
                    Spacer(Modifier.height(4.dp))
                    servers.forEachIndexed { index, server ->
                        if (index > 0) HorizontalDivider(color = c.stroke)
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconBadge(Icons.Outlined.Dns)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    server.name.ifBlank { server.ip },
                                    color = c.ink,
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(server.ip, color = c.muted, style = MaterialTheme.typography.bodySmall)
                            }
                            if (gameAvailable) {
                                SoftButton("Join") { onJoin(server) }
                            } else {
                                Tag("CLIENT PENDING", c.warn)
                            }
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                Icons.Outlined.Delete,
                                contentDescription = "Remove",
                                tint = c.muted,
                                modifier = Modifier
                                    .clickable { onRemove(index) }
                                    .padding(8.dp)
                                    .size(20.dp),
                            )
                        }
                    }
                }
            }
        }
        item {
            Plate {
                SectionLabel("Remote control")
                Spacer(Modifier.height(8.dp))
                InfoRow(
                    icon = Icons.Outlined.Lan,
                    title = "Console and players",
                    subtitle = "Run commands, kick and ban, watch TPS and memory",
                    onClick = { onMessage("Available once a server is connected.") },
                )
                InfoRow(
                    icon = Icons.Outlined.Storage,
                    title = "Backups and updates",
                    subtitle = "Back up now, restore, and roll back a bad update",
                    onClick = { onMessage("Available once a server is connected.") },
                )
            }
        }
    }
}
