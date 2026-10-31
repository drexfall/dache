package dev.dache.core.provider.mock

import dev.dache.core.model.Billing
import dev.dache.core.model.Capability
import dev.dache.core.model.CiRun
import dev.dache.core.model.DeployStatus
import dev.dache.core.model.Deployment
import dev.dache.core.model.DnsRecord
import dev.dache.core.model.Domain
import dev.dache.core.model.Monitor
import dev.dache.core.model.MonitorStatus
import dev.dache.core.model.ProviderId
import dev.dache.core.model.ProviderSnapshot
import dev.dache.core.model.Server
import dev.dache.core.model.ServerStatus
import dev.dache.core.model.Site
import dev.dache.core.model.Worker
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * A fictional "acme" organisation spread over all six providers, wired so the linker has something to find:
 * acme.dev (Cloudflare) → www CNAME → Netlify site → github.com/acme/marketing, and so on.
 * IPs use the 203.0.113.0/24 documentation range. History comes from [MockHistory] and is deterministic.
 */
@Suppress("LongMethod")
object MockFixtures {
    fun capabilities(id: ProviderId): Set<Capability> =
        when (id) {
            ProviderId.NETLIFY, ProviderId.VERCEL -> setOf(Capability.SITES, Capability.DEPLOYMENTS, Capability.DOMAINS)
            ProviderId.CLOUDFLARE ->
                setOf(Capability.DOMAINS, Capability.DNS, Capability.SITES, Capability.DEPLOYMENTS, Capability.WORKERS)
            ProviderId.GITHUB -> setOf(Capability.CI, Capability.DEPLOYMENTS)
            ProviderId.DIGITALOCEAN -> setOf(Capability.SERVERS, Capability.DOMAINS, Capability.DNS, Capability.SITES)
            ProviderId.UPTIMEROBOT -> setOf(Capability.MONITORS)
            else -> emptySet()
        }

    fun snapshot(
        id: ProviderId,
        accountId: String,
        now: Instant,
    ): ProviderSnapshot =
        when (id) {
            ProviderId.NETLIFY -> netlify(accountId, now)
            ProviderId.VERCEL -> vercel(accountId, now)
            ProviderId.CLOUDFLARE -> cloudflare(accountId, now)
            ProviderId.GITHUB -> github(accountId, now)
            ProviderId.DIGITALOCEAN -> digitalOcean(accountId, now)
            ProviderId.UPTIMEROBOT -> uptimeRobot(accountId, now)
            else -> ProviderSnapshot()
        }

    /** Latest deploy as given, plus generated history before it. */
    private class SiteSpec(
        val provider: ProviderId,
        val id: String,
        val name: String,
        val webUrl: String,
        val primaryUrl: String,
        val customDomains: List<String>,
        val repoUrl: String,
        val latestStatus: DeployStatus,
        val latestBranch: String,
        val latestMessage: String,
        val latestAgo: Duration,
        val historyCount: Int,
        val failRate: Double,
        val buildSeconds: Int,
    )

    private fun site(
        accountId: String,
        now: Instant,
        spec: SiteSpec,
    ): Site {
        val latestAt = now - spec.latestAgo
        val latest =
            Deployment(
                nativeId = "${spec.id}-latest",
                webUrl = "${spec.webUrl}/deploys/${spec.id}-latest",
                status = spec.latestStatus,
                branch = spec.latestBranch,
                commitMessage = spec.latestMessage,
                createdAt = latestAt,
                durationSeconds = spec.buildSeconds.takeIf { spec.latestStatus != DeployStatus.BUILDING },
            )
        val history =
            MockHistory
                .builds(spec.id, now, latestAt, spec.historyCount, spec.failRate, spec.buildSeconds)
                .map { build ->
                    Deployment(
                        nativeId = build.id,
                        webUrl = "${spec.webUrl}/deploys/${build.id}",
                        status = build.status,
                        branch = "main",
                        commitMessage = null,
                        createdAt = build.at,
                        durationSeconds = build.durationSeconds,
                    )
                }
        return Site(
            providerId = spec.provider,
            accountId = accountId,
            nativeId = spec.id,
            webUrl = spec.webUrl,
            name = spec.name,
            primaryUrl = spec.primaryUrl,
            customDomains = spec.customDomains,
            repoUrl = spec.repoUrl,
            deployments = listOf(latest) + history,
        )
    }

    private fun netlify(
        accountId: String,
        now: Instant,
    ) = ProviderSnapshot(
        sites =
            listOf(
                site(
                    accountId,
                    now,
                    SiteSpec(
                        provider = ProviderId.NETLIFY,
                        id = "nf-site-marketing",
                        name = "acme-marketing",
                        webUrl = "https://app.netlify.com/sites/acme-marketing",
                        primaryUrl = "https://acme-marketing.netlify.app",
                        customDomains = listOf("acme.dev", "www.acme.dev"),
                        repoUrl = "https://github.com/acme/marketing",
                        latestStatus = DeployStatus.SUCCESS,
                        latestBranch = "main",
                        latestMessage = "Update pricing page copy",
                        latestAgo = 42.minutes,
                        historyCount = 34,
                        failRate = 0.05,
                        buildSeconds = 74,
                    ),
                ),
                site(
                    accountId,
                    now,
                    SiteSpec(
                        provider = ProviderId.NETLIFY,
                        id = "nf-site-docs",
                        name = "acme-docs",
                        webUrl = "https://app.netlify.com/sites/acme-docs",
                        primaryUrl = "https://acme-docs.netlify.app",
                        customDomains = listOf("docs.acme.dev"),
                        repoUrl = "git@github.com:acme/docs.git",
                        latestStatus = DeployStatus.FAILED,
                        latestBranch = "main",
                        latestMessage = "Add API reference for v2",
                        latestAgo = 12.minutes,
                        historyCount = 21,
                        failRate = 0.2,
                        buildSeconds = 138,
                    ),
                ),
            ),
    )

    private fun vercel(
        accountId: String,
        now: Instant,
    ) = ProviderSnapshot(
        sites =
            listOf(
                site(
                    accountId,
                    now,
                    SiteSpec(
                        provider = ProviderId.VERCEL,
                        id = "prj_app",
                        name = "acme-app",
                        webUrl = "https://vercel.com/acme/acme-app",
                        primaryUrl = "https://acme-app.vercel.app",
                        customDomains = listOf("app.acme.dev"),
                        repoUrl = "https://github.com/acme/app",
                        latestStatus = DeployStatus.BUILDING,
                        latestBranch = "feature/billing",
                        latestMessage = "Stripe checkout session",
                        latestAgo = 2.minutes,
                        historyCount = 58,
                        failRate = 0.08,
                        buildSeconds = 96,
                    ),
                ),
                site(
                    accountId,
                    now,
                    SiteSpec(
                        provider = ProviderId.VERCEL,
                        id = "prj_status",
                        name = "status-page",
                        webUrl = "https://vercel.com/acme/status-page",
                        primaryUrl = "https://status-page-acme.vercel.app",
                        customDomains = listOf("status.acme.dev"),
                        repoUrl = "https://github.com/acme/status",
                        latestStatus = DeployStatus.SUCCESS,
                        latestBranch = "main",
                        latestMessage = "Bump dependencies",
                        latestAgo = 3.days,
                        historyCount = 6,
                        failRate = 0.0,
                        buildSeconds = 41,
                    ),
                ),
            ),
    )

    private fun cloudflare(
        accountId: String,
        now: Instant,
    ): ProviderSnapshot {
        fun record(
            id: String,
            type: String,
            name: String,
            content: String,
        ) = DnsRecord(
            providerId = ProviderId.CLOUDFLARE,
            accountId = accountId,
            nativeId = id,
            webUrl = "https://dash.cloudflare.com/acme/acme.dev/dns/records",
            zoneName = "acme.dev",
            type = type,
            name = name,
            content = content,
        )
        return ProviderSnapshot(
            domains =
                listOf(
                    Domain(
                        providerId = ProviderId.CLOUDFLARE,
                        accountId = accountId,
                        nativeId = "zone-acme-dev",
                        webUrl = "https://dash.cloudflare.com/acme/acme.dev",
                        name = "acme.dev",
                        expiresAt = now + 21.days,
                    ),
                ),
            dnsRecords =
                listOf(
                    record("rec-apex", "A", "acme.dev", "75.2.60.5"),
                    record("rec-www", "CNAME", "www.acme.dev", "acme-marketing.netlify.app"),
                    record("rec-docs", "CNAME", "docs.acme.dev", "acme-docs.netlify.app"),
                    record("rec-app", "CNAME", "app.acme.dev", "cname.vercel-dns.com"),
                    record("rec-status", "CNAME", "status.acme.dev", "cname.vercel-dns.com"),
                    record("rec-blog", "CNAME", "blog.acme.dev", "acme-blog.pages.dev"),
                    record("rec-api", "A", "api.acme.dev", "203.0.113.10"),
                ),
            workers =
                listOf(
                    Worker(
                        providerId = ProviderId.CLOUDFLARE,
                        accountId = accountId,
                        nativeId = "acme-edge",
                        webUrl = "https://dash.cloudflare.com/acme/workers/services/view/acme-edge/production",
                        name = "acme-edge",
                        routes = listOf("api.acme.dev/v2/*"),
                        modifiedAt = now - 2.days,
                    ),
                ),
            sites =
                listOf(
                    site(
                        accountId,
                        now,
                        SiteSpec(
                            provider = ProviderId.CLOUDFLARE,
                            id = "pages-blog",
                            name = "acme-blog",
                            webUrl = "https://dash.cloudflare.com/acme/pages/view/acme-blog",
                            primaryUrl = "https://acme-blog.pages.dev",
                            customDomains = listOf("blog.acme.dev"),
                            repoUrl = "https://github.com/acme/blog",
                            latestStatus = DeployStatus.CANCELLED,
                            latestBranch = "drafts",
                            latestMessage = "WIP: launch post",
                            latestAgo = 5.hours,
                            historyCount = 12,
                            failRate = 0.1,
                            buildSeconds = 52,
                        ),
                    ),
                ),
        )
    }

    private fun github(
        accountId: String,
        now: Instant,
    ): ProviderSnapshot {
        class Workflow(
            val repo: String,
            val name: String,
            val branch: String,
            val status: DeployStatus,
            val ago: Duration,
            val historyCount: Int,
            val failRate: Double,
            val seconds: Int,
        )

        fun runs(
            id: Int,
            workflow: Workflow,
        ): List<CiRun> {
            fun run(
                runId: String,
                status: DeployStatus,
                branch: String,
                at: Instant,
                seconds: Int?,
            ) = CiRun(
                providerId = ProviderId.GITHUB,
                accountId = accountId,
                nativeId = runId,
                webUrl = "https://github.com/acme/${workflow.repo}/actions/runs/$runId",
                repoFullName = "acme/${workflow.repo}",
                repoUrl = "https://github.com/acme/${workflow.repo}",
                workflowName = workflow.name,
                branch = branch,
                status = status,
                createdAt = at,
                durationSeconds = seconds,
            )
            val latestAt = now - workflow.ago
            val latestSeconds = workflow.seconds.takeIf { workflow.status != DeployStatus.BUILDING }
            val latest = run("$id", workflow.status, workflow.branch, latestAt, latestSeconds)
            val seed = "gh-${workflow.repo}-${workflow.name}"
            return listOf(latest) +
                MockHistory
                    .builds(seed, now, latestAt, workflow.historyCount, workflow.failRate, workflow.seconds)
                    .mapIndexed { index, build ->
                        run("${id}0${index + 1}", build.status, "main", build.at, build.durationSeconds)
                    }
        }
        val workflows =
            listOf(
                Workflow("docs", "CI", "main", DeployStatus.FAILED, 14.minutes, 24, 0.18, 212),
                Workflow("app", "Tests", "feature/billing", DeployStatus.BUILDING, 3.minutes, 66, 0.1, 384),
                Workflow("marketing", "Lint", "main", DeployStatus.SUCCESS, 45.minutes, 30, 0.03, 48),
                Workflow("api", "Build & push image", "main", DeployStatus.SUCCESS, 2.hours, 19, 0.05, 296),
                Workflow("blog", "Preview", "drafts", DeployStatus.CANCELLED, 5.hours, 9, 0.0, 87),
            )
        return ProviderSnapshot(ciRuns = workflows.flatMapIndexed { index, w -> runs(9001 + index, w) })
    }

    private fun digitalOcean(
        accountId: String,
        now: Instant,
    ) = ProviderSnapshot(
        servers =
            listOf(
                Server(
                    providerId = ProviderId.DIGITALOCEAN,
                    accountId = accountId,
                    nativeId = "41000001",
                    webUrl = "https://cloud.digitalocean.com/droplets/41000001",
                    name = "api-prod-1",
                    region = "nyc3",
                    status = ServerStatus.ACTIVE,
                    publicIpv4 = "203.0.113.10",
                ),
                Server(
                    providerId = ProviderId.DIGITALOCEAN,
                    accountId = accountId,
                    nativeId = "41000002",
                    webUrl = "https://cloud.digitalocean.com/droplets/41000002",
                    name = "worker-1",
                    region = "fra1",
                    status = ServerStatus.OFF,
                    publicIpv4 = "203.0.113.24",
                ),
            ),
        domains =
            listOf(
                Domain(
                    providerId = ProviderId.DIGITALOCEAN,
                    accountId = accountId,
                    nativeId = "acme-internal.net",
                    webUrl = "https://cloud.digitalocean.com/networking/domains/acme-internal.net",
                    name = "acme-internal.net",
                    expiresAt = null,
                ),
            ),
        dnsRecords =
            listOf(
                DnsRecord(
                    providerId = ProviderId.DIGITALOCEAN,
                    accountId = accountId,
                    nativeId = "do-rec-1",
                    webUrl = "https://cloud.digitalocean.com/networking/domains/acme-internal.net",
                    zoneName = "acme-internal.net",
                    type = "A",
                    name = "worker.acme-internal.net",
                    content = "203.0.113.24",
                ),
            ),
        billing =
            Billing(
                currency = "USD",
                monthToDateUsage = 43.17,
                accountBalance = -43.17,
                dailyCost = MockHistory.dailyCost("do-billing", now, base = 1.62),
            ),
    )

    private fun uptimeRobot(
        accountId: String,
        now: Instant,
    ): ProviderSnapshot {
        class Spec(
            val id: String,
            val name: String,
            val url: String,
            val status: MonitorStatus,
            val baseMs: Int,
            val incidentDays: Int,
        )

        fun monitor(spec: Spec): Monitor {
            val down = spec.status == MonitorStatus.DOWN
            val responses =
                if (spec.status == MonitorStatus.PAUSED) {
                    emptyList()
                } else {
                    MockHistory.responseTimes(spec.id, now, spec.baseMs, downForHours = if (down) 2 else 0)
                }
            val daily =
                MockHistory.dailyUptime(
                    seed = spec.id,
                    now = now,
                    incidentDays = spec.incidentDays,
                    todayPercent = if (down) DOWN_TODAY_UPTIME else null,
                )
            return Monitor(
                providerId = ProviderId.UPTIMEROBOT,
                accountId = accountId,
                nativeId = spec.id,
                webUrl = "https://dashboard.uptimerobot.com/monitors/${spec.id}",
                name = spec.name,
                url = spec.url,
                status = spec.status,
                // Keep the headline figure consistent with the daily history it summarises.
                uptimePercent = kotlin.math.round(daily.map { it.value }.average() * 100) / 100,
                responseTimeMs = if (down) null else responses.lastOrNull()?.value?.toInt(),
                responseTimes = responses,
                dailyUptime =
                    MockHistory.dailyUptime(
                        seed = spec.id,
                        now = now,
                        incidentDays = spec.incidentDays,
                        todayPercent = if (down) DOWN_TODAY_UPTIME else null,
                    ),
            )
        }
        return ProviderSnapshot(
            monitors =
                listOf(
                    Spec("m-780001", "Marketing site", "https://acme.dev", MonitorStatus.UP, 182, 1),
                    Spec("m-780002", "Docs", "https://docs.acme.dev", MonitorStatus.DOWN, 240, 4),
                    Spec("m-780003", "Web app", "https://app.acme.dev/login", MonitorStatus.UP, 264, 2),
                    Spec("m-780004", "API health", "https://api.acme.dev/health", MonitorStatus.UP, 88, 0),
                    Spec("m-780005", "Status page", "https://status.acme.dev", MonitorStatus.PAUSED, 120, 0),
                ).map(::monitor),
        )
    }

    private const val DOWN_TODAY_UPTIME = 91.7
}
