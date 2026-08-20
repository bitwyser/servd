# servd - Scaffold Plan (v1)

> A portable local-network server tool. One device runs encrypted HTTP/S, SSH, and FTP
> services plus real-time chat, drag-n-drop file sharing, and a live roster of connected
> devices. Wi-Fi hotspot + LAN only. UI is a served web dashboard.

## 1. Tech stack

| Concern | Choice |
|---|---|
| Language / build | Kotlin Multiplatform, Gradle (Kotlin DSL) + version catalog (`libs.versions.toml`) |
| Targets | `androidTarget()`, `jvm("desktop")` |
| HTTP/S + WebSocket | **Ktor** - one server for dashboard, files, chat, presence. Engine: **Netty** on desktop (Ktor CIO does not support server-side HTTPS); Android engine TBD in Phase 8 |
| SSH server | Apache MINA SSHD (optional module) |
| FTP server | Apache FtpServer, FTPS (optional module) |
| Serialization / async | kotlinx.serialization, kotlinx.coroutines |
| TLS | Self-signed cert generated on first run (Ktor + BouncyCastle), keystore persisted |
| Discovery | mDNS - JmDNS (desktop JVM), `NsdManager` (Android) |
| Desktop CLI | Clikt (or kotlinx-cli) |
| Logging | slf4j + logback (desktop); Android logcat bridge |

## 2. Module / source-set layout

```
servd/
├─ settings.gradle.kts
├─ gradle/libs.versions.toml
├─ core/                     (KMP library - the engine)
│   ├─ commonMain            Service interface, models, ServiceManager, ChatHub, config
│   ├─ jvmSharedMain         Ktor server, SSHD, FtpServer, TLS, JmDNS  (shared by both JVM targets)
│   ├─ androidMain           NsdManager, Android IP detection (actual impls)
│   └─ desktopMain           JmDNS, desktop IP detection, entrypoint (actual impls)
├─ desktopHost/              (JVM app) CLI launcher, main(), auto-opens browser
├─ androidApp/               (Android app) foreground service + one-screen control
└─ webui/                    dashboard assets (HTML/CSS/JS), bundled into core resources
```

**KMP note:** Android and Desktop are both JVM, but KMP's default hierarchy has no shared
"android+jvm" source set. We add an intermediate **`jvmSharedMain`** that both `androidMain`
and `desktopMain` depend on - the plain-JVM server libraries (Ktor/SSHD/FtpServer) live there
once.

## 3. Core abstractions (commonMain)

```kotlin
enum class ServiceStatus { Stopped, Starting, Running, Error }

interface Service {
    val id: String            // "http", "ssh", "ftp"
    val port: Int
    val status: StateFlow<ServiceStatus>
    suspend fun start(bind: BindConfig)
    suspend fun stop()
}

data class BindConfig(
    val lanAddress: String,   // chosen NetworkInterface address (hotspot/LAN)
    val keyStore: KeyStoreRef,// self-signed TLS material
    val credentials: Credentials?
)

class ServiceManager(private val services: List<Service>)   // start/stop/toggle, holds BindConfig
class ChatHub                                               // peers, presence, message broadcast
```

- **HttpService (Ktor)** - always on (the core; serves dashboard, files, chat, presence)
- **SshService (MINA SSHD)** - optional, credentialed
- **FtpService (FtpServer/FTPS)** - optional, credentialed

## 4. Feature wiring

- **Chat + presence:** Ktor `/ws` WebSocket. On connect, peer registers a device name →
  `ChatHub` broadcasts join/leave + messages. Roster = live set of connected sessions.
- **File sharing:** `POST /files` (multipart, drag-n-drop from webui), `GET /files` (list),
  `GET /files/{id}` (download). Stored in a shared dir; metadata in memory for v1.
- **Device roster:** the presence set above, surfaced in the dashboard chat-app style.
- **TLS:** first run generates a self-signed cert, persists keystore in the app data dir, and
  exposes its **SHA-256 fingerprint** (shown in CLI + dashboard) so users can verify it once.
- **Discovery (mDNS):** advertise `_servd._tcp`; clients/hosts can auto-find hubs instead of
  typing an IP.

## 5. Security model (trusted-LAN, no PIN)

- **Client routes** (chat, files, roster) → bound to the **LAN interface**; open to the network.
- **Admin routes** (service toggles, credentials, cert) → gated to **loopback only**
  (`remoteHost` is localhost) - only the host machine's browser can administer.
- **SSH / FTP** → real credentials, never anonymous. FTP uses FTPS (TLS).
- Everything on the wire is encrypted (HTTPS/WSS, SSH by design, FTPS). Transport encryption,
  not end-to-end - the host (server) can see traffic, as expected for a hub.

## 6. Platform specifics

**Desktop (Windows + Linux + macOS - same JVM jar):**
- CLI: `servd start [--port] [--dir] [--no-open]`, `servd stop`, `servd status`
- Auto-opens the default browser to `https://localhost:<port>`
- IP detection: enumerate `NetworkInterface`, pick by **address** (not name)
- Packaging: `jpackage` → `.exe`/`.msi` (Win), `.deb`/`.rpm` (Linux), `.dmg` (macOS)

**Android (thin native shell):**
- Foreground service hosts the core (persistent notification keeps it alive)
- One control screen: Start/Stop, show URL/IP + cert fingerprint, "Open dashboard"
- Permissions: `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`,
  `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`, `POST_NOTIFICATIONS` (13+),
  `NEARBY_WIFI_DEVICES` (13+), `CHANGE_WIFI_MULTICAST_STATE` (mDNS)

## 7. Build order (milestones)

1. Gradle skeleton, `core` module, version catalog; `Service` interface; `desktopHost` prints LAN IP
2. Ktor **HTTPS** server + self-signed cert + serves `webui` ("hello")
3. WebSocket **chat + presence roster** + minimal dashboard
4. **File** upload/download + drag-n-drop
5. **mDNS** discovery (`_servd._tcp`)
6. **Admin panel** (loopback-gated) + service toggles
7. **SSH** service (MINA SSHD) + credentials
8. **FTP(S)** service (Apache FtpServer) + credentials
9. **Android** app shell + foreground service
10. **Packaging** (jpackage) for Windows + Linux

## 8. Explicitly out of scope for v1

- Bluetooth (removed)
- Native Compose UI (web dashboard is the UI)
- True end-to-end encryption (transport encryption only)
- Full LAN device scanning (roster = peers connected to the hub, + mDNS hub discovery)
- macOS/iOS packaging polish (works, but not a focus)
