pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "HorizonAndroid"
include(":app")
include(":zalith:InputMap")
project(":zalith:InputMap").projectDir = file("app/third_party/zalith/InputMap")
include(":zalith:LayerController")
project(":zalith:LayerController").projectDir = file("app/third_party/zalith/LayerController")
