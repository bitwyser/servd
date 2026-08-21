# servd

[![CI](https://github.com/patkarmandar/servd/actions/workflows/ci.yml/badge.svg)](https://github.com/patkarmandar/servd/actions/workflows/ci.yml)
[![Release](https://github.com/patkarmandar/servd/actions/workflows/release.yml/badge.svg)](https://github.com/patkarmandar/servd/actions/workflows/release.yml)
[![Latest release](https://img.shields.io/github/v/release/patkarmandar/servd?sort=semver&label=release)](https://github.com/patkarmandar/servd/releases)
[![License: MIT](https://img.shields.io/badge/license-MIT-green.svg)](LICENSE)
![Platforms](https://img.shields.io/badge/platforms-Windows%20%7C%20Linux%20%7C%20macOS%20%7C%20Android-informational)
![Kotlin](https://img.shields.io/badge/Kotlin-2.1.0-7F52FF?logo=kotlin&logoColor=white)

**A portable local-network server tool.** Run it on a laptop or an Android phone and that device
becomes a small hub every other device on the same Wi-Fi or hotspot can reach: an encrypted
dashboard with real-time chat, a live roster of who's connected, drag-n-drop file sharing, and
built-in SSH/SFTP and FTPS servers.

Everything is served over HTTPS/WSS with a self-signed certificate. A LAN has no public domain and
no certificate authority, so you verify the connection by its **SHA-256 fingerprint** instead.

> **Version 1.0.0** · Windows, Linux, macOS (desktop) and Android (min SDK 24) · MIT-licensed ·
> runs fully offline.

---

## Contents

- [Overview](#overview)
- [Features](#features)
- [Requirements](#requirements)
- [Quick start (desktop)](#quick-start-desktop)
- [Android](#android)
- [Usage & configuration](#usage--configuration)
- [Security model](#security-model)
- [Packaging](#packaging)
- [Releases](#releases)
- [Architecture](#architecture)
- [Building & testing](#building--testing)
- [Troubleshooting](#troubleshooting)
- [Tech stack & versions](#tech-stack--versions)
- [License](#license)

---

## Overview

servd turns one device into a hub for a trusted local network. The host runs a single HTTPS server
that serves a web dashboard, real-time chat, and file sharing; two optional file-transfer servers
(SSH/SFTP and FTPS) can be toggled on from a host-only admin panel. Other devices join by opening
the hub's URL in a browser (or scanning a QR) - no client to install.

The **same stack runs on desktop and Android**; a phone is a full hub, not a lesser version. The UI
is the served web dashboard on every platform (no native app UI beyond a thin Android control
screen).

It is a **trusted-LAN tool**: there is no account or PIN gate on the dashboard, the admin controls
are reachable only from the host device itself, and the SSH/FTP servers use real credentials. See
[Security model](#security-model).

**Fully offline.** servd needs no internet to run. It talks only to devices on your local network,
serves a self-contained dashboard (no CDN, webfonts, analytics, or telemetry), and works on an
isolated Wi-Fi or hotspot with no uplink. The only time internet is used is the **first build**, to
download dependencies; a packaged build - or one you've already built - runs with none.

---

## Features

- **HTTPS dashboard** bound to your LAN/hotspot IP on port `8443`, with light/dark themes.
- **Real-time chat** between everyone connected, over WSS (multi-line, clear-for-everyone).
- **Connected-device roster** - see who is on the hub, live, with join/leave.
- **Drag-n-drop file sharing** - drop a file and it appears on every device instantly; download,
  delete one, or clear all.
- **Scan-to-join QR** of the hub URL, so a phone joins without typing an IP.
- **SSH/SFTP server** (optional) - credentialed SFTP jailed to the shared files, no OS shell.
- **FTPS server** (optional) - explicit-TLS FTP, same credential, jailed to the shared files.
- **Auto-discovery (mDNS)** - the hub advertises `_servd._tcp`; other devices find it without an IP.
- **Host-only admin panel** - host info, cert fingerprint, and live server toggles, served on
  loopback only.
- **Self-signed TLS** with a printed, verifiable SHA-256 fingerprint.
- **Desktop CLI** that starts the hub, prints the fingerprint, and auto-opens the dashboard.
- **Android host** with a foreground service (survives backgrounding) and a native control screen.
- **Fully offline** - LAN only, with a self-contained dashboard (no CDN, webfonts, or telemetry).

---

## Requirements

**To build from source:**

- **JDK 17 or newer** (JDK 21 is fine - the build targets Java 17 bytecode). No separate Gradle
  install is needed; the repo ships a Gradle wrapper.
- **Internet access on the first build only**, to download Gradle and dependencies. Nothing after
  that reaches the internet - servd itself runs fully offline.
- For the Android app, the **Android SDK**, with `local.properties` pointing at it, e.g.
  `sdk.dir=C:\\Android`.

**To run:** nothing beyond a JVM (a packaged desktop build bundles its own JRE, so it needs
nothing at all), plus a Wi-Fi or hotspot network - which does **not** need an internet uplink.

---

## Quick start (desktop)

From the repo root:

```bash
# Windows
.\run.ps1

# Linux / macOS
./run.sh
```

(`run.bat` also works on Windows.) This builds servd, binds it to your detected LAN address, and
opens the dashboard in your browser. You'll see:

```
servd v1.0.0 - local-network server tool

admin   : https://127.0.0.1:8443   (this machine only)
serving : https://10.205.210.12:8443   (share with other devices)
cert    : self-signed, SHA-256 fingerprint:
          12:3A:07:38:...:F5:CF
keystore: C:\Users\you\.servd\keystore.jks

Press Ctrl+C to stop.
```

The **admin** URL (`127.0.0.1`) is your own machine, with the server-toggle panel; the **serving**
URL is what you share. Your browser warns that the certificate isn't trusted - expected for a
self-signed LAN cert. Confirm the fingerprint shown in the page matches the one printed above, then
proceed.

**Prefer `run.ps1` for a clean `Ctrl+C`.** `run.bat` works, but cmd.exe adds its own "Terminate
batch job?" prompt after the server has already stopped.

### Connect another device

1. Put the other device on the **same Wi-Fi** (or connect it to this machine's **hotspot**).
2. Open **`https://<the-serving-address>:8443`** in its browser.
3. Accept the certificate warning, set a display name, and start chatting. Each connected device
   appears in the **Devices** roster; the **Files** tab does drag-n-drop to everyone. Or just
   **scan the QR** in the Devices panel to open the hub on a phone.

> **On Linux/macOS, use `./run.sh` wherever this README shows `.\run.ps1`** - both take the same
> servd flags and Gradle tasks.
>
> **Why a runner instead of `gradlew` directly?** It launches the server as a direct JVM process
> for a clean `Ctrl+C`, and on Windows it also applies a temp-dir workaround (not needed on
> Linux/macOS). See [Troubleshooting](#troubleshooting). You can always call `./gradlew` yourself
> instead.

---

## Android

The Android app is a **full hub**, hosting the same stack as desktop. It is a thin native shell
around the shared engine: a foreground service keeps the servers alive when the app is
backgrounded, and a control screen starts/stops the hub and shows how to reach it.

Build and install the debug APK:

```bash
.\run.ps1 :androidApp:assembleDebug
adb install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk
```

Then, on the phone:

1. Connect it to the **same Wi-Fi** as the other devices (or turn on its **hotspot** and have them
   join it).
2. Open **servd** and tap **Start hub**. The control screen shows the **share URL**, a
   **scan-to-join QR**, the cert **fingerprint** to verify, and the **SSH/FTP credentials**.
3. On another device, open the share URL (or scan the QR) and accept the certificate warning - the
   same dashboard as desktop. SSH and FTP start off; enable them from the admin dashboard, which the
   phone reaches on its own `127.0.0.1`.

The hub keeps running in the background with a persistent notification (tap **Stop** there or in the
app to stop it).

---

## Usage & configuration

### Default ports

| Service | Port | Notes |
|---|---|---|
| HTTPS dashboard + chat + files | `8443` | always on |
| SSH / SFTP | `2222` | optional, off until enabled from the admin panel |
| FTPS | `2121` | optional, off until enabled from the admin panel |

All are non-privileged ports. SSH/FTP share one generated credential (username `servd`), shown in
the admin panel.

### Desktop CLI

The runner passes servd flags straight through:

```bash
.\run.ps1 --port 9443 --no-open
```

| Flag | Meaning | Default |
|---|---|---|
| `--port N` | HTTPS port | `8443` |
| `--host IP` | Bind address | detected LAN IP (else `127.0.0.1`) |
| `--dir PATH` | Data dir (keystore, files, credential) | `~/.servd` |
| `--no-open` | Don't auto-open the browser | (opens) |

Find running hubs on the network without typing an IP:

```bash
.\run.ps1 discover
```

Any Gradle task passes through too, e.g. `.\run.ps1 :core:desktopTest`.

### Data directory

On desktop, servd keeps its TLS keystore, the shared files, and the SSH/FTP credential under
`~/.servd` (override with `--dir`). On Android the equivalent lives in the app's private storage.

---

## Security model

servd is built for a **trusted LAN**, and its boundaries are deliberate:

- **Client routes** (dashboard, chat, files, roster) are bound to the LAN interface and open to the
  network - anyone who can reach the hub and accept the cert can use them. There is **no PIN**; that
  is the intended model for a local tool.
- **Admin routes** (service toggles, host info, cert) are gated to **loopback only**. Only a browser
  on the host device itself can administer the hub; other devices on the LAN cannot.
- **SSH and FTP** require the generated credential - never anonymous. FTP is FTPS (explicit TLS),
  SFTP is encrypted by design, and both are **jailed to the shared files directory** (no OS shell,
  no access outside that folder).
- **Transport is encrypted** end to end on the wire (HTTPS/WSS, SSH, FTPS), verified by the cert
  **fingerprint** rather than a CA. This is **transport encryption, not end-to-end**: the host
  device (the server) can see traffic, as expected for a hub.

---

## Packaging

Build distributable bundles - a self-contained desktop app and the Android APKs - into a `dist/`
folder.

### One-shot: `package.ps1` / `package.sh`

```bash
# Windows
.\package.ps1

# Linux / macOS
./package.sh
```

Builds the desktop **app image** (bundled JRE) and the Android **debug APK**, plus the signed
**release APK** if release signing is set up (below), all in `dist/`:

```
dist/
├─ servd-1.0.0-windows.zip           self-contained desktop app (Windows)
├─ servd-1.0.0-linux-x86_64.tar.gz   self-contained desktop app (Linux/macOS)
├─ servd-1.0.0-debug.apk             Android debug build
└─ servd-1.0.0-release.apk           Android release build (only if signed)
```

| `package.ps1` flag | `package.sh` flag | Effect |
|---|---|---|
| _(none)_ | _(none)_ | desktop app image + Android APK(s) |
| `-Installer` | `--installer` | also build the OS-native installer (`.msi`/`.dmg`/`.deb`) |
| - | `--appimage` | also build a single-file `*.AppImage` (Linux; needs `appimagetool`) |
| `-Desktop` | `--desktop` | desktop only |
| `-Android` | `--android` | Android only |
| `-Clean` | `--clean` | wipe `dist/` first |

`package.sh` also takes `-h` / `--help` to print its usage. The Gradle tasks these scripts wrap are
below, if you'd rather run them directly.

### Desktop app image & installer

servd bundles its own JRE via **jpackage**, so an end user installs nothing else.

```bash
# Windows                                    # Linux / macOS
.\run.ps1 :desktopHost:jpackageImage         ./run.sh :desktopHost:jpackageImage      # self-contained folder
.\run.ps1 :desktopHost:jpackageInstaller     ./run.sh :desktopHost:jpackageInstaller  # native installer
```

`jpackageImage` writes a self-contained folder to `desktopHost/build/jpackage/servd/` (~166 MB with
the bundled runtime). The launcher inside is the **click-to-run executable**, the equivalent of a
Windows `.exe`:

- **Windows:** `servd\servd.exe`
- **Linux / macOS:** `servd/bin/servd`

It opens a console showing the hub URL and cert fingerprint; **press `Ctrl+C` or close it to stop
the hub.** `jpackageInstaller` builds `.msi` on Windows (needs the
[WiX Toolset](https://wixtoolset.org/) on `PATH`), `.dmg` on macOS, `.deb` on Linux. **jpackage is
per-OS - build each artifact on its own platform.**

**Single-file Linux executable (AppImage).** For one portable file you `chmod +x` and double-click
(the closest match to a Windows `.exe`), build an AppImage:

```bash
./package.sh --appimage
```

It wraps the app image into `dist/servd-1.0.0-<arch>.AppImage`, already marked executable. This
needs [`appimagetool`](https://appimage.github.io/appimagetool/) on `PATH`; without it the step is
skipped with a note.

### Android signed release APK

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

Then build:

```bash
.\run.ps1 :androidApp:assembleRelease
```

The signed APK lands at `androidApp/build/outputs/apk/release/androidApp-release.apk`. Without a
`keystore.properties`, `assembleRelease` still builds but leaves the APK unsigned. The debug build
(`assembleDebug`) needs no keystore.

---

## Releases

Tagged releases are built and published automatically by GitHub Actions
([`.github/workflows/release.yml`](.github/workflows/release.yml)). Because jpackage is per-OS,
each artifact is built on its native runner, then all are attached to a GitHub Release for the tag:

- **Windows** - `.zip` (contains `servd.exe` + bundled JRE)
- **Linux** - single-file `.AppImage`
- **Android** - signed release `.apk`

### Cut a release

1. Bump the version in one place - `servdVersion` in [`gradle.properties`](gradle.properties) - and
   the matching `Servd.VERSION` constant in `core/.../Servd.kt` (CI fails if the two disagree).
2. Commit, then tag and push. The tag must be `v<servdVersion>`:

   ```bash
   git tag v1.0.0
   git push origin v1.0.0
   ```

CI verifies the tag matches the version, builds all three artifacts, and publishes the release with
auto-generated notes. (You can also build locally any time with
[`package.ps1` / `package.sh`](#packaging).)

### Android signing secrets

The signed release APK needs your keystore, stored as encrypted repository secrets
(**Settings -> Secrets and variables -> Actions**). Generate a keystore once and keep it safe -
reuse it for every release so updates install over the previous version:

```bash
keytool -genkeypair -v -keystore servd-release.jks -alias servd \
  -keyalg RSA -keysize 2048 -validity 10000
```

> `keytool` ships with the JDK. If your shell can't find it (common in Git Bash on Windows, where
> `java` is an Oracle shim without the JDK tools), call it by full path, e.g.
> `"/c/Program Files/Java/jdk-21/bin/keytool" ...`, or add the JDK's `bin` to your `PATH`.

Then add these four secrets:

| Secret | Value |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | the keystore, base64-encoded |
| `ANDROID_STORE_PASSWORD` | keystore (store) password |
| `ANDROID_KEY_ALIAS` | key alias (e.g. `servd`) |
| `ANDROID_KEY_PASSWORD` | key password |

Base64-encode the keystore with `base64 -w0 servd-release.jks` (Linux/macOS/Git Bash) or, in
PowerShell, `[Convert]::ToBase64String([IO.File]::ReadAllBytes("servd-release.jks"))`.

---

## Architecture

### Modules

```
servd/
├─ core/         Kotlin Multiplatform library - the engine (shared by desktop + Android)
│   ├─ commonMain      protocol models, PeerRegistry, LAN-address selection, Service model
│   ├─ jvmSharedMain   Ktor server, TLS, chat hub, file store, QR, SSH + FTP services
│   ├─ desktopMain     desktop-only: JmDNS discovery
│   ├─ androidMain     Android glue
│   └─ .../resources/webui   the served dashboard (single HTML/CSS/JS file)
├─ desktopHost/  JVM app - the CLI that starts the server (Netty engine)
├─ androidApp/   Android host - foreground service + native control screen
├─ run.ps1 / run.bat   run servd on Windows (temp-dir workaround; run.ps1 = clean Ctrl+C)
├─ run.sh              run servd on Linux / macOS
├─ package.ps1 / package.sh   build distributable bundles into dist/ (Windows / Linux-macOS)
└─ gradlew*            Gradle wrapper (no separate Gradle install needed)
```

**Shared JVM source set.** Android and desktop are both JVM, but Kotlin Multiplatform's default
hierarchy has no shared "android + jvm" source set. servd adds an intermediate **`jvmSharedMain`**
that both `androidMain` and `desktopMain` depend on, so the plain-JVM server code (Ktor server, TLS,
SSH, FTP) lives there once and runs on both platforms.

**One HTTP engine.** `ServdServer` holds all routing and TLS wiring and is generic over the Ktor
engine factory; each platform injects the engine. Both desktop and Android use **Netty** (Ktor's
CIO engine cannot serve HTTPS). The keystore is persisted as **PKCS12** because Android has no JKS
provider.

### Key abstractions (`commonMain`)

- **`Service`** - a startable/stoppable server with an `id`, `port`, and state. Implementations:
  `HttpService` (always on), `SshService`, `FtpService`.
- **`ServiceManager`** - starts/stops/toggles services and serializes those calls.
- **`ChatHub`** - tracks connected peers (presence) and broadcasts chat and roster events.

### Request flow

- **Dashboard & status:** `GET /` serves the single-page dashboard; `GET /status` returns host
  info + the cert fingerprint as JSON.
- **Chat & presence:** clients connect to the `/ws` WebSocket, announce a name, then exchange chat;
  `ChatHub` broadcasts join/leave and messages.
- **Files:** `POST /files` (multipart upload), `GET /files` (list), `GET /files/{id}` (download),
  with delete/clear routes.
- **Admin:** `/admin/*` routes report and toggle services, gated to loopback.

### Deliberately out of scope

- **Bluetooth** (Wi-Fi/hotspot + LAN only).
- **Native Compose UI** (the served web dashboard is the UI).
- **True end-to-end encryption** (transport encryption only; the host can see traffic).
- **Full LAN device scanning** (the roster is peers connected to the hub, plus mDNS hub discovery).
- **External database** (state is in-memory).

---

## Building & testing

The wrappers set up a temp-dir workaround (see [Troubleshooting](#troubleshooting)) and pass any
Gradle task through:

```bash
.\run.ps1 :core:desktopTest        # unit tests (LAN-address selection, chat hub, protocol)
.\run.ps1 :desktopHost:installDist # build the desktop distribution
.\run.ps1 :androidApp:assembleDebug
```

Tests cover the pure logic with `kotlin-test`; networked features are verified with a
dependency-free two-client WSS harness and `curl` smoke checks, and the Android build is verified on
a Pixel emulator via `adb`.

---

## Troubleshooting

**`Unable to establish loopback connection` when building or running.** JDK 21's NIO creates an
AF_UNIX socket for its internal pipe in the OS temp dir, and some Windows temp folders (e.g.
`%LOCALAPPDATA%\Temp` under certain AV/EDR setups) reject socket files, which breaks Gradle and the
server. The fix - already built into `run.ps1` / `run.bat` - points `TMP`/`TEMP` at a repo-local
`.sock-tmp/` folder first. If you invoke `gradlew` directly, set them yourself:

```bash
set TMP=%CD%\.sock-tmp && set TEMP=%CD%\.sock-tmp && gradlew :desktopHost:run
```

**Browser says "Not secure / certificate invalid."** Expected - servd uses a self-signed cert
because a LAN has no certificate authority. Verify the fingerprint in the page matches the one the
CLI (or the Android control screen) shows, then proceed.

**No LAN address found.** Connect to Wi-Fi or start a hotspot, then restart servd. Without one it
binds to `127.0.0.1`, reachable only from the same machine.

**`Ctrl+C` prints a wall of `RejectedExecutionException`.** Harmless shutdown noise from in-flight
HTTP/2 frames racing the event loop; the server stops cleanly regardless. servd silences it, so if
you see it you're on an old build - rebuild.

---

## Tech stack & versions

Dependency versions are pinned in [`gradle/libs.versions.toml`](gradle/libs.versions.toml).

| Area | Component | Version | Role |
|---|---|---|---|
| Language | Kotlin (Multiplatform) | `2.1.0` | one shared `core` for desktop JVM + Android |
| Build | Gradle (Kotlin DSL) | `8.11.1` | wrapper committed; no separate install |
| Build | Android Gradle Plugin | `8.7.3` | Android build (`multiDexEnabled` for Netty) |
| Runtime | JDK | 17 target | Java 17 bytecode (built with JDK 21) |
| Android | SDK levels | compile 35 / min 24 / target 35 | |
| Server | Ktor | `3.0.3` | HTTP/S + WebSocket framework |
| Server | Netty (via Ktor) | | HTTPS engine on desktop **and** Android |
| TLS | JSSE / PKCS12 | JDK | self-signed keystore; SHA-256 fingerprint |
| Net | `java.net.NetworkInterface` | JDK | cross-platform LAN/hotspot address detection |
| SSH | Apache MINA SSHD | `2.13.2` | SSH / SFTP server |
| FTP | Apache FtpServer | `1.2.0` | FTPS server |
| Discovery | JmDNS (desktop) / `NsdManager` (Android) | `3.5.9` | mDNS/DNS-SD |
| QR | ZXing | `3.5.3` | scan-to-join QR |
| Data | kotlinx.serialization | `1.7.3` | JSON WebSocket protocol |
| Async | kotlinx.coroutines | `1.9.0` | async server + guarded broadcast |
| Logging | SLF4J + slf4j-simple | `2.0.16` | desktop server logging |
| Frontend | plain HTML/CSS/JS | | single-file dashboard, no framework |

---

## License

MIT - see [LICENSE](LICENSE).
