use std::fs;
use std::os::unix::process::CommandExt;
use std::path::Path;
use std::process::{Command, Stdio};

use crate::config::ServerConfig;

pub fn build_server_command(exe: &Path, config: &ServerConfig) -> Command {
    let mut cmd = Command::new(exe);

    let ServerConfig {
        port,
        backend,
        model_path,
        lib_dir,
        cache_dir,
        api_key,
    } = config;

    let existing_env =
        |var: &str| -> Option<String> { std::env::var(var).ok().filter(|v| !v.is_empty()) };

    // Environment setup for Qualcomm DSPs
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

    let port_str = port.to_string();

    let cl_cache_dir = Path::new(&cache_dir).join("cl-cache");
    let work_dir = Path::new(&cache_dir).join("run");

    let _ = fs::create_dir_all(&cl_cache_dir);
    let _ = fs::create_dir_all(&work_dir);

    let mut args = vec![
        "--host",
        "0.0.0.0",
        "--port",
        &port_str,
        "-m",
        &model_path,
        "-fa",
        "on",
        "-t",
        "6",
        "-ub",
        "16",
        "--no-warmup",
    ];

    if !api_key.is_empty() {
        args.extend(["--api-key", api_key]);
    }

    match backend.to_lowercase().as_str() {
        "gpu" | "opencl" => {
            args.extend(["--device", "GPUOpenCL", "-ngl", "99"]);
            cmd.env("GGML_OPENCL_KERNEL_CACHE_DIR", cl_cache_dir.as_os_str());
        }
        "npu" | "hexagon" => {
            args.extend(["--device", "HTP0", "-ngl", "99"]);
            cmd.env("GGML_HEXAGON_DEVICES", "HTP0");
        }
        _ => {
            args.extend(["-ngl", "0"]);
        }
    }

    cmd.args(&args)
        .current_dir(&work_dir)
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
