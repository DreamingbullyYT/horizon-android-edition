# Build the Android preview

The source ZIP contains the Android launcher, Horizon Rust workspace, and the
vendored Zalith LayerController/InputMap modules. It excludes Cargo/Gradle build
outputs and prebuilt native libraries.

Requirements: Android SDK with NDK 26.3.11579264, JDK 17+, Gradle 8.7, Rust,
and the `aarch64-linux-android` Rust target.

From the extracted project root, set `ANDROID_HOME` to the SDK path and run:

```sh
gradle assembleDebug
```

The output is `app/build/outputs/apk/debug/app-debug.apk`. The build creates both
the Rust launcher JNI library and the NativeActivity game library for ARM64.
The app uses a debug signing key and this command does not create a release
signing configuration.
