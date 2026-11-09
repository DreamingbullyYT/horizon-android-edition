package dev.horizon.launcher.ui.dynamic

import org.json.JSONArray
import org.json.JSONObject

/**
 * Versioned, data-only UI contract shared by the Rust core and the Android renderer.
 * The contract intentionally contains identifiers and presentation hints, never executable code.
 */
data class UiDocument(
    val schemaVersion: Int,
    val title: String,
    val navigation: List<UiNavigationItem>,
    val pages: List<UiPage>,
) {
    fun page(id: String): UiPage? = pages.firstOrNull { it.id == id }

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
        private const val MAX_PAGES = 64
        private const val MAX_COMPONENTS_PER_PAGE = 256
        private const val MAX_TEXT_LENGTH = 4096

        fun parse(json: String): UiDocument {
            require(json.length <= 1_000_000) { "UI description is too large" }
            val root = JSONObject(json)
            val version = root.optInt("schemaVersion", -1)
            require(version == CURRENT_SCHEMA_VERSION) { "Unsupported UI schema version: $version" }

            val pagesJson = root.optJSONArray("pages") ?: JSONArray()
            require(pagesJson.length() in 1..MAX_PAGES) { "UI description must contain 1-$MAX_PAGES pages" }
            val pages = buildList {
                for (i in 0 until pagesJson.length()) {
                    val page = pagesJson.getJSONObject(i)
                    val components = page.optJSONArray("components") ?: JSONArray()
                    require(components.length() <= MAX_COMPONENTS_PER_PAGE) { "Too many components on page" }
                    add(UiPage(
                        id = requiredId(page, "id"),
                        title = safeText(page.optString("title")),
                        components = buildList {
                            for (j in 0 until components.length()) {
                                add(UiComponent.parse(components.getJSONObject(j)))
                            }
                        }
                    ))
                }
            }
            require(pages.map { it.id }.distinct().size == pages.size) { "Duplicate page id" }

            val navJson = root.optJSONArray("navigation") ?: JSONArray()
            val navigation = buildList {
                for (i in 0 until navJson.length()) {
                    val item = navJson.getJSONObject(i)
                    val target = requiredId(item, "page")
                    require(pages.any { it.id == target }) { "Navigation points to unknown page: $target" }
                    add(UiNavigationItem(requiredId(item, "id"), safeText(item.optString("label")), target))
                }
            }
            require(navigation.map { it.id }.distinct().size == navigation.size) { "Duplicate navigation id" }
            return UiDocument(version, safeText(root.optString("title", "Horizon")), navigation, pages)
        }

        private fun requiredId(json: JSONObject, key: String): String {
            val value = json.optString(key)
            require(value.matches(Regex("[a-zA-Z0-9_.-]{1,80}"))) { "Invalid $key" }
            return value
        }

        internal fun safeText(value: String): String = value.take(MAX_TEXT_LENGTH)
    }
}

data class UiNavigationItem(val id: String, val label: String, val page: String)
data class UiPage(val id: String, val title: String, val components: List<UiComponent>)

data class UiComponent(
    val id: String,
    val type: String,
    val text: String,
    val value: String?,
    val action: String?,
    val children: List<UiComponent>,
) {
    companion object {
        private val supportedTypes = setOf("text", "heading", "button", "card", "divider", "progress", "list")
        fun parse(json: JSONObject): UiComponent {
            val id = json.optString("id")
            require(id.matches(Regex("[a-zA-Z0-9_.-]{1,80}"))) { "Invalid component id" }
            val type = json.optString("type")
            require(type in supportedTypes) { "Unsupported UI component: $type" }
            val childrenJson = json.optJSONArray("children") ?: JSONArray()
            require(childrenJson.length() <= 64) { "Too many nested components" }
            return UiComponent(
                id = id,
                type = type,
                text = UiDocument.safeText(json.optString("text")),
                value = json.optString("value").takeIf { it.isNotEmpty() }?.let(UiDocument::safeText),
                action = json.optString("action").takeIf { it.matches(Regex("[a-zA-Z0-9_.-]{1,100}")) },
                children = buildList {
                    for (i in 0 until childrenJson.length()) add(parse(childrenJson.getJSONObject(i)))
                }
            )
        }
    }
}
