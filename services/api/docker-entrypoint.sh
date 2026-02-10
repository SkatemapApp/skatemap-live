#!/bin/sh
set -e

if [ -z "$APPLICATION_SECRET" ]; then
  echo "ERROR: APPLICATION_SECRET environment variable is required" >&2
  exit 1
fi

OTEL_AGENT_PATH="/app/opentelemetry-javaagent.jar"

if [ ! -f "$OTEL_AGENT_PATH" ]; then
  echo "WARNING: OpenTelemetry agent not found at $OTEL_AGENT_PATH, starting without instrumentation" >&2
fi

if [ -n "$DEBUG" ]; then
  echo "DEBUG: OTEL configuration:" >&2
  [ -f "$OTEL_AGENT_PATH" ] && echo "  Agent: $OTEL_AGENT_PATH" >&2
  [ -n "$OTEL_SDK_DISABLED" ] && echo "  SDK Disabled: $OTEL_SDK_DISABLED" >&2
  [ -n "$OTEL_EXPORTER_OTLP_ENDPOINT" ] && echo "  Endpoint: $OTEL_EXPORTER_OTLP_ENDPOINT" >&2
  [ -n "$OTEL_SERVICE_NAME" ] && echo "  Service: $OTEL_SERVICE_NAME" >&2
  [ -n "$OTEL_EXPORTER_OTLP_HEADERS" ] && echo "  Headers: [redacted]" >&2
fi

# Use exec to replace shell with Java process (ensures proper signal handling)
# Arguments are passed directly with proper quoting to handle values with spaces
exec bin/skatemap-live \
  ${OTEL_AGENT_PATH:+-J-javaagent:"$OTEL_AGENT_PATH"} \
  ${OTEL_SDK_DISABLED:+-Dotel.sdk.disabled="$OTEL_SDK_DISABLED"} \
  ${OTEL_EXPORTER_OTLP_ENDPOINT:+-Dotel.exporter.otlp.endpoint="$OTEL_EXPORTER_OTLP_ENDPOINT"} \
  ${OTEL_SERVICE_NAME:+-Dotel.service.name="$OTEL_SERVICE_NAME"} \
  ${OTEL_EXPORTER_OTLP_HEADERS:+-Dotel.exporter.otlp.headers="$OTEL_EXPORTER_OTLP_HEADERS"} \
  -Dplay.http.secret.key="${APPLICATION_SECRET}"
