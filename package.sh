#!/usr/bin/env bash
# package.sh - build distributable servd bundles into dist/ (Linux/macOS).
#
# Desktop: a self-contained app image (bundled JRE - no Java needed on the target). Its launcher,
# servd/bin/servd, is the click-to-run executable, the Linux/macOS equivalent of the Windows .exe.
# With --appimage it is also wrapped into a single-file *.AppImage (one file you chmod +x and run,
# the closest analog to a Windows .exe). Android: the debug APK always, plus a signed release APK
# when androidApp/keystore.properties exists (see README > Packaging).
#
# Usage:
#   ./package.sh                # desktop app image (tar.gz) + Android APK(s)
#   ./package.sh --appimage     # also build a single-file *.AppImage (needs appimagetool)
#   ./package.sh --installer    # also build the OS-native installer (.deb/.rpm/.dmg)
#   ./package.sh --desktop      # desktop only
#   ./package.sh --android      # Android only
#   ./package.sh --clean        # wipe dist/ first
set -euo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
GRADLEW="$DIR/gradlew"
# Version from the single source of truth (gradle.properties: servdVersion).
version="$(grep -E '^[[:space:]]*servdVersion[[:space:]]*=' "$DIR/gradle.properties" | head -1 | cut -d= -f2 | tr -d '[:space:]')"
[ -n "$version" ] || version="1.0.0"

# ---- flags ----
desktop=0; android=0; installer=0; appimage=0; clean=0
for a in "$@"; do
    case "$a" in
        --desktop) desktop=1 ;;
        --android) android=1 ;;
        --installer) installer=1 ;;
        --appimage) appimage=1 ;;
        --clean) clean=1 ;;
        -h|--help) grep '^#' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
        *) echo "unknown flag: $a" >&2; exit 2 ;;
    esac
done
# Default to building both when neither target is named.
if [ "$desktop" -eq 0 ] && [ "$android" -eq 0 ]; then desktop=1; android=1; fi

# ---- minimal, meaningful color (step / artifact / skip / failure) ----
if [ -t 1 ]; then C_STEP=$'\033[36m'; C_OK=$'\033[32m'; C_WARN=$'\033[33m'; C_ERR=$'\033[91m'; C_OFF=$'\033[0m'
else C_STEP=''; C_OK=''; C_WARN=''; C_ERR=''; C_OFF=''; fi
step() { printf '%s==> %s%s\n' "$C_STEP" "$1" "$C_OFF"; }
ok()   { printf '%s    %s%s\n' "$C_OK" "$1" "$C_OFF"; }
warn() { printf '%s    %s%s\n' "$C_WARN" "$1" "$C_OFF"; }
die()  { printf '%s%s%s\n' "$C_ERR" "$1" "$C_OFF" >&2; exit 1; }

case "$(uname -s)" in Darwin) os="macos" ;; *) os="linux" ;; esac
arch="$(uname -m)"
dist="$DIR/dist"
[ "$clean" -eq 1 ] && rm -rf "$dist"
mkdir -p "$dist"

run_gradle() { "$GRADLEW" "$@" --console=plain || die "gradle failed: $*"; }

imageDir="$DIR/desktopHost/build/jpackage/servd"

build_appimage() {
    if ! command -v appimagetool >/dev/null 2>&1; then
        warn "appimagetool not found - skipping AppImage. Get it from https://appimage.github.io/appimagetool/ then re-run with --appimage."
        return
    fi
    step "Desktop: wrapping the app image into a single-file AppImage..."
    local appdir="$DIR/desktopHost/build/appimage/servd.AppDir"
    rm -rf "$appdir"; mkdir -p "$appdir/usr"
    cp -r "$imageDir"/. "$appdir/usr/"          # bin/ lib/ runtime/
    cat > "$appdir/AppRun" <<'EOF'
#!/bin/sh
HERE="$(dirname "$(readlink -f "$0")")"
exec "$HERE/usr/bin/servd" "$@"
EOF
    chmod +x "$appdir/AppRun"
    cat > "$appdir/servd.desktop" <<'EOF'
[Desktop Entry]
Type=Application
Name=servd
Exec=servd
Icon=servd
Categories=Network;Utility;
Terminal=true
EOF
    # appimagetool needs an icon file matching Icon=; ship a minimal 1x1 PNG placeholder.
    printf 'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+M8AAAMBAQDJ/pLvAAAAAElFTkSuQmCC' \
        | base64 -d > "$appdir/servd.png"
    local out="$dist/servd-$version-$arch.AppImage"
    ARCH="$arch" appimagetool "$appdir" "$out" >/dev/null || die "appimagetool failed"
    chmod +x "$out"
    ok "AppImage   -> $out   (chmod +x already set; run it directly)"
}

if [ "$desktop" -eq 1 ]; then
    step "Desktop: building app image (bundled JRE)..."
    run_gradle :desktopHost:jpackageImage
    [ -d "$imageDir" ] || die "app image not found at $imageDir"
    tarball="$dist/servd-$version-$os-$arch.tar.gz"
    rm -f "$tarball"
    tar -C "$(dirname "$imageDir")" -czf "$tarball" "$(basename "$imageDir")"
    ok "app image  -> $tarball   (extract, then run servd/bin/servd)"

    [ "$appimage" -eq 1 ] && build_appimage

    if [ "$installer" -eq 1 ]; then
        step "Desktop: building native installer..."
        run_gradle :desktopHost:jpackageInstaller
        found=0
        for f in "$DIR"/desktopHost/build/jpackage/*.deb "$DIR"/desktopHost/build/jpackage/*.rpm \
                 "$DIR"/desktopHost/build/jpackage/*.dmg "$DIR"/desktopHost/build/jpackage/*.pkg; do
            [ -e "$f" ] || continue
            cp -f "$f" "$dist/"; ok "installer  -> $dist/$(basename "$f")"; found=1
        done
        [ "$found" -eq 0 ] && warn "no installer artifact produced (on Linux this needs the packaging tools for .deb/.rpm)."
    fi
fi

if [ "$android" -eq 1 ]; then
    step "Android: building debug APK..."
    run_gradle :androidApp:assembleDebug
    cp -f "$DIR/androidApp/build/outputs/apk/debug/androidApp-debug.apk" "$dist/servd-$version-debug.apk"
    ok "debug APK  -> $dist/servd-$version-debug.apk"

    if [ -f "$DIR/androidApp/keystore.properties" ]; then
        step "Android: building signed release APK..."
        run_gradle :androidApp:assembleRelease
        cp -f "$DIR/androidApp/build/outputs/apk/release/androidApp-release.apk" "$dist/servd-$version-release.apk"
        ok "release APK-> $dist/servd-$version-release.apk"
    else
        warn "(no androidApp/keystore.properties - skipping signed release; see README > Packaging)"
    fi
fi

printf '\n%sDone. Bundles are in: %s%s\n' "$C_OK" "$dist" "$C_OFF"
ls -1sh "$dist"
