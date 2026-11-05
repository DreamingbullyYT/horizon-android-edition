# UI API

A UI reaches Horizon only through `dev.horizon.launcher.api.HorizonApi`. It forwards to the Rust core the
launcher already ships (`Native`), so launcher core and engine stay unmodified.

```kotlin
val instances = HorizonApi.read("instances")          // JSONArray
val r = HorizonApi.call("launch")                     // Result(json, error)
HorizonApi.call("selectInstance", JSONObject().put("id", "abc"))
HorizonApi.describe()                                 // every source / call / icon name
```

JSON screens (`assets/ui/`, override in `filesDir/ui/`) use the same names: `"sources": ["instances"]` and
`{"do":"call","name":"launch"}`.

**Sources** (no arguments): instances, accounts, signInInfo, settings, touchControls, installed, releases,
worlds, servers, mods, platform, telemetry, installProgress, updates, updateProgress, browseCatalog,
browseProgress, browseInstalled, gameReady, about.

**Calls** (arguments in the JSON object): launch{server}, selectInstance{id}, createInstance{name},
deleteInstance{id}, installVersion{tag}, removeVersion{tag}, saveSettings{patch}, setNoAi{on},
setTelemetry{on}, saveTouchControls{patch}, selectAccount{uuid}, removeAccount{uuid},
addOfflineName/playOfflineName/removeOfflineName{name}, playAsGameName, addServer{name,ip},
removeServer{index}, importMod{source}, removeMod{id}, importShader{source}, removeShader{name},
browseSearch{source,kind,text,offset,everything}, browseDetails{source,id}, browseInstall{source,id},
browseRemove{name}, updateDownload{tag,what,dest}, installGame{cache}, setImportCache{cache},
importCachedFile{name,source}.

Adding a core function to the API = one line in `sdui/Registry.kt`. Nothing else changes.
`HorizonApi.VERSION` rises only when something is removed or changes meaning.
