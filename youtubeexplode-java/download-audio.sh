#!/usr/bin/env bash
# Usage: ./download-audio.sh <video id or url> [output dir]
set -euo pipefail
cd "$(dirname "$0")"
mvn -q compile
[ -f target/cp.txt ] || mvn -q dependency:build-classpath -Dmdep.outputFile=target/cp.txt
CP="target/classes:$(cat target/cp.txt)"
mkdir -p target/examples
javac -cp "$CP" -d target/examples examples/DownloadAudio.java
java -cp "$CP:target/examples" DownloadAudio "$@"
