package dev.servd.core.service

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private class FakeService(
    override val id: String,
    override val toggleable: Boolean,
) : Service {
    override val label: String = id
    override val port: Int = 1234
    var current: ServiceState = ServiceState.Stopped
    override val state: ServiceState get() = current
    override suspend fun start() { current = ServiceState.Running }
    override suspend fun stop() { current = ServiceState.Stopped }
}

class ServiceManagerTest {

    @Test
    fun toggleable_service_starts_and_stops() = runBlocking {
        val ssh = FakeService("ssh", toggleable = true)
        val mgr = ServiceManager(listOf(ssh))

        assertTrue(mgr.start("ssh"))
        assertEquals("Running", mgr.list().first { it.id == "ssh" }.state)
        assertTrue(mgr.stop("ssh"))
        assertEquals("Stopped", mgr.list().first { it.id == "ssh" }.state)
    }

    @Test
    fun non_toggleable_and_unknown_ids_are_rejected() = runBlocking {
        val http = FakeService("http", toggleable = false)
        val mgr = ServiceManager(listOf(http))

        assertFalse(mgr.start("http"))   // cannot toggle the always-on service
        assertFalse(mgr.stop("http"))
        assertFalse(mgr.start("nope"))   // unknown id
        assertEquals(ServiceState.Stopped, http.current) // untouched
    }

    @Test
    fun list_reports_all_registered_services() {
        val mgr = ServiceManager(listOf(FakeService("http", false), FakeService("ssh", true)))
        assertEquals(listOf("http", "ssh"), mgr.list().map { it.id })
    }
}
