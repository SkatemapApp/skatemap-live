#!/bin/sh
set -e

if [ -z "$APPLICATION_SECRET" ]; then
  echo "ERROR: APPLICATION_SECRET environment variable is required" >&2
  exit 1
fi

OTEL_AGENT_PATH="/app/opentelemetry-javaagent.jar"
JAVA_OPTS=""

if [ -f "$OTEL_AGENT_PATH" ]; then
  JAVA_OPTS="-J-javaagent:$OTEL_AGENT_PATH"
else
  echo "WARNING: OpenTelemetry agent not found at $OTEL_AGENT_PATH, starting without instrumentation" >&2
fi

# Use exec to replace shell with Java process (ensures proper signal handling)
exec bin/skatemap-live \
  $JAVA_OPTS \
  -Dplay.http.secret.key="${APPLICATION_SECRET}"
