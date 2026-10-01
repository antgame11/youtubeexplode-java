#!/usr/bin/env bash
# Usage: ./web.sh [--web-port 8080] [--host 127.0.0.1] [--cache DIR] [--offset-ms 0]
set -euo pipefail
cd "$(dirname "$0")"
# Load the repo's .env (SPOTIFY_CLIENT_ID); variables already set in your shell win
if [ -f ../.env ]; then set -a; . ../.env; set +a; fi
JAR=target/spotify-nowplaying-1.0.0-SNAPSHOT.jar
if [ ! -f "$JAR" ] || [ -n "$(find src pom.xml -newer "$JAR" -print -quit)" ]; then
  mvn -q package -DskipTests
fi
exec java -cp "$JAR" nowplaying.web.WebApp "$@"
