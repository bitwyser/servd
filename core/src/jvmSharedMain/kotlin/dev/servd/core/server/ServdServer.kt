package dev.servd.core.server

import dev.servd.core.Servd
import dev.servd.core.chat.ChatHub
import dev.servd.core.chat.ChatSend
import dev.servd.core.chat.Hello
import dev.servd.core.tls.TlsKeyStore
import io.ktor.http.ContentType
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.ApplicationEngineFactory
import io.ktor.server.engine.applicationEnvironment
import io.ktor.server.engine.embeddedServer
import io.ktor.server.engine.sslConnector
import io.ktor.server.http.content.staticResources
import io.ktor.server.plugins.origin
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText

/**
 * servd's single HTTPS server. Phase 1 serves the dashboard shell and a `/status` endpoint;
 * later phases add WebSocket chat, presence, and file sharing on the same server.
 *
 * The routing/module and TLS wiring here are engine-agnostic; the concrete Ktor engine is
 * passed in by each platform — Netty on desktop (Ktor CIO does not support server HTTPS).
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
) {
    val url: String get() = "https://$advertisedHost:$port"

    private val chatHub = ChatHub(serverName = advertisedHost)

    private val engine = embeddedServer(
        engineFactory,
        applicationEnvironment { },
        configure = {
            sslConnector(
                keyStore = tls.keyStore,
                keyAlias = tls.alias,
                keyStorePassword = { tls.keyStorePassword },
                privateKeyPassword = { tls.privateKeyPassword },
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
            // Machine-readable status — includes the cert fingerprint for verification.
            get("/status") {
                val json = buildString {
                    append('{')
                    append("\"name\":\"").append(Servd.NAME).append("\",")
                    append("\"version\":\"").append(Servd.VERSION).append("\",")
                    append("\"address\":\"").append(bindHost).append("\",")
                    append("\"port\":").append(port).append(',')
                    append("\"tls\":\"self-signed\",")
                    append("\"fingerprintSha256\":\"").append(tls.fingerprintSha256).append('"')
                    append('}')
                }
                call.respondText(json, ContentType.Application.Json)
            }
            // The served dashboard shell (webui/ resources), default document index.html.
            staticResources("/", "webui") {
                default("index.html")
            }
        }
    }
}
