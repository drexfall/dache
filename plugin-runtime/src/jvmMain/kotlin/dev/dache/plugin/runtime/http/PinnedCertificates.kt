package dev.dache.plugin.runtime.http

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

// Kept identical in jvmMain and androidMain: both use OkHttp and javax.net.ssl.

/**
 * An engine that trusts exactly one certificate: the server's leaf must have SHA-256 [fingerprint] (hex, any
 * case, colons allowed). For a self-hosted server with a self-signed certificate the user chose to trust.
 */
internal actual fun pinnedHttpEngine(fingerprint: String): HttpClientEngine {
    val pinned = normalizeFingerprint(fingerprint)
    val trust = PinnedTrustManager(pinned)
    val context = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trust), null) }
    return OkHttp.create {
        config {
            sslSocketFactory(context.socketFactory, trust)
            // The pin identifies the server; a self-signed certificate rarely names the address it's reached at.
            hostnameVerifier { _, _ -> true }
        }
    }
}

/**
 * Connects to [host]:[port] and reads the certificate it presents, without sending any request. Null when
 * nothing answers with TLS.
 */
internal actual suspend fun probeCertificate(
    host: String,
    port: Int,
): CertificateProbe? =
    withContext(Dispatchers.IO) {
        val capture = CapturingTrustManager()
        val context = SSLContext.getInstance("TLS").apply { init(null, arrayOf(capture), null) }
        try {
            (context.socketFactory.createSocket(host, port) as SSLSocket).use { socket ->
                socket.soTimeout = PROBE_TIMEOUT_MS
                socket.startHandshake()
            }
        } catch (_: IOException) {
            // A failed handshake can still have shown the certificate.
        }
        capture.leaf?.let { leaf -> CertificateProbe(fingerprintOf(leaf), capture.trusted) }
    }

private class PinnedTrustManager(
    private val pinned: String,
) : X509TrustManager {
    override fun checkServerTrusted(
        chain: Array<out X509Certificate>,
        authType: String,
    ) {
        val leaf = chain.firstOrNull() ?: throw CertificateException("no certificate")
        if (fingerprintOf(leaf) != pinned) throw CertificateException("certificate doesn't match the trusted one")
    }

    override fun checkClientTrusted(
        chain: Array<out X509Certificate>,
        authType: String,
    ) = throw CertificateException("not a server")

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}

/** Accepts anything, but only to record what was shown and whether the system would have trusted it. */
private class CapturingTrustManager : X509TrustManager {
    var leaf: X509Certificate? = null
    var trusted = false
    private val system =
        TrustManagerFactory
            .getInstance(TrustManagerFactory.getDefaultAlgorithm())
            .apply { init(null as java.security.KeyStore?) }
            .trustManagers
            .filterIsInstance<X509TrustManager>()
            .first()

    override fun checkServerTrusted(
        chain: Array<out X509Certificate>,
        authType: String,
    ) {
        leaf = chain.firstOrNull()
        trusted =
            try {
                system.checkServerTrusted(chain, authType)
                true
            } catch (_: CertificateException) {
                false
            }
    }

    override fun checkClientTrusted(
        chain: Array<out X509Certificate>,
        authType: String,
    ) = throw CertificateException("not a server")

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}

private fun fingerprintOf(certificate: X509Certificate): String =
    MessageDigest.getInstance("SHA-256").digest(certificate.encoded).joinToString(":") { "%02X".format(it) }

private const val PROBE_TIMEOUT_MS = 10_000
