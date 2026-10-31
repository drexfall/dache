// Generated with a throwaway test key; the matching private key is not kept anywhere.
package dev.dache.plugin.registry

// Whole manifests, so the class is large by design.
@Suppress("LargeClass")
internal object RegistryFixtures {
    const val PUBLIC_KEY = "QW5K8VWmcQeTgWyq6fXuCE+SFlfnxYvQQDK+BrmeBK0="

    val NETLIFY_50_0_0 =
        """{
  "${'$'}schema": "../../schema/manifest.schema.json",
  "manifestVersion": 1,
  "id": "netlify",
  "name": "Netlify",
  "version": "50.0.0",
  "publisher": "Dache",
  "dashboardUrl": "https://app.netlify.com",
  "tokenUrl": "https://app.netlify.com/user/applications#personal-access-tokens",
  "tokenHelp": "Netlify personal access tokens can’t be scoped. Give it an expiry date and use it only for Dache. Actions (deploys, deleting DNS records) use the same token.",
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
  "status": {
    "url": "https://www.netlifystatus.com/api/v2/summary.json",
    "pageUrl": "https://www.netlifystatus.com",
    "indicator": "status.indicator",
    "description": "status.description",
    "levelMap": {
      "none": "OPERATIONAL",
      "minor": "DEGRADED",
      "major": "PARTIAL_OUTAGE",
      "critical": "MAJOR_OUTAGE",
      "maintenance": "MAINTENANCE"
    },
    "components": {
      "items": "components",
      "fields": {
        "id": "id",
        "name": "name",
        "status": "status",
        "parent": "group_id"
      },
      "statusMap": {
        "operational": "OPERATIONAL",
        "degraded_performance": "DEGRADED",
        "partial_outage": "PARTIAL_OUTAGE",
        "major_outage": "MAJOR_OUTAGE",
        "under_maintenance": "MAINTENANCE"
      }
    },
    "incidents": {
      "items": "incidents",
      "fields": {
        "name": "name",
        "status": "status",
        "impact": "impact",
        "updatedAt": "updated_at | date",
        "url": "shortlink"
      },
      "statusMap": {
        "none": "OPERATIONAL",
        "minor": "DEGRADED",
        "major": "PARTIAL_OUTAGE",
        "critical": "MAJOR_OUTAGE",
        "maintenance": "MAINTENANCE"
      }
    }
  },
  "actions": [
    {
      "id": "trigger-deploy",
      "label": "Trigger deploy",
      "target": "site",
      "description": "Starts a new build of the site from its linked repository.",
      "request": {
        "url": "https://api.netlify.com/api/v1/sites/{id}/builds",
        "method": "POST"
      }
    },
    {
      "id": "delete-record",
      "label": "Delete record",
      "target": "dnsRecord",
      "destructive": true,
      "permission": "Personal access token",
      "description": "Removes this DNS record from the zone.",
      "request": {
        "url": "https://api.netlify.com/api/v1/dns_zones/{^.id}/dns_records/{id}",
        "method": "DELETE"
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

    val NETLIFY_60_0_0 =
        """{
  "${'$'}schema": "../../schema/manifest.schema.json",
  "manifestVersion": 1,
  "id": "netlify",
  "name": "Netlify",
  "version": "60.0.0",
  "publisher": "Dache",
  "dashboardUrl": "https://app.netlify.com",
  "tokenUrl": "https://app.netlify.com/user/applications#personal-access-tokens",
  "tokenHelp": "Netlify personal access tokens can’t be scoped. Give it an expiry date and use it only for Dache. Actions (deploys, deleting DNS records) use the same token.",
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
  "status": {
    "url": "https://www.netlifystatus.com/api/v2/summary.json",
    "pageUrl": "https://www.netlifystatus.com",
    "indicator": "status.indicator",
    "description": "status.description",
    "levelMap": {
      "none": "OPERATIONAL",
      "minor": "DEGRADED",
      "major": "PARTIAL_OUTAGE",
      "critical": "MAJOR_OUTAGE",
      "maintenance": "MAINTENANCE"
    },
    "components": {
      "items": "components",
      "fields": {
        "id": "id",
        "name": "name",
        "status": "status",
        "parent": "group_id"
      },
      "statusMap": {
        "operational": "OPERATIONAL",
        "degraded_performance": "DEGRADED",
        "partial_outage": "PARTIAL_OUTAGE",
        "major_outage": "MAJOR_OUTAGE",
        "under_maintenance": "MAINTENANCE"
      }
    },
    "incidents": {
      "items": "incidents",
      "fields": {
        "name": "name",
        "status": "status",
        "impact": "impact",
        "updatedAt": "updated_at | date",
        "url": "shortlink"
      },
      "statusMap": {
        "none": "OPERATIONAL",
        "minor": "DEGRADED",
        "major": "PARTIAL_OUTAGE",
        "critical": "MAJOR_OUTAGE",
        "maintenance": "MAINTENANCE"
      }
    }
  },
  "actions": [
    {
      "id": "trigger-deploy",
      "label": "Trigger deploy",
      "target": "site",
      "description": "Starts a new build of the site from its linked repository.",
      "request": {
        "url": "https://api.netlify.com/api/v1/sites/{id}/builds",
        "method": "POST"
      }
    },
    {
      "id": "delete-record",
      "label": "Delete record",
      "target": "dnsRecord",
      "destructive": true,
      "permission": "Personal access token",
      "description": "Removes this DNS record from the zone.",
      "request": {
        "url": "https://api.netlify.com/api/v1/dns_zones/{^.id}/dns_records/{id}",
        "method": "DELETE"
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
      "version": "50.0.0",
      "publisher": "Dache",
      "path": "plugins/netlify/50.0.0.json",
      "sha256": "34e984b17497522bd2bb83b0155895fdee8ac96727de47b40dd7292fb0ad079c",
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

    const val INDEX_10_SIG = "0e+VTULxa7oa1UmT1EJLNkAAELdom9SQU6uoviAuzaqiXdSQQ3x//3dSC7+RB89f2Em5s7OwBcXA5rytojckAQ=="

    const val INDEX_20 =
        """{
  "indexVersion": 1,
  "serial": 20,
  "plugins": [
    {
      "id": "netlify",
      "name": "Netlify",
      "version": "60.0.0",
      "publisher": "Dache",
      "path": "plugins/netlify/60.0.0.json",
      "sha256": "ac57b75373da76d9dbbeefbc230db02625054a154717a183359f71d037b312cd",
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

    const val INDEX_20_SIG = "85ZxDpkA+3+dlIuVIQzprXmozqBXIDX4ReNkfUKHKXX+4Td4ajqUSws2uww4CCCVGdwiCcLCiK9TZLVLjb4HDQ=="

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

    const val INDEX_5_SIG = "CCjOthzz6joHthBc1wIvqukDdkI6SseteSccnZX3gi72ABFF0nlei6x5VeXz5zUs1GGY/FtPFKOizjIIcxYvAw=="
}
