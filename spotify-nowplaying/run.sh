#!/usr/bin/env bash
# Usage: ./run.sh [--out DIR] [--watch] [--port 8888]
set -euo pipefail
cd "$(dirname "$0")"
# Load the repo's .env (SPOTIFY_CLIENT_ID); variables already set in your shell win
if [ -f ../.env ]; then set -a; . ../.env; set +a; fi
JAR=target/spotify-nowplaying-1.0.0-SNAPSHOT.jar
if [ ! -f "$JAR" ] || [ -n "$(find src pom.xml -newer "$JAR" -print -quit)" ]; then
  mvn -q package -DskipTests
fi
exec java -jar "$JAR" "$@"
