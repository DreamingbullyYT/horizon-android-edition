package dev.horizon.launcher.ui2

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.horizon.launcher.MainActivity
import java.io.File
import org.json.JSONObject

/**
 * Screen id -> screen. `ui/app.json` decides which ids appear in the rail; adding a screen is one
 * entry here plus one entry in app.json. A screen draws itself from [AppState] and nothing else.
 */
object ScreenRegistry {
    val ids = setOf("home", "instances", "mods", "servers", "updates", "settings")

    @Composable
    fun Show(id: String, s: AppState) {
        when (id) {
            "home" -> HomeScreen(s)
            "instances" -> InstancesScreen(s)
            "mods" -> ModsScreen(s)
            "servers" -> ServersScreen(s)
            "updates" -> UpdatesScreen(s)
            "settings" -> SettingsScreen(s)
            else -> Empty("Screen '$id' does not exist.")
        }
    }
}

private fun obj(vararg pairs: Pair<String, Any?>) = JSONObject().also { o -> pairs.forEach { o.put(it.first, it.second) } }

@Composable
fun HomeScreen(s: AppState) {
    val current = s.currentInstance()
    val ready = s.obj("gameReady").optBoolean("ready", true)
    Column(Modifier.fillMaxSize(), Arrangement.SpaceBetween) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Pill(current?.optString("version")?.ifEmpty { null } ?: "No version", "versions") { s.screen = "updates" }
            Pill(s.playerName().ifEmpty { "Account" }, "person") { s.screen = "settings" }
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Hint("INSTANCE")
            Text(
                (current?.optString("name") ?: "No instance yet").uppercase(),
                color = Ink, fontSize = 44.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2,
            )
            if (!ready) Hint("Game files are missing. Open Settings → Game files.")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Pill("Play", "play", primary = true, enabled = s.busy == null) {
                    if (!ready) s.say("Game files are missing. Open Settings → Game files.")
                    else s.act("Starting", "launch", ok = "Starting…")
                }
                Pill("Configure", "tune") { s.screen = "instances" }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Pill("Mods", "mods") { s.screen = "mods" }
            Pill("Servers", "servers") { s.screen = "servers" }
            Pill("Updates", "cloud-download") { s.screen = "updates" }
        }
    }
}

@Composable
fun InstancesScreen(s: AppState) {
    var creating by remember { mutableStateOf(false) }
    val chosen = s.obj("settings").optString("instance")
    val list = s.array("instances").objects()
    Column(Modifier.fillMaxSize()) {
        Header("Instances") { Pill("New", "add") { creating = true } }
        if (list.isEmpty()) Empty("No instance yet.")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(list) { i ->
                val id = i.optString("id")
                Card(Modifier.fillMaxWidth(), onClick = { s.act("Selecting", "selectInstance", obj("id" to id), null, "instances", "settings") }) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f)) {
                            Label(i.optString("name"))
                            Hint(listOf(i.optString("version"), if (id == chosen) "selected" else "").filter { it.isNotEmpty() }.joinToString(" · "))
                        }
                        Pill(null, "delete") { s.act("Deleting", "deleteInstance", obj("id" to id), "Deleted", "instances", "settings") }
                    }
                }
            }
        }
    }
    if (creating) TextDialog("New instance", "Name", "Create", { creating = false }) { name ->
        creating = false
        s.act("Creating", "createInstance", obj("name" to name), "Created $name", "instances", "settings")
    }
}

@Composable
fun ServersScreen(s: AppState) {
    var adding by remember { mutableStateOf(false) }
    val list = s.array("servers").objects()
    Column(Modifier.fillMaxSize()) {
        Header("Servers") { Pill("Add", "add") { adding = true } }
        if (list.isEmpty()) Empty("No servers saved.")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(list.size) { index ->
                val sv = list[index]
                Card(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(Modifier.weight(1f)) { Label(sv.optString("name")); Hint(sv.optString("ip")) }
                        Pill("Join", "play") { s.act("Joining", "launch", obj("server" to sv.optString("ip")), "Joining ${sv.optString("name")}") }
                        Pill(null, "delete") { s.act("Removing", "removeServer", obj("index" to index), null, "servers") }
                    }
                }
            }
        }
    }
    if (adding) ServerDialog({ adding = false }) { name, ip ->
        adding = false
        s.act("Adding", "addServer", obj("name" to name, "ip" to ip), null, "servers")
    }
}

@Composable
fun ModsScreen(s: AppState) {
    val context = LocalContext.current
    val list = s.array("mods").objects()
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val file = copyToCache(context, uri)
            s.act("Importing", "importMod", obj("source" to file.absolutePath), "Imported. Restart the game to load it.", "mods", after = { file.delete() })
        }
    }
    Column(Modifier.fillMaxSize()) {
        Header("Mods") { Pill("Import", "add") { pick.launch(arrayOf("*/*")) } }
        if (list.isEmpty()) Empty("No mods installed.")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(list) { m ->
                Card(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(Modifier.weight(1f)) { Label(m.optString("name")); Hint(m.optString("file")) }
                        Pill(null, "delete") { s.act("Removing", "removeMod", obj("id" to m.optString("id")), "Removed. Restart the game to apply.", "mods") }
                    }
                }
            }
        }
    }
}

@Composable
fun UpdatesScreen(s: AppState) {
    val installed = s.array("installed").objects()
    val releases = s.array("releases").objects()
    val have = installed.map { it.optString("tag") }.toSet()
    Column(Modifier.fillMaxSize()) {
        Header("Updates") { Pill(null, "refresh") { s.refresh("releases", "installed") } }
        if (releases.isEmpty()) Empty("Loading releases…")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(releases) { r ->
                val tag = r.optString("tag")
                Card(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(Modifier.weight(1f)) {
                            Label(r.optString("name").ifEmpty { tag })
                            Hint(r.optString("date").take(10))
                        }
                        if (tag in have) Pill("Remove", "delete") { s.act("Removing", "removeVersion", obj("tag" to tag), "Removed $tag", "installed") }
                        else Pill("Install", "download", enabled = s.busy == null) { s.act("Installing $tag", "installVersion", obj("tag" to tag), "Installed $tag", "installed") }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(s: AppState) {
    val context = LocalContext.current
    val settings = s.obj("settings")
    val telemetry = s.obj("telemetry")
    val about = s.obj("about")
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), Arrangement.spacedBy(12.dp)) {
        Header("Settings")
        ToggleCard("Disable AI features", settings.optBoolean("noAi")) { s.act("Saving", "setNoAi", obj("on" to it), null, "settings") }
        ToggleCard(
            "Allow telemetry",
            telemetry.optBoolean("performance") || telemetry.optBoolean("crashes") || telemetry.optBoolean("bugs") || telemetry.optBoolean("benchmarks"),
        ) { s.act("Saving", "setTelemetry", obj("on" to it), null, "telemetry") }
        Card(Modifier.fillMaxWidth(), onClick = { openClassic(context, "settings") }) {
            Column { Label("Game files, accounts, touch controls"); Hint("Opens the classic screens") }
        }
        Card(Modifier.fillMaxWidth()) {
            Column { Label("Horizon Android"); Hint(about.optString("version")); Hint(about.optString("dataDir")) }
        }
    }
}

@Composable
private fun ToggleCard(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box1(Modifier.weight(1f)) { Label(label) }
            Switch(value, onChange, colors = SwitchDefaults.colors(checkedTrackColor = LocalAccent.current))
        }
    }
}

@Composable
private fun Box1(modifier: Modifier, content: @Composable () -> Unit) = androidx.compose.foundation.layout.Box(modifier) { content() }

@Composable
private fun TextDialog(title: String, hint: String, ok: String, onDismiss: () -> Unit, onOk: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(text, { text = it }, label = { Text(hint) }, singleLine = true) },
        confirmButton = { TextButton(onClick = { if (text.isNotBlank()) onOk(text.trim()) }) { Text(ok) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ServerDialog(onDismiss: () -> Unit, onOk: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var ip by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add server") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                OutlinedTextField(ip, { ip = it }, label = { Text("Address") }, singleLine = true)
            }
        },
        confirmButton = { TextButton(onClick = { if (ip.isNotBlank()) onOk(name.ifBlank { ip }.trim(), ip.trim()) }) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun openClassic(context: Context, screen: String) {
    context.startActivity(Intent(context, MainActivity::class.java).putExtra("screen", screen).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

private fun copyToCache(context: Context, uri: Uri): File {
    val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
        if (it.moveToFirst()) it.getString(0) else null
    } ?: "import.bin"
    val file = File(context.cacheDir, name.replace('/', '_'))
    context.contentResolver.openInputStream(uri)?.use { input -> file.outputStream().use { input.copyTo(it) } }
    return file
}
