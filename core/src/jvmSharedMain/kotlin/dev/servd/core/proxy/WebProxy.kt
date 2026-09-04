package dev.servd.core.proxy

import dev.servd.core.tls.TlsKeyStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.webSocket as clientWebSocket
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.ApplicationEngineFactory
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.applicationEnvironment
import io.ktor.server.engine.embeddedServer
import io.ktor.server.engine.sslConnector
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.httpMethod
import io.ktor.server.request.receiveChannel
import io.ktor.server.request.uri
import io.ktor.server.response.respondBytesWriter
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.head
import io.ktor.server.routing.options
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.utils.io.copyTo
import io.ktor.websocket.close
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import io.ktor.client.plugins.websocket.WebSockets as ClientWebSockets

/**
 * A LAN reverse proxy: re-serves a host-run web app (any HTTP/HTTPS server - a Node/Vite dev
 * server, a static host, another service) to everyone on the network over servd's own HTTPS.
 *
 * It runs its own HTTPS listener on a dedicated port (so the app is served at "/" and works
 * unchanged - SPA routes and absolute asset paths included), terminating TLS with servd's
 * self-signed cert and forwarding both plain HTTP requests and WebSocket upgrades (HMR /
 * live-reload) to the target. Off until the host sets a target (loopback admin only).
 *
 * Generic over the Ktor engine, like [dev.servd.core.server.ServdServer] - the platform injects it.
 */
class WebProxy<TEngine : ApplicationEngine, TConfiguration : ApplicationEngine.Configuration>(
    private val engineFactory: ApplicationEngineFactory<TEngine, TConfiguration>,
    private val bindHost: String,
    private val advertisedHost: String,
    private val tls: TlsKeyStore,
) {
    @Volatile private var server: EmbeddedServer<TEngine, TConfiguration>? = null
    @Volatile private var currentTarget: String? = null
    @Volatile private var currentPort: Int = 0

    val enabled: Boolean get() = server != null

    /** Trust the target's cert unconditionally: it's the host's own server on the trusted LAN. */
    private val client: HttpClient by lazy {
        HttpClient(CIO) {
            install(ClientWebSockets)
            followRedirects = false // relay 3xx to the browser (with Location rewritten)
            expectSuccess = false   // 4xx/5xx are normal responses to forward, not errors
            engine {
                https { trustManager = TrustAll }
            }
        }
    }

    @Serializable
    data class Config(val enabled: Boolean, val target: String?, val port: Int, val url: String?)

    fun config(): Config =
        Config(enabled, currentTarget, currentPort, if (enabled) publicOrigin() else null)

    private fun publicOrigin(): String = "https://$advertisedHost:$currentPort"

    /** Start proxying [target] (e.g. "http://localhost:3000") on [port]. Replaces any running proxy. */
    fun enable(target: String, port: Int) {
        val normalized = normalizeTarget(target)
        require(port in 1..65535) { "port out of range: $port" }
        disable()
        val srv = embeddedServer(
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
                    this.port = port
                }
            },
            module = { proxyModule(normalized) },
        )
        srv.start(wait = false)
        server = srv
        currentTarget = normalized
        currentPort = port
    }

    fun disable() {
        server?.let { runCatching { it.stop(200, 800) } }
        server = null
        currentTarget = null
        currentPort = 0
    }

    private fun Application.proxyModule(target: String) {
        install(WebSockets)
        routing {
            // WebSocket upgrades on any path (HMR / live-reload / app sockets).
            webSocket("{...}") { proxyWebSocket(target, this) }
            // Everything else: any method, any path.
            route("{...}") {
                get { proxyHttp(target, call) }
                post { proxyHttp(target, call) }
                put { proxyHttp(target, call) }
                delete { proxyHttp(target, call) }
                patch { proxyHttp(target, call) }
                head { proxyHttp(target, call) }
                options { proxyHttp(target, call) }
            }
        }
    }

    private suspend fun proxyHttp(target: String, call: ApplicationCall) {
        val url = target + call.request.uri
        val method = call.request.httpMethod
        val hasBody = method != HttpMethod.Get && method != HttpMethod.Head
        val response = client.request(url) {
            this.method = method
            call.request.headers.forEach { name, values ->
                if (!isRequestHopByHop(name)) values.forEach { headers.append(name, it) }
            }
            if (hasBody) setBody(call.receiveChannel())
        }
        response.headers.forEach { name, values ->
            if (!isResponseHopByHop(name)) values.forEach { value ->
                call.response.headers.append(name, rewriteLocation(name, value, target), safeOnly = false)
            }
        }
        call.respondBytesWriter(status = response.status, contentType = response.contentType()) {
            response.bodyAsChannel().copyTo(this)
        }
    }

    private suspend fun proxyWebSocket(
        target: String,
        downstream: io.ktor.server.websocket.DefaultWebSocketServerSession,
    ) {
        val wsUrl = toWsScheme(target) + downstream.call.request.uri
        try {
            client.clientWebSocket(wsUrl) {
                val upstream = this
                coroutineScope {
                    val scope = this
                    launch {
                        runCatching { downstream.incoming.consumeEach { upstream.send(it) } }
                        scope.cancel()
                    }
                    launch {
                        runCatching { upstream.incoming.consumeEach { downstream.send(it) } }
                        scope.cancel()
                    }
                }
            }
        } catch (e: Throwable) {
            runCatching { downstream.close() }
        }
    }

    /** Rewrite a redirect that points back at the target origin so the client stays on the proxy. */
    private fun rewriteLocation(name: String, value: String, target: String): String {
        if (!name.equals(HttpHeaders.Location, ignoreCase = true)) return value
        return if (value.startsWith(target)) publicOrigin() + value.removePrefix(target) else value
    }

    private fun normalizeTarget(raw: String): String {
        val trimmed = raw.trim().trimEnd('/')
        val withScheme = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "http://$trimmed"
        }
        require(withScheme.length > "http://".length) { "empty target" }
        return withScheme
    }

    private fun toWsScheme(target: String): String = when {
        target.startsWith("https://") -> "wss://" + target.removePrefix("https://")
        target.startsWith("http://") -> "ws://" + target.removePrefix("http://")
        else -> target
    }

    private companion object {
        val TrustAll = object : javax.net.ssl.X509TrustManager {
            override fun checkClientTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = emptyArray()
        }

        // Connection-level headers that must not be forwarded (RFC 7230), plus length/encoding
        // the engine recomputes and Host (set from the target URL).
        val REQUEST_STRIP = setOf(
            "host", "connection", "keep-alive", "proxy-authenticate", "proxy-authorization",
            "te", "trailer", "transfer-encoding", "upgrade", "content-length",
        )
        val RESPONSE_STRIP = setOf(
            "connection", "keep-alive", "proxy-authenticate", "proxy-authorization",
            "te", "trailer", "transfer-encoding", "upgrade", "content-length", "content-type",
        )

        fun isRequestHopByHop(name: String) = name.lowercase() in REQUEST_STRIP
        fun isResponseHopByHop(name: String) = name.lowercase() in RESPONSE_STRIP
    }
}
