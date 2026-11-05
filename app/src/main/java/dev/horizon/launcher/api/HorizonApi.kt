package dev.horizon.launcher.api

import dev.horizon.launcher.sdui.Registry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * The one door a UI uses to reach Horizon, whatever the UI is made of (JSON screens, Compose, anything).
 * It only forwards to the Rust core (`Native`) that the launcher already has, so the core is not touched:
 * a new core function reaches every UI by adding one line to [Registry].
 *
 * Reading:  `HorizonApi.read("instances")`  -> parsed JSON (JSONArray or JSONObject)
 * Acting:   `HorizonApi.call("launch")`      -> [Result]
 * Typed Kotlin screens can keep using `data.Core`, `data.Browse` and `data.Updater` directly; they sit on
 * the same `Native` functions.
 */
object HorizonApi {
    /** Bump when a source or call is removed or changes meaning. Adding is not a change. */
    const val VERSION = 1

    /** The outcome of a call: [json] is whatever the core answered, [error] is set when it said `{"error": ...}`. */
    data class Result(val json: Any?, val error: String?) {
        val ok get() = error == null
    }

    val sources: Set<String> get() = Registry.sources.keys
    val calls: Set<String> get() = Registry.calls.keys

    suspend fun read(source: String): Any? = withContext(Dispatchers.IO) {
        val fn = Registry.sources[source] ?: return@withContext JSONObject().put("error", "unknown source: $source")
        parse(runCatching { fn() }.getOrElse { """{"error":${JSONObject.quote(it.message ?: "failed")}}""" })
    }

    suspend fun call(name: String, args: JSONObject = JSONObject()): Result = withContext(Dispatchers.IO) {
        val fn = Registry.calls[name] ?: return@withContext Result(null, "not implemented: $name")
        val raw = runCatching { fn(args) }.getOrElse { """{"error":${JSONObject.quote(it.message ?: "failed")}}""" }
        val value = parse(raw)
        val error = (value as? JSONObject)?.optString("error")?.takeIf { it.isNotEmpty() }
        Result(value, error)
    }

    /** What exists, as JSON: lets a UI (or its author) list every source and call. */
    fun describe(): JSONObject = JSONObject()
        .put("apiVersion", VERSION)
        .put("sources", JSONArray(Registry.sources.keys.sorted()))
        .put("calls", JSONArray(Registry.calls.keys.sorted()))
        .put("icons", JSONArray(Registry.icons.keys.sorted()))

    private fun parse(text: String): Any = runCatching {
        val t = text.trimStart()
        if (t.startsWith("[")) JSONArray(t) else JSONObject(t)
    }.getOrElse { JSONObject().put("value", text) }
}
