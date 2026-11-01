package dev.horizon.launcher.ui

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
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.horizon.launcher.data.InstalledVersion
import dev.horizon.launcher.data.Release
import dev.horizon.launcher.data.ReleasesState

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
    LazyColumn(
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ScreenHeader(
                kicker = "Builds",
                title = "Versions",
                subtitle = "Nightly and release builds, listed straight from horizon-rust/horizon by the Rust core.",
            )
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
                        Text("Desktop archives", color = c.ink, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "No Android game archive is published yet. Releases are shown for reference; install stays disabled until a native Android build is available.",
                            color = c.muted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
        item {
            Plate(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)) {
                SectionLabel("Installed versions")
                Spacer(Modifier.height(4.dp))
                if (installed.isEmpty()) {
                    InfoRow(
                        icon = Icons.Outlined.Inventory2,
                        title = "Nothing installed",
                        subtitle = "Install a build from the list below",
                        trailing = "None",
                    )
                } else {
                    installed.forEachIndexed { index, version ->
                        if (index > 0) HorizontalDivider(color = c.stroke)
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconBadge(Icons.Outlined.Inventory2)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(version.tag, color = c.ink, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "${version.version} · " +
                                        (if (version.prepared) "prepared" else "not prepared") +
                                        if (version.sounds) " · sounds" else "",
                                    color = c.muted,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            Tag(if (version.prepared) "READY" else "RAW", if (version.prepared) c.success else c.warn)
                            Spacer(Modifier.width(6.dp))
                            SoftButton("Remove") { onRemove(version.tag) }
                        }
                    }
                }
            }
        }
        when (releases) {
            is ReleasesState.Loading -> item {
                Plate {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = c.accentHi,
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(12.dp))
                        Text("Reading releases from GitHub…", color = c.muted, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            is ReleasesState.Failed -> item {
                Plate(color = c.danger.copy(alpha = 0.1f), borderColor = c.danger.copy(alpha = 0.35f)) {
                    Text("Could not read the release list", color = c.ink, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(releases.message, color = c.muted, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(12.dp))
                    SoftButton("Try again", onClick = onRetry)
                }
            }

            is ReleasesState.Ready -> {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionLabel("Latest builds")
                        Spacer(Modifier.weight(1f))
                        Text(
                            "${releases.items.size} listed",
                            color = c.muted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                items(releases.items.take(30)) { release ->
                    ReleaseCard(
                        release = release,
                        installed = installed.any { it.tag == release.tag },
                        busy = busy,
                        onInstall = onInstall,
                        onRemove = onRemove,
                    )
                }
            }
        }
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
                Text(
                    release.tag,
                    color = c.ink,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = Mono,
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Text(release.date, color = c.muted, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.width(10.dp))
            Tag(if (release.nightly) "NIGHTLY" else "RELEASE", if (release.nightly) c.accentHi else c.success)
        }
        if (release.body.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(
                release.body.replace("\n", " ").trim().take(160),
                color = c.muted,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = c.stroke)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (release.assetName == null) {
                Tag("NO ANDROID ASSET", c.danger)
            } else {
                Tag("${release.assetSize / (1024 * 1024)} MB", c.success)
            }
            Spacer(Modifier.weight(1f))
            when {
                installed -> SoftButton("Remove", icon = Icons.Outlined.Delete) { onRemove(release.tag) }
                release.assetName == null -> Tag("NOT AVAILABLE", c.warn)
                else -> SoftButton(
                    if (busy != null) "Working…" else "Install",
                    icon = Icons.Outlined.FileDownload,
                ) {
                    if (busy == null) onInstall(release.tag)
                }
            }
        }
    }
}
