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
    } = config;

    // Environment setup for Qualcomm DSPs
    let adsp_existing = std::env::var("ADSP_LIBRARY_PATH").unwrap_or_default();
    let adsp_path = if adsp_existing.is_empty() {
        format!("{lib_dir};/system/lib/rfsa/adsp;/system/vendor/lib/rfsa/adsp;/dsp;/vendor/dsp")
    } else {
        format!("{lib_dir};{adsp_existing}")
    };

    let ld_existing = std::env::var("LD_LIBRARY_PATH").unwrap_or_default();
    let ld_path = format!("{lib_dir}:/vendor/lib64:/system/lib64:{ld_existing}");

    let port_str = port.to_string();

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

    let cl_cache_dir = Path::new(&cache_dir).join("cl-cache");
    let _ = fs::create_dir_all(&cl_cache_dir);

    match backend.to_lowercase().as_str() {
        "gpu" | "opencl" => {
            args.extend(["--device", "GPUOpenCL", "-ngl", "99"]);
            cmd.env(
                "GGML_OPENCL_KERNEL_CACHE_DIR",
                cl_cache_dir.to_string_lossy().as_ref(),
            );
        }
        "npu" | "hexagon" => {
            args.extend(["--device", "HTP0", "-ngl", "99"]);
            cmd.env("GGML_HEXAGON_DEVICES", "HTP0")
                .env("GGML_HEXAGON_OPPOLL", "1")
                .env("GGML_HEXAGON_OPFILTER", "ADD");
        }
        _ => {
            // CPU fallback
            args.extend(["-ngl", "0"]);
        }
    }

    let work_dir = Path::new(&cache_dir).join("run");
    let _ = fs::create_dir_all(&work_dir);

    cmd.args(&args)
        .current_dir(&work_dir)
        .env("LD_LIBRARY_PATH", ld_path)
        .env("ADSP_LIBRARY_PATH", adsp_path)
        .stdout(Stdio::piped())
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
