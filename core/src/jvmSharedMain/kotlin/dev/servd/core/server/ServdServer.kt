package dev.servd.core.server

import dev.servd.core.Servd
import dev.servd.core.chat.ChatHub
import dev.servd.core.chat.ChatSend
import dev.servd.core.chat.FileMeta
import dev.servd.core.chat.Hello
import dev.servd.core.files.FileStore
import dev.servd.core.qr.Qr
import dev.servd.core.service.HttpService
import dev.servd.core.service.Service
import dev.servd.core.service.ServiceManager
import dev.servd.core.tls.TlsKeyStore
import io.ktor.server.application.ApplicationCall
import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.ApplicationEngineFactory
import io.ktor.server.engine.applicationEnvironment
import io.ktor.server.engine.embeddedServer
import io.ktor.server.engine.sslConnector
import io.ktor.server.http.content.staticResources
import io.ktor.server.plugins.origin
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondFile
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import java.net.InetAddress
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.utils.io.jvm.javaio.toInputStream
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * servd's single HTTPS server. Phase 1 serves the dashboard shell and a `/status` endpoint;
 * later phases add WebSocket chat, presence, and file sharing on the same server.
 *
 * The routing/module and TLS wiring here are engine-agnostic; the concrete Ktor engine is
 * passed in by each platform - Netty on desktop (Ktor CIO does not support server HTTPS).
 * Lives in `jvmSharedMain` so the same server logic runs on desktop now and Android later.
 */
class ServdServer<TEngine : ApplicationEngine, TConfiguration : ApplicationEngine.Configuration>(
    engineFactory: ApplicationEngineFactory<TEngine, TConfiguration>,
    /** Interface to listen on (e.g. "0.0.0.0" for all interfaces). */
    private val bindHost: String,
    /** Address shown to users / used in the dashboard URL (e.g. the LAN IP). */
    private val advertisedHost: String,
    val port: Int,
    private val tls: TlsKeyStore,
    /** Directory where shared files are stored. */
    filesDir: File,
    /** Platform services (e.g. SSH/FTP on desktop) added alongside the always-on HTTP one. */
    extraServices: List<Service> = emptyList(),
    /** Machine host name, shown in the host card. */
    private val hostName: String = "servd",
    /** Bound network interface name (e.g. "wlan0"), shown in the host card. */
    private val interfaceName: String? = null,
) {
    val url: String get() = "https://$advertisedHost:$port"

    private val startedAt = System.currentTimeMillis()
    private val chatHub = ChatHub(serverName = advertisedHost)
    private val fileStore = FileStore(filesDir)
    private val serviceManager = ServiceManager(listOf(HttpService(port)) + extraServices)
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    private val engine = embeddedServer(
        engineFactory,
        applicationEnvironment { },
        configure = {
            sslConnector(
                keyStore = tls.keyStore,
                keyAlias = tls.alias,
                keyStorePassword = { tls.keyStorePasswordChars() },
                privateKeyPassword = { tls.privateKeyPasswordChars() },
            ) {
                host = bindHost
                port = this@ServdServer.port
            }
        },
        module = { servdModule() },
    )

    fun start(wait: Boolean) {
        engine.start(wait)
    }

    fun stop() {
        engine.stop(gracePeriodMillis = 300, timeoutMillis = 1500)
    }

    private fun Application.servdModule() {
        install(WebSockets)
        routing {
            // Real-time chat + presence. Clients connect, say hello (name), then exchange chat.
            webSocket("/ws") {
                val address = call.request.origin.remoteAddress // raw IP, no reverse-DNS
                val id = chatHub.onConnect(this, address)
                try {
                    for (frame in incoming) {
                        if (frame is Frame.Text) {
                            when (val msg = chatHub.parseClient(frame.readText())) {
                                is Hello -> chatHub.onHello(id, msg.name)
                                is ChatSend -> chatHub.onChat(id, msg.text)
                                null -> {}
                            }
                        }
                    }
                } finally {
                    chatHub.onDisconnect(id)
                }
            }
            // Machine-readable status - includes the cert fingerprint for verification.
            get("/status") {
                val ifaceJson = interfaceName?.let { "\"$it\"" } ?: "null"
                val json = buildString {
                    append('{')
                    append("\"name\":\"").append(Servd.NAME).append("\",")
                    append("\"version\":\"").append(Servd.VERSION).append("\",")
                    append("\"address\":\"").append(advertisedHost).append("\",")
                    append("\"port\":").append(port).append(',')
                    append("\"hostName\":\"").append(hostName).append("\",")
                    append("\"interfaceName\":").append(ifaceJson).append(',')
                    append("\"uptimeMs\":").append(System.currentTimeMillis() - startedAt).append(',')
                    append("\"connected\":").append(chatHub.connectionCount()).append(',')
                    append("\"tls\":\"self-signed\",")
                    append("\"fingerprintSha256\":\"").append(tls.fingerprintSha256).append('"')
                    append('}')
                }
                call.respondText(json, ContentType.Application.Json)
            }
            // QR code (SVG) encoding the shareable hub URL, so a phone can scan to join.
            get("/qr") {
                call.respondText(Qr.svg("$url/"), ContentType("image", "svg+xml"))
            }
            // File sharing: upload (multipart), list (newest first), download (by id).
            post("/files") {
                val multipart = call.receiveMultipart()
                var from = "someone"
                val saved = mutableListOf<FileMeta>()
                multipart.forEachPart { part ->
                    when (part) {
                        is PartData.FormItem ->
                            if (part.name == "from") from = part.value.take(40).ifBlank { "someone" }
                        is PartData.FileItem -> {
                            val name = part.originalFileName ?: "file"
                            val contentType = part.contentType?.toString()
                            val meta = part.provider().toInputStream().use {
                                fileStore.save(name, contentType, from, it)
                            }
                            chatHub.announceFile(meta)
                            saved += meta
                        }
                        else -> {}
                    }
                    part.dispose()
                }
                call.respondText(json.encodeToString(saved), ContentType.Application.Json)
            }
            get("/files") {
                call.respondText(json.encodeToString(fileStore.list()), ContentType.Application.Json)
            }
            get("/files/{id}") {
                val entry = call.parameters["id"]?.let { fileStore.get(it) }
                if (entry == null) {
                    call.respond(HttpStatusCode.NotFound)
                    return@get
                }
                val (meta, file) = entry
                call.response.header(
                    HttpHeaders.ContentDisposition,
                    ContentDisposition.Attachment
                        .withParameter(ContentDisposition.Parameters.FileName, meta.name)
                        .toString(),
                )
                call.respondFile(file)
            }
            // Host-only admin API: reachable from loopback (the host machine) only, so LAN
            // devices can use the dashboard but cannot reconfigure the server.
            get("/admin/services") {
                if (!call.isLoopbackClient()) return@get call.respond(HttpStatusCode.Forbidden)
                call.respondText(json.encodeToString(serviceManager.list()), ContentType.Application.Json)
            }
            post("/admin/services/{id}/start") {
                if (!call.isLoopbackClient()) return@post call.respond(HttpStatusCode.Forbidden)
                val ok = call.parameters["id"]?.let { serviceManager.start(it) } ?: false
                call.respondText(
                    json.encodeToString(serviceManager.list()),
                    ContentType.Application.Json,
                    status = if (ok) HttpStatusCode.OK else HttpStatusCode.BadRequest,
                )
            }
            post("/admin/services/{id}/stop") {
                if (!call.isLoopbackClient()) return@post call.respond(HttpStatusCode.Forbidden)
                val ok = call.parameters["id"]?.let { serviceManager.stop(it) } ?: false
                call.respondText(
                    json.encodeToString(serviceManager.list()),
                    ContentType.Application.Json,
                    status = if (ok) HttpStatusCode.OK else HttpStatusCode.BadRequest,
                )
            }
            // The served dashboard shell (webui/ resources), default document index.html.
            staticResources("/", "webui") {
                default("index.html")
            }
        }
    }
}

/** True only when the request came from this machine (loopback), gating the admin API. */
private fun ApplicationCall.isLoopbackClient(): Boolean =
    runCatching { InetAddress.getByName(request.origin.remoteAddress).isLoopbackAddress }
        .getOrDefault(false)
