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
const LOG_FILE_NAME: &str = "llama-server.log";
const READINESS_TIMEOUT: Duration = Duration::from_secs(180);
const TERM_GRACE: Duration = Duration::from_secs(4);
const HEALTH_POLL_INTERVAL: Duration = Duration::from_millis(500);
const HEALTH_MAX_MISSES: u32 = 3;

pub(crate) struct Supervisor {
    child: Child,
    port: u16,
    log_writer: Option<Arc<Mutex<LineWriter<File>>>>,
    is_alive: Arc<AtomicBool>,
}

impl Supervisor {
    pub(crate) fn supervise(config: ServerConfig) {
        status::set(Status::Starting, "Starting llama-server...");

        let exe = Path::new(&config.lib_dir).join(SERVER_BIN);
        let log_path = Path::new(&config.cache_dir).join(LOG_FILE_NAME);

        diagnostics::run_diagnostics(&exe, &config.lib_dir);

        let mut sup = match Self::start(&exe, &config, &log_path) {
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
            error!("llama-server supervisor panicked");
            status::set(Status::Error, "llama-server supervisor panicked");
        }
    }

    fn start(exe: &Path, config: &ServerConfig, log_path: &Path) -> Result<Self, String> {
        if !exe.exists() || !Path::new(&config.model_path).exists() {
            return Err(format!(
                "Pre-conditions failed (missing binary or model). exe={}, model={}",
                exe.display(),
                config.model_path
            ));
        }

        let port = u16::try_from(config.port)
            .map_err(|_| format!("Invalid port specified: {}", config.port))?;

        let log_writer = Self::open_log_file(log_path);

        let mut cmd = command::build_server_command(exe, config);
        let child = cmd
            .spawn()
            .map_err(|e| format!("execve failed for {}: {e}", exe.display()))?;

        Ok(Self {
            child,
            port,
            log_writer,
            is_alive: Arc::new(AtomicBool::new(true)),
        })
    }

    fn run(&mut self) {
        let pid = self.child.id();
        info!("llama-server running with PID: {pid}");

        if let Some(stderr) = self.child.stderr.take() {
            self.spawn_pump(stderr, log::Level::Info, "");
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
                    let reason = Self::exit_reason(status);
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

        unsafe {
            libc::kill(pid, libc::SIGTERM);
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
        let _ = self.child.kill();
        let _ = self.child.wait();
    }

    fn spawn_pump<R: Read + Send + 'static>(
        &self,
        pipe: R,
        level: log::Level,
        prefix: &'static str,
    ) {
        let file = self.log_writer.clone();
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

    fn spawn_health_probe(&self) {
        let port = self.port;
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

                if Self::probe_health(addr, req_bytes) {
                    misses = 0;
                    if current == Status::Starting as i32 {
                        info!("llama-server is READY and serving tokens at :{port}");
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
                    // current == Status::Running
                    misses += 1;
                    if misses >= HEALTH_MAX_MISSES {
                        let message = "llama-server stopped responding to /health.".to_string();
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

    fn exit_reason(s: ExitStatus) -> String {
        if let Some(code) = s.code() {
            format!("llama-server exited with code: {code}")
        } else if let Some(sig) = s.signal() {
            let reason = match sig {
                libc::SIGSEGV => "SEGV: FastRPC memory violation or unsupported architecture",
                libc::SIGABRT => "ABRT: GGML assertion failure",
                libc::SIGKILL => "KILL: Process killed externally or OOM-killer",
                _ => "OTHER",
            };
            format!("llama-server killed by signal {sig} ({reason})")
        } else {
            "llama-server exited with an unknown status".to_string()
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
