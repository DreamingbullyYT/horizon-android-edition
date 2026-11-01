//! NativeActivity entry point that hosts Horizon's shared winit game loop.

#[cfg(target_os = "android")]
#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_horizon_launcher_game_GameInput_nativeSendControl(
    mut env: jni::JNIEnv<'_>, _object: jni::objects::JObject<'_>, key: jni::objects::JString<'_>, pressed: jni::sys::jboolean,
) {
    if let Ok(key) = env.get_string(&key) {
        horizon_client::game::send_android_control_event(key.into(), pressed != 0);
    }
}

#[cfg(target_os = "android")]
#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_horizon_launcher_game_GameInput_nativeLook(
    _env: jni::JNIEnv<'_>, _object: jni::objects::JObject<'_>, dx: jni::sys::jfloat, dy: jni::sys::jfloat,
) {
    horizon_client::game::send_android_look(f64::from(dx), f64::from(dy));
}

#[cfg(target_os = "android")]
#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_horizon_launcher_game_GameInput_nativeLookActive(
    _env: jni::JNIEnv<'_>, _object: jni::objects::JObject<'_>, active: jni::sys::jboolean,
) {
    horizon_client::game::set_android_look_active(active != 0);
}

#[cfg(target_os = "android")]
#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_horizon_launcher_game_GameInput_nativeCursorGrabbing(
    _env: jni::JNIEnv<'_>, _object: jni::objects::JObject<'_>,
) -> jni::sys::jboolean {
    horizon_client::game::android_cursor_grabbing() as jni::sys::jboolean
}

#[cfg(target_os = "android")]
#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_horizon_launcher_game_GameInput_nativeGameFps(
    _env: jni::JNIEnv<'_>, _object: jni::objects::JObject<'_>,
) -> jni::sys::jint {
    horizon_client::game::android_game_fps().min(i32::MAX as u32) as jni::sys::jint
}

#[cfg(target_os = "android")]
#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_horizon_launcher_game_GameInput_nativeSetImeAllowed(
    _env: jni::JNIEnv<'_>, _object: jni::objects::JObject<'_>, allowed: jni::sys::jboolean,
) {
    horizon_client::game::set_android_ime_allowed(allowed != 0);
}

#[cfg(target_os = "android")]
#[unsafe(no_mangle)]
pub extern "system" fn Java_dev_horizon_launcher_game_GameInput_nativeMenuTouch(
    _env: jni::JNIEnv<'_>, _object: jni::objects::JObject<'_>, id: jni::sys::jlong,
    x: jni::sys::jfloat, y: jni::sys::jfloat, phase: jni::sys::jint,
) {
    if id >= 0 && (0..=3).contains(&phase) {
        horizon_client::game::send_android_menu_touch(id as u64, f64::from(x), f64::from(y), phase as u8);
    }
}

#[cfg(target_os = "android")]
static ANDROID_APP: std::sync::OnceLock<winit::platform::android::activity::AndroidApp> =
    std::sync::OnceLock::new();

#[cfg(target_os = "android")]
fn vibrate_touch() {
    use jni::{
        JavaVM,
        objects::{JObject, JValue},
    };
    let Some(app) = ANDROID_APP.get() else {
        return;
    };
    let result = (|| -> jni::errors::Result<()> {
        // SAFETY: AndroidApp owns the VM and exposes its Activity as a global ref.
        let vm = unsafe { JavaVM::from_raw(app.vm_as_ptr().cast()) }?;
        let mut env = vm.attach_current_thread()?;
        let raw = app.activity_as_ptr();
        if raw.is_null() {
            return Ok(());
        }
        // SAFETY: this is an unowned JNI global reference owned by AndroidApp.
        let activity = unsafe { JObject::from_raw(raw.cast()) };
        let service = env.new_string("vibrator")?;
        let vibrator = env
            .call_method(
                &activity,
                "getSystemService",
                "(Ljava/lang/String;)Ljava/lang/Object;",
                &[JValue::Object(service.as_ref())],
            )?
            .l()?;
        if vibrator.is_null() {
            return Ok(());
        }
        let effect_class = env.find_class("android/os/VibrationEffect")?;
        let effect = env
            .call_static_method(
                effect_class,
                "createOneShot",
                "(JI)Landroid/os/VibrationEffect;",
                &[JValue::Long(16), JValue::Int(1)],
            )?
            .l()?;
        env.call_method(
            vibrator,
            "vibrate",
            "(Landroid/os/VibrationEffect;)V",
            &[JValue::Object(&effect)],
        )?;
        Ok(())
    })();
    if let Err(error) = result {
        log::debug!("Horizon Android: touch vibration unavailable: {error}");
    }
}

#[cfg(target_os = "android")]
#[unsafe(no_mangle)]
pub fn android_main(app: winit::platform::android::activity::AndroidApp) {
    android_logger::init_once(
        android_logger::Config::default().with_max_level(log::LevelFilter::Info),
    );
    let _ = ANDROID_APP.set(app.clone());
    horizon_client::game::set_android_haptic_callback(vibrate_touch);
    let Some(files) = app.internal_data_path() else {
        log::error!("Horizon Android: app-private files directory is unavailable");
        return;
    };
    let launcher = files.join("launcher");
    let game_home = launcher.join("game");
    if let Err(error) = std::fs::create_dir_all(&game_home) {
        log::error!("Horizon Android: cannot create game directory: {error}");
        return;
    }
    // The NativeActivity shares the launcher process. Set the game paths before Horizon starts
    // worker threads so saves and logs stay in app-private storage.
    unsafe {
        std::env::set_var("HORIZON_LAUNCHER_HOME", &launcher);
        std::env::set_var("HORIZON_HOME", &game_home);
        // The phone's Adreno Vulkan driver fails to link Horizon's sky
        // pipeline during the first missing-resources frame. Use the GLES
        // backend on Android so startup and the game share a stable path.
        std::env::set_var("HORIZON_GRAPHICS_API", "opengl");
    }
    if let Err(error) = std::env::set_current_dir(&game_home) {
        log::error!("Horizon Android: cannot enter game directory: {error}");
        return;
    }
    if let Ok(accounts) = std::fs::read(launcher.join("accounts.json"))
        && let Ok(value) = serde_json::from_slice::<serde_json::Value>(&accounts)
        && let Some(name) = value.get("offline_selected").and_then(|v| v.as_str())
    {
        unsafe { std::env::set_var("HORIZON_PLAYER_NAME", name) };
    }
    if let Ok(controls) = std::fs::read(launcher.join("android-touch-controls.json"))
        && let Ok(controls) = std::str::from_utf8(&controls)
    {
        unsafe { std::env::set_var("HORIZON_ANDROID_TOUCH_CONTROLS", controls) };
    }
    horizon_client::game::set_android_app(app);
    if let Err(error) = horizon_client::game::run() {
        log::error!("Horizon Android game stopped: {error}");
    }
}
