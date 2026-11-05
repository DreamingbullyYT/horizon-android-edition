package dev.horizon.launcher.sdui

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** What the renderer needs from its host: the look, fonts, and a way to run an action. */
interface SduiRuntime {
    val theme: SduiTheme
    val context: Context
    fun font(name: String?): FontFamily?
    fun run(action: Any?, scope: Scope)
}

/** The colour text and icons take inside a button. */
val LocalFg = compositionLocalOf { Color.White }

/** Draws one node of a UI file. Node types: column, row, box, scroll, text, icon, button, image, gradient, list, spacer, switch. */
@Composable
fun SduiNode(node: JSONObject, scope: Scope, rt: SduiRuntime) {
    if (!shown(node, scope)) return
    when (node.optString("type")) {
        "column", "scroll" -> ColumnNode(node, scope, rt)
        "row" -> RowNode(node, scope, rt)
        "box" -> BoxNode(node, scope, rt)
        "text" -> TextNode(node, scope, rt)
        "icon" -> IconNode(node, scope, rt)
        "button" -> ButtonNode(node, scope, rt)
        "image" -> ImageNode(node, scope, rt)
        "gradient" -> GradientNode(node, scope, rt)
        "list" -> ListNode(node, scope, rt)
        "switch" -> SwitchNode(node, scope, rt)
        "spacer" -> Spacer(nodeModifier(node, scope, rt))
        else -> Unit
    }
}

private fun shown(n: JSONObject, scope: Scope) = !n.has("visible") || Expr.truthy(Expr.any(n.opt("visible"), scope))

// ---- sizes, padding and the common modifier ------------------------------------------------------

private fun JSONObject.dp(key: String): Dp? =
    if (has(key) && !isNull(key)) (opt(key) as? Number)?.toFloat()?.dp else null

private fun paddingOf(v: Any?): PaddingValues? = when (v) {
    is Number -> PaddingValues(v.toFloat().dp)
    is JSONArray -> when (v.length()) {
        2 -> PaddingValues(horizontal = v.optDouble(0).toFloat().dp, vertical = v.optDouble(1).toFloat().dp)
        4 -> PaddingValues(v.optDouble(0).toFloat().dp, v.optDouble(1).toFloat().dp, v.optDouble(2).toFloat().dp, v.optDouble(3).toFloat().dp)
        else -> null
    }
    else -> null
}

/** size, fill, alpha, radius, bg, border, onClick and padding: the props every node shares. */
private fun nodeModifier(n: JSONObject, scope: Scope, rt: SduiRuntime): Modifier {
    var m: Modifier = Modifier
    if (n.optBoolean("fill")) m = m.fillMaxSize()
    if (n.optBoolean("fillWidth")) m = m.fillMaxWidth()
    if (n.optBoolean("fillHeight")) m = m.fillMaxHeight()
    n.dp("width")?.let { m = m.width(it) }
    n.dp("height")?.let { m = m.height(it) }
    n.dp("size")?.let { m = m.size(it) }
    if (n.has("alpha")) m = m.alpha(n.optDouble("alpha", 1.0).toFloat())
    val radius = n.dp("radius")
    val shape = if (radius != null) RoundedCornerShape(radius) else RectangleShape
    if (radius != null) m = m.clip(shape)
    n.optString("bg").takeIf { it.isNotEmpty() }?.let { m = m.background(rt.theme.color(it), shape) }
    n.optJSONObject("border")?.let { b ->
        m = m.border(b.dp("width") ?: 1.dp, rt.theme.color(b.optString("color"), Color.White.copy(alpha = 0.1f)), shape)
    }
    if (n.has("onClick")) m = m.clickable { rt.run(n.opt("onClick"), scope) }
    paddingOf(n.opt("padding"))?.let { m = m.padding(it) }
    return m
}

private fun gapOf(n: JSONObject): Dp = n.dp("gap") ?: 0.dp

private fun vArrangement(name: String, gap: Dp): Arrangement.Vertical = when (name) {
    "center" -> Arrangement.spacedBy(gap, Alignment.CenterVertically)
    "end" -> Arrangement.spacedBy(gap, Alignment.Bottom)
    "between" -> Arrangement.SpaceBetween
    "around" -> Arrangement.SpaceAround
    "evenly" -> Arrangement.SpaceEvenly
    else -> Arrangement.spacedBy(gap)
}

private fun hArrangement(name: String, gap: Dp): Arrangement.Horizontal = when (name) {
    "center" -> Arrangement.spacedBy(gap, Alignment.CenterHorizontally)
    "end" -> Arrangement.spacedBy(gap, Alignment.End)
    "between" -> Arrangement.SpaceBetween
    "around" -> Arrangement.SpaceAround
    "evenly" -> Arrangement.SpaceEvenly
    else -> Arrangement.spacedBy(gap)
}

private fun hAlign(name: String) = when (name) {
    "center" -> Alignment.CenterHorizontally
    "end" -> Alignment.End
    else -> Alignment.Start
}

private fun vAlign(name: String) = when (name) {
    "top" -> Alignment.Top
    "bottom" -> Alignment.Bottom
    else -> Alignment.CenterVertically
}

private fun boxAlign(name: String): Alignment = when (name) {
    "topStart" -> Alignment.TopStart
    "topCenter" -> Alignment.TopCenter
    "topEnd" -> Alignment.TopEnd
    "centerStart" -> Alignment.CenterStart
    "centerEnd" -> Alignment.CenterEnd
    "bottomStart" -> Alignment.BottomStart
    "bottomCenter" -> Alignment.BottomCenter
    "bottomEnd" -> Alignment.BottomEnd
    "center" -> Alignment.Center
    else -> Alignment.TopStart
}

// ---- containers ----------------------------------------------------------------------------------

@Composable
private fun ColumnNode(n: JSONObject, scope: Scope, rt: SduiRuntime) {
    var mod = nodeModifier(n, scope, rt)
    if (n.optString("type") == "scroll" || n.optBoolean("scroll")) mod = mod.verticalScroll(rememberScrollState())
    Column(mod, vArrangement(n.optString("arrange"), gapOf(n)), hAlign(n.optString("align"))) {
        val list = n.optJSONArray("children")
        for (i in 0 until (list?.length() ?: 0)) {
            val c = list?.optJSONObject(i) ?: continue
            if (!shown(c, scope)) continue
            val w = c.optDouble("weight", 0.0)
            if (w > 0) Box(Modifier.weight(w.toFloat())) { SduiNode(c, scope, rt) } else SduiNode(c, scope, rt)
        }
    }
}

@Composable
private fun RowNode(n: JSONObject, scope: Scope, rt: SduiRuntime) {
    var mod = nodeModifier(n, scope, rt)
    if (n.optBoolean("scroll")) mod = mod.horizontalScroll(rememberScrollState())
    Row(mod, hArrangement(n.optString("arrange"), gapOf(n)), vAlign(n.optString("align"))) {
        val list = n.optJSONArray("children")
        for (i in 0 until (list?.length() ?: 0)) {
            val c = list?.optJSONObject(i) ?: continue
            if (!shown(c, scope)) continue
            val w = c.optDouble("weight", 0.0)
            if (w > 0) Box(Modifier.weight(w.toFloat())) { SduiNode(c, scope, rt) } else SduiNode(c, scope, rt)
        }
    }
}

@Composable
private fun BoxNode(n: JSONObject, scope: Scope, rt: SduiRuntime) {
    Box(nodeModifier(n, scope, rt), boxAlign(n.optString("align"))) { BoxChildren(n.optJSONArray("children"), scope, rt) }
}

/** Layers; a child's `"self": "bottomEnd"` places it inside the box. */
@Composable
private fun BoxScope.BoxChildren(list: JSONArray?, scope: Scope, rt: SduiRuntime) {
    for (i in 0 until (list?.length() ?: 0)) {
        val c = list?.optJSONObject(i) ?: continue
        if (!shown(c, scope)) continue
        val self = c.optString("self")
        if (self.isNotEmpty()) Box(Modifier.align(boxAlign(self))) { SduiNode(c, scope, rt) } else SduiNode(c, scope, rt)
    }
}

@Composable
private fun GradientNode(n: JSONObject, scope: Scope, rt: SduiRuntime) {
    val tokens = n.optJSONArray("colors")
    val colors = List(tokens?.length() ?: 0) { rt.theme.color(tokens?.optString(it), Color.Black) }
    val brush = when {
        colors.size < 2 -> Brush.verticalGradient(listOf(Color.Black, Color.Black))
        n.optString("direction") == "horizontal" -> Brush.horizontalGradient(colors)
        else -> Brush.verticalGradient(colors)
    }
    Box(nodeModifier(n, scope, rt).background(brush), boxAlign(n.optString("align"))) {
        BoxChildren(n.optJSONArray("children"), scope, rt)
    }
}

@Composable
private fun ListNode(n: JSONObject, scope: Scope, rt: SduiRuntime) {
    val items = Expr.any(n.opt("items"), scope) as? JSONArray
    val template = n.optJSONObject("template")
    if (items == null || items.length() == 0 || template == null) {
        n.optJSONObject("empty")?.let { SduiNode(it, scope, rt) }
        return
    }
    val gap = gapOf(n)
    var mod = nodeModifier(n, scope, rt)
    when (n.optString("direction", "column")) {
        "row" -> {
            if (n.optBoolean("scroll")) mod = mod.horizontalScroll(rememberScrollState())
            Row(mod, Arrangement.spacedBy(gap), vAlign(n.optString("align"))) {
                for (i in 0 until items.length()) SduiNode(template, scope.child(items.opt(i), i), rt)
            }
        }
        "grid" -> {
            val cols = n.optInt("columns", 2).coerceAtLeast(1)
            if (n.optBoolean("scroll")) mod = mod.verticalScroll(rememberScrollState())
            Column(mod, Arrangement.spacedBy(gap)) {
                for (start in 0 until items.length() step cols) {
                    Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(gap)) {
                        for (c in 0 until cols) {
                            val i = start + c
                            Box(Modifier.weight(1f)) { if (i < items.length()) SduiNode(template, scope.child(items.opt(i), i), rt) }
                        }
                    }
                }
            }
        }
        else -> {
            if (n.optBoolean("scroll")) mod = mod.verticalScroll(rememberScrollState())
            Column(mod, Arrangement.spacedBy(gap), hAlign(n.optString("align"))) {
                for (i in 0 until items.length()) SduiNode(template, scope.child(items.opt(i), i), rt)
            }
        }
    }
}

// ---- leaves --------------------------------------------------------------------------------------

@Composable
private fun TextView(text: String, style: String, colorSpec: String?, n: JSONObject, modifier: Modifier, rt: SduiRuntime) {
    val spec = rt.theme.text(style)
    val color = rt.theme.color(colorSpec, LocalFg.current)
    Text(
        text = if (n.optBoolean("caps") || spec.caps) text.uppercase() else text,
        modifier = modifier,
        color = color,
        fontSize = spec.size.sp,
        fontWeight = FontWeight(spec.weight),
        letterSpacing = spec.spacing.sp,
        fontFamily = rt.font(spec.font),
        maxLines = n.optInt("maxLines", Int.MAX_VALUE),
        overflow = TextOverflow.Ellipsis,
        textAlign = when (n.optString("textAlign")) { "center" -> TextAlign.Center; "end" -> TextAlign.End; else -> TextAlign.Start },
    )
}

@Composable
private fun TextNode(n: JSONObject, scope: Scope, rt: SduiRuntime) {
    TextView(Expr.text(n.optString("text"), scope), n.optString("style", "body"), n.optString("color").ifEmpty { null }, n, nodeModifier(n, scope, rt), rt)
}

@Composable
private fun IconView(name: String, size: Dp, colorSpec: String?, modifier: Modifier, rt: SduiRuntime) {
    val icon = Registry.icons[name] ?: return
    Icon(icon, contentDescription = null, modifier = modifier.size(size), tint = rt.theme.color(colorSpec, LocalFg.current))
}

@Composable
private fun IconNode(n: JSONObject, scope: Scope, rt: SduiRuntime) {
    val size = n.dp("iconSize") ?: n.dp("size") ?: 20.dp
    var mod = nodeModifier(n, scope, rt)
    if (n.has("size")) mod = Modifier.then(nodeModifier(JSONObject(n.toString()).apply { remove("size") }, scope, rt))
    IconView(Expr.text(n.optString("name"), scope), size, n.optString("color").ifEmpty { null }, mod, rt)
}

@Composable
private fun ButtonNode(n: JSONObject, scope: Scope, rt: SduiRuntime) {
    val merged = JSONObject(rt.theme.variant(n.optString("variant", "primary")).toString())
    for (key in n.keys()) merged.put(key, n.get(key))
    val enabled = !merged.has("enabled") || Expr.truthy(Expr.any(merged.opt("enabled"), scope))
    if (!enabled) merged.remove("onClick")
    var mod = nodeModifier(merged, scope, rt)
    if (!enabled) mod = mod.alpha(0.4f)
    val fg = rt.theme.color(merged.optString("fg"), Color.White)
    Row(
        mod.heightIn(min = 44.dp),
        Arrangement.spacedBy(merged.dp("gap") ?: 8.dp, Alignment.CenterHorizontally),
        Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalFg provides fg) {
            merged.optString("icon").takeIf { it.isNotEmpty() }?.let {
                IconView(Expr.text(it, scope), merged.dp("iconSize") ?: 18.dp, null, Modifier, rt)
            }
            merged.optString("text").takeIf { it.isNotEmpty() }?.let {
                TextView(Expr.text(it, scope), merged.optString("textStyle", "label"), null, merged, Modifier, rt)
            }
            val list = merged.optJSONArray("children")
            for (i in 0 until (list?.length() ?: 0)) list?.optJSONObject(i)?.let { SduiNode(it, scope, rt) }
        }
    }
}

@Composable
private fun SwitchNode(n: JSONObject, scope: Scope, rt: SduiRuntime) {
    Switch(
        checked = Expr.truthy(Expr.any(n.opt("value"), scope)),
        onCheckedChange = { rt.run(n.opt("onChange"), scope.with("value", it)) },
        modifier = nodeModifier(n, scope, rt),
        colors = SwitchDefaults.colors(checkedTrackColor = rt.theme.color("accent", Color(0xFF7656FA))),
    )
}

@Composable
private fun ImageNode(n: JSONObject, scope: Scope, rt: SduiRuntime) {
    val src = Expr.text(n.optString("src"), scope)
    val bitmap by produceState<ImageBitmap?>(null, src) { value = withContext(Dispatchers.IO) { loadImage(rt.context, src) } }
    Box(nodeModifier(n, scope, rt)) {
        bitmap?.let {
            var img: Modifier = Modifier.fillMaxSize()
            n.dp("blur")?.let { b -> img = img.blur(b) }
            Image(it, null, img, contentScale = if (n.optString("fit") == "fit") ContentScale.Fit else ContentScale.Crop)
        }
        val dim = n.optDouble("dim", 0.0).toFloat()
        if (dim > 0f) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = dim)))
    }
}

/** `asset:ui/bg.jpg` reads from the APK, `file:/path` from storage. */
private fun loadImage(context: Context, src: String): ImageBitmap? = runCatching {
    when {
        src.startsWith("asset:") -> context.assets.open(src.removePrefix("asset:")).use { BitmapFactory.decodeStream(it) }
        src.startsWith("file:") -> BitmapFactory.decodeFile(src.removePrefix("file:"))
        else -> null
    }?.asImageBitmap()
}.getOrNull()
