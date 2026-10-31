package dev.dache.plugin.cli

import dev.dache.plugin.api.IndexEntry
import dev.dache.plugin.api.RegistryIndex
import dev.dache.plugin.runtime.LoadResult
import dev.dache.plugin.runtime.ManifestLoader
import java.io.File
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/*
 * The registry side: build the index from a registry checkout, sign it, and make keys. Keys travel as base64 of
 * their raw 32 bytes (the Ed25519 seed, and the public point), the form the app pins.
 */

/**
 * Builds `index.json` from `<registry>/plugins/<id>/manifest.json` into [out], copying each manifest to
 * `plugins/<id>/<version>.json` byte for byte. Fails without writing anything when a manifest is invalid or sits
 * in a folder not named after its id.
 */
fun buildIndex(
    registry: File,
    out: File,
    serial: Long,
): Outcome {
    val folders =
        registry
            .resolve("plugins")
            .listFiles(File::isDirectory)
            .orEmpty()
            .sortedBy { it.name }
    val problems = mutableListOf<String>()
    if (folders.isEmpty()) problems += "${registry.path}: no plugins/<id>/ folders"
    val entries =
        folders.mapNotNull { folder ->
            val file = folder.resolve("manifest.json")
            entryFor(folder.name, file).onFailure { problems += "${file.path}: ${it.message}" }.getOrNull()
        }
    if (problems.isNotEmpty()) return Outcome(problems.joinToString("\n"), ok = false)
    entries.forEach { (entry, bytes) ->
        out.resolve(entry.path).apply { parentFile.mkdirs() }.writeBytes(bytes)
    }
    val index = RegistryIndex(RegistryIndex.CURRENT_VERSION, serial, entries.map { it.first })
    out.resolve(INDEX_FILE).writeText(RegistryIndex.encode(index) + "\n")
    return Outcome(entries.joinToString("\n") { "${it.first.id} ${it.first.version}" }, ok = true)
}

private fun entryFor(
    folder: String,
    file: File,
): Result<Pair<IndexEntry, ByteArray>> {
    if (!file.isFile) return Result.failure(IllegalArgumentException("missing"))
    val bytes = file.readBytes()
    return when (val result = ManifestLoader.load(bytes.decodeToString())) {
        is LoadResult.Invalid -> Result.failure(IllegalArgumentException(result.problems.joinToString("; ")))
        is LoadResult.Loaded -> {
            val manifest = result.manifest
            if (manifest.id != folder) {
                Result.failure(IllegalArgumentException("id \"${manifest.id}\" must match its folder \"$folder\""))
            } else {
                val entry =
                    IndexEntry(
                        id = manifest.id,
                        name = manifest.name,
                        version = manifest.version,
                        publisher = manifest.publisher,
                        path = "plugins/${manifest.id}/${manifest.version}.json",
                        sha256 = sha256Hex(bytes),
                        hosts = manifest.hosts,
                        auth = manifest.auth,
                        capabilities = manifest.capabilities,
                    )
                Result.success(entry to bytes)
            }
        }
    }
}

/** Writes `<file>.sig`: the base64 Ed25519 signature of [file]'s exact bytes. */
fun signFile(
    file: File,
    privateKey: String,
): Outcome {
    val key = runCatching { privateKeyOf(privateKey) }.getOrNull()
    return when {
        !file.isFile -> Outcome("${file.path}: file not found", ok = false)
        key == null -> Outcome("the key is not valid", ok = false)
        else -> {
            val sigFile = File(file.path + ".sig")
            sigFile.writeText(Base64.getEncoder().encodeToString(sign(file.readBytes(), key)) + "\n")
            Outcome("wrote ${sigFile.path}", ok = true)
        }
    }
}

/** Writes a new private key to [out], readable by its owner only, and returns the public key to pin. */
fun generateKey(out: File): Outcome {
    if (out.exists()) return Outcome("${out.path} exists; not overwriting a key", ok = false)
    val pair = KeyPairGenerator.getInstance(ALGORITHM).generateKeyPair()
    val seed =
        pair.private.encoded
            .takeLast(RAW_KEY_SIZE)
            .toByteArray()
    val point =
        pair.public.encoded
            .takeLast(RAW_KEY_SIZE)
            .toByteArray()
    out.writeText(Base64.getEncoder().encodeToString(seed) + "\n")
    out.setReadable(false, false)
    out.setReadable(true, true)
    out.setWritable(false, false)
    out.setWritable(true, true)
    return Outcome("private key written to ${out.path}\npublic key: ${Base64.getEncoder().encodeToString(point)}", true)
}

fun sign(
    data: ByteArray,
    key: PrivateKey,
): ByteArray =
    Signature.getInstance(ALGORITHM).run {
        initSign(key)
        update(data)
        sign()
    }

fun verify(
    data: ByteArray,
    signature: ByteArray,
    key: PublicKey,
): Boolean =
    Signature.getInstance(ALGORITHM).run {
        initVerify(key)
        update(data)
        verify(signature)
    }

/** A base64 raw 32-byte seed, wrapped in the fixed PKCS#8 header the JDK expects. */
fun privateKeyOf(base64: String): PrivateKey {
    val seed = Base64.getDecoder().decode(base64.trim())
    require(seed.size == RAW_KEY_SIZE)
    return KeyFactory.getInstance(ALGORITHM).generatePrivate(PKCS8EncodedKeySpec(PKCS8_PREFIX + seed))
}

/** A base64 raw 32-byte public key, wrapped in the fixed X.509 header the JDK expects. */
fun publicKeyOf(base64: String): PublicKey {
    val point = Base64.getDecoder().decode(base64.trim())
    require(point.size == RAW_KEY_SIZE)
    return KeyFactory.getInstance(ALGORITHM).generatePublic(X509EncodedKeySpec(X509_PREFIX + point))
}

fun sha256Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

const val INDEX_FILE = "index.json"
private const val ALGORITHM = "Ed25519"
private const val RAW_KEY_SIZE = 32
private val PKCS8_PREFIX = hex("302e020100300506032b657004220420")
private val X509_PREFIX = hex("302a300506032b6570032100")

private fun hex(text: String): ByteArray = text.chunked(2).map { it.toInt(radix = 16).toByte() }.toByteArray()
