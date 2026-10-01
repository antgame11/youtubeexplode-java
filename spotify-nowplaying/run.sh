#!/usr/bin/env bash
# Usage: ./run.sh [download|web|login|logout] [options]   (see: ./run.sh help)
# Builds the single jar if needed, then runs it. Settings are read from ../.env by the jar itself.
set -euo pipefail
cd "$(dirname "$0")"
JAR=target/spotify-nowplaying-1.0.0-SNAPSHOT.jar
if [ ! -f "$JAR" ] || [ -n "$(find src pom.xml -newer "$JAR" -print -quit)" ]; then
  mvn -q package -DskipTests
fi
exec java -jar "$JAR" "$@"
