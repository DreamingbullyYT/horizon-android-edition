package dev.horizon.launcher.ui

import android.net.Uri
import android.provider.DocumentsContract
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class BrowserFolder(val id: String, val name: String)
private data class BrowserEntry(
    val id: String,
    val name: String,
    val isDirectory: Boolean,
    val size: Long,
)

/** SAF-backed in-app browser. The system picker is used only once to grant a storage tree. */
@Composable
fun MinecraftFileBrowserDialog(
    treeUri: Uri,
    onDismiss: () -> Unit,
    onChangeStorage: () -> Unit,
    onSelectFolder: (String) -> Unit,
    onSelectZip: (Uri) -> Unit,
) {
    val context = LocalContext.current
    val rootId = remember(treeUri) { DocumentsContract.getTreeDocumentId(treeUri) }
    var path by remember(treeUri) { mutableStateOf(listOf(BrowserFolder(rootId, "Selected storage"))) }
    var entries by remember { mutableStateOf<List<BrowserEntry>>(emptyList()) }
    var selectedZip by remember { mutableStateOf<BrowserEntry?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val current = path.last()

    LaunchedEffect(treeUri, current.id) {
        loading = true
        error = null
        selectedZip = null
        val queried = withContext(Dispatchers.IO) {
            runCatching {
                val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, current.id)
                val found = mutableListOf<BrowserEntry>()
                context.contentResolver.query(
                    childrenUri,
                    arrayOf(
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                        DocumentsContract.Document.COLUMN_MIME_TYPE,
                        DocumentsContract.Document.COLUMN_SIZE,
                    ),
                    null,
                    null,
                    null,
                )?.use { cursor ->
                    val idColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    val nameColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    val mimeColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                    val sizeColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                    while (cursor.moveToNext()) {
                        if (idColumn < 0 || nameColumn < 0 || mimeColumn < 0) continue
                        val id = cursor.getString(idColumn) ?: continue
                        val name = cursor.getString(nameColumn) ?: continue
                        val mime = cursor.getString(mimeColumn)
                        val isDirectory = mime == DocumentsContract.Document.MIME_TYPE_DIR
                        val isZip = name.endsWith(".zip", ignoreCase = true) || mime == "application/zip" || mime == "application/x-zip-compressed"
                        if (isDirectory || isZip) {
                            found += BrowserEntry(id, name, isDirectory, if (sizeColumn >= 0 && !cursor.isNull(sizeColumn)) cursor.getLong(sizeColumn) else 0L)
                        }
                    }
                }
                found.sortedWith(compareBy<BrowserEntry> { !it.isDirectory }.thenBy { it.name.lowercase() })
            }
        }
        queried.onSuccess { entries = it }
            .onFailure { entries = emptyList(); error = it.message ?: "Could not read this folder." }
        loading = false
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.94f).heightIn(max = 700.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Minecraft files", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
                Text(
                    path.joinToString(" / ") { it.name },
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        enabled = path.size > 1,
                        onClick = { if (path.size > 1) path = path.dropLast(1) },
                        modifier = Modifier.weight(1f),
                    ) {
                        androidx.compose.material3.Icon(Icons.Outlined.ArrowBack, contentDescription = null)
                        Spacer(Modifier.size(6.dp))
                        Text("Up")
                    }
                    OutlinedButton(onClick = onChangeStorage, modifier = Modifier.weight(1f)) {
                        androidx.compose.material3.Icon(Icons.Outlined.Refresh, contentDescription = null)
                        Spacer(Modifier.size(6.dp))
                        Text("Change storage")
                    }
                }
                Spacer(Modifier.height(8.dp))
                when {
                    loading -> Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.Center) {
                        CircularProgressIndicator(Modifier.size(22.dp))
                    }
                    error != null -> Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    entries.isEmpty() -> Text("No folders or ZIP backups here.", style = MaterialTheme.typography.bodyMedium)
                    else -> LazyColumn(Modifier.weight(1f, fill = false)) {
                        items(entries, key = { it.id }) { entry ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (entry.isDirectory) {
                                            path = path + BrowserFolder(entry.id, entry.name)
                                            selectedZip = null
                                        } else selectedZip = entry
                                    }
                                    .padding(vertical = 10.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                androidx.compose.material3.Icon(
                                    if (entry.isDirectory) Icons.Outlined.Folder else Icons.Outlined.Archive,
                                    contentDescription = null,
                                    tint = if (entry.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                                )
                                Spacer(Modifier.size(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(entry.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        if (entry.isDirectory) "Folder" else if (entry.size > 0) "ZIP · ${browserSize(entry.size)}" else "ZIP backup",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (selectedZip?.id == entry.id) {
                                    Text("Selected", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                if (selectedZip != null) {
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            val selected = selectedZip ?: return@Button
                            onSelectZip(DocumentsContract.buildDocumentUriUsingTree(treeUri, selected.id))
                        },
                    ) { Text("Import ${selectedZip!!.name}", maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    Spacer(Modifier.height(6.dp))
                }
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onSelectFolder(current.id) },
                ) { Text("Import this folder") }
                Text(
                    "Choose a Minecraft folder containing versions/26.3, or select a Horizon ZIP backup.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun browserSize(bytes: Long): String = when {
    bytes >= 1L shl 30 -> "%.1f GB".format(bytes / (1L shl 30).toDouble())
    bytes >= 1L shl 20 -> "%.1f MB".format(bytes / (1L shl 20).toDouble())
    else -> "%.0f KB".format(bytes / 1024.0)
}

