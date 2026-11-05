package dev.horizon.launcher.sdui

import androidx.compose.ui.graphics.Color
import org.json.JSONObject

/** One text style from theme.json. */
data class TextSpec(val size: Float, val weight: Int, val spacing: Float, val font: String?, val caps: Boolean)

/**
 * The look of every UI file, read from `ui/theme.json`: colours, text styles and button variants.
 * A colour is a token (`accent`), a hex (`#7656FA`), or either with an alpha percentage (`ink@60`).
 */
class SduiTheme(private val json: JSONObject) {
    private val colors = json.optJSONObject("colors") ?: JSONObject()
    private val texts = json.optJSONObject("text") ?: JSONObject()
    private val variants = json.optJSONObject("variants") ?: JSONObject()

    fun color(spec: String?, fallback: Color = Color.Unspecified): Color {
        if (spec.isNullOrBlank()) return fallback
        val alpha = spec.substringAfter('@', "").toFloatOrNull()?.div(100f)
        val name = spec.substringBefore('@').trim()
        val raw = if (name.startsWith("#")) name else colors.optString(name, "")
        val parsed = parse(raw) ?: return fallback
        return if (alpha != null) parsed.copy(alpha = parsed.alpha * alpha) else parsed
    }

    private fun parse(hex: String): Color? = try {
        if (hex.isBlank()) null else Color(android.graphics.Color.parseColor(hex))
    } catch (_: IllegalArgumentException) {
        null
    }

    fun text(name: String): TextSpec {
        val o = texts.optJSONObject(name) ?: texts.optJSONObject("body") ?: JSONObject()
        return TextSpec(
            size = o.optDouble("size", 14.0).toFloat(),
            weight = o.optInt("weight", 400),
            spacing = o.optDouble("spacing", 0.0).toFloat(),
            font = o.optString("font").ifEmpty { null },
            caps = o.optBoolean("caps"),
        )
    }

    /** Defaults for a button variant (`primary`, `ghost`, `pill`, `icon`); a node's own props win. */
    fun variant(name: String): JSONObject = variants.optJSONObject(name) ?: JSONObject()

    companion object {
        val EMPTY = SduiTheme(JSONObject())
    }
}
