// Generated with a throwaway test key; the matching private key is not kept anywhere.
package dev.dache.plugin.registry

internal object RegistryFixtures {
    const val PUBLIC_KEY = "q/1oC7fmpf5kH148zMC0OWfZ5RFLrSqsbNl1yqFB1Ds="

    val NETLIFY_1_1_0 =
        """{
  "${'$'}schema": "../../schema/manifest.schema.json",
  "manifestVersion": 1,
  "id": "netlify",
  "name": "Netlify",
  "version": "1.1.0",
  "publisher": "Dache",
  "dashboardUrl": "https://app.netlify.com",
  "tokenUrl": "https://app.netlify.com/user/applications#personal-access-tokens",
  "tokenHelp": "Netlify personal access tokens can’t be scoped. Give it an expiry date and use it only for Dache.",
  "hosts": [
    "api.netlify.com"
  ],
  "auth": {
    "type": "bearer"
  },
  "capabilities": [
    "SITES",
    "DEPLOYMENTS",
    "DOMAINS",
    "DNS",
    "SERVICES"
  ],
  "validate": {
    "request": {
      "url": "https://api.netlify.com/api/v1/user"
    },
    "displayName": "email | default:Netlify"
  },
  "resources": [
    {
      "kind": "site",
      "request": {
        "url": "https://api.netlify.com/api/v1/sites",
        "query": {
          "filter": "all"
        },
        "paging": {
          "type": "page",
          "param": "page",
          "sizeParam": "per_page",
          "size": 100,
          "maxPages": 10
        }
      },
      "fields": {
        "nativeId": "id",
        "name": "name",
        "webUrl": "admin_url",
        "primaryUrl": "ssl_url",
        "customDomains": [
          "custom_domain | lower",
          "domain_aliases"
        ],
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
          "statusMap": {
            "ready": "SUCCESS",
            "error": "FAILED",
            "rejected": "CANCELLED",
            "new": "BUILDING",
            "pending_review": "BUILDING",
            "accepted": "BUILDING",
            "enqueued": "BUILDING",
            "building": "BUILDING",
            "uploading": "BUILDING",
            "uploaded": "BUILDING",
            "preparing": "BUILDING",
            "prepared": "BUILDING",
            "processing": "BUILDING",
            "processed": "BUILDING",
            "retrying": "BUILDING"
          }
        }
      }
    },
    {
      "kind": "domain",
      "permission": "DNS Read",
      "optional": true,
      "request": {
        "url": "https://api.netlify.com/api/v1/dns_zones"
      },
      "fields": {
        "nativeId": "id",
        "name": "name",
        "webUrl": "https://app.netlify.com/teams/{account_slug}/dns/{name}"
      }
    },
    {
      "kind": "dnsRecord",
      "permission": "DNS Read",
      "optional": true,
      "forEach": {
        "request": {
          "url": "https://api.netlify.com/api/v1/dns_zones"
        }
      },
      "request": {
        "url": "https://api.netlify.com/api/v1/dns_zones/{id}/dns_records"
      },
      "fields": {
        "nativeId": "id",
        "webUrl": "https://app.netlify.com/teams/{^.account_slug}/dns/{^.name}",
        "zoneName": "^.name",
        "type": "type",
        "name": "hostname",
        "content": "value"
      }
    },
    {
      "kind": "service",
      "service": {
        "id": "form",
        "label": "Forms"
      },
      "permission": "Forms Read",
      "optional": true,
      "forEach": {
        "request": {
          "url": "https://api.netlify.com/api/v1/sites",
          "query": {
            "filter": "all"
          },
          "paging": {
            "type": "page",
            "param": "page",
            "sizeParam": "per_page",
            "size": 100,
            "maxPages": 10
          }
        }
      },
      "request": {
        "url": "https://api.netlify.com/api/v1/sites/{id}/forms"
      },
      "fields": {
        "nativeId": "id",
        "name": "name",
        "webUrl": "https://app.netlify.com/sites/{^.name}/forms/{id}",
        "detail": "{submission_count} submissions",
        "createdAt": "created_at | date"
      }
    }
  ],
  "pages": [
    {
      "id": "sites",
      "title": "Sites",
      "resources": [
        "site"
      ]
    },
    {
      "id": "dns",
      "title": "DNS",
      "resources": [
        "domain",
        "dnsRecord"
      ]
    },
    {
      "id": "forms",
      "title": "Forms",
      "resources": [
        "form"
      ]
    }
  ]
}"""

    val NETLIFY_2_0_0 =
        """{
  "${'$'}schema": "../../schema/manifest.schema.json",
  "manifestVersion": 1,
  "id": "netlify",
  "name": "Netlify",
  "version": "2.0.0",
  "publisher": "Dache",
  "dashboardUrl": "https://app.netlify.com",
  "tokenUrl": "https://app.netlify.com/user/applications#personal-access-tokens",
  "tokenHelp": "Netlify personal access tokens can’t be scoped. Give it an expiry date and use it only for Dache.",
  "hosts": [
    "api.netlify.com",
    "uploads.netlify.com"
  ],
  "auth": {
    "type": "bearer"
  },
  "capabilities": [
    "SITES",
    "DEPLOYMENTS",
    "DOMAINS",
    "DNS",
    "SERVICES"
  ],
  "validate": {
    "request": {
      "url": "https://api.netlify.com/api/v1/user"
    },
    "displayName": "email | default:Netlify"
  },
  "resources": [
    {
      "kind": "site",
      "request": {
        "url": "https://api.netlify.com/api/v1/sites",
        "query": {
          "filter": "all"
        },
        "paging": {
          "type": "page",
          "param": "page",
          "sizeParam": "per_page",
          "size": 100,
          "maxPages": 10
        }
      },
      "fields": {
        "nativeId": "id",
        "name": "name",
        "webUrl": "admin_url",
        "primaryUrl": "ssl_url",
        "customDomains": [
          "custom_domain | lower",
          "domain_aliases"
        ],
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
          "statusMap": {
            "ready": "SUCCESS",
            "error": "FAILED",
            "rejected": "CANCELLED",
            "new": "BUILDING",
            "pending_review": "BUILDING",
            "accepted": "BUILDING",
            "enqueued": "BUILDING",
            "building": "BUILDING",
            "uploading": "BUILDING",
            "uploaded": "BUILDING",
            "preparing": "BUILDING",
            "prepared": "BUILDING",
            "processing": "BUILDING",
            "processed": "BUILDING",
            "retrying": "BUILDING"
          }
        }
      }
    },
    {
      "kind": "domain",
      "permission": "DNS Read",
      "optional": true,
      "request": {
        "url": "https://api.netlify.com/api/v1/dns_zones"
      },
      "fields": {
        "nativeId": "id",
        "name": "name",
        "webUrl": "https://app.netlify.com/teams/{account_slug}/dns/{name}"
      }
    },
    {
      "kind": "dnsRecord",
      "permission": "DNS Read",
      "optional": true,
      "forEach": {
        "request": {
          "url": "https://api.netlify.com/api/v1/dns_zones"
        }
      },
      "request": {
        "url": "https://api.netlify.com/api/v1/dns_zones/{id}/dns_records"
      },
      "fields": {
        "nativeId": "id",
        "webUrl": "https://app.netlify.com/teams/{^.account_slug}/dns/{^.name}",
        "zoneName": "^.name",
        "type": "type",
        "name": "hostname",
        "content": "value"
      }
    },
    {
      "kind": "service",
      "service": {
        "id": "form",
        "label": "Forms"
      },
      "permission": "Forms Read",
      "optional": true,
      "forEach": {
        "request": {
          "url": "https://api.netlify.com/api/v1/sites",
          "query": {
            "filter": "all"
          },
          "paging": {
            "type": "page",
            "param": "page",
            "sizeParam": "per_page",
            "size": 100,
            "maxPages": 10
          }
        }
      },
      "request": {
        "url": "https://api.netlify.com/api/v1/sites/{id}/forms"
      },
      "fields": {
        "nativeId": "id",
        "name": "name",
        "webUrl": "https://app.netlify.com/sites/{^.name}/forms/{id}",
        "detail": "{submission_count} submissions",
        "createdAt": "created_at | date"
      }
    }
  ],
  "pages": [
    {
      "id": "sites",
      "title": "Sites",
      "resources": [
        "site"
      ]
    },
    {
      "id": "dns",
      "title": "DNS",
      "resources": [
        "domain",
        "dnsRecord"
      ]
    },
    {
      "id": "forms",
      "title": "Forms",
      "resources": [
        "form"
      ]
    }
  ]
}"""

    val EXAMPLE_0_1_0 =
        """{
  "${'$'}schema": "../../schema/manifest.schema.json",
  "manifestVersion": 1,
  "id": "example",
  "name": "Example",
  "version": "0.1.0",
  "dashboardUrl": "https://dashboard.example.com",
  "tokenUrl": "https://dashboard.example.com/settings/tokens",
  "tokenHelp": "Create a read-only token with access to projects and deployments.",
  "hosts": ["api.example.com"],
  "auth": { "type": "bearer" },
  "settings": [
    { "key": "team", "label": "Team", "hint": "Leave blank for your personal account." }
  ],
  "capabilities": ["SITES", "DEPLOYMENTS", "SERVICES"],
  "validate": {
    "request": { "url": "https://api.example.com/v1/me" },
    "displayName": "email | default:Example",
    "permission": "Account read"
  },
  "resources": [
    {
      "kind": "site",
      "permission": "Projects read",
      "request": {
        "url": "https://api.example.com/v1/projects",
        "query": { "team": "{@team}" },
        "paging": { "type": "page", "sizeParam": "per_page", "size": 50, "maxPages": 4 }
      },
      "items": "projects",
      "fields": {
        "nativeId": "id",
        "name": "name",
        "webUrl": "https://example.com/dashboard/{id}",
        "primaryUrl": "url",
        "customDomains": "domains.name",
        "repoUrl": "repository.url",
        "deployments": {
          "request": { "url": "https://api.example.com/v1/projects/{id}/deployments", "query": { "limit": "5" } },
          "from": "deployments",
          "fields": {
            "nativeId": "id",
            "webUrl": "https://example.com/dashboard/{^.id}/deployments/{id}",
            "status": "state",
            "branch": "branch",
            "createdAt": "created_at | date"
          },
          "statusMap": { "ready": "SUCCESS", "error": "FAILED", "building": "BUILDING", "canceled": "CANCELLED" }
        }
      }
    },
    {
      "kind": "service",
      "service": { "id": "bucket", "label": "Buckets" },
      "permission": "Storage read",
      "optional": true,
      "request": { "url": "https://api.example.com/v1/buckets" },
      "items": "buckets",
      "fields": {
        "nativeId": "id",
        "name": "name",
        "detail": "region",
        "createdAt": "created_at | date"
      }
    }
  ],
  "pages": [
    { "id": "projects", "title": "Projects", "resources": ["site"] },
    { "id": "storage", "title": "Storage", "resources": ["bucket"] }
  ]
}
"""

    const val INDEX_10 =
        """{
  "indexVersion": 1,
  "serial": 10,
  "plugins": [
    {
      "id": "netlify",
      "name": "Netlify",
      "version": "1.1.0",
      "publisher": "Dache",
      "path": "plugins/netlify/1.1.0.json",
      "sha256": "5fca1442c22150a555cbdb3a8257f5e3dc1fcee349c696679c48e4e3319e2d07",
      "hosts": [
        "api.netlify.com"
      ],
      "auth": {
        "type": "bearer"
      },
      "capabilities": [
        "SITES",
        "DEPLOYMENTS",
        "DOMAINS",
        "DNS",
        "SERVICES"
      ]
    },
    {
      "id": "example",
      "name": "Example",
      "version": "0.1.0",
      "publisher": null,
      "path": "plugins/example/0.1.0.json",
      "sha256": "f34bae9bd75e0c44bb0a32d9446ee796bd093d67331239617e65c61f276cab16",
      "hosts": [
        "api.example.com"
      ],
      "auth": {
        "type": "bearer"
      },
      "capabilities": [
        "SITES",
        "DEPLOYMENTS",
        "SERVICES"
      ]
    }
  ]
}"""

    const val INDEX_10_SIG = "DdnpHEwEDcgIX3aCeJx6R8UfkP4w/M7DoB0Znmem7Wf4/wvLGOIFHRw/mVChWnDH8Um2d49oKp+DqpGGFOzmAQ=="

    const val INDEX_20 =
        """{
  "indexVersion": 1,
  "serial": 20,
  "plugins": [
    {
      "id": "netlify",
      "name": "Netlify",
      "version": "2.0.0",
      "publisher": "Dache",
      "path": "plugins/netlify/2.0.0.json",
      "sha256": "23c667566db901f8292582890d4effef257ea91d6d189ec0babf836d0a096272",
      "hosts": [
        "api.netlify.com",
        "uploads.netlify.com"
      ],
      "auth": {
        "type": "bearer"
      },
      "capabilities": [
        "SITES",
        "DEPLOYMENTS",
        "DOMAINS",
        "DNS",
        "SERVICES"
      ]
    }
  ]
}"""

    const val INDEX_20_SIG = "F1t5bmvMVdi25OOovqRdJVZLoPzLsQ4N2xB10Pqh2UarhPm217oFYuWskmKKUVVH3P8czomnNHWlvuDzpQINDQ=="

    const val INDEX_5 =
        """{
  "indexVersion": 1,
  "serial": 5,
  "plugins": [
    {
      "id": "example",
      "name": "Example",
      "version": "0.1.0",
      "publisher": null,
      "path": "plugins/example/0.1.0.json",
      "sha256": "f34bae9bd75e0c44bb0a32d9446ee796bd093d67331239617e65c61f276cab16",
      "hosts": [
        "api.example.com"
      ],
      "auth": {
        "type": "bearer"
      },
      "capabilities": [
        "SITES",
        "DEPLOYMENTS",
        "SERVICES"
      ]
    }
  ]
}"""

    const val INDEX_5_SIG = "zC6X+wplCiGLyK4/9/1bPLskHmoOjTPK82SycNIuQo2tswFSCZZyWaaQzJsFNtRtSH33jo+ydIL542GshIpPCg=="
}
