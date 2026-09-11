use android_logger::Config;
use jni::EnvUnowned;
use jni::JValue;
use jni::errors::ThrowRuntimeExAndDefault;
use jni::objects::JObject;
use jni::strings::JNIString;
use log::info;
use std::panic::{AssertUnwindSafe, catch_unwind};
use std::sync::Mutex;
use std::sync::OnceLock;
use std::sync::atomic::{AtomicBool, Ordering};
use std::thread;

use crate::config::ServerConfig;
use crate::engine::Supervisor;
use crate::status::{Snapshot, Status};

mod command;
mod config;
mod diagnostics;
mod engine;
mod status;

const TAG: &str = "HexaRust";

pub(crate) static STOP_REQUESTED: AtomicBool = AtomicBool::new(false);
static IS_RUNNING: AtomicBool = AtomicBool::new(false);
static LOGGER_INIT: OnceLock<()> = OnceLock::new();
static SUPERVISOR: Mutex<Option<thread::JoinHandle<()>>> = Mutex::new(None);

macro_rules! jni_catch {
    ($env:expr, $name:expr, $body:expr) => {
        $env.with_env(|e| -> jni::errors::Result<()> {
            match catch_unwind(AssertUnwindSafe(|| $body(&mut *e))) {
                Ok(res) => res,
                Err(_) => {
                    log::error!("Panic caught at JNI boundary: {}", $name);

                    if !e.exception_check() {
                        let _ = e.throw_new(
                            JNIString::new("java/lang/RuntimeException"),
                            JNIString::new(concat!("native Rust panic in ", $name)),
                        );
                    }

                    Ok(())
                }
            }
        })
        .resolve::<ThrowRuntimeExAndDefault>()
    };
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_lumarans30_hexamesh_bridge_RustEngine_start<'local>(
    mut env: EnvUnowned<'local>,
    _this: JObject<'local>,
    config_obj: JObject<'local>,
) {
    ensure_logger();
    jni_catch!(env, "RustEngine.start", |e| start_inner(e, &config_obj))
}

fn start_inner<'local>(
    e: &mut jni::Env<'local>,
    config_obj: &JObject<'local>,
) -> jni::errors::Result<()> {
    if IS_RUNNING.swap(true, Ordering::Relaxed) {
        log::warn!("Engine supervisor already active. Ignoring duplicate start.");
        return Ok(());
    }

    if let Some(previous) = SUPERVISOR.lock().unwrap_or_else(|e| e.into_inner()).take() {
        let _ = previous.join();
    }

    let config = match ServerConfig::from_jobject(e, config_obj) {
        Ok(c) => c,
        Err(err) => {
            IS_RUNNING.store(false, Ordering::Relaxed);
            log::error!("Failed to parse ServerConfig from Kotlin: {:?}", err);
            return Err(err);
        }
    };

    STOP_REQUESTED.store(false, Ordering::Relaxed);
    let starting = if config.is_rpc() {
        "Starting ggml-rpc-server..."
    } else {
        "Starting llama-server..."
    };
    status::set(Status::Starting, starting);

    let handle = thread::spawn(move || {
        let result = catch_unwind(AssertUnwindSafe(|| {
            Supervisor::supervise(config);
        }));

        IS_RUNNING.store(false, Ordering::Relaxed);

        if result.is_err() {
            log::error!("engine::Supervisor::supervise panicked");
            status::set(Status::Error, "Rust engine supervisor panicked");
        }
    });

    *SUPERVISOR.lock().unwrap_or_else(|e| e.into_inner()) = Some(handle);

    Ok(())
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_lumarans30_hexamesh_bridge_RustEngine_stop(
    mut env: EnvUnowned,
    _this: JObject,
) {
    ensure_logger();

    jni_catch!(env, "RustEngine.stop", |_e| {
        info!("Stop requested from Android Service.");
        STOP_REQUESTED.store(true, Ordering::Relaxed);

        if let Some(handle) = SUPERVISOR.lock().unwrap_or_else(|e| e.into_inner()).take() {
            let _ = handle.join();
        }

        let _ = status::transition(Status::Running, Status::Stopped, "Stopped by request");
        let _ = status::transition(Status::Starting, Status::Stopped, "Stopped by request");

        Ok(())
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_lumarans30_hexamesh_bridge_RustEngine_pollStatus<'local>(
    mut env: EnvUnowned<'local>,
    _this: JObject<'local>,
) -> jni::sys::jobject {
    let Snapshot { state, message } = status::snapshot();

    let outcome = env.with_env(|e| -> jni::errors::Result<jni::sys::jobject> {
        let class = e.find_class(jni::jni_str!("com/lumarans30/hexamesh/bridge/EngineStatus"))?;
        let message = e.new_string(message)?;
        let obj = e.new_object(
            class,
            jni::jni_sig!("(ILjava/lang/String;)V"),
            &[JValue::Int(state), JValue::Object(&JObject::from(message))],
        )?;
        Ok(obj.into_raw())
    });

    match outcome.into_outcome() {
        jni::Outcome::Ok(ptr) => ptr,
        _ => std::ptr::null_mut(),
    }
}

fn ensure_logger() {
    LOGGER_INIT.get_or_init(|| {
        let level = std::env::var("HEXA_LOG_LEVEL")
            .ok()
            .and_then(|s| s.parse().ok())
            .unwrap_or(log::LevelFilter::Info);

        android_logger::init_once(Config::default().with_max_level(level).with_tag(TAG));
        log_panics::init();
    });
}
