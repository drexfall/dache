#!/usr/bin/env bash
# Wraps the jpackage app image from `./gradlew :app:createDistributable` into an AppImage.
# Usage: build-appimage.sh <app-image dir containing bin/dache> <output .AppImage>
# Downloads appimagetool once, pinned by version and SHA-256, into ${XDG_CACHE_HOME:-~/.cache}/dache.
set -euo pipefail

app_image="$1"
output="$2"
here="$(cd "$(dirname "$0")" && pwd)"

tool_version="1.9.1"
tool_sha256="ed4ce84f0d9caff66f50bcca6ff6f35aae54ce8135408b3fa33abfc3cb384eb0"
cache="${XDG_CACHE_HOME:-$HOME/.cache}/dache"
tool="$cache/appimagetool-$tool_version-x86_64.AppImage"

if [ ! -x "$tool" ]; then
  mkdir -p "$cache"
  curl -fsSL -o "$tool.part" \
    "https://github.com/AppImage/appimagetool/releases/download/$tool_version/appimagetool-x86_64.AppImage"
  echo "$tool_sha256  $tool.part" | sha256sum --check --quiet
  chmod +x "$tool.part"
  mv "$tool.part" "$tool"
fi

[ -x "$app_image/bin/dache" ] || { echo "no launcher at $app_image/bin/dache" >&2; exit 1; }

appdir="$(mktemp -d)/Dache.AppDir"
trap 'rm -rf "$(dirname "$appdir")"' EXIT
mkdir -p "$appdir/usr/lib" "$appdir/usr/share/applications" "$appdir/usr/share/icons/hicolor/256x256/apps"
cp -a "$app_image" "$appdir/usr/lib/dache"
install -m 755 "$here/AppRun" "$appdir/AppRun"
install -m 644 "$here/dache.desktop" "$appdir/dache.desktop"
install -m 644 "$here/dache.desktop" "$appdir/usr/share/applications/dache.desktop"
install -m 644 "$here/dache.png" "$appdir/dache.png"
install -m 644 "$here/dache.png" "$appdir/usr/share/icons/hicolor/256x256/apps/dache.png"

mkdir -p "$(dirname "$output")"
# Extract-and-run works without FUSE, as on CI runners and in containers.
APPIMAGE_EXTRACT_AND_RUN=1 ARCH=x86_64 "$tool" --no-appstream "$appdir" "$output"
echo "built $output"
