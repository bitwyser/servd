package dev.servd.core.net

import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Enumerates the host's real network interfaces (JVM `java.net`) and maps them into the
 * platform-agnostic [NetAddress] shape that [LanAddressSelector] ranks.
 *
 * Lives in `jvmSharedMain` so BOTH JVM targets — Android and desktop — use the exact same
 * enumeration. Interfaces are classified by address, never by OS-specific naming.
 */
fun enumerateAddresses(): List<NetAddress> {
    val interfaces = try {
        NetworkInterface.getNetworkInterfaces()
    } catch (_: Exception) {
        null
    } ?: return emptyList()

    val out = ArrayList<NetAddress>()
    for (iface in interfaces) {
        val isUp = runCatching { iface.isUp }.getOrDefault(false)
        val ifaceLoopback = runCatching { iface.isLoopback }.getOrDefault(false)
        val isVirtual = runCatching { iface.isVirtual }.getOrDefault(false)
        val name = iface.displayName ?: iface.name ?: ""

        for (addr in iface.inetAddresses) {
            val raw = addr.hostAddress ?: continue
            val ip = raw.substringBefore('%') // strip IPv6 scope id, e.g. "fe80::1%eth0"
            out += NetAddress(
                ip = ip,
                interfaceName = name,
                isIPv4 = addr is Inet4Address,
                isLoopback = addr.isLoopbackAddress || ifaceLoopback,
                isSiteLocal = addr.isSiteLocalAddress,
                isUp = isUp,
                isVirtual = isVirtual,
            )
        }
    }
    return out
}

/** The best LAN/hotspot address to bind to, or null if none is currently available. */
fun detectLanAddress(): NetAddress? = LanAddressSelector.select(enumerateAddresses())
