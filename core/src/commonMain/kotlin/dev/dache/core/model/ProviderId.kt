package dev.dache.core.model

/**
 * Providers supported in Phase 1. [id] is the stable plugin identifier used for token storage keys;
 * [tokenPageUrl] is where users create a token for that provider.
 */
enum class ProviderId(
    val id: String,
    val displayName: String,
    val dashboardUrl: String,
    val tokenPageUrl: String,
) {
    NETLIFY(
        id = "netlify",
        displayName = "Netlify",
        dashboardUrl = "https://app.netlify.com",
        tokenPageUrl = "https://app.netlify.com/user/applications#personal-access-tokens",
    ),
    VERCEL(
        id = "vercel",
        displayName = "Vercel",
        dashboardUrl = "https://vercel.com/dashboard",
        tokenPageUrl = "https://vercel.com/account/tokens",
    ),
    CLOUDFLARE(
        id = "cloudflare",
        displayName = "Cloudflare",
        dashboardUrl = "https://dash.cloudflare.com",
        tokenPageUrl = "https://dash.cloudflare.com/profile/api-tokens",
    ),
    GITHUB(
        id = "github",
        displayName = "GitHub",
        dashboardUrl = "https://github.com",
        tokenPageUrl = "https://github.com/settings/personal-access-tokens/new",
    ),
    DIGITALOCEAN(
        id = "digitalocean",
        displayName = "DigitalOcean",
        dashboardUrl = "https://cloud.digitalocean.com",
        tokenPageUrl = "https://cloud.digitalocean.com/account/api/tokens",
    ),
    UPTIMEROBOT(
        id = "uptimerobot",
        displayName = "UptimeRobot",
        dashboardUrl = "https://dashboard.uptimerobot.com",
        tokenPageUrl = "https://dashboard.uptimerobot.com/integrations",
    ),
}
