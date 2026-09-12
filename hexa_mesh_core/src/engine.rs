use crate::STOP_REQUESTED;
use crate::command;
use crate::config::ServerConfig;
use crate::diagnostics;
use crate::status::{self, Status};
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
const RPC_SERVER_BIN: &str = "libggmlrpcserver.so";
const SERVER_LABEL: &str = "llama-server";
const RPC_LABEL: &str = "ggml-rpc-server";
const LOG_FILE_NAME: &str = "llama-server.log";
const RPC_LOG_FILE_NAME: &str = "rpc-server.log";
const READINESS_TIMEOUT: Duration = Duration::from_secs(180);
const TERM_GRACE: Duration = Duration::from_secs(4);
const HEALTH_POLL_INTERVAL: Duration = Duration::from_millis(500);
const HEALTH_MAX_MISSES: u32 = 3;

pub(crate) struct Supervisor {
    child: Child,
    port: u16,
    label: &'static str,
    probe: Probe,
    log_writer: Option<Arc<Mutex<LineWriter<File>>>>,
    is_alive: Arc<AtomicBool>,
}

/// How the supervisor proves the child is alive. llama-server answers HTTP;
/// ggml-rpc-server has no HTTP surface, so a TCP connect is enough.
#[derive(Clone, Copy, PartialEq, Eq)]
enum Probe {
    Http,
    Tcp,
}

impl Supervisor {
    pub(crate) fn supervise(config: ServerConfig) {
        let (bin, log_name, label, probe) = if config.is_rpc() {
            (RPC_SERVER_BIN, RPC_LOG_FILE_NAME, RPC_LABEL, Probe::Tcp)
        } else {
            (SERVER_BIN, LOG_FILE_NAME, SERVER_LABEL, Probe::Http)
        };

        status::set(Status::Starting, format!("Starting {label}..."));

        let exe = Path::new(&config.lib_dir).join(bin);
        let log_path = Path::new(&config.cache_dir).join(log_name);

        diagnostics::run_diagnostics(&exe, &config.lib_dir);

        let mut sup = match Self::start(&exe, &config, &log_path, label, probe) {
            Ok(s) => s,
            Err(message) => {
                error!("{message}");
                status::set(Status::Error, message);
                return;
            }
        };

        let result = catch_unwind(AssertUnwindSafe(move || {
            sup.run();
        }));

        if result.is_err() {
            error!("{label} supervisor panicked");
            status::set(Status::Error, format!("{label} supervisor panicked"));
        }
    }

    fn start(
        exe: &Path,
        config: &ServerConfig,
        log_path: &Path,
        label: &'static str,
        probe: Probe,
    ) -> Result<Self, String> {
        if !exe.exists() {
            return Err(format!("Server binary not found at {}", exe.display()));
        }

        let bind_port = if config.is_rpc() {
            config.rpc_port
        } else {
            config.port
        };
        let port =
            u16::try_from(bind_port).map_err(|_| format!("Invalid port specified: {bind_port}"))?;

        let log_writer = Self::open_log_file(log_path);

        let mut cmd = command::build_command(exe, config);
        let child = cmd
            .spawn()
            .map_err(|e| format!("execve failed for {}: {e}", exe.display()))?;

        Ok(Self {
            child,
            port,
            label,
            probe,
            log_writer,
            is_alive: Arc::new(AtomicBool::new(true)),
        })
    }

    fn run(&mut self) {
        let pid = self.child.id();
        info!("{} running with PID: {pid}", self.label);

        if let Some(stderr) = self.child.stderr.take() {
            self.spawn_pump(stderr);
        }

        self.spawn_health_probe();

        while !STOP_REQUESTED.load(Ordering::Relaxed) {
            if status::current() == Status::Error as i32 {
                self.shutdown();
                return;
            }

            match self.child.try_wait() {
                Ok(Some(status)) => {
                    self.is_alive.store(false, Ordering::Relaxed);
                    let reason = Self::exit_reason(status, self.label);
                    error!("{reason}");
                    status::set(Status::Error, reason);
                    return;
                }
                Ok(None) => thread::sleep(Duration::from_millis(200)),
                Err(e) => {
                    self.is_alive.store(false, Ordering::Relaxed);
                    let message = format!("Error polling child status: {e}");
                    error!("{message}");
                    status::set(Status::Error, message);
                    return;
                }
            }
        }

        self.shutdown();
        status::set(Status::Stopped, "Stopped by request");
    }

    fn shutdown(&mut self) {
        self.is_alive.store(false, Ordering::Relaxed);

        let pid = self.child.id() as libc::pid_t;
        info!("Terminating child process {pid}");

        // The child is a session leader (see `command.rs`), so its pid is also
        // its process group: signal the group to also reach any model instances
        // a router-spawned llama-server created.
        unsafe {
            libc::kill(-pid, libc::SIGTERM);
        }

        let deadline = Instant::now() + TERM_GRACE;
        while Instant::now() < deadline {
            if let Ok(Some(_)) = self.child.try_wait() {
                info!("Child process {pid} exited cleanly.");
                return;
            }
            thread::sleep(Duration::from_millis(100));
        }

        warn!("Child {pid} failed to exit within grace period. Escalating to SIGKILL.");
        unsafe {
            libc::kill(-pid, libc::SIGKILL);
        }
        let _ = self.child.wait();
    }

    fn spawn_pump<R: Read + Send + 'static>(&self, pipe: R) {
        let file = self.log_writer.clone();
        thread::spawn(move || {
            let mut reader = BufReader::new(pipe);
            let mut line = String::new();

            while reader.read_line(&mut line).unwrap_or(0) > 0 {
                let trimmed = line.trim_end();
                if !trimmed.is_empty() {
                    log::info!(target: SERVER_TAG, "{trimmed}");

                    if let Some(f) = &file {
                        let mut guard = f.lock().unwrap_or_else(|e| e.into_inner());
                        let _ = writeln!(guard, "{trimmed}");
                    }
                }
                line.clear();
            }
        });
    }

    fn spawn_health_probe(&self) {
        let port = self.port;
        let label = self.label;
        let probe = self.probe;
        let is_alive = Arc::clone(&self.is_alive);

        thread::spawn(move || {
            let deadline = Instant::now() + READINESS_TIMEOUT;
            let addr = SocketAddr::from((Ipv4Addr::LOCALHOST, port));
            let req = format!(
                "GET /health HTTP/1.1\r\nHost: 127.0.0.1:{port}\r\nConnection: close\r\n\r\n"
            );
            let req_bytes = req.as_bytes();
            let mut misses = 0u32;

            while is_alive.load(Ordering::Relaxed) && !STOP_REQUESTED.load(Ordering::Relaxed) {
                let current = status::current();
                if current != Status::Starting as i32 && current != Status::Running as i32 {
                    return;
                }

                let reachable = match probe {
                    Probe::Http => Self::probe_health(addr, req_bytes),
                    Probe::Tcp => Self::probe_tcp(addr),
                };

                if reachable {
                    misses = 0;
                    if current == Status::Starting as i32 {
                        info!("{label} is READY and listening on :{port}");
                        status::transition(
                            Status::Starting,
                            Status::Running,
                            format!("Ready on port {port}"),
                        );
                    }
                } else if current == Status::Starting as i32 {
                    if Instant::now() > deadline {
                        let message = "Readiness probe reached timeout (model may still be loading graph or out of memory).".to_string();
                        warn!("{message}");
                        status::transition(Status::Starting, Status::Error, message);
                        return;
                    }
                } else {
                    misses += 1;
                    if misses >= HEALTH_MAX_MISSES {
                        let message = format!("{label} stopped responding on port {port}.");
                        error!("{message}");
                        status::transition(Status::Running, Status::Error, message);
                        return;
                    }
                }

                thread::sleep(HEALTH_POLL_INTERVAL);
            }
        });
    }

    fn probe_health(addr: SocketAddr, req: &[u8]) -> bool {
        if let Ok(mut stream) = TcpStream::connect_timeout(&addr, Duration::from_millis(400)) {
            let _ = stream.set_read_timeout(Some(Duration::from_millis(800)));
            if stream.write_all(req).is_ok() {
                let mut buf = [0u8; 128];
                if let Ok(n) = stream.read(&mut buf) {
                    return String::from_utf8_lossy(&buf[..n]).contains("200 OK");
                }
            }
        }
        false
    }

    fn probe_tcp(addr: SocketAddr) -> bool {
        TcpStream::connect_timeout(&addr, Duration::from_millis(400)).is_ok()
    }

    fn exit_reason(s: ExitStatus, label: &str) -> String {
        if let Some(code) = s.code() {
            format!("{label} exited with code: {code}")
        } else if let Some(sig) = s.signal() {
            let reason = match sig {
                libc::SIGSEGV => "SEGV: FastRPC memory violation or unsupported architecture",
                libc::SIGABRT => "ABRT: GGML assertion failure",
                libc::SIGKILL => "KILL: Process killed externally or OOM-killer",
                _ => "OTHER",
            };
            format!("{label} killed by signal {sig} ({reason})")
        } else {
            format!("{label} exited with an unknown status")
        }
    }

    fn open_log_file(log_path: &Path) -> Option<Arc<Mutex<LineWriter<File>>>> {
        match OpenOptions::new()
            .create(true)
            .write(true)
            .truncate(true)
            .open(log_path)
        {
            Ok(f) => Some(Arc::new(Mutex::new(LineWriter::new(f)))),
            Err(e) => {
                warn!("Failed to open log file {}: {e}", log_path.display());
                None
            }
        }
    }
}

impl Drop for Supervisor {
    fn drop(&mut self) {
        if self.is_alive.load(Ordering::Relaxed) {
            self.shutdown();
        }
    }
}
