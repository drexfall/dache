package dev.dache.plugin.runtime

import dev.dache.plugin.api.ManifestJson
import dev.dache.plugin.api.PluginManifest

/** A small but complete manifest for engine tests. Fake host, fake data. */
object TestManifests {
    val sitesJson =
        """
        {
          "manifestVersion": 1,
          "dashboardUrl": "https://dashboard.example.com",
          "id": "netlify",
          "name": "Example",
          "version": "1.0.0",
          "hosts": ["api.example.com"],
          "auth": { "type": "bearer" },
          "capabilities": ["SITES", "DEPLOYMENTS"],
          "validate": {
            "request": { "url": "https://api.example.com/user" },
            "displayName": "email | default:Example account"
          },
          "resources": [
            {
              "kind": "site",
              "request": {
                "url": "https://api.example.com/sites",
                "query": { "filter": "all" },
                "paging": { "type": "page", "sizeParam": "per_page", "size": 2, "maxPages": 3 }
              },
              "fields": {
                "nativeId": "id",
                "name": "name",
                "webUrl": "admin_url",
                "primaryUrl": "ssl_url",
                "customDomains": ["custom_domain | lower", "domain_aliases"],
                "repoUrl": "build_settings.repo_url",
                "deployments": {
                  "from": "published_deploy",
                  "fields": {
                    "nativeId": "id",
                    "webUrl": "deploy_ssl_url",
                    "status": "state",
                    "branch": "branch",
                    "commitMessage": "title",
                    "createdAt": "created_at | date",
                    "durationSeconds": "deploy_time"
                  },
                  "statusMap": { "ready": "SUCCESS", "building": "BUILDING", "error": "FAILED" }
                }
              }
            }
          ]
        }
        """.trimIndent()

    val sites: PluginManifest get() = ManifestJson.decode(sitesJson)
}
