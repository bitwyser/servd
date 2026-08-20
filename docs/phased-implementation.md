# servd - Phased Implementation

Each phase is **self-complete**: the project builds, runs, and delivers a working, testable
increment. The "Verify" line is the acceptance check; passing it = the commit point.
Order is **desktop-first** (fast feedback, no emulator); the Android app module is proven early
(Phase 0.5) but its real host shell is fleshed out last (Phase 8).

**Baseline (locked):** JDK 17 (LTS) + latest stable Kotlin / Ktor. Default ports - HTTPS `8443`,
SSH `2222`, FTPS `2121` (all non-privileged). Gradle Kotlin DSL + version catalog.

---

### Phase 0 - Project skeleton & LAN IP detection
**Build:** Gradle KMP setup (version catalog, `settings.gradle.kts`), `core` module (KMP lib) +
`desktopHost` module (JVM app). `NetworkInterface`-based IP detection in `core` (pick by
address, cross-platform). `desktopHost` `main()` prints `servd v0.1` + detected LAN address(es).
**Test:** unit test for the IP-selection logic.
**Verify:** `./gradlew :desktopHost:run` prints the machine's LAN IP on Windows + Linux.
**Commit:** `chore: project skeleton + LAN IP detection`

### Phase 0.5 - Hello world on both platforms
**Build:** add the `androidApp` module (minimal, targets set up), sharing the same `core`. Android
launches a single screen showing `servd - hello` + the LAN IP pulled from shared `core` (proving
`core` runs on the Android JVM target, and the Android build/install pipeline works). Desktop
already prints the same from Phase 0.
**Verify:** APK installs on a device/emulator and shows the hello screen with the IP from shared
`core`; `:desktopHost:run` still works. Both platforms exercise the same shared code.
**Commit:** `chore: android app module - hello world on shared core`

### Phase 1 - HTTPS server + static dashboard + CLI
**Build:** Ktor (CIO) server in `core`, bound to LAN IP. Self-signed cert generated on first run,
keystore persisted to app-data dir, SHA-256 fingerprint printed. Serves a static "servd is
running" page (start of `webui/`). CLI (`servd start [--port] [--no-open]`) auto-opens the browser.
**Verify:** run it, open `https://<lan-ip>:8443` from another device, see the page (accept the
one-time self-signed warning); fingerprint in the page matches the CLI output.
**Commit:** `feat: https server, self-signed TLS, served dashboard shell`

### Phase 2 - Chat + presence roster
**Build:** Ktor `/ws` WebSocket, `ChatHub` (join/leave presence + message broadcast). Dashboard
wired to real data: live device roster + real-time chat (replaces the mockup's static data).
**Test:** unit test for `ChatHub` join/leave/broadcast.
**Verify:** two devices/tabs connect → each appears in the other's roster → messages arrive live
both ways over WSS.
**Commit:** `feat: real-time chat + connected-device roster`

### Phase 3 - File sharing (drag-n-drop)
**Build:** Ktor routes - `POST /files` (multipart), `GET /files` (list), `GET /files/{id}`
(download). Dashboard Files tab: drag-n-drop upload, shared-file list, download.
**Verify:** drag a file on device A → it appears in the list on device B → downloads intact over
HTTPS.
**Commit:** `feat: encrypted file sharing (upload / list / download)`

### Phase 4 - Auto-discovery (mDNS)
**Build:** advertise `_servd._tcp` via JmDNS (desktop). Small "discovered hubs" list/endpoint so a
device can find the hub without typing an IP.
**Verify:** a second machine on the LAN discovers the running hub by name (e.g. via a browser/CLI
mDNS lookup) - no IP typed.
**Commit:** `feat: mDNS hub advertisement + discovery`

### Phase 5 - ServiceManager + admin panel (loopback-gated)
**Build:** `Service` interface + `ServiceManager`; refactor HTTP into a `Service`. Admin routes
gated to `127.0.0.1`. Admin section in dashboard: host identity, cert fingerprint, service
status + toggles (HTTP shown now; SSH/FTP slots ready).
**Verify:** admin panel loads on `localhost` but is **not** reachable from another device on the
LAN; toggles reflect real service state.
**Commit:** `feat: service manager + host-only admin panel`

### Phase 6 - SSH service (optional module)
**Build:** Apache MINA SSHD behind `Service`, credentialed (key + password), toggleable from admin.
Generated/settable credentials shown host-side.
**Verify:** enable SSH from admin → connect from an `ssh` client on another device with the
credentials → session works; disable → connection refused.
**Commit:** `feat: optional SSH server`

### Phase 7 - FTPS service (optional module)
**Build:** Apache FtpServer with FTPS (TLS), credentialed, toggleable from admin.
**Verify:** enable FTPS → connect with an FTPS client (e.g. FileZilla, explicit TLS) using the
credentials → list/transfer works; disable → refused.
**Commit:** `feat: optional FTPS server`

### Phase 8 - Android host shell (flesh out)
**Build:** grow the `androidApp` from Phase 0.5 into the real host - foreground service hosting
`core`, one control screen (Start/Stop, show URL/IP + fingerprint, Open dashboard), required
permissions, Android `NsdManager` for mDNS. **Resolve the Android HTTPS engine** (Ktor CIO can't
serve HTTPS, and Netty-on-Android is heavy) - candidates: Netty-on-Android, a JSSE TLS-terminating
front socket in front of a plaintext engine, or NanoHTTPD-SSL. `ServdServer` is already generic
over the engine factory, so only the engine + connector differ per platform.
**Verify:** install APK → tap Start → phone hotspot becomes the hub → another device opens the
same dashboard, chats, and shares files against the phone.
**Commit:** `feat: android host app (foreground service + control screen)`

### Phase 9 - Packaging
**Build:** `jpackage` config → Windows `.exe`/`.msi` + Linux `.deb`; Android release APK.
**Verify:** install the Windows and Linux artifacts on a clean machine (no JVM preinstalled) → app
runs.
**Commit:** `build: jpackage installers (Windows + Linux) + release APK`

---

## Rules for every phase
- Must compile and run before commit - no broken intermediate states.
- Pure logic (IP selection, `ChatHub`, service state) gets unit tests; networked features get the
  manual "Verify" integration check.
- Security invariants hold from the phase they're introduced (TLS from P1; admin loopback-gate
  from P5; credentialed SSH/FTP from P6/P7).
- One phase = one focused commit. Push is the user's call.
