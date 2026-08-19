package dev.servd.core.net

import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Enumerates the host's real network interfaces (JVM `java.net`) and maps them into the
 * platform-agnostic [NetAddress] shape that [LanAddressSelector] ranks.
 *
 * The same enumeration approach is reused by the Android target later (Android is JVM too),
 * so interfaces are classified by address, never by name.
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
