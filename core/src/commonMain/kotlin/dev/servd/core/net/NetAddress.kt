package dev.servd.core.net

/**
 * A platform-agnostic snapshot of one network address on one interface.
 *
 * Deliberately holds only primitives so the selection logic in [LanAddressSelector]
 * is pure Kotlin and unit-testable without `java.net`. Platform code (e.g. the desktop
 * `NetworkScan`) enumerates real interfaces and maps them into this shape.
 */
data class NetAddress(
    val ip: String,
    /** Human-facing interface name (Windows uses "Wi-Fi"/"Ethernet"; Linux "wlan0"/"eth0"). */
    val interfaceName: String,
    val isIPv4: Boolean,
    val isLoopback: Boolean,
    /** True for RFC 1918 private ranges (192.168/16, 10/8, 172.16/12) - i.e. a real LAN address. */
    val isSiteLocal: Boolean,
    val isUp: Boolean,
    /** The OS flagged the interface itself as virtual (a sub-interface). */
    val isVirtual: Boolean,
)
