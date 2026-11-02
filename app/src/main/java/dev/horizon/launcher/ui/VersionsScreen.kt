package dev.horizon.launcher.ui

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

private const val YOUR_REPO = "https://github.com/DreamingbullyYT/horizon-android-edition"
private const val UPSTREAM_REPO = "https://github.com/horizon-rust/horizon"

@Composable
fun VersionsScreen(
    releases: ReleasesState,
    installed: List<InstalledVersion>,
    busy: String?,
    onInstall: (String) -> Unit,
    onRemove: (String) -> Unit,
    onRetry: () -> Unit,
) {
    val c = LocalHorizon.current
    val context = LocalContext.current
    fun open(url: String) = runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ScreenHeader(
                kicker = "Launcher builds",
                title = "Horizon Builds",
                subtitle = "Builds of the Android launcher and the original Horizon project. Minecraft 26.3 stays separate.",
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                RepoCard("Your Horizon", "Android edition", YOUR_REPO, Modifier.weight(1f)) { open(YOUR_REPO) }
                RepoCard("Original Horizon", "Upstream project", UPSTREAM_REPO, Modifier.weight(1f)) { open(UPSTREAM_REPO) }
            }
        }
        item {
            Plate(contentPadding = PaddingValues(16.dp)) {
                SectionLabel("MINECRAFT")
                Spacer(Modifier.height(4.dp))
                Text("Minecraft 26.3", color = c.ink, style = MaterialTheme.typography.titleLarge)
                Text("The Android build targets Horizon's current 26.3 game resources; this page does not treat Minecraft versions as launcher builds.", color = c.muted, style = MaterialTheme.typography.bodySmall)
            }
        }
        item {
            Plate(
                color = c.warn.copy(alpha = 0.09f),
                borderColor = c.warn.copy(alpha = 0.3f),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Filled.Warning, tint = c.warn)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Android build channel", color = c.ink, style = MaterialTheme.typography.titleMedium)
                        Text("Only releases carrying a compatible Android asset can be installed from this list.", color = c.muted, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        item {
            Plate(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)) {
                SectionLabel("INSTALLED LAUNCHER BUILDS")
                Spacer(Modifier.height(4.dp))
                if (installed.isEmpty()) {
                    InfoRow(Icons.Outlined.Inventory2, "No launcher build installed", "The active APK is the current Android build", "Current")
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
                        Text("Reading launcher releases…", color = c.muted, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            is ReleasesState.Failed -> item {
                Plate(color = c.danger.copy(alpha = 0.1f), borderColor = c.danger.copy(alpha = 0.35f)) {
                    Text("Could not read releases", color = c.ink, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(releases.message, color = c.muted, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(10.dp))
                    SoftButton("Try again", onClick = onRetry)
                }
            }
            is ReleasesState.Ready -> {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionLabel("LATEST ANDROID BUILDS")
                        Spacer(Modifier.weight(1f))
                        Text("${releases.items.size} listed", color = c.muted, style = MaterialTheme.typography.bodySmall)
                    }
                }
                items(releases.items.take(30), key = { it.tag }) { release ->
                    ReleaseCard(release, installed.any { it.tag == release.tag }, busy, onInstall, onRemove)
                }
            }
        }
    }
}

@Composable
private fun RepoCard(title: String, subtitle: String, url: String, modifier: Modifier, onOpen: () -> Unit) {
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
    onRemove: (String) -> Unit,
) {
    val c = LocalHorizon.current
    Plate(contentPadding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(release.tag, color = c.ink, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = Mono, fontWeight = FontWeight.Medium, fontSize = 13.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(3.dp))
                Text(release.date, color = c.muted, style = MaterialTheme.typography.bodySmall)
            }
            Tag(if (release.nightly) "NIGHTLY" else "RELEASE", if (release.nightly) c.accentHi else c.success)
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
                installed -> SoftButton("Remove", icon = Icons.Outlined.Delete) { onRemove(release.tag) }
                release.assetName == null -> Tag("NOT AVAILABLE", c.warn)
                else -> SoftButton(if (busy == null) "Install" else "Working…") { if (busy == null) onInstall(release.tag) }
            }
        }
    }
}
