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
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.horizon.launcher.data.AccountsInfo
import dev.horizon.launcher.data.PlatformInfo
import dev.horizon.launcher.data.ReleasesState
import dev.horizon.launcher.data.SettingsInfo
import dev.horizon.launcher.data.SignInInfo
import dev.horizon.launcher.data.TelemetryInfo
import dev.horizon.launcher.data.TouchControlsInfo
import dev.horizon.launcher.data.AppUpdater
import dev.horizon.launcher.data.Updater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var updateBusy by remember { mutableStateOf(false) }
    var updateStatus by remember { mutableStateOf<String?>(null) }
    var versions by remember { mutableStateOf(Updater.versions(context.filesDir)) }
    var activeVersion by remember { mutableStateOf(Updater.activeVersion(context.filesDir)) }
    var autoUpdate by remember { mutableStateOf(Updater.autoEnabled(context)) }
    var apkOffer by remember { mutableStateOf<AppUpdater.Offer?>(null) }
    var apkProgress by remember { mutableStateOf<Float?>(null) }
    var installedNativeBuild by remember { mutableStateOf(Updater.installedBuild(context.filesDir)) }
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
            Plate(
                color = c.accent.copy(alpha = 0.07f),
                borderColor = c.accentHi.copy(alpha = 0.28f),
            ) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    SectionLabel("ANDROID NIGHTLY UPDATE")
                    Spacer(Modifier.weight(1f))
                    Tag("ROLLING", c.accentHi)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    if (installedNativeBuild > 0L) "Installed native build #$installedNativeBuild" else "Using the native runtime bundled in this APK",
                    color = c.ink,
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    "Looks for the newest Android Nightly the way the PC launcher does. A download is installed only after its signature and checksum check out, and the build you use now is never replaced if anything fails.",
                    color = c.muted,
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(10.dp))
                SoftButton(
                    text = if (updateBusy) "Checking updates…" else "Check for updates",
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Outlined.Update,
                    onClick = {
                        if (!updateBusy && busy == null) {
                            scope.launch {
                                updateBusy = true
                                updateStatus = "Contacting the rolling update channel…"
                                try {
                                    when (val result = withContext(Dispatchers.IO) { Updater.checkAndInstall(context) }) {
                                        is Updater.Result.UpToDate -> {
                                            installedNativeBuild = result.build
                                            updateStatus = "Already up to date (build #${result.build})."
                                        }
                                        is Updater.Result.Installed -> {
                                            installedNativeBuild = result.build
                                            versions = Updater.versions(context.filesDir)
                                            activeVersion = Updater.activeVersion(context.filesDir)
                                            updateStatus = "Build #${result.build} was downloaded and verified. It starts with the next game launch."
                                        }
                                        is Updater.Result.Failed -> updateStatus = "Update failed: ${result.message}"
                                    }
                                } catch (error: Exception) {
                                    updateStatus = "Update failed: ${error.message ?: error.javaClass.simpleName}"
                                } finally {
                                    updateBusy = false
                                }
                                runCatching { withContext(Dispatchers.IO) { AppUpdater.check(context) } }
                                    .onSuccess { apkOffer = it }
                            }
                        }
                    },
                )
                apkOffer?.let { offer ->
                    Spacer(Modifier.height(10.dp))
                    PrimaryButton(
                        text = apkProgress?.let { "Downloading… ${(it * 100).toInt()}%" } ?: "Install app update (build #${offer.build})",
                        modifier = Modifier.fillMaxWidth(),
                        icon = Icons.Outlined.Update,
                        onClick = {
                            if (apkProgress == null) {
                                if (!AppUpdater.canInstall(context)) {
                                    onMessage("Allow Horizon to install updates, then tap the button again.")
                                    context.startActivity(AppUpdater.settingsIntent(context))
                                } else scope.launch {
                                    apkProgress = 0f
                                    runCatching {
                                        withContext(Dispatchers.IO) {
                                            val file = AppUpdater.download(context, offer) { apkProgress = it }
                                            AppUpdater.install(context, file)
                                        }
                                    }.onFailure { onMessage(it.message ?: "Could not install the update") }
                                    apkProgress = null
                                }
                            }
                        },
                    )
                }
                Spacer(Modifier.height(8.dp))
                ToggleRow("Update automatically (every 30 minutes)", autoUpdate) {
                    autoUpdate = it
                    Updater.setAuto(context, it)
                }
                Spacer(Modifier.height(6.dp))
                SectionLabel("Installed builds")
                InfoRow(
                    icon = Icons.Outlined.Update,
                    title = "Bundled with the app",
                    subtitle = "The version that came inside this APK",
                    trailing = if (activeVersion == null) "In use" else "Use",
                    onClick = {
                        if (activeVersion != null) {
                            Updater.activate(context.filesDir, null, byHand = true)
                            activeVersion = null
                            onMessage("The bundled build starts with the next game launch.")
                        }
                    },
                )
                versions.forEach { v ->
                    InfoRow(
                        icon = Icons.Outlined.Update,
                        title = v.name,
                        subtitle = "Build #${v.build}",
                        trailing = if (v.name == activeVersion) "In use" else "Use",
                        onClick = {
                            if (v.name != activeVersion) {
                                Updater.activate(context.filesDir, v.name, byHand = true)
                                activeVersion = v.name
                                onMessage("${v.name} starts with the next game launch.")
                            }
                        },
                    )
                }
                if (updateStatus != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(updateStatus!!, color = if (updateStatus!!.startsWith("Update failed")) c.danger else c.muted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            Plate {
                SectionLabel("GAME OPTIONS")
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
