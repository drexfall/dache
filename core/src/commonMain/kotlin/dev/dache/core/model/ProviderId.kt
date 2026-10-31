package dev.dache.core.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * A provider, named by the plugin that talks to it. [id] is the stable plugin identifier, also the TokenVault
 * namespace; two ids are equal when their [id]s are, whatever else they carry. The rest comes from the plugin's
 * manifest: [dashboardUrl] is the provider's web dashboard, [tokenPageUrl] where users create a token, and
 * [tokenHelp] least-privilege guidance shown before they do.
 *
 * Stored as [id] alone. A stored id carries only its [id] until the account repository matches it to an installed
 * plugin, so an account whose plugin was removed is still named by its id.
 */
@Serializable(with = ProviderIdSerializer::class)
class ProviderId(
    val id: String,
    val displayName: String = id,
    val dashboardUrl: String? = null,
    val tokenPageUrl: String? = null,
    val tokenHelp: String? = null,
) : Comparable<ProviderId> {
    override fun equals(other: Any?): Boolean = other is ProviderId && other.id == id

    override fun hashCode(): Int = id.hashCode()

    override fun compareTo(other: ProviderId): Int = id.compareTo(other.id)

    override fun toString(): String = id

    /** The bundled providers, for tests and sample data. The app gets its providers from plugins. */
    companion object {
        val NETLIFY =
            ProviderId(
                id = "netlify",
                displayName = "Netlify",
                dashboardUrl = "https://app.netlify.com",
                tokenPageUrl = "https://app.netlify.com/user/applications#personal-access-tokens",
            )
        val VERCEL =
            ProviderId(
                id = "vercel",
                displayName = "Vercel",
                dashboardUrl = "https://vercel.com/dashboard",
                tokenPageUrl = "https://vercel.com/account/tokens",
            )
        val CLOUDFLARE =
            ProviderId(
                id = "cloudflare",
                displayName = "Cloudflare",
                dashboardUrl = "https://dash.cloudflare.com",
                tokenPageUrl = "https://dash.cloudflare.com/profile/api-tokens",
            )
        val GITHUB =
            ProviderId(
                id = "github",
                displayName = "GitHub",
                dashboardUrl = "https://github.com",
                tokenPageUrl = "https://github.com/settings/personal-access-tokens/new",
            )
        val DIGITALOCEAN =
            ProviderId(
                id = "digitalocean",
                displayName = "DigitalOcean",
                dashboardUrl = "https://cloud.digitalocean.com",
                tokenPageUrl = "https://cloud.digitalocean.com/account/api/tokens",
            )
        val UPTIMEROBOT =
            ProviderId(
                id = "uptimerobot",
                displayName = "UptimeRobot",
                dashboardUrl = "https://dashboard.uptimerobot.com",
                tokenPageUrl = "https://dashboard.uptimerobot.com/integrations",
            )

        val entries: List<ProviderId> = listOf(NETLIFY, VERCEL, CLOUDFLARE, GITHUB, DIGITALOCEAN, UPTIMEROBOT)
    }
}

/** Writes the id. Reads ids, and the enum names (`NETLIFY`) that builds before plugins were installable stored. */
internal object ProviderIdSerializer : KSerializer<ProviderId> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("dev.dache.core.model.ProviderId", PrimitiveKind.STRING)

    override fun serialize(
        encoder: Encoder,
        value: ProviderId,
    ) = encoder.encodeString(value.id)

    override fun deserialize(decoder: Decoder): ProviderId = ProviderId(decoder.decodeString().lowercase())
}
