# Manifest reference

Every key a plugin manifest can carry, and what the engine does with it. For the workflow (template, validate, test against recorded responses, run live) see [writing-a-plugin.md](writing-a-plugin.md); `schema/manifest.schema.json` is the JSON Schema editors complete from.

## Hosts, auth and the token check

A manifest declares the exact hosts it may call (HTTPS only, checked on every request and redirect), where the token goes (`bearer`, a named `header`, or a `query` parameter), a request that validates the token, and for each resource the list endpoint, paging, and how fields map onto Dache's models:

```json
"fields": {
  "nativeId": "id",
  "primaryUrl": "ssl_url",
  "customDomains": ["custom_domain | lower", "domain_aliases"],
  "deployments": {
    "from": "published_deploy",
    "fields": { "status": "state", "createdAt": "created_at | date" },
    "statusMap": { "ready": "SUCCESS", "error": "FAILED" }
  }
}
```

## Expressions

Expressions are a dot path into the response (`build_settings.repo_url`, `items.0.id`) followed by pipes: `host`, `lower`, `date`, `millis`, `avg` (the mean of a list of numbers) and `default:<text>`. A `key=value` segment keeps the array items whose key has that value (`networks.v4.type=public.0.ip_address`). See `plugin-registry/manifests/netlify.json` for a complete example.

## Paging

Paging is `page` (numbered pages), `cursor` (an expression such as `pagination.next` names the next cursor) or `link` (follows the `Link: rel="next"` header, or with `next`, a URL in the body such as `nextLink`). Every style stops after `maxPages`.

## Kinds

A resource's `kind` is `site`, `domain` (with TLS `certificates` as nested records), `dnsRecord`, `ciRun`, `server`, `worker`, `service` or `billing`. A `billing` resource reads the account's spend from one object (`"items": "$"`) and is shown on the dashboard, not a page. A `service` is anything without a model of its own (R2 buckets, KV namespaces, tunnels, Netlify forms), described by a `service` spec with an `id` and a plural `label`, and mapped to `name`, an optional one-line `detail`, `status` (`ServiceStatus`), `createdAt` and `updatedAt`. Adding one is manifest-only. Each status field has a `statusMap` from the provider's values to Dache's, e.g. `DeployStatus` names for deployments and `CertificateStatus` names for certificates.

## Requests per item (`forEach`)

A request can also run once per item of an earlier list. `forEach` on a resource lists parents first (Cloudflare accounts, say) and sends the resource request per parent; a `request` on a nested field fetches that field per item (a project's deployments). Placeholders fill the path and query values from that item, and every item fetched this way carries its parent as `^`, so `^.name` is the zone a DNS record came from and `^.^.id` reaches two levels up:

```json
"deployments": {
  "request": { "url": "https://api.example.com/v6/deployments", "query": { "projectId": "{id}" } },
  "from": "deployments",
  "fields": { "nativeId": "uid", "createdAt": "created | date" }
}
```

## Account settings

A manifest can also ask for account `settings` besides the token, such as Vercel's optional team slug. `{@team}` fills them into requests and text fields. A query parameter that is only an unset optional setting is left out, and a text field can be a template like `"https://vercel.com/{@team}/{name}"`. Times in epoch milliseconds use the `millis` pipe, and a key on an array reads it from every item (`alias.domain`).

## Permissions and optional resources

Each resource, nested request and the validate request can name the token `permission` it needs, as the provider names it. Connecting checks every one by sending the first request of each list, and shows a checklist; an account's settings page runs the same check with the stored token. A resource marked `optional` that answers 403 or 404 is left out instead of failing the refresh, and its page says which permission is missing. Optional resources must name their permission.

## Pages

`pages` splits an account into pages beside its dashboard, e.g. `{ "id": "dns", "title": "DNS", "resources": ["domain", "dnsRecord"] }`, naming kinds and service ids. Every resource must be on a page; without `pages`, each kind and service gets its own.

## Rate limits

Requests are paced per account. `"limits": { "requestsPerMinute": 10 }` keeps a plugin under a provider's documented limit (UptimeRobot's free plan), and `maxRequestsPerRefresh` (default 300) stops a refresh that would send more. A `429` whose `Retry-After` is short is waited out and retried; a response with `X-RateLimit-Remaining: 0` (or `RateLimit-Remaining`) holds the account's next requests until the reset. A hold longer than 15 seconds fails the request as rate-limited instead of stalling the refresh.

## Rules the engine enforces

Values filled into the path are percent-encoded whole, so they can't add path segments, and placeholders can't appear in the host. A fan-out sends at most 100 requests, four at a time; a parent whose list returns 404 just has no items.

Actions (writes), self-hosted software and status pages have their own sections in [writing-a-plugin.md](writing-a-plugin.md#actions-writes).
