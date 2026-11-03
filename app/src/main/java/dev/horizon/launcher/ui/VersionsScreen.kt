package dev.horizon.launcher.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.horizon.launcher.data.InstalledVersion
import dev.horizon.launcher.data.Release
import dev.horizon.launcher.data.ReleasesState
import dev.horizon.launcher.data.ServerInfo

private const val YOUR_REPO = "https://github.com/DreamingbullyYT/horizon-android-edition"
private const val UPSTREAM_REPO = "https://github.com/horizon-rust/horizon"

@Composable
fun VersionsScreen(
    servers: List<ServerInfo>,
    gameAvailable: Boolean,
    releases: ReleasesState,
    installed: List<InstalledVersion>,
    busy: String?,
    onAddServer: () -> Unit,
    onRemoveServer: (Int) -> Unit,
    onJoinServer: (ServerInfo) -> Unit,
    onInstall: (String) -> Unit,
    onRemove: (String) -> Unit,
    onRetry: () -> Unit,
) {
    val c = LocalHorizon.current
    val context = LocalContext.current
    @Suppress("DEPRECATION")
    val appBuildLabel = remember {
        runCatching {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            val build = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
            "${info.versionName ?: "Android preview"} · build #$build"
        }.getOrDefault("Android preview")
    }
    var showPreviousNightlies by remember { mutableStateOf(false) }
    var showOldReleases by remember { mutableStateOf(false) }
    fun open(url: String) = runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ScreenHeader(
                kicker = "Multiplayer + launcher",
                title = "Servers & Builds",
                subtitle = "Saved multiplayer servers first, then the current Horizon Nightly channel.",
            )
        }
        item {
            Plate(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel("SERVERS")
                    Spacer(Modifier.weight(1f))
                    Tag("${servers.size} SAVED", c.accentHi)
                }
                Spacer(Modifier.height(8.dp))
                if (!gameAvailable) {
                    Text(
                        "Server entries can be managed now. Joining needs a compatible Android game runtime in this build.",
                        color = c.muted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                PrimaryButton(
                    text = if (busy != null) "Working…" else "Add a server",
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Filled.Add,
                    onClick = { if (busy == null) onAddServer() },
                )
                if (servers.isEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("No saved servers yet. Add an address to keep it in servers.dat.", color = c.muted, style = MaterialTheme.typography.bodySmall)
                } else {
                    Spacer(Modifier.height(8.dp))
                    servers.forEachIndexed { index, server ->
                        if (index > 0) HorizontalDivider(color = c.stroke)
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconBadge(Icons.Outlined.Dns)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(server.name.ifBlank { server.ip }, color = c.ink, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(server.ip, color = c.muted, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            if (gameAvailable) SoftButton("Join") { if (busy == null) onJoinServer(server) }
                            else Tag("PENDING", c.warn)
                            Spacer(Modifier.width(4.dp))
                            androidx.compose.material3.Icon(
                                Icons.Outlined.Delete,
                                contentDescription = "Remove ${server.name}",
                                tint = c.muted,
                                modifier = Modifier.clickable(enabled = busy == null) { onRemoveServer(index) }.padding(8.dp).size(19.dp),
                            )
                        }
                    }
                }
            }
        }
        item {
            ScreenHeader(
                kicker = "Horizon launcher",
                title = "Builds",
                subtitle = "Nightly is the active upstream channel. Older tagged releases are kept for manual rollback only.",
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                RepoCard("Your Android Edition", appBuildLabel, Modifier.weight(1f)) { open(YOUR_REPO) }
                RepoCard("Original Horizon", "Upstream · Nightly-only", Modifier.weight(1f)) { open(UPSTREAM_REPO) }
            }
        }
        item {
            Plate(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)) {
                SectionLabel("INSTALLED HORIZON BUILDS")
                Spacer(Modifier.height(4.dp))
                if (installed.isEmpty()) {
                    InfoRow(Icons.Outlined.Inventory2, "No extra launcher build installed", "The active APK is the current Android build", "Current")
                } else {
                    installed.forEachIndexed { index, version ->
                        if (index > 0) HorizontalDivider(color = c.stroke)
                        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconBadge(Icons.Outlined.Inventory2)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(version.tag, color = c.ink, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${version.version} · ${if (version.prepared) "prepared" else "not prepared"}", color = c.muted, style = MaterialTheme.typography.bodySmall)
                            }
                            Tag(if (version.prepared) "READY" else "RAW", if (version.prepared) c.success else c.warn)
                            Spacer(Modifier.width(6.dp))
                            SoftButton("Remove", icon = Icons.Outlined.Delete) { onRemove(version.tag) }
                        }
                    }
                }
            }
        }
        when (releases) {
            is ReleasesState.Loading -> item {
                Plate {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), color = c.accentHi, strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text("Checking Horizon Nightly builds…", color = c.muted, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            is ReleasesState.Failed -> item {
                Plate(color = c.danger.copy(alpha = 0.1f), borderColor = c.danger.copy(alpha = 0.35f)) {
                    Text("Could not read Horizon builds", color = c.ink, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(releases.message, color = c.muted, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(10.dp))
                    SoftButton("Try again", onClick = onRetry)
                }
            }
            is ReleasesState.Ready -> {
                // GitHub's release API returns newest first, but sort explicitly so a reordered
                // response can never make an older Nightly appear as the active build.
                val ordered = releases.items.sortedByDescending { it.date }
                val nightlies = ordered.filter { it.nightly }
                val oldReleases = ordered.filterNot { it.nightly }
                item {
                    Plate(
                        color = c.accent.copy(alpha = 0.08f),
                        borderColor = c.accentHi.copy(alpha = 0.32f),
                        contentPadding = PaddingValues(16.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SectionLabel("ACTIVE CHANNEL")
                            Spacer(Modifier.weight(1f))
                            SoftButton("Check Nightly", onClick = onRetry)
                            Spacer(Modifier.width(8.dp))
                            Tag("NIGHTLY", c.accentHi)
                        }
                        Spacer(Modifier.height(6.dp))
                        Text("Latest upstream Nightly", color = c.ink, style = MaterialTheme.typography.titleLarge)
                        Text(
                            if (nightlies.isEmpty()) "No Nightly builds were returned by the upstream API. Use refresh to try again."
                            else "${nightlies.first().tag} · ${nightlies.first().date}",
                            color = c.muted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(Modifier.height(10.dp))
                        if (nightlies.isNotEmpty()) {
                            ReleaseCard(
                                nightlies.first(),
                                installed.any { it.tag == nightlies.first().tag },
                                busy,
                                onInstall,
                                installLabel = "Update to Nightly",
                            )
                        } else SoftButton("Refresh Nightly list", onClick = onRetry)
                    }
                }
                if (nightlies.size > 1) {
                    item {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            SectionLabel("PREVIOUS NIGHTLIES (${nightlies.size - 1})")
                            Spacer(Modifier.weight(1f))
                            SoftButton(if (showPreviousNightlies) "Hide" else "Show") { showPreviousNightlies = !showPreviousNightlies }
                        }
                    }
                    if (showPreviousNightlies) {
                        items(nightlies.drop(1).take(20), key = { "nightly-${it.tag}" }) { release ->
                            ReleaseCard(release, installed.any { it.tag == release.tag }, busy, onInstall)
                        }
                    }
                }
                if (oldReleases.isNotEmpty()) {
                    item {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                SectionLabel("OLD RELEASES")
                                Text("Outdated tagged builds · manual rollback only", color = c.muted, style = MaterialTheme.typography.bodySmall)
                            }
                            SoftButton(if (showOldReleases) "Hide" else "Show") { showOldReleases = !showOldReleases }
                        }
                    }
                    if (showOldReleases) {
                        items(oldReleases.take(20), key = { "release-${it.tag}" }) { release ->
                            ReleaseCard(release, installed.any { it.tag == release.tag }, busy, onInstall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RepoCard(title: String, subtitle: String, modifier: Modifier, onOpen: () -> Unit) {
    val c = LocalHorizon.current
    Plate(modifier = modifier, contentPadding = PaddingValues(14.dp)) {
        SectionLabel(title.uppercase())
        Spacer(Modifier.height(4.dp))
        Text(subtitle, color = c.muted, style = MaterialTheme.typography.bodySmall, maxLines = 1)
        Spacer(Modifier.height(10.dp))
        SoftButton("GitHub", icon = Icons.Outlined.OpenInNew, modifier = Modifier.fillMaxWidth(), onClick = onOpen)
    }
}

@Composable
private fun ReleaseCard(
    release: Release,
    installed: Boolean,
    busy: String?,
    onInstall: (String) -> Unit,
    installLabel: String = "Install",
) {
    val c = LocalHorizon.current
    Plate(contentPadding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(release.tag, color = c.ink, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = Mono, fontWeight = FontWeight.Medium, fontSize = 13.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(3.dp))
                Text(release.date, color = c.muted, style = MaterialTheme.typography.bodySmall)
            }
            Tag(if (release.nightly) "NIGHTLY" else "RELEASE", if (release.nightly) c.accentHi else c.muted)
        }
        if (release.body.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(release.body.replace("\n", " ").trim().take(160), color = c.muted, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(10.dp))
        HorizontalDivider(color = c.stroke)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (release.assetName == null) Tag("NO ANDROID ASSET", c.danger)
            else Tag("${release.assetSize / (1024 * 1024)} MB", c.success)
            Spacer(Modifier.weight(1f))
            when {
                installed -> Tag("INSTALLED", c.success)
                release.assetName == null -> Tag("NOT AVAILABLE", c.warn)
                else -> SoftButton(if (busy == null) installLabel else "Working…") { if (busy == null) onInstall(release.tag) }
            }
        }
    }
}
