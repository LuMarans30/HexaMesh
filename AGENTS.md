# AGENTS.md

Agent guidance for **HexaMesh**. Treat this as living documentation: if a fact
here is stale, fix it; do not leave dead plans behind.

## Working agreements

- **Do not commit on your own.** Make the edits, run the checks, and leave the
  changes for the human to review. No `git add` / `commit` / `amend` / `push`
  unless explicitly asked.
- **Small and reviewable.** Prefer focused changes over sweeping rewrites.
- **You may install and screenshot.** Put the debug build on a connected device
  with `./gradlew :app:installDebug -PskipRustBuild` and capture the UI while
  debugging with `adb exec-out screencap -p > /tmp/hexamesh.png`.
- **Never uninstall or clear app data.** `adb uninstall com.lumarans30.hexamesh`
  and `adb shell pm clear com.lumarans30.hexamesh` wipe internal storage and
  destroy the user's imported models. Reinstall in place instead.

## Project overview

HexaMesh runs llama.cpp's `llama-server` inside an Android foreground service
and exposes an OpenAI-compatible HTTP API on the LAN. A Rust supervisor spawns
and watches the server over JNI.

- Release `0.8.0` (`versionCode 7`); the server defaults to **8080** and the port
  is editable through the launch args.
- Backends: Adreno GPU (OpenCL) today; the Hexagon NPU path is **experimental
  and produces corrupted output**.
- **RPC mesh is partly built:** `scripts/enable_rpc.py` forces `GGML_RPC=ON` in
  the Snapdragon build (run by `build_llama.sh` and CI), so `pkg-adb` ships
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
- **RPC build:** the Snapdragon preset ships `GGML_RPC=OFF`; `scripts/enable_rpc.py`
  patches the generated `llama.cpp/CMakeUserPresets.json` to turn it on.
  `build_llama.sh` runs it after pulling and CI runs it before `build.py`. The
  app bundles `bin/ggml-rpc-server` as `libggmlrpcserver.so`; `lib/*.so`
  (including `libggml-rpc.so`) is copied automatically.
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
- **Pure logic + tests:** keep `canLoad`, `canDelete`, `selectionEnabled`,
  `formatSize`, and download/import parsing as pure functions with JVM unit
  tests. Preserve that separation.
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
- **4 —** the RPC backend is built and bundled: `scripts/enable_rpc.py` forces
  `GGML_RPC=ON`, `ggml-rpc-server` ships as `libggmlrpcserver.so`, and the
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
   port included (`--port`, default 8080). The app always injects `-m`, `--host`,
   `--api-key`, `--device` and strips those (plus their values) if the user types
   them, so the app-owned values always win. Rust builds only those required args;
   everything else — port included — arrives via `ServerConfig.extraArgs`
   (newline-joined). The app parses `--port` back out (`parseLaunchPort`) to drive
   the health probe and the LAN URL. `--rpc` is app-owned too: the app strips a
   user-typed one and injects the reachable mesh peers (`ServerConfig.rpcServers`)
   only when the node starts.

Open questions: none. Add new ones below instead of reopening the above.
