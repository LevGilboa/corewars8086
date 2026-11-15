#!/bin/sh
# Launches CodeGuru Extreme. Finds the jar in bin/ regardless of its version.
cd "$(dirname "$0")" || exit 1
JAR=$(ls bin/corewars8086-*.jar 2>/dev/null | sort | tail -n 1)
if [ -z "$JAR" ]; then
    echo "No corewars8086 jar found in bin/" >&2
    exit 1
fi
exec java -jar "$JAR" --noparallel --zombieSpeed 2 "$@"
