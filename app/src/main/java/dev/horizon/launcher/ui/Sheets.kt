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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import dev.horizon.launcher.data.AccountsInfo
import dev.horizon.launcher.data.SignInInfo

@Composable
fun PixelField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    val c = LocalHorizon.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label, color = c.muted) },
        singleLine = true,
        shape = pixelShape(6.dp),
        colors = OutlinedTextFieldDefaults.colors(
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
    var name by remember { mutableStateOf("") }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.side,
        contentColor = c.ink,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp)) {
            ScreenHeader(
                kicker = "Player",
                title = "Accounts",
                subtitle = "Offline names play without a Microsoft account, and can join servers " +
                    "that run in offline mode.",
            )
            Plate {
                SectionLabel("Playing now")
                Spacer(Modifier.height(10.dp))
                Text(
                    accounts?.player?.label ?: "Reading the core…",
                    color = c.ink,
                    style = MaterialTheme.typography.titleLarge,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    when (accounts?.player?.kind) {
                        "offline" -> "Offline name · no session, works on cracked servers"
                        "microsoft" -> "Microsoft account · licensed servers"
                        else -> "The game picks the name from horizon-player.txt"
                    },
                    color = c.muted,
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(12.dp))
                SoftButton("Play as the game's own name", modifier = Modifier.fillMaxWidth()) {
                    onGameName()
                }
            }
            Spacer(Modifier.height(12.dp))
            Plate {
                SectionLabel("Offline names")
                Spacer(Modifier.height(8.dp))
                val offline = accounts?.offline.orEmpty()
                if (offline.isEmpty()) {
                    Text(
                        "No offline names yet. Add one below; 3 to 16 letters, digits or _.",
                        color = c.muted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        offline.forEach { entry ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Person, contentDescription = null, tint = c.accentHi)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(entry, color = c.ink, style = MaterialTheme.typography.titleMedium)
                                    if (accounts?.offlineSelected == entry) {
                                        Text("Playing", color = c.success, style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                                SoftButton("Play") { onPlayOffline(entry) }
                                Spacer(Modifier.width(6.dp))
                                Icon(
                                    Icons.Outlined.Delete,
                                    contentDescription = "Remove",
                                    tint = c.muted,
                                    modifier = Modifier
                                        .padding(8.dp)
                                        .width(22.dp)
                                        .height(22.dp)
                                        .clickable { onRemoveOffline(entry) },
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PixelField(
                        value = name,
                        onValueChange = { name = it },
                        label = "New offline name",
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    SoftButton("Add", icon = Icons.Outlined.Add) {
                        onAddOffline(name)
                        name = ""
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Plate {
                SectionLabel("Microsoft")
                Spacer(Modifier.height(8.dp))
                Text(
                    when {
                        signIn == null -> "Reading sign-in configuration…"
                        signIn.configured -> "A Microsoft client ID is configured, but the Android device-code flow is not connected to this screen yet."
                        else -> signIn.message ?: "Microsoft sign-in is not configured in this build."
                    },
                    color = c.muted,
                    style = MaterialTheme.typography.bodySmall,
                )
                if (signIn?.configured == true) {
                    Spacer(Modifier.height(8.dp))
                    Tag("ANDROID FLOW NOT WIRED", c.warn)
                }
                if (signIn?.keychain == false) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        signIn.noKeychain ?: "This build has no keychain; sign-ins last one run.",
                        color = c.warn,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            SoftButton("Close", modifier = Modifier.fillMaxWidth(), onClick = onDismiss)
            Spacer(Modifier.height(24.dp))
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
            Text(
                "An empty game folder with your options and offline name.",
                color = c.muted,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(14.dp))
            PixelField(value = name, onValueChange = { name = it }, label = "Instance name")
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SoftButton("Cancel", modifier = Modifier.weight(1f), onClick = onDismiss)
                PrimaryButton(
                    text = "Create",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        if (name.isNotBlank()) onCreate(name)
                    },
                )
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
            Text(
                "Saved to servers.dat. Offline-mode servers accept your offline name.",
                color = c.muted,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(14.dp))
            PixelField(value = name, onValueChange = { name = it }, label = "Name (optional)")
            Spacer(Modifier.height(10.dp))
            PixelField(value = address, onValueChange = { address = it }, label = "Address (host:port)")
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SoftButton("Cancel", modifier = Modifier.weight(1f), onClick = onDismiss)
                PrimaryButton(
                    text = "Add",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        if (address.isNotBlank()) onAdd(name, address)
                    },
                )
            }
        }
    }
}

@Composable
fun TextPromptDialog(
    title: String,
    label: String,
    initial: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = LocalHorizon.current
    var value by remember { mutableStateOf(initial) }
    Dialog(onDismissRequest = onDismiss) {
        Plate(color = c.side, contentPadding = PaddingValues(20.dp)) {
            Text(title, color = c.ink, style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(14.dp))
            PixelField(value = value, onValueChange = { value = it }, label = label)
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SoftButton("Cancel", modifier = Modifier.weight(1f), onClick = onDismiss)
                PrimaryButton("Save", modifier = Modifier.weight(1f)) { onSave(value) }
            }
        }
    }
}
