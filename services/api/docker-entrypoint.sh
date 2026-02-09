#!/bin/sh
set -e

if [ -z "$APPLICATION_SECRET" ]; then
  echo "ERROR: APPLICATION_SECRET environment variable is required" >&2
  exit 1
fi

# Use exec to replace shell with Java process (ensures proper signal handling)
exec bin/skatemap-live \
  -J-javaagent:/app/opentelemetry-javaagent.jar \
  -Dplay.http.secret.key="${APPLICATION_SECRET}"
