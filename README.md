# Dache

One dashboard for the hosting, DNS, CI, server and uptime services you use. Kotlin Multiplatform: Android and Linux desktop share one Compose UI.

**Status: MVP with mock data.** Every provider is a `MockProvider` serving a fictional "acme" org (`core/.../provider/mock/MockFixtures.kt`). No network calls are made yet. The manifest engine and real plugins come next (see the project board).

## Modules

| Module | What's in it |
|---|---|
| `core` | KMP (Android + JVM). Unified models, `DevOpsProvider`, mock providers, cross-provider linking, dashboard and account repositories, `TokenVault` (Tink + Keystore on Android, libsecret `secret-tool` on Linux). |
| `app` | KMP Compose Multiplatform UI: sidebar navigation (overview + one page per account), Canvas charts, adaptive phone/desktop layouts. Also the desktop entry point (`jvmMain/Main.kt`). |
| `androidApp` | Android shell: `Application`, `MainActivity`, and three Glance home-screen widgets (status summary, deploys, monitors). |

## Running

Needs a full JDK 17+ (one that includes `javac`). Android Studio's bundled JBR works:

```bash
export JAVA_HOME=~/.local/share/JetBrains/Toolbox/apps/android-studio/jbr   # adjust to your install

./gradlew :app:run                  # desktop (Linux)
./gradlew :androidApp:installDebug  # Android device/emulator
./gradlew test                      # shared unit tests on the JVM
./gradlew ktlintCheck detekt        # lint
```

On Linux, connecting an account stores the token in your keyring via `secret-tool` (package `libsecret` / `libsecret-tools`).

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

- On the empty overview, **Try with demo data** connects one account per provider.
- Or add accounts by hand: any token with 8+ characters is accepted. A token starting with `fail-` makes that account fail every refresh with a rate-limit error, so you can see how one provider failing looks.
- Widgets: long-press the home screen → Widgets → Dache.
