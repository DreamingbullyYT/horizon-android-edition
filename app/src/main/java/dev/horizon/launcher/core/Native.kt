package dev.horizon.launcher.core

/**
 * The JNI surface of the Horizon launcher core (Rust). Every method is blocking; the app calls
 * them from Dispatchers.IO. Strings are JSON (or plain text) so the bridge stays small.
 */
object Native {
    var loadError: String? = null
        private set

    init {
        try {
            System.loadLibrary("horizon_core")
        } catch (t: Throwable) {
            loadError = t.message ?: t.javaClass.simpleName
        }
    }

    external fun nativeInit(home: String): String
    external fun nativeGameReady(): Boolean
    external fun nativeInstallProgress(): String
    external fun nativeSetImportCache(cache: String): String
    external fun nativeImportCachedFile(name: String, source: String): String
    external fun nativeInstallGame(cache: String): String
    external fun nativeVersion(): String
    external fun nativeDataDir(): String
    external fun nativeReleasesJson(): String
    external fun nativeInstancesJson(): String
    external fun nativePlatformJson(): String

    external fun nativeAccountsJson(): String
    external fun nativeAddOfflineName(name: String): String
    external fun nativePlayOfflineName(name: String): String
    external fun nativeRemoveOfflineName(name: String): String
    external fun nativePlayAsGameName(): String
    external fun nativeSelectAccount(uuid: String): String
    external fun nativeRemoveAccount(uuid: String): String
    external fun nativeSignInInfo(): String

    external fun nativeSettingsJson(): String
    external fun nativeSaveSettings(patch: String): String
    external fun nativeSetNoAi(on: Boolean): String
    external fun nativeTouchControlsJson(): String
    external fun nativeSaveTouchControls(patch: String): String

    external fun nativeCreateInstance(name: String): String
    external fun nativeDeleteInstance(id: String): String
    external fun nativeSelectInstance(id: String): String

    external fun nativeInstalledJson(): String
    external fun nativeInstallVersion(tag: String): String
    external fun nativeRemoveVersion(tag: String): String

    external fun nativeWorldsJson(): String
    external fun nativeServersJson(): String
    external fun nativeAddServer(name: String, ip: String): String
    external fun nativeRemoveServer(index: Int): String
    external fun nativeModsJson(): String
    external fun nativeImportMod(source: String): String
    external fun nativeRemoveMod(id: String): String
    external fun nativeImportShader(source: String): String
    external fun nativeRemoveShader(name: String): String

    external fun nativeLaunch(server: String?): String

    external fun nativeTelemetryJson(): String
    external fun nativeSetTelemetry(on: Boolean): String
}
