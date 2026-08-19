package dev.servd.host

import dev.servd.core.Servd
import dev.servd.core.net.LanAddressSelector
import dev.servd.core.net.detectLanAddress
import dev.servd.core.net.enumerateAddresses

/**
 * Phase 0 entrypoint: prove the toolchain and the shared LAN-detection logic.
 * Prints the banner, the chosen bind address, and every candidate interface.
 * Real servers arrive in Phase 1.
 */
fun main() {
    println("${Servd.NAME} v${Servd.VERSION} - ${Servd.TAGLINE}")
    println("java ${System.getProperty("java.version")} | ${System.getProperty("os.name")} ${System.getProperty("os.arch")}")
    println()

    val candidates = enumerateAddresses().filter(LanAddressSelector::isCandidate)
    val chosen = detectLanAddress()

    if (chosen != null) {
        println("LAN address : ${chosen.ip}  (${chosen.interfaceName})")
        println("dashboard   : https://${chosen.ip}:8443   (coming in Phase 1)")
    } else {
        println("LAN address : none found - connect to Wi-Fi or start a hotspot, then retry")
    }

    println()
    println("candidates (${candidates.size}):")
    if (candidates.isEmpty()) {
        println("  (none)")
    } else {
        candidates
            .sortedByDescending(LanAddressSelector::score)
            .forEach { a ->
                val marker = if (a == chosen) ">" else " "
                println("  $marker ${a.ip.padEnd(16)} ${a.interfaceName}")
            }
    }
}
