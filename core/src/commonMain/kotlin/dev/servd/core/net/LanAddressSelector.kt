package dev.servd.core.net

/**
 * Chooses the best LAN / hotspot address to bind servd's servers to, given a list of
 * candidate [NetAddress]es. Pure Kotlin so it can be unit-tested with synthetic input.
 *
 * Strategy: keep only usable candidates (IPv4, up, not loopback), then rank them -
 * real private LAN addresses on Wi-Fi/hotspot interfaces win; VM / container / tunnel
 * interfaces are pushed to the bottom.
 */
object LanAddressSelector {

    // Substrings (matched case-insensitively) that mark an interface as not-a-real-LAN.
    private val VIRTUAL_HINTS = listOf(
        "vmnet", "vmware", "vbox", "virtualbox", "docker", "veth", "virbr",
        "hyper-v", "hyperv", "vethernet", "wsl", "tap", "tun", "loopback",
        "teredo", "isatap", "bluetooth", "pan0",
    )

    // Wi-Fi / hotspot interface hints (Linux wlan0/wlp*, Windows "Wi-Fi"/"Wireless", soft-AP).
    private val WIFI_HINTS = listOf("wlan", "wlp", "wi-fi", "wifi", "wireless", "softap", "ap0", "swlan")

    // Wired interface hints.
    private val ETHER_HINTS = listOf("eth", "enp", "eno", "ens", "ethernet")

    fun isVirtualName(name: String): Boolean = name.lowercase().let { n -> VIRTUAL_HINTS.any { n.contains(it) } }
    fun isWifiName(name: String): Boolean = name.lowercase().let { n -> WIFI_HINTS.any { n.contains(it) } }
    fun isEthernetName(name: String): Boolean = name.lowercase().let { n -> ETHER_HINTS.any { n.contains(it) } }

    /** An address servd could actually bind to. */
    fun isCandidate(a: NetAddress): Boolean = a.isIPv4 && a.isUp && !a.isLoopback

    /** Higher = more likely to be the LAN/hotspot address the user means. */
    fun score(a: NetAddress): Int {
        var s = 0
        if (a.isSiteLocal) s += 100
        if (isWifiName(a.interfaceName)) s += 50
        else if (isEthernetName(a.interfaceName)) s += 25
        if (a.isVirtual || isVirtualName(a.interfaceName)) s -= 200
        // Nudge toward the ranges hotspots/home routers hand out.
        when {
            a.ip.startsWith("192.168.") -> s += 10
            a.ip.startsWith("10.") -> s += 5
        }
        return s
    }

    /** Candidates only, best first (stable within equal scores). */
    fun ranked(list: List<NetAddress>): List<NetAddress> =
        list.filter { isCandidate(it) }.sortedByDescending { score(it) }

    /** The single best bind address, or null if nothing usable was found. */
    fun select(list: List<NetAddress>): NetAddress? = ranked(list).firstOrNull()
}
