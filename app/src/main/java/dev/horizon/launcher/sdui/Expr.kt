package dev.horizon.launcher.sdui

import org.json.JSONArray
import org.json.JSONObject
import java.text.DateFormat
import java.util.Date

/**
 * What an expression can see: [root] (the screen's data sources, `t` strings, `vars`), the current
 * list [item] and its [index], and per-event [extra] values (`value` for switches and sliders).
 */
class Scope(
    val root: JSONObject,
    val item: Any? = null,
    val index: Int = -1,
    val extra: Map<String, Any?> = emptyMap(),
) {
    fun child(item: Any?, index: Int) = Scope(root, item, index, extra)
    fun with(key: String, value: Any?) = Scope(root, item, index, extra + (key to value))
}

/**
 * Tiny expression language for the UI files. `{{path}}` reads data: `instances.0.name`,
 * `instances[id=settings.instance].name` (the element whose id equals settings.instance),
 * `item.name`, `t.play`. Filters follow a pipe: `{{played_secs | hours}}`. `!path` negates and
 * `a == 'x'` / `a != b` compare.
 */
object Expr {
    private val TEMPLATE = Regex("\\{\\{\\s*([^}]+?)\\s*\\}\\}")

    /** The template as text; every `{{...}}` is replaced by its value. */
    fun text(template: String, scope: Scope): String =
        TEMPLATE.replace(template) { m -> Regex.escapeReplacement(display(value(m.groupValues[1], scope))) }

    /** A value keeps its type when the whole string is one `{{...}}`; other strings become text. */
    fun any(raw: Any?, scope: Scope): Any? {
        if (raw !is String) return raw
        val whole = TEMPLATE.matchEntire(raw.trim())
        return if (whole != null) value(whole.groupValues[1], scope) else text(raw, scope)
    }

    fun display(v: Any?): String = when {
        v == null || v == JSONObject.NULL -> ""
        v is Double -> if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()
        v is Float -> if (v % 1f == 0f) v.toLong().toString() else v.toString()
        else -> v.toString()
    }

    fun truthy(v: Any?): Boolean = when (v) {
        null, JSONObject.NULL -> false
        is Boolean -> v
        is Number -> v.toDouble() != 0.0
        is String -> v.isNotEmpty() && v != "false"
        is JSONArray -> v.length() > 0
        is JSONObject -> v.length() > 0
        else -> true
    }

    fun value(expression: String, scope: Scope): Any? {
        val pipe = splitTop(expression, '|')
        var v = single(pipe[0], scope)
        for (i in 1 until pipe.size) v = filter(pipe[i].trim(), v)
        return v
    }

    private fun single(raw: String, scope: Scope): Any? {
        val p = raw.trim()
        if (p.startsWith("!")) return !truthy(single(p.substring(1), scope))
        for (op in listOf("==", "!=")) {
            val i = p.indexOf(" $op ")
            if (i > 0) {
                val eq = display(single(p.substring(0, i), scope)) == display(single(p.substring(i + op.length + 2), scope))
                return if (op == "==") eq else !eq
            }
        }
        if (p.length >= 2 && p.startsWith("'") && p.endsWith("'")) return p.substring(1, p.length - 1)
        if (p == "true") return true
        if (p == "false") return false
        p.toLongOrNull()?.let { return it }
        return walk(p, scope)
    }

    private fun filter(name: String, v: Any?): Any? {
        val n = (v as? Number)?.toDouble() ?: display(v).toDoubleOrNull()
        return when (name) {
            "upper" -> display(v).uppercase()
            "lower" -> display(v).lowercase()
            "hours" -> if (n == null) "" else String.format("%.1f", n / 3600.0)
            "minutes" -> if (n == null) "" else (n / 60.0).toLong().toString()
            "date" -> if (n == null || n <= 0.0) "" else DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date((n * 1000).toLong()))
            "count" -> when (v) { is JSONArray -> v.length(); is JSONObject -> v.length(); else -> 0 }
            "initial" -> display(v).trim().take(1).uppercase()
            else -> v
        }
    }

    private fun splitTop(s: String, sep: Char): List<String> {
        val out = ArrayList<String>(); val sb = StringBuilder(); var depth = 0; var quote = false
        for (c in s) {
            when {
                c == '\'' -> { quote = !quote; sb.append(c) }
                !quote && c == '[' -> { depth++; sb.append(c) }
                !quote && c == ']' -> { depth--; sb.append(c) }
                !quote && depth == 0 && c == sep -> { out.add(sb.toString()); sb.setLength(0) }
                else -> sb.append(c)
            }
        }
        out.add(sb.toString())
        return out
    }

    private fun walk(path: String, scope: Scope): Any? {
        val segs = splitTop(path, '.')
        var cur: Any? = null
        for ((n, seg) in segs.withIndex()) {
            val base = seg.substringBefore('[').trim()
            val filters = seg.substring(seg.indexOf('[').let { if (it < 0) seg.length else it })
            if (n == 0) {
                cur = when (base) {
                    "item" -> scope.item
                    "index" -> scope.index
                    else -> if (scope.extra.containsKey(base)) scope.extra[base] else scope.root.opt(base)
                }
            } else if (base.isNotEmpty()) {
                cur = when (cur) {
                    is JSONObject -> cur.opt(base)
                    is JSONArray -> base.toIntOrNull()?.let { cur.opt(it) }
                    else -> null
                }
            }
            var rest = filters
            while (rest.startsWith("[")) {
                val end = rest.indexOf(']')
                if (end < 0) break
                cur = applyFilter(cur, rest.substring(1, end), scope)
                rest = rest.substring(end + 1)
            }
            if (cur == null || cur == JSONObject.NULL) return null
        }
        return cur
    }

    private fun applyFilter(cur: Any?, f: String, scope: Scope): Any? {
        val arr = cur as? JSONArray ?: return null
        f.toIntOrNull()?.let { return arr.opt(it) }
        val key = f.substringBefore('=').trim()
        val want = display(single(f.substringAfter('='), scope))
        for (i in 0 until arr.length()) {
            val el = arr.opt(i)
            if (el is JSONObject && display(el.opt(key)) == want) return el
        }
        return null
    }
}
