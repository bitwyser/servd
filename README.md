# servd

A portable **local-network server tool**. Run it on a laptop or (soon) a phone and it turns
that device into a small hub other devices on the same Wi-Fi / hotspot can reach - an encrypted
dashboard with real-time chat, a live roster of who's connected, and drag-n-drop file sharing.
FTP and SSH servers are on the roadmap.

Everything is served over **HTTPS/WSS** with a self-signed certificate (there's no public
domain on a LAN, so you verify the connection by its **fingerprint** instead of a CA).

> **Status:** early development. Phases 0-3 are done and usable on **desktop**
> (Windows / Linux / macOS). The Android app currently only proves the shared core runs
> (a "hello" screen) - it does **not** host servers yet. See [Roadmap](#roadmap).

---

## What works today

- **HTTPS dashboard** bound to your LAN/hotspot IP on port **8443**
- **Real-time chat** between everyone connected (over WSS)
- **Connected-device roster** - see who's on the hub, live, with join/leave
- **Drag-n-drop file sharing** - drop a file, it appears on every device instantly, download intact
- **Auto-discovery (mDNS)** - the hub advertises itself; `servd discover` finds hubs without typing an IP
- **Host-only admin panel** - an Admin tab (served on `127.0.0.1` only) showing server status; toggles for SSH/FTP arrive in later phases
- **Self-signed TLS** with a printed **SHA-256 fingerprint** you can verify
- **`/status`** JSON endpoint (version, address, fingerprint)
- **CLI** to start it and auto-open the dashboard

---

## Tech stack

Everything below is **in use today** unless marked _planned_. Versions are pinned in
[`gradle/libs.versions.toml`](gradle/libs.versions.toml).

### Language & build
| | | |
|---|---|---|
| **Kotlin** | `2.1.0` | Kotlin **Multiplatform** - one shared `core` compiled for both desktop JVM and Android |
| **Gradle** | `8.11.1` | Build tool (Kotlin DSL + version catalog); wrapper committed, no separate install |
| **JDK** | 17 target | Compiles to Java 17 bytecode (built with the installed JDK 21) |

### Server & networking
| | | |
|---|---|---|
| **Ktor** | `3.0.3` | HTTP/S + WebSocket server framework |
| ├ `ktor-server-core` | | Routing, application, static resource serving |
| ├ `ktor-server-netty` | | **Netty** engine - serves HTTPS on desktop (Ktor's CIO engine can't) |
| ├ `ktor-server-websockets` | | WSS transport for chat + presence |
| └ `ktor-network-tls-certificates` | | Generates the self-signed keystore |
| **Netty** | via Ktor | Underlying async I/O engine on desktop |
| **JSSE / JKS** | JDK | TLS + the persisted `keystore.jks`; SHA-256 fingerprint via `java.security` |
| **`java.net.NetworkInterface`** | JDK | Cross-platform LAN/hotspot address detection |

### Data & concurrency
| | | |
|---|---|---|
| **kotlinx.serialization** | `1.7.3` | JSON WebSocket protocol (`"type"`-discriminated sealed messages) |
| **kotlinx.coroutines** | `1.9.0` | Async server + `Mutex`-guarded broadcast in the chat hub |

### Frontend (served dashboard)
| | | |
|---|---|---|
| **Plain HTML/CSS/JS** | - | No framework; single file in `core/.../resources/webui/` |
| **WebSocket API + Fetch API** | browser | Live chat/roster and `/status` |
| **System font stacks** | - | Monospace (machine data) + sans (chat); no webfont downloads |

### Android
| | | |
|---|---|---|
| **Android Gradle Plugin** | `8.7.3` | Android build |
| **SDK levels** | compile `35` · min `24` · target `35` | |
| **Android framework** | - | Plain `Activity` + `TextView` (no Compose / AppCompat yet) |

### Logging
| | | |
|---|---|---|
| **SLF4J** + **slf4j-simple** | `2.0.16` | Ktor/server logging on desktop |

### Testing & verification
| | | |
|---|---|---|
| **kotlin-test** | - | Unit tests for pure logic (`LanAddressSelector`, `PeerRegistry`, protocol) |
| **`java.net.http.WebSocket`** | JDK | Dependency-free 2-client WSS integration harness |
| **Android emulator + adb** | SDK | Phase 0.5 on-device verification (Pixel_5 AVD) |
| **curl** | - | HTTPS/`/status` smoke checks |

### Protocols
- **In use:** HTTPS, WSS, TLS (self-signed)
- **Planned:** FTP(S), SSH, mDNS/DNS-SD (auto-discovery)

### Not used (deliberately)
Node.js / Electron (chose a shared Kotlin/JVM core instead), Compose Multiplatform (the UI is
the served web dashboard), and - for now - any external database (state is in-memory).

---

## Requirements

- **JDK 17+** (JDK 21 is fine - the build targets Java 17 bytecode). No separate Gradle
  install needed; the repo ships a Gradle wrapper.
- **Internet on first run** (to download Gradle + dependencies).
- For the Android app: the **Android SDK** (with `local.properties` pointing at it, e.g.
  `sdk.dir=C:\\Android`).

---

## Quick start (desktop)

From the repo root:

```bash
run.bat
```

(or `pwsh run.ps1` / `./run.ps1` on PowerShell). This builds and starts servd, binding to your
detected LAN address and opening the dashboard in your browser.

You'll see something like:

```
servd v0.1.0 - local-network server tool

serving : https://10.205.210.12:8443
cert    : self-signed, SHA-256 fingerprint:
          12:3A:07:38:...:F5:CF
keystore: C:\Users\you\.servd\keystore.jks

Open that URL on any device on this network...
Press Ctrl+C to stop.
```

Your browser will warn that the certificate isn't trusted - that's expected for a self-signed
LAN cert. Confirm the fingerprint shown in the page matches the one printed above, then proceed.

> **Why `run.bat` / `run.ps1` instead of `gradlew` directly?** On this machine JDK 21's NIO
> needs its temp socket in a directory that accepts it; the wrappers point `TMP`/`TEMP` at a
> repo-local `.sock-tmp/` folder before invoking Gradle. See
> [Troubleshooting](#troubleshooting).

### Connect another device

1. Make sure the other device is on the **same Wi-Fi** (or connected to this machine's
   **hotspot**).
2. Open **`https://<the-address-shown>:8443`** (e.g. `https://10.205.210.12:8443`) in its
   browser.
3. Accept the certificate warning, set a display name, and start chatting. Each connected
   device appears in the **Devices** roster. Switch to the **Files** tab to drag-n-drop a file
   to everyone.

### CLI options

Pass servd flags through the wrapper via Gradle's `--args`:

```bash
run.bat :desktopHost:run "--args=--port 9443 --no-open"
```

| Flag | Meaning | Default |
|---|---|---|
| `--port N` | HTTPS port | `8443` |
| `--host IP` | Bind address | detected LAN IP (else `127.0.0.1`) |
| `--dir PATH` | Where the keystore lives | `~/.servd` |
| `--no-open` | Don't auto-open the browser | (opens) |

Find running hubs on the network (no IP needed):

```bash
run.bat :desktopHost:run "--args=discover"
```

Run other Gradle tasks the same way, e.g. tests: `run.bat :core:desktopTest`.

---

## Android app (preview)

The Android module currently just shows a "hello" screen proving the shared core runs on
Android (it detects the phone's LAN address). It does **not** run the servers yet - that's
Phase 8.

Build the debug APK:

```bash
run.bat :androidApp:assembleDebug
```

The APK lands in `androidApp/build/outputs/apk/debug/`. Install it with
`adb install -r <that-apk>`.

---

## Project layout

```
servd/
├─ core/         KMP library - the engine (shared by desktop + Android)
│   ├─ commonMain    protocol models, PeerRegistry, LAN-address selection, config
│   ├─ jvmSharedMain the Ktor server, TLS, chat hub (shared by both JVM targets)
│   ├─ desktopMain   desktop entrypoints
│   └─ androidMain   Android glue
│   └─ resources/webui   the served dashboard (HTML/CSS/JS)
├─ desktopHost/  JVM app - the CLI that starts the server (uses the Netty engine)
├─ androidApp/   Android app shell (hello world for now)
├─ docs/         scaffold plan, phased implementation plan, dashboard mockup
├─ run.bat / run.ps1   run Gradle with the TMP/TEMP workaround
└─ gradlew*      Gradle wrapper (no separate Gradle install needed)
```

Notable pieces: server engine is **Ktor** (Netty on desktop; Ktor CIO can't serve HTTPS).
Self-signed cert handling is in `core/.../tls/ServdCertificates.kt`; chat/presence in
`core/.../chat/`.

---

## Troubleshooting

**`Unable to establish loopback connection` when building/running.** JDK 21's NIO uses an
AF_UNIX socket for its internal pipe, created in the OS temp dir. Some Windows temp folders
(e.g. `%LOCALAPPDATA%\Temp` under certain AV/EDR setups) reject socket files, which breaks
Gradle and the server. The fix - already built into `run.bat` / `run.ps1` - is to point
`TMP`/`TEMP` at a directory that accepts socket files (the repo-local `.sock-tmp/`). If you
invoke `gradlew` directly, set them yourself first:

```bash
set TMP=%CD%\.sock-tmp && set TEMP=%CD%\.sock-tmp && gradlew :desktopHost:run
```

**Browser says "Not secure / certificate invalid."** Expected - servd uses a self-signed cert
because a LAN has no certificate authority. Verify the fingerprint in the page matches the one
the CLI printed, then proceed.

**No LAN address found.** Connect to Wi-Fi or start a hotspot, then restart servd. Without one
it binds to `127.0.0.1` (reachable only from the same machine).

---

## Roadmap

Built in self-contained phases; each is independently runnable.

| Phase | | Status |
|---|---|---|
| 0 | Project skeleton + LAN IP detection | ✅ |
| 0.5 | Hello world on desktop + Android (shared core) | ✅ |
| 1 | HTTPS server, self-signed TLS, dashboard shell, CLI | ✅ |
| 2 | Real-time chat + connected-device roster | ✅ |
| 3 | Drag-n-drop file sharing | ✅ |
| 4 | mDNS auto-discovery of hubs | ✅ |
| 5 | Service manager + host-only admin panel | ✅ |
| 6 | Optional SSH server | ▫️ next |
| 7 | Optional FTPS server | ▫️ |
| 8 | Android host app (foreground service, real UI) | ▫️ |
| 9 | Packaging (installers + APK) | ▫️ |

See [`docs/phased-implementation.md`](docs/phased-implementation.md) for details.

---

## License

MIT - see [LICENSE](LICENSE).
