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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.horizon.launcher.data.AccountsInfo
import dev.horizon.launcher.data.PlatformInfo
import dev.horizon.launcher.data.ReleasesState
import dev.horizon.launcher.data.SettingsInfo
import dev.horizon.launcher.data.SignInInfo
import dev.horizon.launcher.data.TelemetryInfo
import dev.horizon.launcher.data.TouchControlsInfo
import org.json.JSONObject

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
    var autoInstall by remember(settings?.installNewVersions) { mutableStateOf(settings?.installNewVersions ?: true) }
    var prune by remember(settings?.pruneOldVersions) { mutableStateOf(settings?.pruneOldVersions ?: true) }
    var skipOpening by remember(settings?.skipOpening) { mutableStateOf(settings?.skipOpening ?: false) }
    var noAi by remember(settings?.noAi) { mutableStateOf(settings?.noAi ?: false) }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ScreenHeader(
                kicker = "Launcher",
                title = "Settings",
                subtitle = "Accounts, appearance, updates and diagnostics. The old Touch Editor has been removed.",
            )
        }
        item {
            Plate {
                SectionLabel("ACCOUNT")
                Spacer(Modifier.height(8.dp))
                Text(accounts?.player?.label ?: "Reading account…", color = c.ink, style = MaterialTheme.typography.titleLarge)
                Text("${accounts?.accounts?.size ?: 0} Microsoft account(s) · ${accounts?.offline?.size ?: 0} offline name(s)", color = c.muted, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(10.dp))
                SoftButton("Manage accounts", modifier = Modifier.fillMaxWidth(), onClick = onAccounts)
                Spacer(Modifier.height(10.dp))
                Text(
                    when {
                        signIn?.configured == true -> "Microsoft device-code sign-in is configured."
                        else -> signIn?.message ?: "Microsoft sign-in is not configured in this build."
                    },
                    color = c.muted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        item {
            Plate {
                SectionLabel("APPEARANCE")
                Spacer(Modifier.height(8.dp))
                Text("Accent", color = c.ink, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Accent.entries.forEach { choice ->
                        Pill(choice.label, choice == accent) {
                            onAccent(choice)
                            onSave(JSONObject().put("accent", choice.name.lowercase()))
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("Tablet layout", color = c.ink, style = MaterialTheme.typography.titleMedium)
                Text("Detected automatically from the screen configuration; tablets use a compact density without changing phones.", color = c.muted, style = MaterialTheme.typography.bodySmall)
            }
        }
        item {
            Plate {
                SectionLabel("UPDATES & GAME")
                Spacer(Modifier.height(8.dp))
                ToggleRow("Install new Horizon versions", autoInstall) {
                    autoInstall = it
                    onSave(JSONObject().put("install_new_versions", it))
                }
                ToggleRow("Prune old versions", prune) {
                    prune = it
                    onSave(JSONObject().put("prune_old_versions", it))
                }
                ToggleRow("Skip opening animation", skipOpening) {
                    skipOpening = it
                    onSave(JSONObject().put("skip_opening", it))
                }
                ToggleRow("Disable AI features", noAi) {
                    noAi = it
                    onSave(JSONObject().put("noAi", it))
                }
            }
        }
        item {
            Plate {
                SectionLabel("PRIVACY")
                Spacer(Modifier.height(8.dp))
                Text("Telemetry", color = c.ink, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(3.dp))
                Text("Performance, crash, bug and benchmark telemetry stays under the launcher's existing controls.", color = c.muted, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                ToggleRow("Allow telemetry", telemetry?.any == true) { onTelemetry(it) }
            }
        }
        item {
            Plate {
                SectionLabel("ABOUT")
                Spacer(Modifier.height(8.dp))
                InfoRow(Icons.Outlined.Info, "Horizon Android", platform?.version ?: "Android preview")
                InfoRow(Icons.Outlined.Settings, "Data directory", platform?.dataDir ?: homeDir)
                InfoRow(Icons.Outlined.Security, "Account storage", if (accounts?.keychain == true) "Persistent secret store" else "Session/file-backed store")
                Text("This Android edition is an unofficial client for Horizon. It contains no Minecraft assets; you provide your own Java Edition resources.", color = c.muted, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val c = LocalHorizon.current
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = c.ink, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        androidx.compose.material3.Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
