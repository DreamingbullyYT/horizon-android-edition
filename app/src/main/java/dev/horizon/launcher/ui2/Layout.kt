package dev.horizon.launcher.ui2

import android.content.Context
import androidx.compose.ui.graphics.Color
import dev.horizon.launcher.sdui.UiStore
import org.json.JSONArray
import org.json.JSONObject

/** One entry of the navigation rail. [id] picks a screen from [ScreenRegistry]. */
data class NavItem(val id: String, val icon: String, val label: String)

/**
 * What the app looks like at the top level, read from `ui/app.json` (a copy in filesDir/ui wins over
 * the one in the APK): accent, background, which screens are in the rail and in what order. Screens
 * themselves are Kotlin; this file only chooses and arranges them.
 */
class Layout(json: JSONObject) {
    val accent: Color = color(json.optString("accent"), Color(0xFF7656FA))
    val background: Color = color(json.optString("background"), Color(0xFF0B0A12))
    val start: String = json.optString("start", "home")
    val nav: List<NavItem> = items(json.optJSONArray("nav"))
    val footer: List<NavItem> = items(json.optJSONArray("footer"))

    companion object {
        fun load(context: Context): Layout {
            val json = UiStore.read(context, "app.json")?.let { runCatching { JSONObject(it) }.getOrNull() }
            return Layout(json ?: JSONObject())
        }

        private fun items(a: JSONArray?): List<NavItem> = buildList {
            for (i in 0 until (a?.length() ?: 0)) {
                val o = a!!.optJSONObject(i) ?: continue
                add(NavItem(o.optString("id"), o.optString("icon", "home"), o.optString("label", o.optString("id"))))
            }
        }

        private fun color(hex: String, fallback: Color): Color =
            runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(fallback)
    }
}
