#!/bin/sh
set -e

if [ -z "$APPLICATION_SECRET" ]; then
  echo "ERROR: APPLICATION_SECRET environment variable is required" >&2
  exit 1
fi

OTEL_AGENT_PATH="/app/opentelemetry-javaagent.jar"
OTEL_AGENT_OPTS=""

if [ -f "$OTEL_AGENT_PATH" ]; then
  OTEL_AGENT_OPTS="-J-javaagent:$OTEL_AGENT_PATH"

  if [ -n "$OTEL_SDK_DISABLED" ]; then
    OTEL_AGENT_OPTS="$OTEL_AGENT_OPTS -Dotel.sdk.disabled=$OTEL_SDK_DISABLED"
  fi
  if [ -n "$OTEL_EXPORTER_OTLP_ENDPOINT" ]; then
    OTEL_AGENT_OPTS="$OTEL_AGENT_OPTS -Dotel.exporter.otlp.endpoint=$OTEL_EXPORTER_OTLP_ENDPOINT"
  fi
  if [ -n "$OTEL_SERVICE_NAME" ]; then
    OTEL_AGENT_OPTS="$OTEL_AGENT_OPTS -Dotel.service.name=$OTEL_SERVICE_NAME"
  fi
  if [ -n "$OTEL_EXPORTER_OTLP_HEADERS" ]; then
    OTEL_AGENT_OPTS="$OTEL_AGENT_OPTS -Dotel.exporter.otlp.headers=$OTEL_EXPORTER_OTLP_HEADERS"
  fi

  if [ -n "$DEBUG" ]; then
    echo "DEBUG: Applied OTEL options: $OTEL_AGENT_OPTS" >&2
  fi
else
  echo "WARNING: OpenTelemetry agent not found at $OTEL_AGENT_PATH, starting without instrumentation" >&2
fi

# Use exec to replace shell with Java process (ensures proper signal handling)
exec bin/skatemap-live \
  $OTEL_AGENT_OPTS \
  -Dplay.http.secret.key="${APPLICATION_SECRET}"
