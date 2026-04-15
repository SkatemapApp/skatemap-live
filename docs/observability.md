# Observability

## OpenTelemetry Auto-Instrumentation

Skatemap Live uses OpenTelemetry for distributed tracing, metrics collection, and application monitoring. The OpenTelemetry Java agent (v2.24.0) provides automatic instrumentation without requiring code changes.

### What Gets Instrumented

The OpenTelemetry agent automatically captures:

- **HTTP Requests/Responses** - Method, route, status code, duration for all Play Framework endpoints
- **WebSocket Connections** - Connection lifecycle, upgrade requests
- **JVM Metrics** - Heap usage, garbage collection, thread counts, CPU usage
- **Pekko Streams** - Stream materialisation events (where supported)

### Configuration

#### Required Environment Variables

To export telemetry to Honeycomb (or any OTLP-compatible backend):

```bash
OTEL_SERVICE_NAME=skatemap-live
OTEL_EXPORTER_OTLP_ENDPOINT=https://api.honeycomb.io:443
OTEL_EXPORTER_OTLP_PROTOCOL=http/protobuf
OTEL_EXPORTER_OTLP_HEADERS=x-honeycomb-team=YOUR_API_KEY
```

**Critical:** `OTEL_EXPORTER_OTLP_PROTOCOL=http/protobuf` must be set for Honeycomb. Without it, the exporter defaults to gRPC, which causes authentication failures.

#### Optional Environment Variables

```bash
# Additional resource attributes
OTEL_RESOURCE_ATTRIBUTES=environment=production,region=eu-west-1

# Sampling strategy (default: parentbased_always_on)
OTEL_TRACES_SAMPLER=parentbased_traceidratio
OTEL_TRACES_SAMPLER_ARG=0.1  # Sample 10% of traces

# Export protocols (default: otlp for both)
OTEL_METRICS_EXPORTER=otlp
OTEL_LOGS_EXPORTER=otlp
```

### Railway Deployment Setup

1. **Obtain Honeycomb API Key**
   - Sign up at [honeycomb.io](https://www.honeycomb.io/)
   - Navigate to Team Settings → API Keys
   - Create new API key with "Send Events" permission

2. **Configure Railway Environment Variables**
   - Open your Railway project
   - Go to Variables tab
   - Add the required environment variables:
     ```
     OTEL_SERVICE_NAME=skatemap-live
     OTEL_EXPORTER_OTLP_ENDPOINT=https://api.honeycomb.io:443
     OTEL_EXPORTER_OTLP_PROTOCOL=http/protobuf
     OTEL_EXPORTER_OTLP_HEADERS=x-honeycomb-team=YOUR_API_KEY_HERE
     ```

3. **Deploy and Verify**
   - Deploy the application
   - Send test requests to your endpoints
   - Check Honeycomb UI (traces appear within 1-2 minutes)
   - Verify HTTP request spans show route, status, and duration

### How It Works

The OpenTelemetry agent is integrated into the Docker container startup:

1. **Build Time:** The agent JAR is downloaded during Docker image build (see `services/api/Dockerfile`)
2. **Runtime:** The `docker-entrypoint.sh` script attaches the agent via the `-javaagent` JVM flag
3. **Export:** Telemetry data is sent to the configured OTLP endpoint via HTTPS

The application starts normally even if:
- OpenTelemetry environment variables are not set (agent loads but doesn't export)
- The OTLP endpoint is unreachable (agent buffers and retries)
- Agent configuration is invalid (warnings logged, app continues)

### Verifying Telemetry

After deployment, verify traces are being captured:

1. Send requests to your application:
   ```bash
   curl https://your-app.railway.app/health
   curl -X PUT https://your-app.railway.app/skatingEvents/YOUR_EVENT_ID/skaters/YOUR_SKATER_ID \
     -H "Content-Type: application/json" \
     -d '{"coordinates": [-0.1278, 51.5074]}'
   ```

2. Open Honeycomb UI and check for incoming traces

3. Look for spans with:
   - `span.name = http.server.request`
   - Attributes: `http.method`, `http.route`, `http.status_code`
   - Duration measurements

### Troubleshooting

#### No Traces Appearing in Honeycomb

**Missing OTEL_EXPORTER_OTLP_PROTOCOL:**
```bash
# Check Railway variables - this MUST be set
OTEL_EXPORTER_OTLP_PROTOCOL=http/protobuf
```
Without this, the agent uses gRPC and fails to authenticate with Honeycomb.

**Invalid Header Format:**
```bash
# Correct format (no quotes, no spaces around =)
OTEL_EXPORTER_OTLP_HEADERS=x-honeycomb-team=YOUR_API_KEY

# Wrong - extra quotes
OTEL_EXPORTER_OTLP_HEADERS="x-honeycomb-team=YOUR_API_KEY"
```

**API Key Permissions:**
- Verify the API key has "Send Events" permission in Honeycomb Team Settings
- Old or revoked keys will cause silent export failures

**Ingestion Delay:**
- Traces can take 1-2 minutes to appear in Honeycomb after being sent
- Wait a few minutes before assuming traces are not arriving

#### Agent Warnings in Logs

**`WARN io.opentelemetry.exporter`:**
- Usually indicates the OTLP endpoint is unreachable
- Verify `OTEL_EXPORTER_OTLP_ENDPOINT` is correct
- Check Railway deployment logs for network issues

**`ERROR BatchSpanProcessor`:**
- Export failures due to network connectivity or authentication
- Check Honeycomb API key is valid
- Verify endpoint URL is correct

#### High Memory Usage

**Agent Buffer Growth:**
- If the OTLP backend is unavailable for extended periods, the agent buffers telemetry in memory
- Buffer can grow and consume heap space
- Restart the application to clear the buffer
- Fix backend connectivity to prevent recurrence

### Alternative Backends

While Honeycomb is the primary target, the application can export to any OTLP-compatible backend:

**Grafana Cloud:**
```bash
OTEL_EXPORTER_OTLP_ENDPOINT=https://otlp-gateway-prod-eu-west-0.grafana.net/otlp
OTEL_EXPORTER_OTLP_PROTOCOL=http/protobuf
OTEL_EXPORTER_OTLP_HEADERS=Authorization=Basic <base64-encoded-credentials>
```

**Datadog:**
```bash
OTEL_EXPORTER_OTLP_ENDPOINT=https://api.datadoghq.eu
OTEL_EXPORTER_OTLP_HEADERS=dd-api-key=YOUR_DATADOG_API_KEY
```

**New Relic:**
```bash
OTEL_EXPORTER_OTLP_ENDPOINT=https://otlp.nr-data.net:4318
OTEL_EXPORTER_OTLP_HEADERS=api-key=YOUR_NEW_RELIC_LICENSE_KEY
```

The instrumentation remains portable—only endpoint and authentication headers change.

### Limitations

**Auto-Instrumentation Only:**
- Current implementation uses only OpenTelemetry auto-instrumentation
- Business-specific metrics (active event count, location publish rate) require manual instrumentation
- Planned for future implementation (see [ADR 0006](adr/0006-observability-strategy-opentelemetry.md))

**No Local Development Configuration:**
- OpenTelemetry is configured for Railway deployment only
- Local development with `sbt run` does not include the agent
- To test locally with the agent, use Docker:
  ```bash
  docker build -t skatemap-live services/api
  docker run -p 9000:9000 \
    -e APPLICATION_SECRET="$(openssl rand -hex 32)" \
    -e OTEL_SERVICE_NAME=skatemap-live-local \
    -e OTEL_EXPORTER_OTLP_ENDPOINT=https://api.honeycomb.io:443 \
    -e OTEL_EXPORTER_OTLP_PROTOCOL=http/protobuf \
    -e OTEL_EXPORTER_OTLP_HEADERS=x-honeycomb-team=YOUR_API_KEY \
    skatemap-live
  ```

### Further Reading

- [OpenTelemetry Java Agent Configuration](https://opentelemetry.io/docs/languages/java/automatic/configuration/)
- [Honeycomb OpenTelemetry Documentation](https://docs.honeycomb.io/send-data/opentelemetry/)
- [ADR 0006: Observability Strategy](adr/0006-observability-strategy-opentelemetry.md) - Rationale for OpenTelemetry adoption and phased implementation plan
