# Dache

One dashboard for the hosting, DNS, CI, server and uptime services you use. Kotlin Multiplatform: Android and Linux desktop share one Compose UI.

**Status: Phase 2.** Netlify, Vercel, Cloudflare, GitHub, DigitalOcean and UptimeRobot are real: they run as declarative plugins through the manifest engine and talk to the provider's API with your token. `MockProvider` and its fictional "acme" org (`core/.../provider/mock/MockFixtures.kt`) are for tests only.

## Modules

| Module | What's in it |
|---|---|
| `core` | KMP (Android + JVM). Unified models, `DevOpsProvider`, mock providers, cross-provider linking, dashboard and account repositories, `TokenVault` (Tink + Keystore on Android, libsecret `secret-tool` on Linux). |
| `plugin-api` | KMP. The plugin manifest schema (`PluginManifest`) and its strict JSON codec. No code runs from a manifest. |
| `plugin-runtime` | KMP. The declarative engine: manifest validator, mapping expressions, `ManifestProvider` (a `DevOpsProvider` that runs a manifest), and the HTTP layer with `HostGuard`, `AuthInjector` and redaction. Ktor with OkHttp on both targets. |
| `plugin-registry` | KMP. Bundled plugins. Manifests live in `plugin-registry/manifests/*.json` and are compiled into the app at build time. |
| `tools/plugin-cli` | JVM command line for plugin authors: `validate`, `test` against recorded responses, `run` against the live API. |
| `app` | KMP Compose Multiplatform UI: sidebar navigation (overview + one page per account), Canvas charts, adaptive phone/desktop layouts. Also the desktop entry point (`jvmMain/Main.kt`). |
| `androidApp` | Android shell: `Application`, `MainActivity`, and three Glance home-screen widgets (status summary, deploys, monitors). |

## Running

Needs a full JDK 17+ (one that includes `javac`). Android Studio's bundled JBR works:

```bash
export JAVA_HOME=~/.local/share/JetBrains/Toolbox/apps/android-studio/jbr   # adjust to your install

./gradlew :app:run                  # desktop (Linux)
./gradlew :androidApp:installDebug  # Android device/emulator
./gradlew test                      # shared unit tests on Android (host JVM) and desktop
DACHE_RENDER_DIR=/tmp/shots ./gradlew :app:jvmTest --tests '*RenderScreens*'  # screenshots with mock data
./gradlew ktlintCheck detekt        # lint
./gradlew :tools:plugin-cli:run --args="validate plugin-registry/manifests/netlify.json"
```

`test <manifest> --fixtures <dir>` runs a manifest against recorded responses and prints the token check and the models it produced. A request's response is the file `<dir>/<host><path>.json`, or `<host><path>?<query>.json` for one exact query (parameters sorted by name), with a status before `.json` (`….403.json`) to answer with it. A request with no file gets a 404. Add `--setting key=value` for each account setting the manifest asks for.

`schema/manifest.schema.json` is the JSON Schema for manifests, generated from plugin-api with `dache-plugin schema`; a test fails when it goes stale. Point a manifest's `$schema` at it and editors complete keys, kinds, fields and capabilities. It checks shape only; `validate` also checks hosts, expressions and placeholders.

`run <manifest> --token-env VAR` does the same against the live API, with the token read from environment variable `VAR` so it stays out of shell history: `NETLIFY_TOKEN=… ./gradlew :tools:plugin-cli:run --args="run $PWD/plugin-registry/manifests/netlify.json --token-env NETLIFY_TOKEN"`.

The registry side: `keygen <file>` writes a new Ed25519 signing key to `<file>` (owner-only) and prints the public key to pin in the app; `index <registry-dir> --out <dir>` builds `index.json` from `plugins/<id>/manifest.json`, copying each manifest beside it under its version with its SHA-256; `sign <file> --key-env VAR` writes `<file>.sig`. The registry itself is the separate `drexfall/dache-plugins` repo, whose CI runs these.

### Linux AppImage

```bash
PACKAGING_JDK=/path/to/jdk-21 ./gradlew :app:packageAppImage   # → app/build/compose/binaries/main/appimage/
```

`PACKAGING_JDK` must be a JDK with `jpackage` (Temurin has it; Android Studio's JBR doesn't). The task builds the jpackage app image and wraps it with `packaging/linux/build-appimage.sh`, which downloads appimagetool 1.9.1 once (pinned by SHA-256) into `~/.cache/dache`. Add `-Pdache.distDir=<dir>` to build somewhere else. Pushing a `v*` tag runs `.github/workflows/release.yml`, which attaches the AppImage to a GitHub release. The AppImage still needs `secret-tool` and a Secret Service from the host; background checks use the host's `systemd` and `notify-send`.

### Windows

The desktop build runs on Windows too. Tokens are encrypted with DPAPI for the signed-in user (`%LOCALAPPDATA%\Dache\tokens`, one file per account under a hashed name), background checks run as the scheduled task "Dache\Background check", and alerts show as tray notifications. `./gradlew :app:packageMsi` builds a per-user MSI on Windows (jpackage needs WiX there); the release workflow builds and tests it on a Windows runner. None of this has been run on Windows yet, only unit-tested on Linux.

On Linux, connecting an account stores the token in your keyring via `secret-tool` (package `libsecret` / `libsecret-tools`). A running Secret Service (GNOME Keyring or KWallet) is required; without one, connecting fails with an explanation.

Debug request logging: debuggable Android builds log every plugin request to Logcat; on desktop set `DACHE_LOG_HTTP=1`. Tokens are redacted from URLs, headers and bodies before anything is logged. Release builds log nothing.

## Plugins

Every provider is a plugin: one JSON manifest that names the hosts it may call, where the token goes and how list endpoints map onto Dache's models. No code runs from a manifest, and the engine sends only GET requests outside the actions a manifest declares. To write one, start with [docs/writing-a-plugin.md](docs/writing-a-plugin.md) and the template in `docs/plugin-template/`; [docs/manifest-reference.md](docs/manifest-reference.md) covers every key: expressions, paging, kinds, `forEach`, account settings, permissions, pages and rate limits.

## Plugin registry

Besides the bundled plugins, the app installs plugins from the signed registry ([`drexfall/dache-plugins`](https://github.com/drexfall/dache-plugins)). The app pins the registry's Ed25519 public key (`RegistryKeys`), checks the signature of `index.json`, refuses an index older than the last one it accepted, and checks each manifest's SHA-256 against the index before installing. An installed plugin replaces the bundled one with the same id; removing it brings the bundled one back.

Once a day the app checks the registry and installs updates that keep a plugin's hosts and auth. An update that changes either waits for the user to approve it again.

**Plugins** in the sidebar lists the plugins in use (built in, from the registry, or from a link or file), updates, and registry plugins not installed yet. Installing shows a consent screen first: the hosts the plugin contacts (an update marks new ones), how it sends the token, what it asks for and shows, its publisher, and whether the registry verified it. Plugins from a link or file need **Install from outside the registry** switched on, are marked unverified, and need an extra confirmation.

## Background checks

**Manage accounts → Background checks** refreshes every account on a schedule while Dache is closed and notifies about failed deploys, monitors going down and failed CI runs. The first check only records what is already wrong; after that each problem notifies once, and again only if it clears and comes back.

- **Android:** WorkManager, every 15 minutes to 3 hours while online. Turning it on asks for the notification permission (Android 13+).
- **Linux:** a systemd user timer, `~/.config/systemd/user/dache-check.timer`, runs `dache --check` (the same check, no window) and alerts through `notify-send`. Turning checks off disables and deletes the units. The check keeps what it already reported in `~/.local/share/dache/reported-alerts`, so it never writes the app's own settings file.

With the app lock's "hide names" option on, notifications say what kind of thing broke but not which.

## Provider status

Providers with a public status page (Cloudflare, GitHub, Netlify, Vercel, DigitalOcean) show it on each account's page: the overall level, active incidents, and every component by group, such as Cloudflare's 400-odd data centres by region, worst groups first. The overview shows a banner while any of them is impaired. Status pages are read without your token, from their own host only, on every refresh.

## Pagination

Every list (resources, chart tables, monitors, status components, plugins, accounts, recent changes) shows 10 rows at a time with previous/next controls.

## Actions

Expanding a resource (or opening its page) shows the writes its plugin offers: trigger a Netlify deploy, redeploy or roll back a Vercel deployment, retry or roll back a Cloudflare Pages deployment, re-run a GitHub workflow, reboot or power-cycle a droplet, pause or resume an UptimeRobot monitor, and edit or delete DNS records on Cloudflare and DigitalOcean (delete only on Netlify). Every action goes through a review screen that shows each value before and after and the exact request, with the token redacted; nothing is sent before you confirm, and the dashboard refreshes afterwards. Destructive ones (deletes, power cycles, production rollbacks) are marked in red.

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

- Every provider needs a real token. Add it under **Manage accounts**. For a Vercel team, also enter the team slug (the part after `vercel.com/` on the team's dashboard); leave it blank for your personal account.
- Widgets: long-press the home screen → Widgets → Dache.

## License

[MIT](LICENSE).
