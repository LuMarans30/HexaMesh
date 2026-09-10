use crate::STOP_REQUESTED;
use crate::command;
use crate::config::ServerConfig;
use crate::diagnostics;
use log::{error, info, warn};
use std::fs::{File, OpenOptions};
use std::io::{BufRead, BufReader, LineWriter, Read, Write};
use std::net::{Ipv4Addr, SocketAddr, TcpStream};
use std::os::unix::process::ExitStatusExt;
use std::panic::{AssertUnwindSafe, catch_unwind};
use std::path::Path;
use std::process::{Child, ExitStatus};
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::{Arc, Mutex};
use std::thread;
use std::time::{Duration, Instant};

const SERVER_TAG: &str = "LlamaServer";
const SERVER_BIN: &str = "libllamaserver.so";
const LOG_FILE_NAME: &str = "llama-server.log";
const READINESS_TIMEOUT: Duration = Duration::from_secs(180);
const TERM_GRACE: Duration = Duration::from_secs(4);

pub fn run_supervisor(config: ServerConfig) {
    let exe = Path::new(&config.lib_dir).join(SERVER_BIN);
    let log_path = Path::new(&config.cache_dir).join(LOG_FILE_NAME);

    diagnostics::run_diagnostics(&exe, &config.lib_dir);

    if !exe.exists() || !Path::new(&config.model_path).exists() {
        error!("Pre-conditions failed (missing binary or model). Aborting launch.");
        return;
    }

    let log_writer = match OpenOptions::new()
        .create(true)
        .write(true)
        .truncate(true)
        .open(&log_path)
    {
        Ok(f) => Some(Arc::new(Mutex::new(LineWriter::new(f)))),
        Err(e) => {
            warn!("Failed to open log file {}: {e}", log_path.display());
            None
        }
    };

    let mut cmd = command::build_server_command(&exe, &config);
    let mut child = match cmd.spawn() {
        Ok(c) => c,
        Err(e) => {
            error!("execve failed for {}: {e}", exe.display());
            return;
        }
    };

    let port = config.port;
    let is_alive = Arc::new(AtomicBool::new(true));

    let result = catch_unwind(AssertUnwindSafe(|| {
        supervise_child(&mut child, port, log_writer, Arc::clone(&is_alive));
    }));

    if result.is_err() {
        is_alive.store(false, Ordering::Relaxed);
        error!("llama-server supervisor panicked; killing child process");

        let _ = child.kill();
        let _ = child.wait();
    }
}

fn supervise_child(
    child: &mut Child,
    port: i32,
    log_writer: Option<Arc<Mutex<LineWriter<File>>>>,
    is_alive: Arc<AtomicBool>,
) {
    let pid = child.id();
    info!("llama-server running with PID: {pid}");

    if let Some(stderr) = child.stderr.take() {
        spawn_pump(stderr, log::Level::Info, log_writer, "");
    }

    if let Ok(port) = u16::try_from(port) {
        spawn_readiness_probe(port, Arc::clone(&is_alive));
    } else {
        error!("Invalid port specified: {}", port);
    }

    while !STOP_REQUESTED.load(Ordering::Relaxed) {
        match child.try_wait() {
            Ok(Some(status)) => {
                is_alive.store(false, Ordering::Relaxed);
                log_exit_status(status);
                return;
            }
            Ok(None) => thread::sleep(Duration::from_millis(200)),
            Err(e) => {
                is_alive.store(false, Ordering::Relaxed);
                error!("Error polling child status: {e}");
                return;
            }
        }
    }

    is_alive.store(false, Ordering::Relaxed);
    terminate_process(child);
}

fn spawn_pump<R: Read + Send + 'static>(
    pipe: R,
    level: log::Level,
    file: Option<Arc<Mutex<LineWriter<File>>>>,
    prefix: &'static str,
) {
    thread::spawn(move || {
        let mut reader = BufReader::new(pipe);
        let mut line = String::new();

        while reader.read_line(&mut line).unwrap_or(0) > 0 {
            let trimmed = line.trim_end();
            if !trimmed.is_empty() {
                log::log!(target: SERVER_TAG, level, "{prefix}{trimmed}");

                if let Some(f) = &file {
                    let mut guard = f.lock().unwrap_or_else(|e| e.into_inner());
                    let _ = writeln!(guard, "{prefix}{trimmed}");
                }
            }
            line.clear();
        }
    });
}

fn spawn_readiness_probe(port: u16, is_alive: Arc<AtomicBool>) {
    thread::spawn(move || {
        let deadline = Instant::now() + READINESS_TIMEOUT;
        let addr = SocketAddr::from((Ipv4Addr::LOCALHOST, port));
        let req =
            format!("GET /health HTTP/1.1\r\nHost: 127.0.0.1:{port}\r\nConnection: close\r\n\r\n");
        let req_bytes = req.as_bytes();

        while Instant::now() < deadline && is_alive.load(Ordering::Relaxed) {
            if let Ok(mut stream) = TcpStream::connect_timeout(&addr, Duration::from_millis(400)) {
                let _ = stream.set_read_timeout(Some(Duration::from_millis(800)));
                if stream.write_all(req_bytes).is_ok() {
                    let mut buf = [0u8; 128];
                    if let Ok(n) = stream.read(&mut buf) {
                        let response = String::from_utf8_lossy(&buf[..n]);
                        if response.contains("200 OK") {
                            info!("llama-server is READY and serving tokens at :{port}");
                            return;
                        }
                    }
                }
            }
            thread::sleep(Duration::from_millis(500));
        }

        if is_alive.load(Ordering::Relaxed) {
            warn!(
                "Readiness probe reached timeout (model may still be loading graph or out of memory)."
            );
        }
    });
}

fn terminate_process(child: &mut Child) {
    let pid = child.id() as libc::pid_t;
    info!("Terminating child process {pid}");

    unsafe {
        libc::kill(pid, libc::SIGTERM);
    }

    let deadline = Instant::now() + TERM_GRACE;
    while Instant::now() < deadline {
        if let Ok(Some(_)) = child.try_wait() {
            info!("Child process {pid} exited cleanly.");
            return;
        }
        thread::sleep(Duration::from_millis(100));
    }

    warn!("Child {pid} failed to exit within grace period. Escalating to SIGKILL.");
    let _ = child.kill();
    let _ = child.wait();
}

fn log_exit_status(s: ExitStatus) {
    if let Some(code) = s.code() {
        error!("llama-server exited with code: {code}");
    } else if let Some(sig) = s.signal() {
        let reason = match sig {
            libc::SIGSEGV => "SEGV: FastRPC memory violation or unsupported architecture",
            libc::SIGABRT => "ABRT: GGML assertion failure",
            libc::SIGKILL => "KILL: Process killed externally or OOM-killer",
            _ => "OTHER",
        };
        error!("llama-server killed by signal {sig} ({reason})");
    }
}
