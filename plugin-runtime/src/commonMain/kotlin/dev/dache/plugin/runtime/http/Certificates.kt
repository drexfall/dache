package dev.dache.plugin.runtime.http

import io.ktor.client.engine.HttpClientEngine

/** What a server presented: its leaf certificate's SHA-256 fingerprint, and whether the system trusts it. */
data class CertificateProbe(
    val fingerprint: String,
    val trusted: Boolean,
)

internal expect fun pinnedHttpEngine(fingerprint: String): HttpClientEngine

internal expect suspend fun probeCertificate(
    host: String,
    port: Int,
): CertificateProbe?

/** `ab:CD…` and `abcd…` name the same certificate; compare them as uppercase colon-separated pairs. */
internal fun normalizeFingerprint(fingerprint: String): String =
    fingerprint
        .filter { it.isLetterOrDigit() }
        .uppercase()
        .chunked(2)
        .joinToString(":")
