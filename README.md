# Dache

One dashboard for the hosting, DNS, CI, server and uptime services you use. Kotlin Multiplatform: Android and Linux desktop share one Compose UI.

**Status: Phase 1c.** Netlify, Vercel, Cloudflare, GitHub and DigitalOcean are real: they run as declarative plugins through the manifest engine and talk to the provider's API with your token. The other providers are still `MockProvider`s serving a fictional "acme" org (`core/.../provider/mock/MockFixtures.kt`) until their plugins land (see the project board).

## Modules

| Module | What's in it |
|---|---|
| `core` | KMP (Android + JVM). Unified models, `DevOpsProvider`, mock providers, cross-provider linking, dashboard and account repositories, `TokenVault` (Tink + Keystore on Android, libsecret `secret-tool` on Linux). |
| `plugin-api` | KMP. The plugin manifest schema (`PluginManifest`) and its strict JSON codec. No code runs from a manifest. |
| `plugin-runtime` | KMP. The declarative engine: manifest validator, mapping expressions, `ManifestProvider` (a `DevOpsProvider` that runs a manifest), and the HTTP layer with `HostGuard`, `AuthInjector` and redaction. Ktor with OkHttp on both targets. |
| `plugin-registry` | KMP. Bundled plugins. Manifests live in `plugin-registry/manifests/*.json` and are compiled into the app at build time. |
| `tools/plugin-cli` | JVM command line for plugin authors: `dache-plugin validate <manifest.json>...`. |
| `app` | KMP Compose Multiplatform UI: sidebar navigation (overview + one page per account), Canvas charts, adaptive phone/desktop layouts. Also the desktop entry point (`jvmMain/Main.kt`). |
| `androidApp` | Android shell: `Application`, `MainActivity`, and three Glance home-screen widgets (status summary, deploys, monitors). |

## Running

Needs a full JDK 17+ (one that includes `javac`). Android Studio's bundled JBR works:

```bash
export JAVA_HOME=~/.local/share/JetBrains/Toolbox/apps/android-studio/jbr   # adjust to your install

./gradlew :app:run                  # desktop (Linux)
./gradlew :androidApp:installDebug  # Android device/emulator
./gradlew test                      # shared unit tests on Android (host JVM) and desktop
./gradlew ktlintCheck detekt        # lint
./gradlew :tools:plugin-cli:run --args="validate plugin-registry/manifests/netlify.json"
```

On Linux, connecting an account stores the token in your keyring via `secret-tool` (package `libsecret` / `libsecret-tools`). A running Secret Service (GNOME Keyring or KWallet) is required; without one, connecting fails with an explanation.

Debug request logging: debuggable Android builds log every plugin request to Logcat; on desktop set `DACHE_LOG_HTTP=1`. Tokens are redacted from URLs, headers and bodies before anything is logged. Release builds log nothing.

## Plugins

A plugin is a JSON manifest, not code. It declares the exact hosts it may call (HTTPS only, checked on every request and redirect), where the token goes (`bearer`, a named `header`, or a `query` parameter), a request that validates the token, and for each resource the list endpoint, paging, and how fields map onto Dache's models:

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

Expressions are a dot path into the response (`build_settings.repo_url`, `items.0.id`) followed by pipes: `host`, `lower`, `date` and `default:<text>`. See `plugin-registry/manifests/netlify.json` for a complete example.

Paging is `page` (numbered pages), `cursor` (an expression such as `pagination.next` names the next cursor) or `link` (follows the `Link: rel="next"` header). Every style stops after `maxPages`.

A resource's `kind` is `site`, `domain` (with TLS `certificates` as nested records), `dnsRecord`, `ciRun`, `server`, `worker`, `service` or `billing`. A `billing` resource reads the account's spend from one object (`"items": "$"`) and is shown on the dashboard, not a page. A `service` is anything without a model of its own (R2 buckets, KV namespaces, tunnels, Netlify forms), described by a `service` spec with an `id` and a plural `label`, and mapped to `name`, an optional one-line `detail`, `status` (`ServiceStatus`), `createdAt` and `updatedAt`. Adding one is manifest-only. Each status field has a `statusMap` from the provider's values to Dache's, e.g. `DeployStatus` names for deployments and `CertificateStatus` names for certificates.

A request can also run once per item of an earlier list. `forEach` on a resource lists parents first (Cloudflare accounts, say) and sends the resource request per parent; a `request` on a nested field fetches that field per item (a project's deployments). Placeholders fill the path and query values from that item, and every item fetched this way carries its parent as `^`, so `^.name` is the zone a DNS record came from and `^.^.id` reaches two levels up:

```json
"deployments": {
  "request": { "url": "https://api.example.com/v6/deployments", "query": { "projectId": "{id}" } },
  "from": "deployments",
  "fields": { "nativeId": "uid", "createdAt": "created | date" }
}
```

A manifest can also ask for account `settings` besides the token, such as Vercel's optional team slug. `{@team}` fills them into requests and text fields. A query parameter that is only an unset optional setting is left out, and a text field can be a template like `"https://vercel.com/{@team}/{name}"`. Times in epoch milliseconds use the `millis` pipe, and a key on an array reads it from every item (`alias.domain`).

Each resource, nested request and the validate request can name the token `permission` it needs, as the provider names it. Connecting checks every one by sending the first request of each list, and shows a checklist; an account's settings page runs the same check with the stored token. A resource marked `optional` that answers 403 or 404 is left out instead of failing the refresh, and its page says which permission is missing. Optional resources must name their permission.

`pages` splits an account into pages beside its dashboard, e.g. `{ "id": "dns", "title": "DNS", "resources": ["domain", "dnsRecord"] }`, naming kinds and service ids. Every resource must be on a page; without `pages`, each kind and service gets its own.

Values filled into the path are percent-encoded whole, so they can't add path segments, and placeholders can't appear in the host. A fan-out sends at most 100 requests, four at a time; a parent whose list returns 404 just has no items.

## Layouts

The layout follows window width, not platform, so a narrow desktop window gets the phone UI.

| | Phone (< 840dp) | Desktop / large tablet (≥ 840dp) |
|---|---|---|
| Navigation | Sidebar behind ☰, closes after picking | Permanent sidebar |
| Content | One column, 2×2 stat tiles | Multi-column grid, stat tiles in one row |
| Refresh | Pull to refresh | Toolbar button, F5 or Ctrl/Cmd+R |
| Chart readouts | Tap a column/point | Hover |
| Add account | Bottom sheet | Dialog |

Every chart has a **Table** toggle with the same numbers.

## Interaction

- Tapping a row, stat tile, section, chart title or error card expands it in place. Nothing leaves the app on tap.
- Tapping or clicking a chart mark (column, bar, uptime day, point) pins it and shows what's behind it.
- Linked-resource chips (→) navigate inside the app. Anything that opens the browser is marked ↗ and asks first; "Don't ask again" can be undone under **Manage accounts → Ask before opening links**.

## Trying it

- On the empty overview, **Try with demo data** connects one account per provider that still serves sample data (only UptimeRobot now).
- Netlify, Vercel, Cloudflare, GitHub and DigitalOcean need a real token. Add it under **Manage accounts**. For a Vercel team, also enter the team slug (the part after `vercel.com/` on the team's dashboard); leave it blank for your personal account.
- For sample-data providers, any token with 8+ characters is accepted. A token starting with `fail-` makes that account fail every refresh with a rate-limit error, so you can see how one provider failing looks.
- Widgets: long-press the home screen → Widgets → Dache.
