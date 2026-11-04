package dev.horizon.launcher.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

private enum class ExplorerAction { DETAILS, RENAME, DELETE }

@Composable
fun MinecraftFilesScreen(gameDirectory: File, onMessage: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val root = remember(gameDirectory) { gameDirectory.canonicalFile }
    var current by remember(root) { mutableStateOf(root) }
    var entries by remember { mutableStateOf<List<File>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var newestFirst by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var showCreate by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    var target by remember { mutableStateOf<File?>(null) }
    var action by remember { mutableStateOf<ExplorerAction?>(null) }
    var renameValue by remember { mutableStateOf("") }
    var menuTarget by remember { mutableStateOf<File?>(null) }

    fun isInsideRoot(file: File): Boolean {
        val path = runCatching { file.canonicalPath }.getOrNull() ?: return false
        val rootPath = root.canonicalPath
        return path == rootPath || path.startsWith(rootPath + File.separator)
    }

    fun refresh() {
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    if (!root.exists() && !root.mkdirs()) error("Cannot create game directory")
                    check(isInsideRoot(current)) { "Invalid directory" }
                    check(current.isDirectory) { "Directory no longer exists" }
                    val comparator = Comparator<File> { a, b ->
                        val dirs = b.isDirectory.compareTo(a.isDirectory)
                        if (dirs != 0) dirs
                        else if (newestFirst) b.lastModified().compareTo(a.lastModified())
                        else a.name.compareTo(b.name, ignoreCase = true)
                    }
                    current.listFiles()?.filter(::isInsideRoot)?.sortedWith(comparator).orEmpty()
                }
            }
            result.onSuccess { entries = it; error = null }
                .onFailure { entries = emptyList(); error = it.message ?: "Could not read folder" }
        }
    }

    LaunchedEffect(current, refreshKey, newestFirst) { refresh() }
    val visible = remember(entries, query) {
        if (query.isBlank()) entries else entries.filter { it.name.contains(query.trim(), true) }
    }

    Column(
        Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("Minecraft files", style = MaterialTheme.typography.headlineSmall)
            Text("Internal game directory", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f),
        ) {
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Storage, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    val relative = runCatching { current.relativeTo(root).path }.getOrDefault("")
                    Text(
                        if (relative.isBlank() || relative == ".") "game /" else "game / $relative",
                        Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { current = current.parentFile ?: root },
                        enabled = current != root,
                        modifier = Modifier.weight(1f),
                    ) { Icon(Icons.Outlined.ArrowBack, null); Spacer(Modifier.width(4.dp)); Text("Up") }
                    OutlinedButton(onClick = { refreshKey++ }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.Refresh, null); Spacer(Modifier.width(4.dp)); Text("Refresh")
                    }
                    OutlinedButton(onClick = { newFolderName = ""; showCreate = true }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.CreateNewFolder, null); Spacer(Modifier.width(4.dp)); Text("New")
                    }
                }
            }
        }

        OutlinedTextField(
            value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(),
            singleLine = true, leadingIcon = { Icon(Icons.Outlined.Search, null) },
            placeholder = { Text("Search files and folders") },
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("${visible.size} item(s)", Modifier.weight(1f),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { newestFirst = !newestFirst }) {
                Icon(Icons.Outlined.Sort, null); Spacer(Modifier.width(4.dp))
                Text(if (newestFirst) "Newest" else "Name")
            }
        }

        when {
            error != null -> Text(error!!, color = MaterialTheme.colorScheme.error)
            visible.isEmpty() -> Surface(
                Modifier.fillMaxWidth().weight(1f),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .3f),
            ) {
                Column(Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center) {
                    Icon(Icons.Outlined.FolderOpen, null, modifier = Modifier.size(38.dp),
                        tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))
                    Text(if (query.isBlank()) "This folder is empty" else "No matching items")
                    Text("This view shows files in Horizon's private game folder.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            else -> LazyColumn(
                Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                items(visible, key = { it.absolutePath }) { file ->
                    Surface(
                        Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .28f),
                    ) {
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                if (file.isDirectory && isInsideRoot(file)) current = file
                                else { target = file; action = ExplorerAction.DETAILS }
                            }.padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                when {
                                    file.isDirectory -> Icons.Outlined.Folder
                                    file.extension.equals("zip", true) -> Icons.Outlined.Archive
                                    else -> Icons.Outlined.Description
                                }, null, tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(25.dp),
                            )
                            Spacer(Modifier.width(11.dp))
                            Column(Modifier.weight(1f)) {
                                Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    if (file.isDirectory) "Folder"
                                    else "${formatExplorerSize(file.length())} · ${file.extension.uppercase(Locale.ROOT).ifBlank { "FILE" }}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { menuTarget = file }) {
                                Icon(Icons.Outlined.MoreVert, "File actions")
                            }
                            DropdownMenu(menuTarget == file, { menuTarget = null }) {
                                DropdownMenuItem(text = { Text("Details") }, onClick = {
                                    menuTarget = null; target = file; action = ExplorerAction.DETAILS
                                })
                                DropdownMenuItem(text = { Text("Rename") }, onClick = {
                                    menuTarget = null; target = file; renameValue = file.name
                                    action = ExplorerAction.RENAME
                                })
                                DropdownMenuItem(
                                    text = { Text("Delete") },
                                    leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                                    onClick = { menuTarget = null; target = file; action = ExplorerAction.DELETE },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreate) AlertDialog(
        onDismissRequest = { showCreate = false },
        title = { Text("Create folder") },
        text = { OutlinedTextField(newFolderName, { newFolderName = it }, singleLine = true,
            label = { Text("Folder name") }) },
        confirmButton = {
            TextButton(onClick = {
                val name = newFolderName.trim()
                val valid = name.isNotBlank() && name !in setOf(".", "..") &&
                    !name.contains('/') && !name.contains('\\') && !name.contains(':')
                if (!valid) onMessage("Enter a valid folder name")
                else {
                    scope.launch {
                        val made = withContext(Dispatchers.IO) {
                            isInsideRoot(current) && File(current, name).mkdir()
                        }
                        onMessage(if (made) "Folder created" else "Could not create folder")
                        if (made) refreshKey++
                    }
                    showCreate = false
                }
            }) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = { showCreate = false }) { Text("Cancel") } },
    )

    val selected = target
    when (action) {
        ExplorerAction.DETAILS -> if (selected != null) AlertDialog(
            onDismissRequest = { action = null; target = null },
            title = { Text(selected.name) },
            text = {
                Text(buildString {
                    append(if (selected.isDirectory) "Folder" else "File")
                    append("\nPath: ${runCatching { selected.relativeTo(root).path }.getOrDefault("Unavailable")}")
                    if (selected.isFile) append("\nSize: ${formatExplorerSize(selected.length())}")
                    append("\nModified: ${java.text.DateFormat.getDateTimeInstance().format(selected.lastModified())}")
                })
            },
            confirmButton = { TextButton(onClick = { action = null; target = null }) { Text("Close") } },
        )
        ExplorerAction.RENAME -> if (selected != null) AlertDialog(
            onDismissRequest = { action = null; target = null },
            title = { Text("Rename") },
            text = { OutlinedTextField(renameValue, { renameValue = it }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    val name = renameValue.trim()
                    val valid = name.isNotBlank() && name !in setOf(".", "..") &&
                        !name.contains('/') && !name.contains('\\') && !name.contains(':')
                    val destination = File(selected.parentFile, name)
                    if (!valid || !isInsideRoot(selected) || !isInsideRoot(destination)) {
                        onMessage("Invalid file name")
                    } else scope.launch {
                        val renamed = withContext(Dispatchers.IO) {
                            !destination.exists() && selected.renameTo(destination)
                        }
                        onMessage(if (renamed) "Renamed successfully" else "Could not rename item")
                        refreshKey++
                    }
                    action = null; target = null
                }) { Text("Rename") }
            },
            dismissButton = { TextButton(onClick = { action = null; target = null }) { Text("Cancel") } },
        )
        ExplorerAction.DELETE -> if (selected != null) AlertDialog(
            onDismissRequest = { action = null; target = null },
            title = { Text("Delete ${if (selected.isDirectory) "folder" else "file"}?") },
            text = { Text("This permanently deletes “${selected.name}” and everything inside it. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    if (!isInsideRoot(selected) || selected == root) onMessage("Refusing to delete the game root")
                    else scope.launch {
                        val deleted = withContext(Dispatchers.IO) { selected.deleteRecursively() }
                        onMessage(if (deleted) "Deleted ${selected.name}" else "Could not delete ${selected.name}")
                        refreshKey++
                    }
                    action = null; target = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { action = null; target = null }) { Text("Cancel") } },
        )
        null -> Unit
    }
}

private fun formatExplorerSize(bytes: Long): String = when {
    bytes >= 1L shl 30 -> "%.2f GB".format(bytes / (1L shl 30).toDouble())
    bytes >= 1L shl 20 -> "%.1f MB".format(bytes / (1L shl 20).toDouble())
    bytes >= 1L shl 10 -> "%.0f KB".format(bytes / (1L shl 10).toDouble())
    else -> "$bytes B"
}
