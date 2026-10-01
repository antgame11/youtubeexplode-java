#!/usr/bin/env bash
# Usage: ./music-search.sh "<query>" [--limit N] [--download N]
set -euo pipefail
cd "$(dirname "$0")"
INVOKE_DIR="$PWD"
mvn -q compile
[ -f target/cp.txt ] || mvn -q dependency:build-classpath -Dmdep.outputFile=target/cp.txt
CP="target/classes:$(cat target/cp.txt)"
mkdir -p target/examples
javac -cp "$CP" -d target/examples examples/MusicSearch.java
java -cp "$CP:target/examples" MusicSearch "$@"
