package dev.dache.plugin.registry

/** Trimmed Cloudflare API v4 responses with made-up ids and data. */
object CloudflareFixtures {
    const val TOKEN = "cf_fake_token_for_tests_only"
    const val ACCOUNT_ID = "0123456789abcdef0123456789abcdef"
    const val ZONE_ID = "fedcba9876543210fedcba9876543210"

    const val UNAUTHORIZED =
        """{"success":false,"errors":[{"code":9109,"message":"Invalid access token"}],"messages":[],"result":null}"""

    private fun list(result: String) = """{"success": true, "errors": [], "messages": [], "result": $result}"""

    val accounts = list("""[{"id": "$ACCOUNT_ID", "name": "Acme Inc", "type": "standard"}]""")

    val zones =
        list(
            """
            [
              {
                "id": "$ZONE_ID",
                "name": "acme.example",
                "status": "active",
                "account": { "id": "$ACCOUNT_ID", "name": "Acme Inc" }
              }
            ]
            """.trimIndent(),
        )

    val certificatePacks =
        list(
            """
            [
              {
                "id": "pack-universal-1",
                "type": "universal",
                "hosts": ["acme.example", "*.acme.example"],
                "status": "active",
                "certificates": [
                  { "id": "cert-1", "status": "active", "expires_on": "2027-01-15T12:00:00Z" }
                ]
              },
              {
                "id": "pack-advanced-2",
                "type": "advanced",
                "hosts": ["shop.acme.example"],
                "status": "validation_timed_out",
                "certificates": []
              }
            ]
            """.trimIndent(),
        )

    val dnsRecords =
        list(
            """
            [
              { "id": "rec-www", "type": "CNAME", "name": "www.acme.example", "content": "acme-blog.pages.dev",
                "proxied": true, "ttl": 1 },
              { "id": "rec-apex", "type": "A", "name": "acme.example", "content": "203.0.113.10",
                "proxied": false, "ttl": 300 }
            ]
            """.trimIndent(),
        )

    val pagesProjects =
        list(
            """
            [
              {
                "id": "pages-fake-blog-0001",
                "name": "acme-blog",
                "subdomain": "acme-blog.pages.dev",
                "domains": ["acme-blog.pages.dev", "www.acme.example"],
                "production_branch": "main",
                "source": { "type": "github", "config": { "owner": "acme", "repo_name": "blog" } }
              },
              {
                "id": "pages-fake-docs-0002",
                "name": "acme-docs",
                "subdomain": "acme-docs.pages.dev",
                "domains": ["acme-docs.pages.dev"]
              }
            ]
            """.trimIndent(),
        )

    val blogDeployments =
        list(
            """
            [
              {
                "id": "dep-fake-0002",
                "short_id": "dep0002",
                "url": "https://dep0002.acme-blog.pages.dev",
                "created_on": "2026-10-27T09:30:00.123456Z",
                "latest_stage": { "name": "build", "status": "failure" },
                "deployment_trigger": {
                  "type": "github:push",
                  "metadata": { "branch": "main", "commit_message": "Add launch post" }
                }
              },
              {
                "id": "dep-fake-0001",
                "short_id": "dep0001",
                "url": "https://dep0001.acme-blog.pages.dev",
                "created_on": "2026-10-26T08:00:00Z",
                "latest_stage": { "name": "deploy", "status": "success" },
                "deployment_trigger": { "type": "ad_hoc", "metadata": { "branch": "main" } }
              }
            ]
            """.trimIndent(),
        )

    val noDeployments = list("[]")

    val workers =
        list(
            """
            [
              {
                "id": "acme-api",
                "created_on": "2026-01-10T10:00:00Z",
                "modified_on": "2026-10-20T16:45:00Z",
                "routes": [{ "id": "route-1", "pattern": "api.acme.example/*", "script": "acme-api" }]
              },
              { "id": "acme-cron", "created_on": "2026-02-01T10:00:00Z", "modified_on": "2026-02-01T10:00:00Z" }
            ]
            """.trimIndent(),
        )
}
