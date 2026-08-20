package dev.servd.core.service

/**
 * The always-on HTTPS service. It hosts the dashboard, chat, files, and the admin panel itself,
 * so it cannot be toggled off (that would kill the panel doing the toggling). SSH and FTP arrive
 * as toggleable services in later phases.
 */
class HttpService(override val port: Int) : Service {
    override val id: String = "http"
    override val label: String = "HTTPS dashboard"
    override val toggleable: Boolean = false
    override val state: ServiceState = ServiceState.Running
    override suspend fun start() {}
    override suspend fun stop() {}
}
