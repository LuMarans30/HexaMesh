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

- Release `0.7.0` (`versionCode 6`); server listens on **8080** (hardcoded — Phase 2).
- Backends: Adreno GPU (OpenCL) today; the Hexagon NPU path is **experimental
  and produces corrupted output**.
- **Not built yet:** llama.cpp RPC mesh / peer discovery. No NSD/mDNS code
  exists; the only trace is the multicast lock in `platform/LockManager.kt`.
  The Mesh tab and per-peer logs are blocked on this backend.

## Build & verify

```bash
./gradlew :app:compileDebugKotlin -PskipRustBuild --offline   # fast feedback
./gradlew :app:testDebugUnitTest -PskipRustBuild --offline    # must stay green
./gradlew :app:assembleDebug -PskipRustBuild                  # full debug APK
```

- `-PskipRustBuild` reuses `app/build/jniLibs/`; drop it to rebuild the Rust
  core. `-PupdateRustDeps` runs `cargo update`.
- `llama.cpp/` is a submodule (excluded from editor search); its `AGENTS.md` is
  unrelated to this project.
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
| 3 | Logs tab (local diagnostics only) | in progress |
| 4 | Mesh tab + per-peer logs | blocked on RPC backend |

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
- **3 —** build the data sources first: tail the supervisor's
  `cacheDir/llama-server.log` (already written by `engine.rs` `spawn_pump`, so no
  core change needed), poll `127.0.0.1:<port>/slots` for tok/s, read memory
  (`ActivityManager`/`Debug.MemoryInfo`/`/proc/<pid>/status`) and thermal
  (`PowerManager` + `/sys/class/thermal/*`). Terminal view is a `LazyColumn`
  over a ~2000-line capped buffer. Per-peer logs are out of scope here.
- **4 —** blocked until llama.cpp RPC meshing exists. Define the data models
  (`PeerNode`, `LayerAssignment`, `PeerStats`) before any UI; render with
  Compose `Canvas`; prefer one merged, timestamped, peer-filterable log stream.

## Gotchas

- **Compose state loss:** a raw `when(tab)` disposes the previous tab; use
  `SaveableStateHolder` to keep per-tab scroll position.
- **Polling lifecycle:** tie all polling to the composed tab (`LaunchedEffect`);
  never poll in the background.
- **OEM battery screens:** MIUI et al. commit the whitelist change *after* the
  activity regains focus, so a single `onResume` read is racy. Keep the retry
  logic in `MainActivity.scheduleBatteryRefresh()`.
- **Icons:** self-contained drawables only.

## Known debt

- Phase 2 is incomplete: no `NsdManager` discovery and no persisted fallback-IP
  list yet.
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
6. **Launch args:** one text field of editable flags, the single source of truth,
   port included (`--port`, default 8080). The app always injects `-m`, `--host`,
   `--api-key`, `--device` and strips those (plus their values) if the user types
   them, so the app-owned values always win. Rust builds only those required args;
   everything else — port included — arrives via `ServerConfig.extraArgs`
   (newline-joined). The app parses `--port` back out (`parseLaunchPort`) to drive
   the health probe and the LAN URL.

Open questions: none. Add new ones below instead of reopening the above.
