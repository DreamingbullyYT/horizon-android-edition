package dev.horizon.launcher.sdui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.ui.graphics.vector.ImageVector
import dev.horizon.launcher.core.Native
import org.json.JSONObject

/**
 * The only place the UI files meet the Horizon core. A UI file names a data source (`"call": "instances"`)
 * or an action (`"name": "launch"`); this table says which core function that is. Adding a core
 * function to the UI is one line here, not a new screen. Every function returns the core's JSON text.
 */
object Registry {
    /** Data a screen can read: name -> the core's JSON (an array or an object). */
    val sources: Map<String, () -> String> = mapOf(
        "instances" to { Native.nativeInstancesJson() },
        "accounts" to { Native.nativeAccountsJson() },
        "settings" to { Native.nativeSettingsJson() },
        "installed" to { Native.nativeInstalledJson() },
        "releases" to { Native.nativeReleasesJson() },
        "worlds" to { Native.nativeWorldsJson() },
        "servers" to { Native.nativeServersJson() },
        "mods" to { Native.nativeModsJson() },
        "platform" to { Native.nativePlatformJson() },
        "telemetry" to { Native.nativeTelemetryJson() },
        "installProgress" to { Native.nativeInstallProgress() },
        "signInInfo" to { Native.nativeSignInInfo() },
        "touchControls" to { Native.nativeTouchControlsJson() },
        "updates" to { Native.nativeUpdateList() },
        "updateProgress" to { Native.nativeUpdateProgress() },
        "browseCatalog" to { Native.nativeBrowseCatalog() },
        "browseProgress" to { Native.nativeBrowseProgress() },
        "browseInstalled" to { Native.nativeBrowseInstalled() },
        "gameReady" to { """{"ready":${Native.nativeGameReady()}}""" },
        "about" to { """{"version":${JSONObject.quote(Native.nativeVersion())},"dataDir":${JSONObject.quote(Native.nativeDataDir())}}""" },
    )

    /** What a button or switch can do: name -> function of the arguments the UI file gave. */
    val calls: Map<String, (JSONObject) -> String> = mapOf(
        "launch" to { a -> Native.nativeLaunch(a.optString("server").ifEmpty { null }) },
        "selectInstance" to { a -> Native.nativeSelectInstance(a.optString("id")) },
        "createInstance" to { a -> Native.nativeCreateInstance(a.optString("name")) },
        "deleteInstance" to { a -> Native.nativeDeleteInstance(a.optString("id")) },
        "installVersion" to { a -> Native.nativeInstallVersion(a.optString("tag")) },
        "removeVersion" to { a -> Native.nativeRemoveVersion(a.optString("tag")) },
        "saveSettings" to { a -> Native.nativeSaveSettings((a.optJSONObject("patch") ?: JSONObject()).toString()) },
        "setNoAi" to { a -> Native.nativeSetNoAi(a.optBoolean("on")) },
        "setTelemetry" to { a -> Native.nativeSetTelemetry(a.optBoolean("on")) },
        "selectAccount" to { a -> Native.nativeSelectAccount(a.optString("uuid")) },
        "removeAccount" to { a -> Native.nativeRemoveAccount(a.optString("uuid")) },
        "addOfflineName" to { a -> Native.nativeAddOfflineName(a.optString("name")) },
        "playOfflineName" to { a -> Native.nativePlayOfflineName(a.optString("name")) },
        "removeOfflineName" to { a -> Native.nativeRemoveOfflineName(a.optString("name")) },
        "playAsGameName" to { _ -> Native.nativePlayAsGameName() },
        "addServer" to { a -> Native.nativeAddServer(a.optString("name"), a.optString("ip")) },
        "removeServer" to { a -> Native.nativeRemoveServer(a.optInt("index")) },
        "removeMod" to { a -> Native.nativeRemoveMod(a.optString("id")) },
        "removeShader" to { a -> Native.nativeRemoveShader(a.optString("name")) },
        "importMod" to { a -> Native.nativeImportMod(a.optString("source")) },
        "importShader" to { a -> Native.nativeImportShader(a.optString("source")) },
        "saveTouchControls" to { a -> Native.nativeSaveTouchControls((a.optJSONObject("patch") ?: JSONObject()).toString()) },
        "browseSearch" to { a ->
            Native.nativeBrowseSearch(a.optString("source"), a.optString("kind"), a.optString("text"), a.optInt("offset"), a.optBoolean("everything"))
        },
        "browseDetails" to { a -> Native.nativeBrowseDetails(a.optString("source"), a.optString("id")) },
        "browseInstall" to { a -> Native.nativeBrowseInstall(a.optString("source"), a.optString("id")) },
        "browseRemove" to { a -> Native.nativeBrowseRemove(a.optString("name")) },
        "updateDownload" to { a -> Native.nativeUpdateDownload(a.optString("tag"), a.optString("what"), a.optString("dest")) },
        "installGame" to { a -> Native.nativeInstallGame(a.optString("cache")) },
        "setImportCache" to { a -> Native.nativeSetImportCache(a.optString("cache")) },
        "importCachedFile" to { a -> Native.nativeImportCachedFile(a.optString("name"), a.optString("source")) },
    )

    /** Icon names a UI file can use. */
    val icons: Map<String, ImageVector> = mapOf(
        "add" to Icons.Outlined.Add, "back" to Icons.Outlined.ArrowBack, "bolt" to Icons.Outlined.Bolt,
        "check" to Icons.Outlined.Check, "close" to Icons.Outlined.Close, "cloud" to Icons.Outlined.Cloud,
        "cloud-download" to Icons.Outlined.CloudDownload, "delete" to Icons.Outlined.Delete,
        "servers" to Icons.Outlined.Dns, "download" to Icons.Outlined.Download, "edit" to Icons.Outlined.Edit,
        "mods" to Icons.Outlined.Extension, "folder" to Icons.Outlined.Folder,
        "folder-open" to Icons.Outlined.FolderOpen, "list" to Icons.Outlined.FormatListBulleted,
        "grid" to Icons.Outlined.GridView, "home" to Icons.Outlined.Home, "info" to Icons.Outlined.Info,
        "versions" to Icons.Outlined.Inventory2, "language" to Icons.Outlined.Language,
        "link" to Icons.Outlined.Link, "lock" to Icons.Outlined.Lock, "more" to Icons.Outlined.MoreVert,
        "palette" to Icons.Outlined.Palette, "person" to Icons.Outlined.Person, "play" to Icons.Outlined.PlayArrow,
        "globe" to Icons.Outlined.Public, "refresh" to Icons.Outlined.Refresh, "search" to Icons.Outlined.Search,
        "settings" to Icons.Outlined.Settings, "game" to Icons.Outlined.SportsEsports, "star" to Icons.Outlined.Star,
        "tune" to Icons.Outlined.Tune,
    )
}
