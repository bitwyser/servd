package dev.servd.core.service

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable

enum class ServiceState { Stopped, Starting, Running, Error }

/**
 * A server servd can run (HTTP, and later SSH / FTP). The HTTP service is always on (it hosts
 * the dashboard and admin panel); optional services are [toggleable] from the admin panel.
 */
interface Service {
    val id: String        // "http", "ssh", "ftp"
    val label: String
    val port: Int
    val toggleable: Boolean
    val state: ServiceState

    /** Optional connection info shown in the (host-only) admin panel, e.g. credentials. */
    val detail: String? get() = null

    suspend fun start()
    suspend fun stop()
}

/** Serializable snapshot of a [Service] for the admin API. */
@Serializable
data class ServiceInfo(
    val id: String,
    val label: String,
    val port: Int,
    val toggleable: Boolean,
    val state: String,
    val detail: String? = null,
)

fun Service.toInfo(): ServiceInfo = ServiceInfo(id, label, port, toggleable, state.name, detail)

/** Registers servd's services and starts/stops the toggleable ones on request. */
class ServiceManager(services: List<Service>) {
    private val byId = LinkedHashMap<String, Service>()

    // Serialize all start/stop operations so a double-tap on a toggle can't start a service
    // twice (or race a start against a stop).
    private val lock = Mutex()

    init {
        services.forEach { byId[it.id] = it }
    }

    fun list(): List<ServiceInfo> = byId.values.map { it.toInfo() }

    fun get(id: String): Service? = byId[id]

    /** Start a toggleable service. Returns false for unknown or non-toggleable ids. */
    suspend fun start(id: String): Boolean = lock.withLock {
        val service = byId[id] ?: return@withLock false
        if (!service.toggleable) return@withLock false
        service.start()
        true
    }

    /** Stop a toggleable service. Returns false for unknown or non-toggleable ids. */
    suspend fun stop(id: String): Boolean = lock.withLock {
        val service = byId[id] ?: return@withLock false
        if (!service.toggleable) return@withLock false
        service.stop()
        true
    }
}
