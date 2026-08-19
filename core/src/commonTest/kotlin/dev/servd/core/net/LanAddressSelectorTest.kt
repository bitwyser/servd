package dev.servd.core.net

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LanAddressSelectorTest {

    private fun addr(
        ip: String,
        iface: String,
        ipv4: Boolean = true,
        loopback: Boolean = false,
        siteLocal: Boolean = ip.startsWith("192.168.") || ip.startsWith("10.") || ip.startsWith("172."),
        up: Boolean = true,
        virtual: Boolean = false,
    ) = NetAddress(ip, iface, ipv4, loopback, siteLocal, up, virtual)

    @Test
    fun prefers_wifi_lan_over_ethernet_and_virtual() {
        val chosen = LanAddressSelector.select(
            listOf(
                addr("127.0.0.1", "Loopback", loopback = true, siteLocal = false),
                addr("192.168.56.1", "VirtualBox Host-Only Network"),
                addr("192.168.1.20", "Ethernet"),
                addr("192.168.43.1", "Wi-Fi"),
            )
        )
        assertEquals("192.168.43.1", chosen?.ip)
    }

    @Test
    fun excludes_loopback_down_and_ipv6() {
        assertTrue(!LanAddressSelector.isCandidate(addr("127.0.0.1", "lo", loopback = true)))
        assertTrue(!LanAddressSelector.isCandidate(addr("192.168.1.5", "eth0", up = false)))
        assertTrue(!LanAddressSelector.isCandidate(addr("fe80::1", "wlan0", ipv4 = false)))
        assertTrue(LanAddressSelector.isCandidate(addr("192.168.1.5", "wlan0")))
    }

    @Test
    fun virtual_interfaces_rank_below_real_ones() {
        val real = addr("10.0.0.8", "eth0")
        val docker = addr("10.0.0.9", "docker0")
        assertTrue(LanAddressSelector.score(real) > LanAddressSelector.score(docker))
    }

    @Test
    fun returns_null_when_nothing_usable() {
        val chosen = LanAddressSelector.select(
            listOf(
                addr("127.0.0.1", "lo", loopback = true, siteLocal = false),
                addr("fe80::1", "wlan0", ipv4 = false, siteLocal = false),
            )
        )
        assertNull(chosen)
    }

    @Test
    fun linux_style_wifi_names_are_detected() {
        assertTrue(LanAddressSelector.isWifiName("wlp3s0"))
        assertTrue(LanAddressSelector.isWifiName("wlan0"))
        assertTrue(LanAddressSelector.isEthernetName("enp0s25"))
        assertTrue(LanAddressSelector.isVirtualName("br-docker0"))
    }
}
