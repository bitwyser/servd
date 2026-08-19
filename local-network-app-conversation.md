# Local Network App Planning — Conversation Log

## Goal
Build a cross-platform app (Android + Desktop) that creates a local network hub combining:
- Wi-Fi hotspot detection/binding
- FTP server
- SSH server
- Local chat (real-time, LAN-based)
- File sharing (upload/download)
- HTTP server (serves a dashboard UI)

## Key Decisions Made

**Architecture chosen:** Kotlin/JVM core shared between Android and Desktop, rather than Node.js or Electron, since:
- Same server code (pure Java/Kotlin libraries) runs unmodified on both Android and desktop JVM
- Avoids maintaining two separate codebases (Node doesn't run natively cross-platform the same way)

**Libraries selected per feature:**

| Feature | Library |
|---|---|
| HTTP server | NanoHTTPD — lightweight, pure Java |
| FTP server | Apache FtpServer |
| SSH server | Apache MINA SSHD (or SSHJ as alternative) |
| Local chat | WebSocket server (Ktor WebSockets or plain Java ServerSocket) |
| File sharing | Built via the HTTP dashboard (upload/download pages) |
| Hotspot | Android `WifiManager` / `LocalOnlyHotspot` API (Android 8+) — limited, can't fully customize SSID/password on newer Android; desktop OSes have similar restrictions |

**UI framework recommendation:** Compose Multiplatform — lets the same UI code target Android, Windows, macOS, and Linux.

**Desktop packaging option:** `jpackage` (built into JDK 14+) to bundle into a native `.exe` / `.dmg` / `.deb`, no separate JVM install required by the user.

**Alternatives considered and set aside:**
- Node.js + Express/ftp-srv/ssh2/socket.io — works but requires Node runtime on both platforms, less "native"
- Electron — desktop only, would need separate Android codebase
- Go — single lightweight binary, strong libraries (net/http, goftp, gliderlabs/ssh, gorilla/websocket), good minimal/headless option but UI would just be a served web page

**Important caveat flagged:** No app (on Android 10+ or modern desktop OSes) can fully automate hotspot *creation* — user must manually turn on the hotspot/Wi-Fi first. The app can then detect the local IP and bind all servers (FTP/SSH/HTTP/chat) to it automatically.

## How It Works End-to-End
1. User manually turns on hotspot (phone) or connects both devices to same Wi-Fi
2. App detects local IP, starts FTP/SSH/HTTP/chat servers bound to that IP
3. Other devices connect via `http://<host-ip>:PORT` for the dashboard, or directly via FTP/SSH clients
4. Same app/core code can run as host on either the phone or the laptop

## App Name Brainstorm
Shortlist words: Local, Hub, Net, Link, Portal, Relay

Combos generated:
- LocalHub
- NetLink
- HubLink
- PortalNet
- RelayHub
- **LocalRelay** (favorite — descriptive + clean)
- NetPortal

No final name locked in yet.

## Next Step (not yet started)
Scaffold the shared Kotlin core:
- NanoHTTPD server setup
- Apache MINA SSHD server setup
- Apache FtpServer setup
- WebSocket chat server
- Basic project structure for Android + Compose Multiplatform desktop target

---
*Exported for continuation in a new Claude conversation.*
