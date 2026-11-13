# Writing a plugin

A Dache plugin is one JSON file, a manifest. It names the hosts the plugin may call, says where the token goes, and maps list endpoints onto Dache's models: sites, domains, DNS records, CI runs, servers, workers, monitors, billing, and `service` resources for anything else. Manifests hold no code, so installing one can't run anything on the device. The engine sends only GET requests.

[The manifest reference](manifest-reference.md) covers every key. This page is the workflow.

## 1. Start from the template

Copy `docs/plugin-template/example.json` and its `fixtures/` folder. The template lists projects with their deployments, plus an optional storage list, split into two pages. Set `id` (lowercase, also the token's storage namespace), `name`, `hosts` and `auth`. Then `dashboardUrl`, the provider's web dashboard (required, HTTPS), `tokenUrl`, where users create a token, and `tokenHelp`, a sentence or two on which permissions to grant. The app shows the last two when someone connects an account.

Keep `"$schema": "../../schema/manifest.schema.json"` (adjust the path to where your file lives). Editors then complete keys, kinds, field names and capabilities, and flag a misspelt field as you type.

## 2. Map the endpoints

For each list the provider offers:

1. Pick a `kind`. If none fits, use `service` with an `id` and a plural `label`; it shows with a name, a one-line `detail`, an optional status and dates.
2. Point `request.url` at the endpoint and `items` at the array in the response (leave it out when the response is the array; use `$` for a single object such as a balance).
3. Fill `fields` with expressions: a dot path into each item, then pipes. `created_at | date`, `domains.name` (every item's name), `networks.v4.type=public.0.ip_address` (the first public address), `conclusion | default:running`.
4. A status field needs a `statusMap` from the provider's values to Dache's.
5. Add `paging` if the list is paged, and `forEach` if the endpoint needs an id from another list (projects per account, records per zone). Fanned-out items carry their parent as `^`.

Name the token permission each request needs in `permission`, exactly as the provider's token screen names it. Connecting shows these as a checklist. Mark a list `optional` when an account can work without it; a token that can't read it then leaves its page empty instead of failing the refresh.

Group what you list into `pages`. Every resource has to be on one, except billing, which goes on the account's dashboard.

## 3. Check it

```bash
./gradlew :tools:plugin-cli:run --args="validate $PWD/my-plugin.json"
```

`validate` checks everything the schema can't: that every URL is on a declared host, that placeholders only appear where there is an item to fill them, that expressions parse and that time fields end in `date` or `millis`.

## 4. Test against recorded responses

Save real responses (with tokens, emails and ids you don't want to share replaced) under a fixtures folder, one file per request, named after its host and path:

```
fixtures/api.example.com/v1/me.json
fixtures/api.example.com/v1/projects?page=1&per_page=50.json
fixtures/api.example.com/v1/projects/p1/deployments?limit=5.json
fixtures/api.example.com/v1/buckets.403.json
```

A query string, with parameters sorted by name, picks one exact request; without it the file answers every request to that path. A number before `.json` answers with that status, so `buckets.403.json` shows what happens when the token lacks a permission. A request with no file gets a 404.

```bash
./gradlew -q :tools:plugin-cli:run --args="test $PWD/docs/plugin-template/example.json --fixtures $PWD/docs/plugin-template/fixtures"
```

```
token works for dev@example.com
  [x] Account read (required)
  [x] Projects read (required)
  [ ] Storage read (optional, missing)
site (2)
  docs  FAILED  https://example.com/dashboard/p1
  shop  SUCCESS  https://example.com/dashboard/p2
left out, the token lacks: Storage read
```

Pass `--setting key=value` for each account setting the manifest asks for.

## 5. Try it live

```bash
EXAMPLE_TOKEN=… ./gradlew -q :tools:plugin-cli:run --args="run $PWD/my-plugin.json --token-env EXAMPLE_TOKEN"
```

The token is read from the environment variable only, so it stays out of shell history.

## Bundling a plugin with the app

Bundled plugins live in `plugin-registry/manifests/`. The manifest is the whole provider: its id, display name, dashboard URL, token page and token help all come from the file, so nothing in the app's code names it. A bundled plugin needs, in the same change:

- the manifest in `plugin-registry/manifests/<id>.json`;
- a test in `plugin-registry/src/commonTest` with recorded responses, like `GitHubPluginTest`, so a provider API change shows up as a failing test rather than an empty page.

`./gradlew test` loads every bundled manifest, and fails if one doesn't validate.

## Actions (writes)

A plugin reads with GET only. Anything else is an action, declared under `actions` and shown on the resource it targets:

```json
"actions": [{
  "id": "redeploy", "label": "Redeploy", "target": "site",
  "request": { "url": "https://api.example.com/sites/{id}/builds", "method": "POST" }
}, {
  "id": "edit-record", "label": "Edit record", "target": "dnsRecord", "permission": "DNS:Edit",
  "inputs": [{ "key": "content", "label": "Content", "default": "content" },
             { "key": "ttl", "label": "TTL", "type": "number", "default": "ttl" }],
  "request": { "url": "https://api.example.com/zones/{^.id}/records/{id}", "method": "PATCH" },
  "body": { "content": "{input.content}", "ttl": "{input.ttl}" }
}]
```

`target` is a kind the plugin lists, or `deployment` for one of a site's deployments (its site is `^`). Placeholders read the target exactly as the provider listed it, `{@key}` an account setting, `{input.key}` an input. An input's `default` is an expression on the target, so the confirmation screen shows each value before and after. A body string that is only a number or boolean input is sent as that type. Mark deletes, power cycles and production rollbacks `destructive` and name their `permission`; the app then asks for the device lock as well. Writes never follow redirects, and the engine refuses any non-GET request that isn't one of the manifest's actions.

## Self-hosted software

For software people run themselves, add `"selfHosted": { "label": "Coolify server", "hint": "coolify.example.com" }`, list only `"hosts": ["self.hosted"]`, and write every URL against `https://self.hosted`. Each account enters its own server (a hostname or IPv4 address, `:port` if it isn't 443); the engine swaps it in for that account only, so its token goes to that server and nowhere else. A server whose certificate the device trusts just works. A self-signed one fails with its SHA-256 fingerprint; the user can compare it with the server's and trust it, after which only that certificate is accepted from that server. Plain HTTP isn't supported. See `coolify.json` and `proxmox.json`.

## Status page

If the provider publishes a public status page, describe its API under `status`, and the app shows it on the account page, with a banner on the overview when something is impaired. The status URL is fetched without the token, from its own host only. For Atlassian Statuspage (Cloudflare, GitHub, Netlify, Vercel, DigitalOcean):

```json
"status": {
  "url": "https://www.cloudflarestatus.com/api/v2/summary.json",
  "pageUrl": "https://www.cloudflarestatus.com",
  "indicator": "status.indicator", "description": "status.description",
  "levelMap": { "none": "OPERATIONAL", "minor": "DEGRADED", "major": "PARTIAL_OUTAGE", "critical": "MAJOR_OUTAGE", "maintenance": "MAINTENANCE" },
  "components": { "items": "components", "fields": { "id": "id", "name": "name", "status": "status", "parent": "group_id" },
                  "statusMap": { "operational": "OPERATIONAL", "degraded_performance": "DEGRADED", "partial_outage": "PARTIAL_OUTAGE", "major_outage": "MAJOR_OUTAGE", "under_maintenance": "MAINTENANCE" } },
  "incidents": { "items": "incidents", "fields": { "name": "name", "status": "status", "impact": "impact", "updatedAt": "updated_at | date", "url": "shortlink" }, "statusMap": { "none": "OPERATIONAL", "minor": "DEGRADED", "major": "PARTIAL_OUTAGE", "critical": "MAJOR_OUTAGE" } }
}
```

`parent` names the group a component belongs to; components that are another's parent become groups, such as Cloudflare's regions holding its data centres. Check it with `dache-plugin status <manifest>`.

## Rate limits

If the provider documents a request limit, declare it: `"limits": { "requestsPerMinute": 10 }`. The engine paces each account to it, and honours `Retry-After` and `X-RateLimit-Remaining`/`X-RateLimit-Reset` (or the IETF `RateLimit-*` names) on its own. `maxRequestsPerRefresh` (default 300, at most 2000) caps one refresh; a refresh that would need more fails instead of hammering the API.

## Rules the engine enforces

- Hosts are exact names over HTTPS. No wildcards, ports or schemes. Redirects are followed only to declared hosts.
- The token travels only in the `auth` slot. Manifests can't set `Authorization`, `Cookie` or `Host`, or a query parameter that collides with the auth one.
- Values filled into a path are encoded whole, so they can't add path segments.
- Paging stops after `maxPages` (at most 50); a fan-out sends at most 100 requests, four at a time.
