package dev.horizon.launcher.ui.dynamic

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Safe Compose renderer for the Core UI contract. New pages and navigation entries are data;
 * the renderer only accepts the small, explicitly supported component vocabulary.
 */
@Composable
fun DynamicUiHost(
    document: UiDocument,
    onAction: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedPage by rememberSaveable(document.schemaVersion) {
        mutableStateOf(document.navigation.firstOrNull()?.page ?: document.pages.first().id)
    }
    val page = document.page(selectedPage) ?: document.pages.first()

    Scaffold(
        modifier = modifier,
        bottomBar = {
            if (document.navigation.isNotEmpty()) {
                NavigationBar {
                    document.navigation.forEach { item ->
                        NavigationBarItem(
                            selected = item.page == selectedPage,
                            onClick = { selectedPage = item.page },
                            icon = {},
                            label = { Text(item.label) }
                        )
                    }
                }
            }
        }
    ) { insets ->
        Column(
            Modifier.fillMaxWidth().padding(insets).padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(page.title, style = MaterialTheme.typography.headlineMedium)
            page.components.forEach { component -> DynamicComponent(component, onAction) }
        }
    }
}

@Composable
private fun DynamicComponent(component: UiComponent, onAction: (String) -> Unit) {
    when (component.type) {
        "heading" -> Text(component.text, style = MaterialTheme.typography.titleLarge)
        "text" -> Text(component.text, style = MaterialTheme.typography.bodyMedium)
        "button" -> Button(
            onClick = { component.action?.let(onAction) },
            enabled = component.action != null,
            modifier = Modifier.fillMaxWidth()
        ) { Text(component.text) }
        "divider" -> Divider()
        "progress" -> {
            Text(component.text, style = MaterialTheme.typography.labelMedium)
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        "card" -> Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (component.text.isNotBlank()) Text(component.text, style = MaterialTheme.typography.titleMedium)
                component.children.forEach { DynamicComponent(it, onAction) }
            }
        }
        "list" -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            component.children.forEach { DynamicComponent(it, onAction) }
        }
    }
}
