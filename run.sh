#!/usr/bin/env bash
# run.sh - build/run servd on Linux/macOS.
#
# Running the server (no args, or args starting with --) launches the JVM directly and execs it,
# so Ctrl+C stops it cleanly. Any other argument is treated as a Gradle task and passed through.
#
# The TMP/TEMP socket workaround the Windows scripts need does NOT apply here - Linux/macOS temp
# dirs accept the JDK's AF_UNIX pipe socket, so this script keeps the default temp.
#
# Usage:
#   ./run.sh                       # run the server
#   ./run.sh --no-open --port 9443 # run the server with flags
#   ./run.sh discover              # find hubs on the network
#   ./run.sh :core:desktopTest     # any Gradle task(s)
set -euo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
GRADLEW="$DIR/gradlew"

# Minimal, meaningful color: cyan for a status note, red for a failure (only when on a terminal).
if [ -t 1 ]; then C_INFO=$'\033[36m'; C_ERR=$'\033[91m'; C_OFF=$'\033[0m'; else C_INFO=''; C_ERR=''; C_OFF=''; fi
info() { printf '%s%s%s\n' "$C_INFO" "$1" "$C_OFF"; }
err()  { printf '%s%s%s\n' "$C_ERR" "$1" "$C_OFF" >&2; }

# Server run = no args, or the first arg is a servd flag / the discover verb.
first="${1:-}"
if [ "$#" -eq 0 ] || [[ "$first" == --* ]] || [ "$first" = "discover" ]; then
    info "building servd..."   # the build below is quiet (-q), so say what's happening
    if ! "$GRADLEW" :desktopHost:installDist -q --console=plain; then
        err "build failed"; exit 1
    fi
    # `dir/*` is Java's classpath wildcard for all jars in the dir. Under Git Bash/Cygwin on
    # Windows, hand Java a native Windows path so it can resolve the jars.
    libdir="$DIR/desktopHost/build/install/servd/lib"
    if command -v cygpath >/dev/null 2>&1; then cp_arg="$(cygpath -w "$libdir")\\*"; else cp_arg="$libdir/*"; fi
    exec java -cp "$cp_arg" dev.servd.host.MainKt "$@"
fi

# Gradle task mode.
if ! "$GRADLEW" "$@"; then err "gradle failed"; exit 1; fi
