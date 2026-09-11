use std::fs;
use std::os::unix::process::CommandExt;
use std::path::Path;
use std::process::{Command, Stdio};

use crate::config::ServerConfig;

pub fn build_server_command(exe: &Path, config: &ServerConfig) -> Command {
    let mut cmd = Command::new(exe);

    let ServerConfig {
        backend,
        model_path,
        lib_dir,
        cache_dir,
        api_key,
        extra_args,
        ..
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

    let cl_cache_dir = Path::new(&cache_dir).join("cl-cache");
    let work_dir = Path::new(&cache_dir).join("run");

    let _ = fs::create_dir_all(&cl_cache_dir);
    let _ = fs::create_dir_all(&work_dir);

    // Only the args the app owns. Everything else (port, sampling, threads, GPU
    // layers, ...) arrives through `extra_args` so the user can edit the defaults.
    let mut args: Vec<String> = vec![
        "--host".into(),
        "0.0.0.0".into(),
        "-m".into(),
        model_path.clone(),
    ];

    if !api_key.is_empty() {
        args.push("--api-key".into());
        args.push(api_key.clone());
    }

    match backend.to_lowercase().as_str() {
        "gpu" | "opencl" => {
            args.push("--device".into());
            args.push("GPUOpenCL".into());
            cmd.env("GGML_OPENCL_KERNEL_CACHE_DIR", cl_cache_dir.as_os_str());
        }
        "npu" | "hexagon" => {
            args.push("--device".into());
            args.push("HTP0".into());
            cmd.env("GGML_HEXAGON_DEVICES", "HTP0");
        }
        _ => {}
    }

    args.extend(extra_args.iter().cloned());

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
