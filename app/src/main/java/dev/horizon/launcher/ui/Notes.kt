package dev.horizon.launcher.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun NotesOverlay(onClose: () -> Unit) {
    val c = LocalHorizon.current
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Plate(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            color = c.side,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "PREVIEW NOTES",
                        color = c.accentHi,
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text("Horizon on Android", color = c.ink, style = MaterialTheme.typography.headlineLarge)
                }
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Close",
                    tint = c.muted,
                    modifier = Modifier.padding(4.dp).clickable(onClick = onClose),
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "A Kotlin/Compose launcher over the real Rust core, compiled for Android and linked " +
                    "through JNI. Nothing is reimplemented in Kotlin except the UI.",
                color = c.muted,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(18.dp))

            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Note("Running natively", "The launcher's 30k-line core — versions, downloads, " +
                    "checksums, instances, worlds, mods, modpacks, servers, accounts — built as an " +
                    "AArch64/x86-64 shared library. The Versions screen is live GitHub data from it.")
                Note("The seam", "UniFFI-style boundary, currently a small JNI surface returning JSON. " +
                    "Kotlin calls the core on Dispatchers.IO; progress and cancellation map to coroutines.")
                Note("Ported UI", "The Night palette, pixel-cut plates, Bricolage + Pixelify type, " +
                    "accent colours and the Home/Mods/Servers/Versions/Settings navigation are all Compose.")
                Note("Current Android gaps", "Microsoft device-code sign-in is not wired to the Compose UI yet, " +
                    "credentials have no Android Keystore-backed store, and downloads still need a foreground " +
                    "service. Play, Join and release installation stay gated until a native Android game asset " +
                    "is published (upstream ROADMAP.md).")
                Note("Next", "Add Android Keystore-backed account storage, connect the device-code sign-in " +
                    "flow, move downloads into a cancellable foreground service, then build the native game " +
                    "runtime and touch controls.")
                Note("Not a launcher for Java", "Horizon is its own native Rust client, not a Java " +
                    "Edition runtime; there is no JVM in this app.")
                Spacer(Modifier.height(20.dp))
                SoftButton("Close", modifier = Modifier.fillMaxWidth(), onClick = onClose)
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun Note(title: String, body: String) {
    val c = LocalHorizon.current
    Column(Modifier.padding(bottom = 16.dp)) {
        Text(title, color = c.ink, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
        Spacer(Modifier.height(4.dp))
        Text(body, color = c.muted, style = MaterialTheme.typography.bodyMedium)
    }
}
