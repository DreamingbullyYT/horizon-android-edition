package dev.horizon.launcher.ui2

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.horizon.launcher.api.HorizonApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * Everything a screen needs: data read through [HorizonApi] (sources are loaded on demand and cached
 * until [refresh]), and [act] to run a core call with a busy flag and a message.
 */
class AppState(private val scope: CoroutineScope, val layout: Layout, val say: (String) -> Unit) {
    var screen by mutableStateOf(layout.start)
    var busy by mutableStateOf<String?>(null)
        private set
    private var version by mutableIntStateOf(0)
    private val cache = HashMap<String, Any?>()

    /** Reads a source (cached). Returns null while the first read is running. */
    fun source(name: String): Any? {
        version // subscribes the caller to refresh()
        if (!cache.containsKey(name)) {
            cache[name] = null
            scope.launch {
                cache[name] = HorizonApi.read(name)
                version++
            }
        }
        return cache[name]
    }

    fun array(name: String): JSONArray = source(name) as? JSONArray ?: JSONArray()
    fun obj(name: String): JSONObject = source(name) as? JSONObject ?: JSONObject()

    fun refresh(vararg names: String) {
        if (names.isEmpty()) cache.clear() else names.forEach { cache.remove(it) }
        version++
    }

    /** Runs a core call; shows [ok] (or the core's error) and reloads [reload] (everything when empty). */
    fun act(label: String, call: String, args: JSONObject = JSONObject(), ok: String? = null, vararg reload: String, after: () -> Unit = {}) {
        if (busy != null) return
        busy = label
        scope.launch {
            val r = HorizonApi.call(call, args)
            busy = null
            if (r.error != null) say(r.error) else {
                ok?.let(say)
                after()
            }
            refresh(*reload)
        }
    }

    /** The instance the player has selected, or the first one. */
    fun currentInstance(): JSONObject? {
        val list = array("instances")
        val chosen = obj("settings").optString("instance")
        var first: JSONObject? = null
        for (i in 0 until list.length()) {
            val o = list.optJSONObject(i) ?: continue
            if (first == null) first = o
            if (o.optString("id") == chosen) return o
        }
        return first
    }

    fun playerName(): String {
        val a = obj("accounts")
        return a.optJSONObject("player")?.optString("name").orEmpty().ifEmpty {
            a.optJSONArray("accounts")?.optJSONObject(0)?.optString("name").orEmpty()
        }.ifEmpty { a.optString("offlineSelected") }
    }
}

fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }
