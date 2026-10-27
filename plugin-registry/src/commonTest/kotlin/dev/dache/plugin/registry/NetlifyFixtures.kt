package dev.dache.plugin.registry

/** Trimmed Netlify API responses with made-up data. */
object NetlifyFixtures {
    const val TOKEN = "nfp_fake_token_for_tests_only"

    val user =
        """
        {
          "id": "5f0c0ffee0000000000000a1",
          "uid": "5f0c0ffee0000000000000a1",
          "full_name": "Dev Example",
          "email": "dev@example.com",
          "site_count": 2,
          "created_at": "2024-02-01T10:00:00.000Z"
        }
        """.trimIndent()

    val sitesPage1 =
        """
        [
          {
            "id": "2f9c6a1e-0000-4000-8000-00000000d0c5",
            "site_id": "2f9c6a1e-0000-4000-8000-00000000d0c5",
            "name": "acme-docs",
            "state": "current",
            "url": "https://docs.acme.example",
            "ssl_url": "https://docs.acme.example",
            "admin_url": "https://app.netlify.com/sites/acme-docs",
            "custom_domain": "Docs.Acme.example",
            "domain_aliases": ["www.docs.acme.example"],
            "build_settings": {
              "provider": "github",
              "repo_url": "https://github.com/acme/docs",
              "repo_branch": "main"
            },
            "published_deploy": {
              "id": "6720aa000000000000000001",
              "site_id": "2f9c6a1e-0000-4000-8000-00000000d0c5",
              "state": "ready",
              "name": "acme-docs",
              "admin_url": "https://app.netlify.com/sites/acme-docs",
              "deploy_ssl_url": "https://6720aa000000000000000001--acme-docs.netlify.app",
              "created_at": "2026-10-27T08:15:02.531Z",
              "published_at": "2026-10-27T08:16:40.112Z",
              "title": "Update install guide",
              "context": "production",
              "branch": "main",
              "commit_ref": "0a1b2c3d4e5f60718293a4b5c6d7e8f901234567",
              "deploy_time": 94
            },
            "created_at": "2024-03-10T12:00:00.000Z",
            "updated_at": "2026-10-27T08:16:40.112Z"
          },
          {
            "id": "7b3e2d4c-0000-4000-8000-0000000b10c9",
            "site_id": "7b3e2d4c-0000-4000-8000-0000000b10c9",
            "name": "acme-blog",
            "state": "current",
            "url": "http://acme-blog.netlify.app",
            "ssl_url": "https://acme-blog.netlify.app",
            "admin_url": "https://app.netlify.com/sites/acme-blog",
            "custom_domain": null,
            "domain_aliases": [],
            "build_settings": {},
            "published_deploy": null,
            "created_at": "2025-01-05T09:00:00.000Z",
            "updated_at": "2025-01-05T09:00:00.000Z"
          }
        ]
        """.trimIndent()

    const val UNAUTHORIZED = """{"code":401,"message":"Access Denied: Invalid token"}"""
}
