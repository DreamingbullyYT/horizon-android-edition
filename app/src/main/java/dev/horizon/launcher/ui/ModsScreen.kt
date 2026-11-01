package dev.horizon.launcher.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.horizon.launcher.data.ModInfo

@Composable
fun ModsScreen(
    mods: List<ModInfo>,
    shaders: List<String>,
    shaderFolder: String,
    onImportMod: () -> Unit,
    onImportShader: () -> Unit,
    onRemoveMod: (String) -> Unit,
    onRemoveShader: (String) -> Unit,
) {
    val c = LocalHorizon.current
    var tab by remember { mutableStateOf("Mods") }
    var showShaderFolder by remember { mutableStateOf(false) }

    LazyColumn(
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ScreenHeader(
                kicker = "Game files",
                title = "Mods & shaders",
                subtitle = "Import content supported by Horizon into this phone’s private game folder.",
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("Mods", selected = tab == "Mods") { tab = "Mods" }
                Pill("Shaders", selected = tab == "Shaders") { tab = "Shaders" }
            }
        }
        if (tab == "Mods") {
            item {
                Plate {
                    SectionLabel("Horizon content mods")
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Choose a .hmod file. Imported content mods are enabled for the next game start. Java .jar mods (Fabric, Forge and NeoForge code) do not run in Horizon.",
                        color = c.muted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(12.dp))
                    PrimaryButton(
                        text = "Import .hmod",
                        modifier = Modifier.fillMaxWidth(),
                        icon = Icons.Outlined.FileDownload,
                        onClick = onImportMod,
                    )
                }
            }
            if (mods.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Outlined.Extension,
                        title = "No Horizon mods imported",
                        body = "Import a content mod made for Horizon 26.3. The game reloads mods when it starts.",
                        primary = "Choose .hmod",
                        primaryIcon = Icons.Outlined.FileDownload,
                        onPrimary = onImportMod,
                    )
                }
            } else {
                item { SectionLabel("${mods.size} imported") }
                items(mods, key = { it.id }) { mod ->
                    Plate(contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)) {
                        InfoRow(
                            icon = Icons.Outlined.Extension,
                            title = mod.name,
                            subtitle = "${mod.file} · ${if (mod.enabled) "enabled" else "off"}",
                            trailing = "Remove",
                            onClick = { onRemoveMod(mod.id) },
                        )
                    }
                }
            }
        } else {
            item {
                Plate {
                    SectionLabel("Shader packs")
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Import Horizon .hshader packs or compatible .zip packs containing a shaders/ folder. Pick the pack in the game under Options → Horizon Settings → Shader Packs.",
                        color = c.muted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(12.dp))
                    PrimaryButton(
                        text = "Import shader pack",
                        modifier = Modifier.fillMaxWidth(),
                        icon = Icons.Outlined.FileDownload,
                        onClick = onImportShader,
                    )
                    Spacer(Modifier.height(8.dp))
                    SoftButton(
                        "Open private shader folder",
                        modifier = Modifier.fillMaxWidth(),
                        icon = Icons.Outlined.FolderOpen,
                        onClick = { showShaderFolder = true },
                    )
                }
            }
            if (shaders.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Outlined.FolderOpen,
                        title = "No shader packs yet",
                        body = "Import a .zip or .hshader file. The pack is copied into this app’s private game storage.",
                    )
                }
            } else {
                item { SectionLabel("${shaders.size} shader pack(s)") }
                items(shaders, key = { it }) { name ->
                    Plate(contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)) {
                        InfoRow(
                            icon = Icons.Outlined.FolderOpen,
                            title = name,
                            subtitle = "In the private shaderpacks folder",
                            trailing = "Remove",
                            onClick = { onRemoveShader(name) },
                        )
                    }
                }
            }
        }
    }

    if (showShaderFolder) {
        AlertDialog(
            onDismissRequest = { showShaderFolder = false },
            title = { Text("Private shader folder") },
            text = {
                Column {
                    Text(shaderFolder, color = c.muted, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(12.dp))
                    if (shaders.isEmpty()) Text("This folder is empty.", color = c.ink)
                    else shaders.forEach { Text(it, color = c.ink, modifier = Modifier.padding(vertical = 3.dp)) }
                }
            },
            confirmButton = {
                TextButton(onClick = { showShaderFolder = false; onImportShader() }) { Text("Import pack") }
            },
            dismissButton = { TextButton(onClick = { showShaderFolder = false }) { Text("Close") } },
        )
    }
}
