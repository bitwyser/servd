# servd

A portable **local-network server tool**. Run it on a laptop **or an Android phone** and it turns
that device into a small hub other devices on the same Wi-Fi / hotspot can reach - an encrypted
dashboard with real-time chat, a live roster of who's connected, drag-n-drop file sharing, and
built-in SSH/SFTP and FTPS servers.

Everything is served over **HTTPS/WSS** with a self-signed certificate (there's no public
domain on a LAN, so you verify the connection by its **fingerprint** instead of a CA).

> **Status:** early development. Phases 0-9 are done. servd runs as a full hub on **desktop**
> (Windows / Linux / macOS) **and Android** - a phone hosts the exact same stack (HTTPS
> dashboard + chat + files, plus SSH/SFTP and FTPS), not a lesser version. See [Roadmap](#roadmap).

---

## What works today

- **HTTPS dashboard** bound to your LAN/hotspot IP on port **8443**, with light/dark themes
- **Real-time chat** between everyone connected (over WSS) - multi-line, clear-for-everyone
- **Connected-device roster** - see who's on the hub, live, with join/leave
- **Drag-n-drop file sharing** - drop a file, it appears on every device instantly; download,
  delete one, or clear all
- **Scan-to-join QR** of the hub URL, so a phone can open it without typing the IP
- **SSH/SFTP server** - toggle from the admin panel; credentialed SFTP jailed to the shared files (no OS shell)
- **FTPS server** - explicit-TLS FTP, same credential, jailed to the shared files
- **Auto-discovery (mDNS)** - the hub advertises itself; `servd discover` finds hubs without typing an IP
- **Host-only admin panel** - a right-side rail (served on `127.0.0.1` only) with host info, cert
  fingerprint, and live server toggles
- **Self-signed TLS** with a printed, verifiable **SHA-256 fingerprint**
- **CLI** to start it, print the fingerprint, and auto-open the dashboard

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
| **Netty** | via Ktor | Underlying async I/O engine - HTTPS on desktop **and Android** |
| **JSSE / PKCS12** | JDK | TLS + the persisted keystore (PKCS12 - loads on desktop **and** Android, which has no JKS provider); SHA-256 fingerprint via `java.security` |
| **`java.net.NetworkInterface`** | JDK | Cross-platform LAN/hotspot address detection |
| **Apache MINA SSHD** | `2.13.2` | SSH / SFTP server (desktop **and Android**) |
| **Apache FtpServer** | `1.2.0` | FTPS server (desktop **and Android**) |
| **JmDNS** | `3.5.9` | mDNS/DNS-SD advertisement + discovery (desktop; Android uses `NsdManager`) |
| **ZXing** | `3.5.3` | QR-code generation (as SVG) for scan-to-join |

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

### Android (full host)
| | | |
|---|---|---|
| **Android Gradle Plugin** | `8.7.3` | Android build; `multiDexEnabled` for Netty |
| **SDK levels** | compile `35` · min `24` · target `35` | |
| **Foreground `Service`** | framework | `dataSync` service keeps the hub alive when backgrounded |
| **`NsdManager`** | framework | mDNS/DNS-SD advertisement of `_servd._tcp` on Android |
| **Android framework** | - | Native control screen: plain `Activity` + views (no Compose); the real UI is the served dashboard |

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
- **In use (desktop and Android):** HTTPS, WSS, TLS (self-signed), SSH/SFTP, FTPS, mDNS/DNS-SD

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
run.ps1
```

(or `run.bat`). This builds and starts servd, binding to your detected LAN address and opening
the dashboard in your browser. **Prefer `run.ps1` for a clean `Ctrl+C`** - `run.bat` works too,
but cmd.exe adds its own "Terminate batch job?" prompt after the server has already stopped.

You'll see something like:

```
servd v0.1.0 - local-network server tool

admin   : https://127.0.0.1:8443   (this machine only)
serving : https://10.205.210.12:8443   (share with other devices)
cert    : self-signed, SHA-256 fingerprint:
          12:3A:07:38:...:F5:CF
keystore: C:\Users\you\.servd\keystore.jks

Press Ctrl+C to stop.
```

The **admin** URL (`127.0.0.1`) is your own machine, with the server-toggle panel; the
**serving** URL is what you share. Your browser warns that the certificate isn't trusted -
expected for a self-signed LAN cert; confirm the fingerprint in the page matches the one printed
above, then proceed.

> **Why `run.ps1` / `run.bat` instead of `gradlew` directly?** JDK 21's NIO needs its temp
> socket in a directory that accepts it; the wrappers point `TMP`/`TEMP` at a repo-local
> `.sock-tmp/` folder first, then launch the server as a direct JVM process so `Ctrl+C` stops it
> cleanly. See [Troubleshooting](#troubleshooting).

### Connect another device

1. Make sure the other device is on the **same Wi-Fi** (or connected to this machine's
   **hotspot**).
2. Open **`https://<the-address-shown>:8443`** (e.g. `https://10.205.210.12:8443`) in its
   browser.
3. Accept the certificate warning, set a display name, and start chatting. Each connected
   device appears in the **Devices** roster. Switch to the **Files** tab to drag-n-drop a file
   to everyone - or just **scan the QR** in the Devices panel to open the hub on a phone.

### CLI options

The runner takes servd flags directly:

```bash
run.ps1 --port 9443 --no-open
```

| Flag | Meaning | Default |
|---|---|---|
| `--port N` | HTTPS port | `8443` |
| `--host IP` | Bind address | detected LAN IP (else `127.0.0.1`) |
| `--dir PATH` | Where the keystore lives | `~/.servd` |
| `--no-open` | Don't auto-open the browser | (opens) |

Find running hubs on the network (no IP needed):

```bash
run.ps1 discover
```

Gradle tasks pass through too, e.g. tests: `run.ps1 :core:desktopTest`.

---

## Android app

The Android app is a **full hub**, hosting the same stack as desktop - HTTPS dashboard + chat +
files, plus SSH/SFTP and FTPS. It's a thin native shell around the shared core: a foreground
service keeps the servers alive when the app is backgrounded, and a small control screen lets you
start/stop the hub and shows how to reach it.

Build the debug APK:

```bash
run.ps1 :androidApp:assembleDebug
```

The APK lands in `androidApp/build/outputs/apk/debug/`. Install it with
`adb install -r <that-apk>`.

Then, on the phone:

1. Connect the phone to the **same Wi-Fi** as the other devices (or turn on its **hotspot** and
   have them join it).
2. Open **servd** and tap **Start hub**. The control screen shows the **share URL**, a
   **scan-to-join QR**, the cert **fingerprint** to verify, and the **SSH/FTP credentials**.
3. On another device, open the share URL (or scan the QR) and accept the certificate warning -
   same dashboard as desktop. SSH and FTP start off; enable them from the admin dashboard, which
   the phone reaches on its own `127.0.0.1`.

The hub keeps running in the background with a notification (tap **Stop** there or in the app to
stop it).

---

## Packaging

Build your own distributable bundles - a self-contained desktop app and the Android APKs - into a
`dist/` folder.

### One-shot: `package.ps1`

```bash
.\package.ps1
```

This builds the desktop **app image** (zipped) and the Android **debug APK**, and - if you've set
up release signing (below) - the signed **release APK**, dropping them all in `dist/`:

```
dist/
├─ servd-0.1.0-windows-app-image.zip   self-contained desktop app (bundled JRE)
├─ servd-0.1.0-debug.apk               Android debug build
└─ servd-0.1.0-release.apk             Android release build (only if signed)
```

| Flag | Effect |
|---|---|
| _(none)_ | desktop app image + Android APK(s) |
| `-Installer` | also build the OS-native installer (`.msi`/`.dmg`/`.deb`) |
| `-Desktop` | desktop only |
| `-Android` | Android only |
| `-Clean` | wipe `dist/` first |

The individual Gradle tasks it wraps are below, if you'd rather run them directly.

### Desktop (bundled app, no Java needed on the target)

servd bundles its own JRE via **jpackage**, so an end user installs nothing else.

```bash
run.ps1 :desktopHost:jpackageImage
```

This writes a self-contained folder to `desktopHost/build/jpackage/servd/` (the launcher plus a
bundled runtime, ~166 MB). Run it directly - on Windows, `servd\servd.exe`; on macOS/Linux,
`servd/bin/servd`. Zip that folder to distribute it.

For a **native installer** instead of a folder:

```bash
run.ps1 :desktopHost:jpackageInstaller
```

This builds the installer type for the OS you run it on: `.msi` on Windows (needs the
[WiX Toolset](https://wixtoolset.org/) on `PATH`), `.dmg` on macOS, `.deb` on Linux. jpackage is
per-OS - build each installer on its own platform. Output lands in `desktopHost/build/jpackage/`.

### Android (signed release APK)

Release signing reads from an **untracked** `androidApp/keystore.properties`, so no secret is
committed. Generate a keystore once:

```bash
keytool -genkeypair -v -keystore androidApp/servd-release.jks -alias servd \
  -keyalg RSA -keysize 2048 -validity 10000
```

Create `androidApp/keystore.properties` (git-ignored) pointing at it:

```properties
storeFile=androidApp/servd-release.jks
storePassword=your-store-password
keyAlias=servd
keyPassword=your-key-password
```

Then build the signed release:

```bash
run.ps1 :androidApp:assembleRelease
```

The signed APK lands at `androidApp/build/outputs/apk/release/androidApp-release.apk`. Without a
`keystore.properties`, `assembleRelease` still builds but leaves the APK unsigned (you'd sign it
yourself). The debug build (`assembleDebug`) needs no keystore.

---

## Project layout

```
servd/
├─ core/         KMP library - the engine (shared by desktop + Android)
│   ├─ commonMain      protocol models, PeerRegistry, LAN-address selection, Service model
│   ├─ jvmSharedMain   Ktor server, TLS, chat hub, file store, QR, SSH + FTP services (both JVM targets)
│   ├─ desktopMain     desktop-only: JmDNS discovery
│   ├─ androidMain      Android glue
│   └─ .../resources/webui   the served dashboard (single HTML/CSS/JS file)
├─ desktopHost/  JVM app - the CLI that starts the server (Netty engine)
├─ androidApp/   Android host - foreground service + native control screen (dashboard is the UI)
├─ docs/         scaffold plan + phased implementation plan
├─ run.ps1 / run.bat   run servd with the TMP/TEMP workaround (run.ps1 = clean Ctrl+C)
├─ package.ps1         build distributable bundles (desktop app image + APKs) into dist/
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
| 6 | Optional SSH server | ✅ |
| 7 | Optional FTPS server | ✅ |
| 8 | Android host app (full stack: foreground service, control screen, NsdManager) | ✅ |
| 9 | Packaging (jpackage app image + native installer, signed release APK) | ✅ |

See [`docs/phased-implementation.md`](docs/phased-implementation.md) for details.

---

## License

MIT - see [LICENSE](LICENSE).
