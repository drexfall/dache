package dev.dache.core.icons

import dev.dache.core.dashboard.JsonDirectory
import dev.dache.core.model.ProviderIcon
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.serialization.Serializable
import okio.FileSystem
import okio.Path
import kotlin.io.encoding.Base64
import kotlin.time.Instant

/**
 * The last favicon fetch for [site]. [data] is the icon, base64; null when the site had none ([failed] false) or
 * couldn't be reached ([failed] true), so the next try waits accordingly instead of happening on every launch.
 */
@Serializable
data class CachedIcon(
    val site: String,
    val fetchedAt: Instant,
    val data: String? = null,
    val failed: Boolean = false,
) {
    val icon: ProviderIcon? get() = data?.let { runCatching { Base64.decode(it) }.getOrNull() }?.let(ProviderIcon::of)

    companion object {
        fun of(
            site: String,
            fetchedAt: Instant,
            icon: ProviderIcon?,
        ) = CachedIcon(site, fetchedAt, icon?.bytes?.let { Base64.encode(it) })
    }
}

/** Fetched provider icons, kept so they aren't fetched again on every launch. */
interface IconStore {
    fun load(): List<CachedIcon>

    suspend fun save(icon: CachedIcon)

    /** Forgets every site not in [sites]: those of removed plugins and accounts. */
    suspend fun keepOnly(sites: Collection<String>)

    object None : IconStore {
        override fun load(): List<CachedIcon> = emptyList()

        override suspend fun save(icon: CachedIcon) = Unit

        override suspend fun keepOnly(sites: Collection<String>) = Unit
    }
}

/** One small JSON file per site under [dir]. */
class FileIconStore(
    fileSystem: FileSystem,
    dir: Path,
    io: CoroutineDispatcher = Dispatchers.IO,
) : IconStore {
    private val files = JsonDirectory(fileSystem, dir, io)

    override fun load(): List<CachedIcon> = files.readAll(CachedIcon.serializer()).values.toList()

    override suspend fun save(icon: CachedIcon) = files.write(keyFor(icon.site), CachedIcon.serializer(), icon)

    override suspend fun keepOnly(sites: Collection<String>) = files.keepOnly(sites.map(::keyFor))

    // JsonDirectory keeps only letters, digits, '-' and '_' in file names; spell the rest of a site out so that
    // `a.b.com` and `ab.com` stay apart.
    private fun keyFor(site: String): String =
        site
            .substringAfter("://")
            .replace("_", "__")
            .replace(".", "_d")
            .replace(":", "_p")
}
