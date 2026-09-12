use std::fs;
use std::os::unix::process::CommandExt;
use std::path::Path;
use std::process::{Command, Stdio};

use crate::config::ServerConfig;

/// llama-server: the coordinator that serves the OpenAI API and can offload to
/// peers. Only the args the app owns are built here; port, sampling, threads and
/// GPU layers arrive through `extra_args` so the user can edit the defaults.
pub fn build_server_command(exe: &Path, config: &ServerConfig) -> Command {
    let mut cmd = base_command(exe, config);

    let mut args: Vec<String> = vec![
        "--host".into(),
        "0.0.0.0".into(),
        "-m".into(),
        config.model_path.clone(),
    ];

    if !config.api_key.is_empty() {
        args.push("--api-key".into());
        args.push(config.api_key.clone());
    }

    // `--device` restricts llama.cpp to the listed devices, and RPC peers are
    // registered separately, so pinning a device would exclude them. When
    // meshing, let llama.cpp pick every available device instead.
    if config.rpc_servers.is_empty()
        && let Some(device) = backend_device(&config.backend)
    {
        args.push("--device".into());
        args.push(device.into());
    }

    if !config.rpc_servers.is_empty() {
        args.push("--rpc".into());
        args.push(config.rpc_servers.clone());
    }

    args.extend(config.extra_args.iter().cloned());

    cmd.args(&args);
    cmd
}

/// ggml-rpc-server: exposes this device's accelerators so a coordinator can use
/// them. It takes a much smaller flag set than llama-server, so the user's
/// launch args are intentionally not forwarded.
pub fn build_rpc_command(exe: &Path, config: &ServerConfig) -> Command {
    let mut cmd = base_command(exe, config);

    let mut args: Vec<String> = vec![
        "--host".into(),
        "0.0.0.0".into(),
        "--port".into(),
        config.port.to_string(),
    ];

    if let Some(device) = backend_device(&config.backend) {
        args.push("--device".into());
        args.push(device.into());
    }

    cmd.args(&args);
    cmd
}

/// Environment and process hygiene shared by both roles: the Qualcomm DSP search
/// paths, the cache/work directories, and a SIGKILL on JVM death.
fn base_command(exe: &Path, config: &ServerConfig) -> Command {
    let ServerConfig {
        lib_dir,
        cache_dir,
        llama_cache_dir,
        backend,
        ..
    } = config;

    let mut cmd = Command::new(exe);

    let existing_env =
        |var: &str| -> Option<String> { std::env::var(var).ok().filter(|v| !v.is_empty()) };

    let adsp_fallback = "/system/lib/rfsa/adsp;/system/vendor/lib/rfsa/adsp;/dsp;/vendor/dsp";
    let system_libs = "/vendor/lib64:/system/lib64";

    let adsp_path = match existing_env("ADSP_LIBRARY_PATH") {
        Some(val) => format!("{lib_dir};{val}"),
        None => format!("{lib_dir};{adsp_fallback}"),
    };

    let ld_path = match existing_env("LD_LIBRARY_PATH") {
        Some(val) => format!("{lib_dir}:{system_libs}:{val}"),
        None => format!("{lib_dir}:{system_libs}"),
    };

    let cl_cache_dir = Path::new(cache_dir).join("cl-cache");
    let work_dir = Path::new(cache_dir).join("run");

    let _ = fs::create_dir_all(&cl_cache_dir);
    let _ = fs::create_dir_all(&work_dir);

    // Android has no usable $HOME, so pin llama.cpp's cache (Hugging Face hub and
    // the general file cache both read `LLAMA_CACHE`) to an app-owned directory.
    if !llama_cache_dir.is_empty() {
        let _ = fs::create_dir_all(llama_cache_dir);
        cmd.env("LLAMA_CACHE", llama_cache_dir);
    }

    match backend.to_lowercase().as_str() {
        "gpu" | "opencl" => {
            cmd.env("GGML_OPENCL_KERNEL_CACHE_DIR", cl_cache_dir.as_os_str());
        }
        "npu" | "hexagon" => {
            cmd.env("GGML_HEXAGON_DEVICES", "HTP0");
        }
        _ => {}
    }

    cmd.current_dir(&work_dir)
        .env("LD_LIBRARY_PATH", &ld_path)
        .env("ADSP_LIBRARY_PATH", &adsp_path)
        .stdout(Stdio::null())
        .stderr(Stdio::piped());

    // Ensure the child dies if the Android JVM dies
    unsafe {
        cmd.pre_exec(|| {
            libc::setsid();
            libc::prctl(libc::PR_SET_PDEATHSIG, libc::SIGKILL);
            Ok(())
        });
    }

    cmd
}

/// Maps the app's backend label to a ggml device name, or None to let the
/// binary pick its default.
fn backend_device(backend: &str) -> Option<&'static str> {
    match backend.to_lowercase().as_str() {
        "gpu" | "opencl" => Some("GPUOpenCL"),
        "npu" | "hexagon" => Some("HTP0"),
        _ => None,
    }
}

/// Builds the right command for the config's role.
pub fn build_command(exe: &Path, config: &ServerConfig) -> Command {
    if config.is_rpc() {
        build_rpc_command(exe, config)
    } else {
        build_server_command(exe, config)
    }
}
