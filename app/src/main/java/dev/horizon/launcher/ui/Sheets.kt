package dev.horizon.launcher.ui

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.OpenInNew
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import dev.horizon.launcher.auth.MicrosoftAuth
import dev.horizon.launcher.data.AccountsInfo
import dev.horizon.launcher.data.SignInInfo
import kotlinx.coroutines.launch
import org.json.JSONObject

@Composable
fun PixelField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    val c = LocalHorizon.current
    androidx.compose.material3.OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label, color = c.muted) },
        singleLine = true,
        shape = pixelShape(6.dp),
        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
            focusedBorderColor = c.accentHi,
            unfocusedBorderColor = c.stroke,
            focusedContainerColor = c.card.copy(alpha = 0.8f),
            unfocusedContainerColor = c.card.copy(alpha = 0.8f),
            cursorColor = c.accentHi,
            focusedTextColor = c.ink,
            unfocusedTextColor = c.ink,
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsSheet(
    accounts: AccountsInfo?,
    signIn: SignInInfo?,
    onAddOffline: (String) -> Unit,
    onPlayOffline: (String) -> Unit,
    onRemoveOffline: (String) -> Unit,
    onGameName: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = LocalHorizon.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var current by remember(accounts) { mutableStateOf(accounts) }
    var name by remember { mutableStateOf("") }
    var signInError by remember { mutableStateOf<String?>(null) }
    var deviceCode by remember { mutableStateOf<MicrosoftAuth.DeviceCode?>(null) }
    var signingIn by remember { mutableStateOf(false) }

    fun refresh() {
        scope.launch { runCatching { dev.horizon.launcher.data.Core.accounts() }.onSuccess { current = it } }
    }

    fun startMicrosoft() {
        if (signingIn) return
        signingIn = true
        signInError = null
        scope.launch {
            runCatching {
                val code = MicrosoftAuth.start()
                deviceCode = code
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(code.verificationUri))) }
                    .onFailure { signInError = "Open ${code.verificationUri} in your browser and enter the code manually." }
                MicrosoftAuth.complete()
            }.onSuccess {
                refresh()
            }.onFailure {
                signInError = it.message ?: "Microsoft sign-in failed."
            }
            deviceCode = null
            signingIn = false
        }
    }

    ModalBottomSheet(
        onDismissRequest = { if (!signingIn) onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.side,
        contentColor = c.ink,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                ScreenHeader(
                    kicker = "Player",
                    title = "Accounts",
                    subtitle = "Use a real Microsoft account for online Minecraft, or an offline name for offline-mode play.",
                )
            }
            item {
                Plate {
                    SectionLabel("MICROSOFT")
                    Spacer(Modifier.height(8.dp))
                    Text(
                        current?.accounts?.firstOrNull { it.uuid == current?.selected }?.name
                            ?: "No Microsoft account selected",
                        color = c.ink,
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        when {
                            signIn?.configured == true -> "Real Microsoft device-code sign-in is available in this build."
                            else -> signIn?.message ?: "Microsoft sign-in is not configured."
                        },
                        color = c.muted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(12.dp))
                    PrimaryButton(
                        text = when {
                            signingIn -> "Waiting for Microsoft…"
                            signIn?.configured == true -> "Sign in with Microsoft"
                            else -> "Microsoft sign-in unavailable"
                        },
                        modifier = Modifier.fillMaxWidth(),
                        icon = Icons.Filled.Person,
                        onClick = { if (!signingIn && signIn?.configured == true) startMicrosoft() },
                    )
                    if (signIn?.keychain == false) {
                        Spacer(Modifier.height(8.dp))
                        Text(signIn.noKeychain ?: "This build cannot persist account secrets between runs.", color = c.warn, style = MaterialTheme.typography.bodySmall)
                    }
                    if (signInError != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(signInError!!, color = c.danger, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item {
                Plate {
                    SectionLabel("SIGNED-IN ACCOUNTS")
                    Spacer(Modifier.height(8.dp))
                    val signed = current?.accounts.orEmpty()
                    if (signed.isEmpty()) {
                        Text("No Microsoft accounts yet.", color = c.muted, style = MaterialTheme.typography.bodySmall)
                    } else {
                        signed.forEach { account ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Person, null, tint = c.accentHi)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(account.name, color = c.ink, style = MaterialTheme.typography.titleMedium)
                                    Text(if (current?.selected == account.uuid) "Playing now" else "Signed in", color = if (current?.selected == account.uuid) c.success else c.muted, style = MaterialTheme.typography.bodySmall)
                                }
                                if (current?.selected != account.uuid) {
                                    SoftButton("Use") {
                                        scope.launch { runCatching { dev.horizon.launcher.data.Core.selectAccount(account.uuid) }.onSuccess { current = it } }
                                    }
                                }
                                Spacer(Modifier.width(6.dp))
                                Icon(Icons.Outlined.Delete, "Remove", tint = c.muted, modifier = Modifier.padding(8.dp).clickable {
                                    scope.launch { runCatching { dev.horizon.launcher.data.Core.removeAccount(account.uuid) }.onSuccess { current = it } }
                                })
                            }
                        }
                    }
                }
            }
            item {
                Plate {
                    SectionLabel("OFFLINE NAMES")
                    Spacer(Modifier.height(8.dp))
                    current?.offline.orEmpty().forEach { entry ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Person, null, tint = c.accentHi)
                            Spacer(Modifier.width(10.dp))
                            Text(entry, color = c.ink, modifier = Modifier.weight(1f))
                            if (current?.offlineSelected == entry) Tag("PLAYING", c.success) else SoftButton("Play") { onPlayOffline(entry); refresh() }
                            Spacer(Modifier.width(6.dp))
                            Icon(Icons.Outlined.Delete, "Remove", tint = c.muted, modifier = Modifier.padding(8.dp).clickable { onRemoveOffline(entry); refresh() })
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PixelField(name, { name = it }, "New offline name", Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp))
                        SoftButton("Add", icon = Icons.Outlined.Add) { if (name.isNotBlank()) { onAddOffline(name); name = ""; refresh() } }
                    }
                    Spacer(Modifier.height(8.dp))
                    SoftButton("Play as game's own name", modifier = Modifier.fillMaxWidth()) { onGameName(); refresh() }
                }
            }
            item {
                SoftButton("Close", modifier = Modifier.fillMaxWidth(), onClick = onDismiss)
            }
        }
    }

    if (deviceCode != null) {
        Dialog(onDismissRequest = { MicrosoftAuth.cancel(); deviceCode = null; signingIn = false }) {
            Plate(color = c.side, contentPadding = PaddingValues(20.dp)) {
                ScreenHeader("Microsoft", "Finish sign-in", "Open the Microsoft page and enter this one-time code.")
                Text(deviceCode!!.userCode, color = c.ink, style = MaterialTheme.typography.displayMedium)
                Spacer(Modifier.height(8.dp))
                Text(deviceCode!!.verificationUri, color = c.accentHi, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                SoftButton("Open Microsoft", icon = Icons.Outlined.OpenInNew, modifier = Modifier.fillMaxWidth()) {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(deviceCode!!.verificationUri)))
                }
                Spacer(Modifier.height(8.dp))
                SoftButton("Cancel", modifier = Modifier.fillMaxWidth()) { MicrosoftAuth.cancel(); deviceCode = null; signingIn = false }
            }
        }
    }
}

@Composable
fun NewInstanceDialog(onCreate: (String) -> Unit, onDismiss: () -> Unit) {
    val c = LocalHorizon.current
    var name by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss) {
        Plate(color = c.side, contentPadding = PaddingValues(20.dp)) {
            Text("New instance", color = c.ink, style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(6.dp))
            Text("An empty game folder with its own worlds, mods and settings.", color = c.muted, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(14.dp))
            PixelField(name, { name = it }, "Instance name")
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SoftButton("Cancel", Modifier.weight(1f), onClick = onDismiss)
                PrimaryButton("Create", Modifier.weight(1f)) { if (name.isNotBlank()) onCreate(name) }
            }
        }
    }
}

@Composable
fun AddServerDialog(onAdd: (String, String) -> Unit, onDismiss: () -> Unit) {
    val c = LocalHorizon.current
    var name by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss) {
        Plate(color = c.side, contentPadding = PaddingValues(20.dp)) {
            Text("Add a server", color = c.ink, style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(6.dp))
            Text("Saved to servers.dat.", color = c.muted, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(14.dp))
            PixelField(name, { name = it }, "Name (optional)")
            Spacer(Modifier.height(10.dp))
            PixelField(address, { address = it }, "Address (host:port)")
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SoftButton("Cancel", Modifier.weight(1f), onClick = onDismiss)
                PrimaryButton("Add", Modifier.weight(1f)) { if (address.isNotBlank()) onAdd(name, address) }
            }
        }
    }
}
