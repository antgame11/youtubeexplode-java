#!/usr/bin/env bash
# Builds everything: installs the library into your local Maven repo, then builds the single app jar.
# Usage: ./build.sh [--skip-tests]
set -euo pipefail
cd "$(dirname "$0")"
SKIP=""
[ "${1:-}" = "--skip-tests" ] && SKIP="-DskipTests"

echo "== Library =="
(cd youtubeexplode-java && mvn -q clean install $SKIP)
echo "== App =="
(cd spotify-nowplaying && mvn -q clean package $SKIP)
echo "Done: spotify-nowplaying/target/spotify-nowplaying-1.0.0-SNAPSHOT.jar"
echo "If the app is already running, stop it and start it again to pick up the new build."
