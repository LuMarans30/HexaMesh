use log::{error, info, warn};
use std::fs::{self, File};
use std::os::unix::fs::PermissionsExt;
use std::path::Path;

pub fn run_diagnostics(exe: &Path, lib_dir: &str) {
    if let Ok(md) = fs::metadata(exe) {
        let mode = md.permissions().mode();
        if mode & 0o111 == 0 {
            error!("Executable missing +x bit ({:o}). Chmodding...", mode);
            let mut p = md.permissions();
            p.set_mode(mode | 0o111);
            let _ = fs::set_permissions(exe, p);
        }
    } else {
        error!("Server binary not found at {}", exe.display());
    }

    for dev in &["/dev/cdsprpc-smd", "/dev/adsprpc-smd"] {
        match File::open(dev) {
            Ok(_) => info!("DSP Device {dev}: ACCESSIBLE"),
            Err(e) => warn!("DSP Device {dev}: CANNOT OPEN ({e})"),
        }
    }

    if let Ok(entries) = fs::read_dir(lib_dir) {
        let skels: Vec<_> = entries
            .filter_map(|e| e.ok())
            .map(|e| e.file_name())
            .filter(|n| n.to_string_lossy().starts_with("libggml-htp-v"))
            .collect();
        info!("Hexagon skeletons present: {:?}", skels);
    }
}
