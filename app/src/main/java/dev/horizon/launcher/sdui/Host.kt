package dev.horizon.launcher.sdui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * Runs the UI files: loads a screen (`ui/screens/<name>.json`), reads the data sources it lists
 * through [Registry], and performs its actions. Nothing here knows what a screen looks like.
 *
 * Action (a button's `onClick`): an object or an array of objects, run in order.
 *   {"do":"nav","to":"settings"} {"do":"back"} {"do":"set","var":"tab","value":"mods"}
 *   {"do":"url","url":"https://..."} {"do":"toast","text":"..."} {"do":"reload"} {"do":"exit"}
 *   {"do":"call","name":"launch","args":{...},"ok":"{{t.started}}","refresh":true}
 */
class HostState(
    override val context: Context,
    private val io: CoroutineScope,
    private val start: String,
    private val exit: () -> Unit,
) : SduiRuntime {
    override var theme: SduiTheme by mutableStateOf(SduiTheme.EMPTY)
        private set
    var root: JSONObject? by mutableStateOf(null)
        private set
    var screenName: String by mutableStateOf(start)
        private set
    var failure: String? by mutableStateOf(null)
        private set

    private val stack = ArrayDeque<String>()
    private var vars = JSONObject()
    private var strings = JSONObject()
    private var layout: JSONObject? = null
    private val errors = mutableListOf<String>()

    override fun font(name: String?): FontFamily? {
        if (name.isNullOrBlank()) return null
        val id = context.resources.getIdentifier(name, "font", context.packageName)
        return if (id == 0) null else runCatching { FontFamily(androidx.compose.ui.text.font.Font(id)) }.getOrNull()
    }

    fun open() {
        theme = SduiTheme(readJson("theme.json") ?: JSONObject())
        strings = loadStrings()
        load(start)
    }

    private fun readJson(path: String): JSONObject? =
        UiStore.read(context, path)?.let { runCatching { JSONObject(it) }.getOrNull() }

    private fun loadStrings(): JSONObject {
        val merged = readJson("i18n/en.json") ?: JSONObject()
        val lang = Locale.getDefault().language
        if (lang != "en") readJson("i18n/$lang.json")?.let { o -> for (k in o.keys()) merged.put(k, o.get(k)) }
        return merged
    }

    private fun load(name: String) {
        io.launch {
            val screen = withContext(Dispatchers.IO) { readJson("screens/$name.json") }
            if (screen == null) {
                failure = "Missing UI screen: $name"
                return@launch
            }
            layout = screen
            screenName = name
            failure = null
            refresh()
        }
    }

    /** Reads every data source the screen lists again and redraws it. */
    fun refresh() {
        val screen = layout ?: return
        io.launch {
            val data = withContext(Dispatchers.IO) {
                val out = JSONObject()
                errors.clear()
                val wanted = screen.optJSONArray("sources")
                for (i in 0 until (wanted?.length() ?: 0)) {
                    val key = wanted!!.optString(i)
                    val text = Registry.sources[key]?.let { runCatching { it() }.getOrElse { e -> """{"error":"${e.message}"}""" } } ?: continue
                    val value = parse(text)
                    if (value is JSONObject && value.has("error")) {
                        errors.add("$key: ${value.optString("error")}")
                        out.put(key, if (key == "instances") JSONArray() else JSONObject())
                    } else out.put(key, value)
                }
                out
            }
            // Handy shortcuts so a screen never depends on one lookup working.
            val list = data.optJSONArray("instances")
            val chosen = data.optJSONObject("settings")?.optString("instance").orEmpty()
            var current: JSONObject? = null
            for (i in 0 until (list?.length() ?: 0)) {
                val o = list!!.optJSONObject(i) ?: continue
                if (current == null || o.optString("id") == chosen) current = o
            }
            data.put("current", current ?: JSONObject().put("name", strings.optString("no_instances")).put("version", ""))
            val acc = data.optJSONObject("accounts")
            val who = acc?.optJSONObject("player")?.optString("name").orEmpty().ifEmpty {
                acc?.optJSONArray("accounts")?.optJSONObject(0)?.optString("name").orEmpty()
            }.ifEmpty { acc?.optString("offlineSelected").orEmpty() }
            data.put("me", JSONObject().put("name", who.ifEmpty { strings.optString("account") }))
            data.put("t", strings)
            data.put("vars", vars)
            data.put("errors", errors.joinToString("\n"))
            root = JSONObject().apply {
                put("data", data)
                put("node", screen.optJSONObject("root") ?: JSONObject())
            }
        }
    }

    private fun parse(text: String): Any = runCatching {
        if (text.trimStart().startsWith("[")) JSONArray(text) else JSONObject(text)
    }.getOrDefault(JSONObject())

    fun back(): Boolean {
        val previous = stack.removeLastOrNull() ?: return false
        load(previous)
        return true
    }

    override fun run(action: Any?, scope: Scope) {
        when (action) {
            is JSONArray -> for (i in 0 until action.length()) run(action.opt(i), scope)
            is JSONObject -> step(action, scope)
            else -> Unit
        }
    }

    private fun step(a: JSONObject, scope: Scope) {
        when (a.optString("do")) {
            "nav" -> {
                stack.addLast(screenName)
                load(Expr.text(a.optString("to"), scope))
            }
            "back" -> if (!back()) exit()
            "exit" -> exit()
            "reload" -> refresh()
            "set" -> {
                vars.put(a.optString("var"), Expr.any(a.opt("value"), scope))
                refresh()
            }
            "url" -> runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Expr.text(a.optString("url"), scope))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            "toast" -> toast(Expr.text(a.optString("text"), scope))
            "call" -> call(a, scope)
        }
    }

    private fun call(a: JSONObject, scope: Scope) {
        val fn = Registry.calls[a.optString("name")]
        if (fn == null) {
            toast("Not implemented: ${a.optString("name")}")
            return
        }
        val args = JSONObject()
        a.optJSONObject("args")?.let { o -> for (k in o.keys()) args.put(k, Expr.any(o.get(k), scope)) }
        io.launch {
            val reply = withContext(Dispatchers.IO) { runCatching { fn(args) }.getOrElse { """{"error":"${it.message}"}""" } }
            val error = runCatching { JSONObject(reply).optString("error") }.getOrDefault("")
            if (error.isNotEmpty()) toast(error)
            else a.optString("ok").takeIf { it.isNotEmpty() }?.let { toast(Expr.text(it, scope)) }
            if (a.optBoolean("refresh", true)) refresh()
        }
    }

    private fun toast(text: String) {
        if (text.isNotBlank()) Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    }
}

/** Draws the current UI screen. `start` is the name of the first screen in `ui/screens/`. */
@Composable
fun SduiHost(start: String = "home", onExit: () -> Unit) {
    val context = LocalContext.current
    val io = rememberCoroutineScope()
    val host = remember { HostState(context, io, start, onExit) }
    LaunchedEffect(Unit) { host.open() }
    BackHandler { if (!host.back()) onExit() }
    val page = host.theme.color("page", Color(0xFF0B0A12))
    Box(Modifier.fillMaxSize().background(page)) {
        val tree = host.root
        val failed = host.failure
        when {
            failed != null -> Text(failed, color = Color.White, modifier = Modifier.align(Alignment.Center))
            tree != null -> SduiNode(tree.getJSONObject("node"), Scope(tree.getJSONObject("data")), host)
        }
    }
}
