use android_logger::Config;
use jni::EnvUnowned;
use jni::errors::ThrowRuntimeExAndDefault;
use jni::objects::JObject;
use log::info;
use std::sync::atomic::{AtomicBool, Ordering};
use std::thread;

use crate::config::ServerConfig;

mod command;
mod config;
mod diagnostics;
mod engine;

const TAG: &str = "HexaRust";

// Shared state accessible by the engine supervisor
pub(crate) static STOP_REQUESTED: AtomicBool = AtomicBool::new(false);
static IS_RUNNING: AtomicBool = AtomicBool::new(false);

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_lumarans30_hexamesh_bridge_RustEngine_start<'local>(
    mut env: EnvUnowned<'local>,
    _this: JObject<'local>,
    config_obj: JObject<'local>,
) {
    android_logger::init_once(
        Config::default()
            .with_max_level(log::LevelFilter::Info)
            .with_tag(TAG),
    );

    env.with_env(|e| -> jni::errors::Result<()> {
        let config = match ServerConfig::from_jobject(e, &config_obj) {
            Ok(c) => c,
            Err(err) => {
                log::error!("Failed to parse ServerConfig from Kotlin: {:?}", err);
                return Err(err);
            }
        };

        if IS_RUNNING.swap(true, Ordering::SeqCst) {
            log::warn!("Engine supervisor already active. Ignoring duplicate start.");
            return Ok(());
        }

        STOP_REQUESTED.store(false, Ordering::SeqCst);

        thread::spawn(move || {
            let _ = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
                engine::run_supervisor(config);
            }));
            IS_RUNNING.store(false, Ordering::SeqCst);
        });

        Ok(())
    })
    .resolve::<ThrowRuntimeExAndDefault>();
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_lumarans30_hexamesh_bridge_RustEngine_stop(
    _env: EnvUnowned,
    _this: JObject,
) {
    info!("Stop requested from Android Service.");
    STOP_REQUESTED.store(true, Ordering::SeqCst);
}
