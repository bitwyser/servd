package dev.servd.host

import dev.servd.core.Servd
import dev.servd.core.discovery.MdnsAdvertiser
import dev.servd.core.discovery.MdnsBrowser
import dev.servd.core.net.detectLanAddress
import dev.servd.core.server.ServdServer
import dev.servd.core.service.FtpService
import dev.servd.core.service.ServdCredentials
import dev.servd.core.service.SshService
import dev.servd.core.tls.ServdCertificates
import io.ktor.server.netty.Netty
import java.awt.Desktop
import java.io.File
import java.net.InetAddress
import java.net.URI

/**
 * servd desktop entrypoint.
 *
 *   servd [start] [--port N] [--host IP] [--dir PATH] [--no-open]   start the hub
 *   servd discover                                                  find hubs on the network
 */
fun main(args: Array<String>) {
    // Quiet the benign Netty TLS-handshake warnings logged when a browser declines the
    // self-signed cert before the user clicks "proceed". Set before any logging happens.
    System.setProperty("org.slf4j.simpleLogger.log.io.netty", "error")
    // Silence the Ktor Netty dispatch logger. On Ctrl+C it floods the console with
    // "RejectedExecutionException: event executor terminated" - an in-flight HTTP/2 frame
    // racing the event loop's shutdown. The server stops cleanly regardless; this is only noise.
    System.setProperty("org.slf4j.simpleLogger.log.io.ktor.server.netty.CIO", "off")

    if (args.firstOrNull() == "discover") {
        runDiscover()
        return
    }

    val opts = Opts.parse(args)

    val lan = detectLanAddress()
    // Bind all interfaces by default (so localhost AND the LAN IP both work); a specific
    // --host overrides both. Advertise the LAN IP in the URL for other devices.
    val advertisedHost = opts.host ?: lan?.ip ?: "127.0.0.1"
    val bindHost = opts.host ?: "0.0.0.0"
    val dataDir = File(opts.dir ?: (System.getProperty("user.home") + File.separator + ".servd"))

    println("${Servd.NAME} v${Servd.VERSION} - ${Servd.TAGLINE}")
    if (opts.host == null && lan == null) {
        println("(no LAN address found - reachable at 127.0.0.1 only; connect Wi-Fi/hotspot for other devices)")
    }

    val tls = ServdCertificates.loadOrCreate(dataDir, listOfNotNull(advertisedHost, lan?.ip))
    val filesDir = File(dataDir, "files")
    // Optional services (off until enabled from the admin panel), sharing one credential.
    val servdPassword = ServdCredentials.loadOrCreatePassword(dataDir)
    val sshService = SshService(
        port = 2222,
        filesDir = filesDir,
        hostKeyFile = File(dataDir, "ssh_host_key.ser"),
        username = ServdCredentials.USERNAME,
        password = servdPassword,
    )
    val ftpService = FtpService(
        port = 2121,
        filesDir = filesDir,
        tls = tls,
        username = ServdCredentials.USERNAME,
        password = servdPassword,
    )
    val server = ServdServer(
        Netty, bindHost, advertisedHost, opts.port, tls, filesDir, listOf(ftpService, sshService),
        hostName = hostName(),
        interfaceName = lan?.interfaceName,
        browseDir = opts.browseDir,
        browseWritable = opts.browseWrite,
    )

    // When bound to all interfaces, the host reaches admin via loopback; a specific --host
    // means admin is only available if that host is itself loopback.
    val localUrl = if (bindHost == "0.0.0.0") "https://127.0.0.1:${opts.port}/" else server.url

    println()
    if (bindHost == "0.0.0.0") {
        println("admin   : https://127.0.0.1:${opts.port}   (this machine only)")
    }
    println("serving : ${server.url}   (share with other devices)")
    println("cert    : self-signed, SHA-256 fingerprint:")
    println("          ${tls.fingerprintSha256}")
    println("keystore: ${tls.file}")
    println()
    // Advertise over mDNS so other devices can find this hub without typing an IP.
    val advertiser = MdnsAdvertiser()
    val advertised = runCatching {
        advertiser.start(
            bindAddress = InetAddress.getByName(advertisedHost),
            instanceName = "servd@" + hostName(),
            port = opts.port,
            txt = mapOf(
                "version" to Servd.VERSION,
                "fingerprint" to tls.fingerprintSha256,
                "https" to "true",
                "path" to "/",
            ),
        )
    }.isSuccess
    Runtime.getRuntime().addShutdownHook(Thread { println("\nshutting down..."); advertiser.stop(); server.stop() })

    println("Open that URL on any device on this network. The browser will warn about the")
    println("self-signed certificate - that's expected; verify the fingerprint above, then proceed.")
    if (advertised) println("discovery: advertising _servd._tcp - other devices can run `servd discover`.")
    println("services: SSH/SFTP (2222) and FTPS (2121) available - enable them from the admin panel.")
    println("Press Ctrl+C to stop.")

    if (!opts.noOpen) openBrowserSoon(localUrl)

    server.start(wait = true)
}

private fun runDiscover() {
    println("${Servd.NAME} - searching for hubs on the local network (~3s)...")
    val lan = detectLanAddress()
    val bind = runCatching { lan?.ip?.let { InetAddress.getByName(it) } }.getOrNull()
    val hubs = runCatching { MdnsBrowser().discover(durationMillis = 3000, bindAddress = bind) }.getOrDefault(emptyList())
    println()
    if (hubs.isEmpty()) {
        println("No servd hubs found. Make sure a hub is running on this network.")
    } else {
        println("Found ${hubs.size} hub(s):")
        hubs.forEach { println("  ${it.url}   ${it.name}   v${it.version ?: "?"}") }
    }
}

private fun hostName(): String =
    runCatching { InetAddress.getLocalHost().hostName }.getOrNull()?.substringBefore('.') ?: "host"

private fun openBrowserSoon(url: String) {
    Thread {
        runCatching {
            Thread.sleep(900)
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI(url))
            }
        }
    }.apply { isDaemon = true }.start()
}

private data class Opts(
    val port: Int = 8443,
    val host: String? = null,
    val dir: String? = null,
    val noOpen: Boolean = false,
    val browseDir: String? = null,
    val browseWrite: Boolean = false,
) {
    companion object {
        fun parse(args: Array<String>): Opts {
            var port = 8443
            var host: String? = null
            var dir: String? = null
            var noOpen = false
            var browseDir: String? = null
            var browseWrite = false
            var i = 0
            while (i < args.size) {
                when (args[i]) {
                    "start" -> {} // optional verb
                    "--port" -> port = args.getOrNull(++i)?.toIntOrNull() ?: port
                    "--host" -> host = args.getOrNull(++i)
                    "--dir" -> dir = args.getOrNull(++i)
                    "--no-open" -> noOpen = true
                    "--browse-dir" -> browseDir = args.getOrNull(++i)
                    "--browse-write" -> browseWrite = true
                }
                i++
            }
            return Opts(port, host, dir, noOpen, browseDir, browseWrite)
        }
    }
}
