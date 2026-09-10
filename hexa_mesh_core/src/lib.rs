use android_logger::Config;
use jni::EnvUnowned;
use jni::errors::ThrowRuntimeExAndDefault;
use jni::objects::JObject;
use jni::strings::JNIString;
use log::info;
use std::panic::{AssertUnwindSafe, catch_unwind};
use std::sync::OnceLock;
use std::sync::atomic::{AtomicBool, Ordering};
use std::thread;

use crate::config::ServerConfig;

mod command;
mod config;
mod diagnostics;
mod engine;

const TAG: &str = "HexaRust";

pub(crate) static STOP_REQUESTED: AtomicBool = AtomicBool::new(false);
static IS_RUNNING: AtomicBool = AtomicBool::new(false);
static LOGGER_INIT: OnceLock<()> = OnceLock::new();

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

struct RunningGuard;

impl Drop for RunningGuard {
    fn drop(&mut self) {
        IS_RUNNING.store(false, Ordering::Relaxed);
    }
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

    let guard = RunningGuard;

    let config = match ServerConfig::from_jobject(e, config_obj) {
        Ok(c) => c,
        Err(err) => {
            log::error!("Failed to parse ServerConfig from Kotlin: {:?}", err);
            return Err(err);
        }
    };

    STOP_REQUESTED.store(false, Ordering::Relaxed);

    thread::spawn(move || {
        let result = catch_unwind(AssertUnwindSafe(|| {
            engine::run_supervisor(config);
        }));

        IS_RUNNING.store(false, Ordering::Relaxed);

        if result.is_err() {
            log::error!("engine::run_supervisor panicked");
        }
    });

    std::mem::forget(guard);

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
        Ok(())
    })
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
