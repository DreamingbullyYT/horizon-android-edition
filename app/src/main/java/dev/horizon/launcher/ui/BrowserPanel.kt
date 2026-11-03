package dev.horizon.launcher.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.horizon.launcher.data.Browse
import dev.horizon.launcher.data.BrowseEntry
import dev.horizon.launcher.data.BrowseItem
import dev.horizon.launcher.data.BrowseProgress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** State of the in-app mod browser (logic lives in the Rust core, so it follows upstream). */
class BrowserState(
    private val scope: CoroutineScope,
    private val say: (String) -> Unit,
    private val onChanged: () -> Unit,
) {
    val kinds = listOf("mod" to "Mods", "resource-pack" to "Resource packs", "shader" to "Shaders")
    var kind by mutableStateOf("mod")
    var source by mutableStateOf("horizon")
    var text by mutableStateOf("")
    var items by mutableStateOf<List<BrowseItem>>(emptyList())
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var next by mutableStateOf(0)
    var canLoadMore by mutableStateOf(false)
    var installing by mutableStateOf<String?>(null)
    var progress by mutableStateOf(BrowseProgress(false, "", 0f, null))
    var entries by mutableStateOf<List<BrowseEntry>>(emptyList())
    private var started = false

    fun start() {
        if (started) return
        started = true
        refreshEntries()
        search(true)
    }

    fun refreshEntries() {
        scope.launch { runCatching { Browse.installed() }.onSuccess { entries = it } }
    }

    fun selectKind(value: String) {
        kind = value
        // Horizon's own shader packs live on its registry only.
        if (value == "shader") source = "horizon"
        search(true)
    }

    fun selectSource(value: String) {
        source = value
        search(true)
    }

    fun search(reset: Boolean) {
        if (loading) return
        loading = true
        error = null
        val offset = if (reset) 0 else next
        scope.launch {
            runCatching { Browse.search(source, kind, text.trim(), offset) }
                .onSuccess { (found, nextOffset) ->
                    items = if (reset) found else items + found
                    next = nextOffset
                    canLoadMore = found.isNotEmpty()
                }
                .onFailure { error = it.message ?: "Could not reach the registry" ; if (reset) items = emptyList(); canLoadMore = false }
            loading = false
        }
    }

    fun install(item: BrowseItem) {
        if (installing != null) return
        installing = item.id
        progress = BrowseProgress(true, "Starting", 0f, null)
        val poll = scope.launch {
            while (true) {
                runCatching { Browse.progress() }.onSuccess { progress = it }
                delay(300)
            }
        }
        scope.launch {
            runCatching { Browse.install(item.source, item.id) }
                .onSuccess { names ->
                    items = items.map { if (it.id == item.id && it.source == item.source) it.copy(installed = true) else it }
                    say("Installed ${names.joinToString().ifEmpty { item.title }}. Restart the game to apply.")
                    refreshEntries()
                    onChanged()
                }
                .onFailure { say(it.message ?: "Install failed") }
            poll.cancel()
            installing = null
        }
    }

    fun remove(entry: BrowseEntry) {
        scope.launch {
            runCatching { Browse.remove(entry.project) }
                .onSuccess { say("Removed ${it.ifEmpty { entry.title }}. Restart the game to apply.") }
                .onFailure { say(it.message ?: "Could not remove") }
            refreshEntries()
            items = items.map { if (it.id == entry.project) it.copy(installed = false) else it }
            onChanged()
        }
    }
}

private fun compact(n: Long): String = when {
    n >= 1_000_000 -> "%.1fM".format(n / 1_000_000.0)
    n >= 1_000 -> "%.1fK".format(n / 1_000.0)
    else -> n.toString()
}

/** The "Discover" tab: search Horizon's registry and Modrinth, install with one tap. */
fun LazyListScope.browserItems(state: BrowserState) {
    item {
        val c = LocalHorizon.current
        androidx.compose.runtime.LaunchedEffect(Unit) { state.start() }
        Plate {
            SectionLabel("Find something new")
            Spacer(Modifier.height(10.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.kinds.forEach { (id, label) -> Pill(label, selected = state.kind == id) { state.selectKind(id) } }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("Horizon registry", selected = state.source == "horizon") { state.selectSource("horizon") }
                if (state.kind != "shader") Pill("Modrinth", selected = state.source == "modrinth") { state.selectSource("modrinth") }
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = state.text,
                onValueChange = { state.text = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search…", color = c.muted) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { state.search(true) }),
            )
            Spacer(Modifier.height(10.dp))
            PrimaryButton("Search", Modifier.fillMaxWidth(), Icons.Outlined.Extension) { state.search(true) }
            if (state.installing != null) {
                Spacer(Modifier.height(12.dp))
                Text(state.progress.line.ifEmpty { "Installing…" }, color = c.inkSoft, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { state.progress.fraction.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                    color = c.accentHi,
                )
            }
        }
    }
    state.error?.let { message ->
        item {
            EmptyState(icon = Icons.Outlined.Extension, title = "Could not load the list", body = message, primary = "Try again", onPrimary = { state.search(true) })
        }
    }
    if (state.items.isEmpty() && state.error == null && !state.loading) {
        item { EmptyState(icon = Icons.Outlined.Extension, title = "Nothing found", body = "Try another search or switch the source.") }
    }
    items(state.items, key = { it.source + ":" + it.id }) { item ->
        val c = LocalHorizon.current
        Plate(contentPadding = PaddingValues(14.dp)) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(item.title, color = c.ink, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${item.author} · ${compact(item.downloads)} downloads", color = c.muted, style = MaterialTheme.typography.bodySmall)
                }
                Tag(item.support, if (item.works) c.success else if (item.installable) c.warn else c.danger)
            }
            if (item.summary.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(item.summary, color = c.inkSoft, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            if (!item.installable && item.reason != null) {
                Spacer(Modifier.height(6.dp))
                Text(item.reason, color = c.danger, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(10.dp))
            when {
                item.installed -> SoftButton("Installed", Modifier.fillMaxWidth(), Icons.Outlined.Check) {}
                !item.installable -> SoftButton("Not supported", Modifier.fillMaxWidth()) {}
                else -> PrimaryButton(
                    if (state.installing == item.id) "Installing…" else "Install",
                    Modifier.fillMaxWidth(),
                    Icons.Outlined.FileDownload,
                ) { state.install(item) }
            }
        }
    }
    if (state.canLoadMore && state.items.isNotEmpty()) {
        item { SoftButton(if (state.loading) "Loading…" else "Load more", Modifier.fillMaxWidth()) { state.search(false) } }
    }
}

/** Items installed through the browser, with Remove. Used on the "Installed" tab. */
fun LazyListScope.browserInstalledItems(state: BrowserState) {
    if (state.entries.isEmpty()) return
    item { SectionLabel("Installed from the browser") }
    items(state.entries, key = { it.source + ":" + it.project }) { entry ->
        Plate(contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)) {
            InfoRow(
                icon = Icons.Outlined.Extension,
                title = entry.title,
                subtitle = "${entry.kind.replace('-', ' ')} · ${entry.number} · ${entry.source}",
                trailing = "Remove",
                onClick = { state.remove(entry) },
            )
        }
    }
}
