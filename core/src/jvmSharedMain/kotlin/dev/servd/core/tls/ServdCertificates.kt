package dev.servd.core.tls

import io.ktor.network.tls.certificates.buildKeyStore
import io.ktor.network.tls.certificates.saveToFile
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.X509Certificate

/**
 * A loaded/generated self-signed keystore plus its verifiable fingerprint. Passwords are kept
 * as immutable Strings and handed out as fresh CharArrays via [keyStorePasswordChars] /
 * [privateKeyPasswordChars] - the TLS stack zeroes any CharArray it is given, so a shared array
 * would be wiped after the first server reads it.
 */
class TlsKeyStore(
    val keyStore: KeyStore,
    val alias: String,
    val keyStorePassword: String,
    val privateKeyPassword: String,
    val file: File,
    val fingerprintSha256: String,
) {
    fun keyStorePasswordChars(): CharArray = keyStorePassword.toCharArray()
    fun privateKeyPasswordChars(): CharArray = privateKeyPassword.toCharArray()
}

/**
 * Generates (once) and persists a self-signed TLS keystore for servd, then reloads it on
 * later runs. A LAN host has no public domain, so there is no certificate authority to trust
 * it - the fingerprint is how a user verifies the connection instead of a CA. The passwords
 * are not secrets: the keystore only protects a self-signed local cert.
 */
object ServdCertificates {
    private const val ALIAS = "servd"
    // Store and key share one password: PKCS12 (and Apache FtpServer's SSL layer) work best
    // when they match, and this is a local self-signed cert, so the value isn't a secret.
    private const val STORE_PASSWORD = "servd-store"
    private const val KEY_PASSWORD = STORE_PASSWORD
    private const val KEYSTORE_FILE = "keystore.jks"

    fun loadOrCreate(dir: File, hosts: List<String>): TlsKeyStore {
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, KEYSTORE_FILE)
        val storePw = STORE_PASSWORD.toCharArray()
        val keyPw = KEY_PASSWORD.toCharArray()

        val keyStore: KeyStore = if (file.exists()) {
            KeyStore.getInstance("JKS").apply {
                file.inputStream().use { load(it, storePw) }
            }
        } else {
            buildKeyStore {
                certificate(ALIAS) {
                    password = KEY_PASSWORD
                    domains = (hosts + listOf("localhost", "127.0.0.1")).distinct()
                    daysValid = 3650
                }
            }.also { it.saveToFile(file, STORE_PASSWORD) }
        }

        val cert = keyStore.getCertificate(ALIAS) as X509Certificate
        val fingerprint = MessageDigest.getInstance("SHA-256")
            .digest(cert.encoded)
            .joinToString(":") { "%02X".format(it) }

        return TlsKeyStore(keyStore, ALIAS, STORE_PASSWORD, KEY_PASSWORD, file, fingerprint)
    }
}
