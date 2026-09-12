# AGENTS.md

Agent guidance for **HexaMesh**. Treat this as living documentation: if a fact
here is stale, fix it; do not leave dead plans behind.

## Working agreements

- **Do not commit on your own.** Make the edits, run the checks, and leave the
  changes for the human to review. No `git add` / `commit` / `amend` / `push`
  unless explicitly asked.
- **Small and reviewable.** Prefer focused changes over sweeping rewrites.
- **You may install and screenshot.** Build with
  `./gradlew :app:assembleDebug -PskipRustBuild`, then put it on a connected
  device with `adb install -r app/build/outputs/apk/debug/app-debug.apk` —
  `:app:installDebug` fails on some OEMs with `INSTALL_FAILED_USER_RESTRICTED`.
  Capture the UI while debugging with `adb exec-out screencap -p > /tmp/hexamesh.png`.
- **Never uninstall or clear app data.** `adb uninstall com.lumarans30.hexamesh`
  and `adb shell pm clear com.lumarans30.hexamesh` wipe internal storage and
  destroy the user's imported models. Reinstall in place instead.

## Project overview

HexaMesh runs llama.cpp's `llama-server` inside an Android foreground service
and exposes an OpenAI-compatible HTTP API on the LAN. A Rust supervisor spawns
and watches the server over JNI.

- Release `0.9.0` (`versionCode 8`); the server defaults to **8080** and the port
  is editable through the launch args.
- Backends: Adreno GPU (OpenCL) today; the Hexagon NPU path is **experimental
  and produces corrupted output**.
- **RPC mesh is partly built:** `scripts/enable_features.py` forces `GGML_RPC=ON`
  (plus `LLAMA_SUBPROCESS=ON` for router mode) in the Snapdragon build (run by
  `build_llama.sh` and CI), so `pkg-adb` ships
  `ggml-rpc-server` (bundled as `libggmlrpcserver.so`) and a `llama-server` that
  accepts `--rpc`. The Rust supervisor runs either role, the pure mesh domain
  lives in `mesh/`, and the Mesh tab advertises/browses `_hexamesh._tcp` (NSD),
  pings peers for latency, and hands the reachable ones to a starting node as
  `--rpc` (verified offloading to a PC `ggml-rpc-server`). **Not built yet:** the
  worker role is not selectable from the UI, `planLayers`/`--tensor-split` is
  unused, and per-peer logs are missing.

## Build & verify

```bash
./gradlew :app:compileDebugKotlin -PskipRustBuild --offline   # fast feedback
./gradlew :app:testDebugUnitTest -PskipRustBuild --offline    # must stay green
./gradlew :app:assembleDebug -PskipRustBuild                  # full debug APK
```

- `-PskipRustBuild` reuses `app/build/jniLibs/`; drop it to rebuild the Rust
  core. `-PupdateRustDeps` runs `cargo update`.
- `llama.cpp/` is an ignored, unpinned checkout at upstream `master` (excluded
  from editor search); its `AGENTS.md` is unrelated to this project. `build_llama.sh`
  and CI always pull the tip, so the build is not reproducible across dates.
- **Feature build flags:** the Snapdragon preset ships `GGML_RPC=OFF` and
  `LLAMA_SUBPROCESS=OFF` — upstream disables the latter on Android as
  "sandbox-unfriendly". `scripts/enable_features.py` patches the generated
  `llama.cpp/CMakeUserPresets.json` to turn both on; `build_llama.sh` runs it
  after pulling and CI runs it before `build.py`. The app bundles
  `bin/ggml-rpc-server` as `libggmlrpcserver.so`; `lib/*.so` (including
  `libggml-rpc.so`) is copied automatically. Router mode **requires**
  `LLAMA_SUBPROCESS=ON`: without it the server starts, then exits with
  "subprocess is not enabled on this build".
- CI (`.github/workflows/build.yml`) builds the APK, uploads artifacts, and
  releases on `v*` tags.
- **Definition of done:** `testDebugUnitTest` green and `assembleDebug`
  succeeds; verify UI changes on a device where possible.

## Conventions

- **Commits:** Conventional Commits, lowercase scope, imperative subject, no
  trailing period, no body paragraphs. E.g. `fix(ui): drop stale transfer
  errors on launch`.
- **Comments:** only when the code can't explain itself. Keep names and
  structure self-explanatory instead of narrating what a line does.
- **Pure logic + tests:** keep `controlsEnabled`, `formatSize`, `isLoadableModel`,
  and download/import parsing as pure functions with JVM unit tests. Preserve
  that separation.
- **No heavy deps, no icon libraries.** Icons are self-contained vector
  drawables (`res/drawable/ic_*.xml`). Do **not** add
  `material-icons-extended`; `material-icons-core` is not on the classpath.
- **Version bumps** update `app/build.gradle.kts` (`versionCode` and
  `versionName`), `hexa_mesh_core/Cargo.toml`, **and**
  `hexa_mesh_core/Cargo.lock`, then tag an annotated `vX.Y.Z`.

## Roadmap

End state: a bottom `NavigationBar` with **Manage / Mesh / Logs / Settings**
plus a persistent Start/Stop action bar above it. Do not reorder phases without
reason.

| Phase | Goal | Status |
| --- | --- | --- |
| 0 | State refactor: ViewModels, `nodeScreen(state, actions)`, no process-global node state, bound-service re-sync | ✅ landed |
| 1 | Tab shell (Manage + placeholders) | ✅ landed |
| 2 | Settings: persisted port + mDNS/NSD + fallback IPs | port landed; NSD + fallback IPs moved to phase 4 |
| 2.5 | Custom launch args: editable defaults, locked required flags | ✅ landed |
| 3 | Logs tab (local diagnostics only) | ✅ landed |
| 4 | Mesh tab + per-peer logs | 🚧 coordinator offloads to peers; worker role + logs pending |
| 5 | Router-only: one listener, every model, HF cache | ✅ landed and device-verified |

Phase notes (the load-bearing bits):

- **2 —** persist settings (**SharedPreferences**, to stay dependency-free and
  offline-buildable); thread the port through `ServerConfig.port` and delete the
  hardcoded `8080`; discover via `NsdManager` (`CHANGE_WIFI_MULTICAST_STATE` is
  already declared); keep a persisted fallback-IP list; keep the "plain HTTP,
  trusted LAN only" warning. Port landed: `platform/ServerSettings.kt` owns the
  value, `node/NodeSettings.kt` is the JVM-testable seam, and the Settings tab
  saves a change that the node reads on its next start. NSD discovery and the
  fallback-IP list are deferred to Phase 4: until RPC meshing exists they have no
  consumer, and building them now would freeze the peer protocol before
  `PeerNode` is defined.
- **3 —** diagnostics on the Logs tab. Logs: tail the supervisor's
  `cacheDir/llama-server.log` (written by `engine.rs` `spawn_pump`). tok/s: poll
  `127.0.0.1:<port>/slots` and diff `n_decoded` between samples — needs `--slots`
  (in the defaults) and the API key, and is the only live source (`/metrics`'
  rate gauge only updates at slot reset). Thermal: hottest CPU/GPU zone from
  `/sys/class/thermal/thermal_zone*`. Memory: `MemAvailable / MemTotal` from
  `/proc/meminfo` (the server's own RSS excludes GPU-offloaded weights, so it is
  not a useful number). Terminal view is a `LazyColumn` over a ~2000-line capped
  buffer. Per-peer logs are out of scope here.
- **4 —** the RPC backend is built and bundled: `scripts/enable_features.py`
  forces `GGML_RPC=ON`, `ggml-rpc-server` ships as `libggmlrpcserver.so`, and the
  supervisor runs either role (`ServerRole.SERVER`/`RPC`, with a TCP liveness
  probe and a separate `rpc-server.log`). Discovery and meshing hand-off landed:
  `NsdDiscovery` browses `_hexamesh._tcp`, `NsdAdvertiser` publishes this device
  (Discoverable toggle), peers publish `free_mem`/`total_mem` TXT, and
  `MeshViewModel` pings each peer over TCP (`tcpLatencyMs`) so `--rpc` selection
  uses `isReachable` + `rankPeers`. `usePeers` gates injection, and a start
  carries the endpoints through `MeshService` → `ServerConfig.rpcServers` → Rust
  `--rpc`. Verified over the LAN against a PC `ggml-rpc-server`: the peer's GPU
  took the weights and served generation. **Next:** a worker-role toggle so this
  phone can run `ggml-rpc-server`, `--tensor-split` from `planLayers`, then the
  Canvas view and per-peer logs. Prefer one merged, timestamped, peer-filterable
  log stream.
- **5 —** one listener, many models. `llama-server` **without `-m`** is a
  *router*: it spawns one child per model on a loopback port and proxies the
  public port, exposing `GET/POST /models`, `/models/load`, `/models/unload`,
  `/models/sse`, and `?reload=1`. Three sources merge in
  `server_models::load_models()` — the HF cache, `--models-dir`, and an INI
  preset — so keep the app's flat `models/` dir on `--models-dir` for imports,
  `adb push`, and arbitrary URLs, and add an HF cache for HF downloads.
  `unset_reserved_args()` strips api-key/model/alias and rewrites host/port per
  child but **not** `--rpc`/`--device`, so mesh offloading is inherited; the
  router enforces the API key and children listen only on loopback. Child
  stdout/stderr is forwarded into the router log with a `[port]` prefix, so
  `llama-server.log` still captures everything (port-tagged, not model-tagged).
  Steps 1–3 landed: the app is **router-only** — it always launches
  `llama-server` with `--models-dir <models dir>` and never `-m`, and there is no
  per-model selection or mode toggle. `LLAMA_CACHE` is pinned to
  `<externalFilesDir>/hf-cache` (`ModelRepository.hfCacheDir()` →
  `ServerConfig.llamaCacheDir` → Rust), because Android has no usable `$HOME` and
  `hf_cache::get_cache_directory()` would otherwise resolve to an unwritable
  path. The Manage list is a plain library — `Model.id` (filename minus `.gguf`,
  the router's `model` value), size and delete, no radio — and `NodeState`
  carries only `serverUrl`. Shutdown signals the child's **process group**
  (`kill(-pid)`, valid because `command.rs` `setsid`s the child and router-spawned
  instances inherit that group), so model grandchildren can no longer orphan a
  SIGKILLed router. Still open: download ownership (`POST /models` vs
  `ModelDownloadWorker`); `SlotsClient` needs `?model=`; and router clients must
  send a valid model id (a hardcoded or missing one gets a 400). The single-model
  escape hatch is typing `-m <path>` in Settings (`-m` is unlocked), though the
  Manage status still reads "all models" because the UI does not track a forced
  mode. `ModelsClient.reload()` (`GET /models?reload=1`) runs from
  `TransferViewModel` after an import/download/delete and on app resume, so a
  model added to the folder while the router runs appears without a restart.
  **Verified on device** (Poco F7/Adreno, `LLAMA_SUBPROCESS=ON`):
  the router lists both models from the app's `models/` dir (a loose
  `mmproj-*.gguf` is ignored), `/v1/models` reports them `unloaded`, an on-demand
  chat loads a second `libllamaserver.so` (topology app → router → instance) and
  returns a completion, and Stop takes the whole group down cleanly. The reload
  was checked the same way: a pushed `.gguf` showed up in `/v1/models` after an
  app resume and disappeared again after removal.

## Gotchas

- **Compose state loss:** a raw `when(tab)` disposes the previous tab; use
  `SaveableStateHolder` to keep per-tab scroll position.
- **Polling lifecycle:** tie all polling to the composed tab (`LaunchedEffect`);
  never poll in the background.
- **`--device` excludes RPC peers:** llama.cpp uses only the devices named by
  `--device`, and RPC peers register separately. The supervisor omits `--device`
  whenever `rpcServers` is set, so the local GPU and the peers are all eligible.
- **OEM battery screens:** MIUI et al. commit the whitelist change *after* the
  activity regains focus, so a single `onResume` read is racy. Keep the retry
  logic in `MainActivity.scheduleBatteryRefresh()`.
- **NSD empty TXT:** a service record with no TXT attributes trips a framework
  bug (`NsdService: Key cannot be empty`). The advertiser always publishes
  `free_mem`/`total_mem`, so the record is never empty.
- **Icons:** self-contained drawables only.
- **Model list parity:** `isLoadableModel` mirrors llama.cpp's
  `load_from_models_dir` for top-level files — lowercase `.gguf` only, skipping
  `mmproj` and `mtp-`/`dspark-`/`dflash-` sidecars — and `Model.id` is the
  filename minus `.gguf`, i.e. the router's `model` value. Keep both in step with
  upstream or the list will advertise ids that 404.

## Known debt

- The worker role is still unreachable: `ServerRole.RPC` exists and the bundled
  `ggml-rpc-server` runs, but nothing in the UI starts it.
- `planLayers` has no caller, so no `--tensor-split`; llama.cpp splits the model
  across the local device and the `--rpc` peers on its own.
- The latency probe opens a TCP connection to the peer's RPC port, which is a
  single-client server; while llama.cpp holds the connection a probe only lands
  in the accept backlog. It has not disturbed a clean session, but a dedicated
  health port would be sturdier.
- Peers only populate while the Mesh tab has been open (discovery and pings live
  there), so starting the node from Manage right after launch meshes with
  nothing.
- In router mode `SlotsClient` polls `/slots` without `?model=`, so tok/s stays
  blank. `/models?reload=1` is wired (`ModelsClient`), but `/models/load` is not:
  the app never loads a model over HTTP — clients pick one per request.
- `bridge/Engine.kt` only exposes `start/stop/pollStatus` — no logs/metrics channel.

## Decisions (do not re-litigate)

1. **Navigation:** `rememberSaveable` tab enum + `SaveableStateHolder`. No
   Navigation Compose.
2. **Node state:** `NodeController` owns the `StateFlow`; `MeshService.onBind()`
   publishes it through a `LocalBinder`, and the UI binds via `NodeViewModel`.
   Never reintroduce a process-global singleton.
3. **Port changes:** part of the launch args; saved immediately and applied on
   the next start, so a running server is never disturbed.
4. **Terminal logs:** tail the supervisor's `llama-server.log`. A JNI
   `pollLogs()` ring buffer was rejected.
5. **Mesh model:** the prompt-receiving node is the Coordinator; it queries
   mDNS peers and ranks them by available VRAM/RAM and LAN latency (mDNS ping).
   Discovery uses `_hexamesh._tcp`; peers publish `free_mem`/`total_mem` TXT
   attributes (a `/proc/meminfo` snapshot), and a node filters its own
   advertisement by the registered instance name. Manual fallback peers never
   expire; discovered ones lapse after `PEER_TTL_MS`.
6. **Launch args:** one text field of editable flags, the single source of truth,
   port included (`--port`, default 8080). The app injects `--host`, `--api-key`,
   `--device`, `--rpc` and `--models-dir` and strips those (plus their values) if
   the user types them, so the app-owned values always win. `-m`/`--model` are
   **not** locked: typing one pins a single model, which makes `--models-dir`
   inert and serves just that file (llama.cpp is single-model whenever
   `model.path` is set). Rust builds only those required args; everything else —
   `-m` included — arrives via `ServerConfig.extraArgs` (newline-joined). The app
   parses `--port` back out (`parseLaunchPort`) to drive the health probe and the
   LAN URL. `--rpc` is app-owned too: the app strips a user-typed one and injects
   the reachable mesh peers (`ServerConfig.rpcServers`) only when the node starts.

Open questions (add new ones below instead of reopening the above):

- **Phase 5:** does the router supersede decision #6 (the app always injecting
  `-m`)? Where do downloads live — the router's `POST /models` or
  `ModelDownloadWorker`? Does `LLAMA_CACHE` stay on external storage
  (symlink-degraded, adb-pushable) or move to internal (dedup, but space-limited)?
