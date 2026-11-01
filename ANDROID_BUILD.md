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

## Build with GitHub Actions

`.github/workflows/android.yml` builds the APK on every push/PR (debug artifact), on manual
runs, and attaches it to a GitHub Release for tags like `v0.2.0`. For a signed release APK add the
secrets `HORIZON_KEYSTORE_BASE64`, `HORIZON_KEYSTORE_PASSWORD`, `HORIZON_KEY_ALIAS` and
(optionally) `HORIZON_KEY_PASSWORD`.
