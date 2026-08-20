package dev.servd.core.service

import java.io.File
import java.security.SecureRandom

/**
 * The single credential shared by servd's SSH and FTP services. Generated once on first run and
 * persisted, so the username/password stay stable across restarts (and match what the admin panel
 * shows). Not a high-value secret - it guards file access on a trusted LAN, not the open internet.
 */
object ServdCredentials {
    const val USERNAME = "servd"

    /** Load the persisted service password, generating one on first run. */
    fun loadOrCreatePassword(dir: File): String {
        val file = File(dir, "servd.password")
        val existing = runCatching { file.readText().trim() }.getOrNull()
        if (!existing.isNullOrBlank()) return existing
        val alphabet = "abcdefghijkmnpqrstuvwxyz23456789" // no easily-confused chars
        val rnd = SecureRandom()
        val password = buildString { repeat(14) { append(alphabet[rnd.nextInt(alphabet.length)]) } }
        runCatching { dir.mkdirs(); file.writeText(password) }
        return password
    }
}
