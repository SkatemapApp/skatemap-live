# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Skatemap Live is a Scala Play Framework application that handles location updates for skating events. The application uses Scala 2.13 and follows functional programming principles with no exception throwing.

## Development Commands

### Build and Test
- `sbt ciBuild` - Run the full CI pipeline (clean, format check, style check, coverage, test, coverage report)
- `sbt devBuild` - Run development build (clean, format, style check, test)
- `sbt test` - Run all tests
- `sbt coverage test coverageReport` - Run tests with coverage reporting
- `sbt scalafmtCheckAll` - Check code formatting
- `sbt scalafmt` - Format code
- `sbt scalastyle` - Check code style rules
- `sbt run` - Start the development server

### Coverage Requirements
- Minimum statement coverage: 100%
- Build fails if coverage is below minimum

## Architecture

### Core Domain
The application follows a clean architecture pattern with clear separation of concerns:

- **Core Domain** (`src/main/scala/core/`): Pure business logic with no framework dependencies
  - `Location`: Core data model for skater locations
  - `LocationValidator`: Validation logic using Either for error handling
  - `IngestService`: Handles location updates
  - `LocationStore`: Data persistence abstraction

- **Validation** (`src/main/scala/core/validation/`): Pure validation functions
  - `UuidValidator`: UUID format validation
  - `CoordinateValidator`: Geographic coordinate validation

- **JSON Processing** (`src/main/scala/core/json/`): JSON parsing utilities
  - `JsonFieldExtractor`: Field extraction from JSON
  - `CoordinateJsonParser`: Coordinate parsing from JSON

### Adapters
- **Play HTTP Adapters** (`src/main/scala/adapters/playhttp/`): Framework-specific adapters
  - `LocationJsonFormats`: Play JSON formatters
  - `ValidationErrorAdapter`: Converts validation errors to HTTP responses

### Controllers
- **Play Controllers** (`src/main/scala/controllers/play/`): HTTP endpoint handlers
  - `LocationController`: Handles PUT `/skatingEvents/:skatingEventId/skaters/:skaterId`

### Key Patterns
- Use `Either` for error handling instead of exceptions
- Validation returns `Either[ValidationError, ValidatedType]`
- Pure functions in core domain with no side effects
- Framework-specific code isolated in adapters
- Pekko Streams patterns documented in `/docs/system-design/stream-topology.md`

## Configuration

All configuration is in `application.conf` under the `skatemap` section:

### Location Configuration
- `skatemap.location.ttlSeconds`: Time-to-live for location data (must be positive integer)

### Cleanup Configuration
- `skatemap.cleanup.initialDelaySeconds`: Delay before first cleanup run (must be positive integer)
- `skatemap.cleanup.intervalSeconds`: Interval between cleanup runs (must be positive integer)

### Stream Configuration
- `skatemap.stream.batchSize`: Maximum locations per WebSocket batch (must be positive integer)
- `skatemap.stream.batchIntervalMillis`: Maximum time between batches in milliseconds (must be positive integer)

**Performance Trade-offs:**
- Higher `batchSize`: Fewer WebSocket messages, better throughput, higher latency
- Lower `batchSize`: More frequent updates, lower latency, higher overhead
- Higher `batchIntervalMillis`: Batches fill before sending, better for high-volume events
- Lower `batchIntervalMillis`: Messages sent more frequently, better for low-activity events

### Hub Configuration
- `skatemap.hub.ttlSeconds`: Time-to-live for unused broadcast hubs (must be positive integer)
- `skatemap.hub.cleanupIntervalSeconds`: Interval between hub cleanup runs (must be positive integer)

**Design Rationale:**
- Default TTL: 300 seconds (5 minutes) - Balances memory usage with connection patterns
- Default cleanup interval: 60 seconds (1 minute) - Provides regular cleanup without excessive overhead
- Cleanup interval should be less than TTL to ensure timely removal of unused hubs
- Hubs are created lazily and removed when not accessed (published to or subscribed) within TTL

All values are validated at startup. Missing or non-positive values cause startup failure.

## Environment Variables

### Required
- `APPLICATION_SECRET`: Generate with `sbt playGenerateSecret`

### Optional (with defaults)
- `PORT`: Application listening port (default: 9000)
- `LOCATION_TTL_SECONDS`: Time-to-live for location data (default: 30)
- `CLEANUP_INITIAL_DELAY_SECONDS`: Delay before first cleanup run (default: 10)
- `CLEANUP_INTERVAL_SECONDS`: Interval between cleanup runs (default: 10)
- `STREAM_BATCH_SIZE`: Maximum locations per WebSocket batch (default: 100)
- `STREAM_BATCH_INTERVAL_MILLIS`: Maximum time between batches in milliseconds (default: 500)
- `HUB_TTL_SECONDS`: Time-to-live for unused broadcast hubs (default: 300)
- `HUB_CLEANUP_INTERVAL_SECONDS`: Interval between hub cleanup runs (default: 60)
- `HUB_BUFFER_SIZE`: Number of messages to buffer per hub (default: 128)

## OpenTelemetry Observability

The application uses OpenTelemetry for distributed tracing and metrics collection. The OpenTelemetry Java agent (v2.24.0) provides automatic instrumentation without code changes, capturing telemetry data and exporting it to observability backends like Honeycomb.

### Overview

**Auto-instrumentation:** The OpenTelemetry Java agent is attached via the `-javaagent` JVM flag at application startup. It automatically instruments:
- HTTP request/response traces (Play Framework controllers)
- WebSocket connection handling (Pekko HTTP)
- JVM runtime metrics (heap usage, garbage collection, thread counts)
- Pekko Streams operations (where supported by the agent)

**Export:** Telemetry data (traces, metrics, and logs) is exported via the OpenTelemetry Protocol (OTLP) to configured backends. The application is designed to work with Honeycomb, but can export to any OTLP-compatible backend.

**No code changes required:** The agent operates through bytecode instrumentation, requiring only environment variable configuration.

### Required Environment Variables

To enable telemetry export to Honeycomb:

- `OTEL_SERVICE_NAME`: Service identifier in telemetry data (e.g., `skatemap-live`)
- `OTEL_EXPORTER_OTLP_ENDPOINT`: OTLP endpoint URL (e.g., `https://api.honeycomb.io:443`)
- `OTEL_EXPORTER_OTLP_PROTOCOL`: Protocol for OTLP export (must be `http/protobuf` for Honeycomb)
- `OTEL_EXPORTER_OTLP_HEADERS`: Authentication headers (format: `x-honeycomb-team=<api-key>`)

**Critical:** `OTEL_EXPORTER_OTLP_PROTOCOL=http/protobuf` is required for Honeycomb and similar OTLP HTTP endpoints. Without this variable, the exporter defaults to gRPC, which causes authentication failures with Honeycomb's HTTP endpoint.

### Optional Environment Variables

- `OTEL_RESOURCE_ATTRIBUTES`: Additional resource attributes as key-value pairs (format: `key1=value1,key2=value2`)
  - Example: `environment=production,region=eu-west-1`
- `OTEL_TRACES_SAMPLER`: Sampling strategy for traces
  - Default: `parentbased_always_on` (samples all traces)
  - Alternative: `parentbased_traceidratio` with `OTEL_TRACES_SAMPLER_ARG` for percentage-based sampling
- `OTEL_METRICS_EXPORTER`: Metrics export protocol
  - Default: `otlp` (exports metrics via OTLP)
  - Alternative: `none` (disables metrics export)
- `OTEL_LOGS_EXPORTER`: Logs export protocol
  - Default: `otlp` (exports logs via OTLP)
  - Alternative: `none` (disables log export)

### Honeycomb Setup

**1. Obtain API Key**

Create a Honeycomb account at [honeycomb.io](https://www.honeycomb.io/) and obtain an API key:
- Log in to Honeycomb
- Navigate to Team Settings → API Keys
- Create a new API key with "Send Events" permission
- Copy the API key for use in environment variables

**2. Configure for Railway Deployment**

Set the following environment variables in Railway:

```
OTEL_SERVICE_NAME=skatemap-live
OTEL_EXPORTER_OTLP_ENDPOINT=https://api.honeycomb.io:443
OTEL_EXPORTER_OTLP_PROTOCOL=http/protobuf
OTEL_EXPORTER_OTLP_HEADERS=x-honeycomb-team=YOUR_API_KEY_HERE
```

Replace `YOUR_API_KEY_HERE` with your actual Honeycomb API key.

**3. Verify Telemetry Export**

After deployment:
- Send test requests to the application
- Check Honeycomb UI for incoming traces (may take 1-2 minutes to appear)
- Verify traces show HTTP request spans with route, status code, and duration

**Documentation:**
- [Honeycomb OpenTelemetry Documentation](https://docs.honeycomb.io/send-data/opentelemetry/)
- [OpenTelemetry Java Agent Configuration](https://opentelemetry.io/docs/languages/java/automatic/configuration/)

**Alternative Backends:** The application can export to any OTLP-compatible backend (Grafana Cloud, Datadog, New Relic, Lightstep) by changing the `OTEL_EXPORTER_OTLP_ENDPOINT` and `OTEL_EXPORTER_OTLP_HEADERS` to match the backend's requirements. The instrumentation remains portable across observability platforms.

### Instrumented Components

The OpenTelemetry Java agent automatically instruments the following components:

**HTTP Requests/Responses (Play Controllers)**
- Traces every HTTP request with method, route, status code, and duration
- Span name: `http.server.request`
- Attributes: `http.method`, `http.route`, `http.status_code`, `http.target`

**WebSocket Connections (Pekko HTTP)**
- Traces WebSocket upgrade requests and connection lifecycle
- Captures connection establishment and termination events

**JVM Runtime Metrics**
- Heap memory usage (used, committed, max)
- Garbage collection events (count, duration, heap size changes)
- Thread counts (active, daemon, peak)
- CPU usage

**Pekko Streams Operations**
- Stream materialisation events (where supported by agent instrumentation)
- Backpressure signals and buffer states may have limited visibility

**Note:** Business-specific metrics (active event count, location publish rate, per-event subscriber count) require manual instrumentation, which is planned for future implementation. See [ADR 0006](../../docs/adr/0006-observability-strategy-opentelemetry.md) for the phased instrumentation strategy.

### Manual Instrumentation with TracedFuture

**Challenge:** OpenTelemetry context is stored in ThreadLocal, which doesn't survive Scala Future boundaries. When a span is created and subsequent work runs in a Future, the trace context is lost because Futures execute on different threads from the ForkJoinPool.

**Solution:** The `TracedFuture` utility (`skatemap.observability.TracedFuture`) captures the OpenTelemetry context before a Future executes and restores it on the Future's thread, ensuring parent-child span relationships are preserved.

**Usage pattern:**
```scala
import skatemap.observability.TracedFuture
import scala.concurrent.Future

// Implicit dependencies (provided by DI)
implicit val tracer: Tracer = ...
implicit val ec: ExecutionContext = ...

// Wrap async operations with traced
TracedFuture.traced("operation-name") {
  Future {
    // Your async work here
  }
}
```

**Example - tracing a store operation:**
```scala
def put(location: Location): Future[Unit] = {
  TracedFuture.traced("location.store") {
    Future {
      store.put(location.eventId, location.skaterId, location)
    }
  }
}
```

**What TracedFuture does:**
1. Captures the current OpenTelemetry context (before Future executes)
2. Creates a span with the specified name
3. Restores the context when the Future runs on its thread
4. Records exceptions if the Future fails
5. Sets span status to OK (success) or ERROR (failure)
6. Ends the span when the Future completes

**When to use TracedFuture:**
- Tracing async operations in domain services (store, broadcaster)
- Creating custom spans for business operations
- Any time you need trace context to survive Future boundaries

**Performance implications:**
- Minimal overhead: one additional Future allocation and Scope lifecycle per traced operation
- Suitable for most business operations (controller actions, service calls, store operations)
- For very high-frequency operations (thousands per second), consider whether the tracing overhead is justified by the observability value

**Dependencies:**
- OpenTelemetry API: `io.opentelemetry:opentelemetry-api:1.32.0`
- Requires implicit `Tracer` (provided by OTel agent) and `ExecutionContext`

**Testing:** See `TracedFutureSpec` for examples of testing traced operations and verifying parent-child span relationships.

### Graceful Degradation

The OpenTelemetry agent is designed to fail gracefully:

**Agent loads unconditionally:** The agent JAR is attached at startup even if OpenTelemetry environment variables are not set. Without configuration, the agent loads but does not export telemetry data.

**Application continues if backend unreachable:** If the OTLP endpoint (e.g., Honeycomb) is unreachable or returns errors, the agent buffers telemetry data and retries export. The application continues serving requests without interruption.

**No startup failure on misconfiguration:** Invalid or missing OTEL environment variables do not cause application startup failure. The agent will log warnings but allow the application to start normally.

**Telemetry buffering and retry:** The agent maintains an in-memory buffer of telemetry data and retries failed exports with exponential backoff. If the buffer fills, older telemetry is dropped to prevent memory exhaustion.

**Silent degradation risk:** Misconfigured OTLP endpoints result in lost telemetry with no application errors. Always verify trace ingestion in Honeycomb after deployment or configuration changes.

### Troubleshooting

**No traces appearing in Honeycomb:**
- Verify `OTEL_EXPORTER_OTLP_PROTOCOL=http/protobuf` is set (missing this causes gRPC authentication failures)
- Check `OTEL_EXPORTER_OTLP_HEADERS` format is `x-honeycomb-team=<api-key>` (no extra quotes or spaces)
- Confirm API key has "Send Events" permission in Honeycomb Team Settings
- Wait 1-2 minutes after sending requests (ingestion has slight delay)

**Agent warnings in application logs:**
- `WARN io.opentelemetry.exporter` usually indicates endpoint unreachable—verify `OTEL_EXPORTER_OTLP_ENDPOINT` is correct
- `ERROR BatchSpanProcessor` suggests export failures—check network connectivity to OTLP endpoint

**High memory usage from agent:**
- Agent buffers telemetry during export failures—if backend is down for extended periods, buffer can grow
- Restart application to clear buffer, then fix backend connectivity

## Code Style
- Uses Scalafmt with 120 character line limit
- Scalastyle enabled for style checking (fails build on violations)
- Wildcard imports prohibited (use explicit imports)
- Wartremover enabled for compile-time checking
- No Twirl templates (disabled PlayLayoutPlugin)
- Follows functional programming idioms

## Profiling and Performance Analysis

The application is configured with JVM profiling flags to support memory leak diagnosis and performance analysis. See [ADR 0003](../../docs/adr/0003-jvm-profiling-tooling.md) for tooling decisions.

### Heap Dump Analysis

Heap dumps are automatically generated on OutOfMemoryError and saved to `heap-dumps/` directory.

Generate heap dump on-demand:
```bash
jcmd $(jps | grep PlayRun | awk '{print $1}') GC.heap_dump heap-dumps/snapshot.hprof
```

Analyse with Eclipse MAT:
```bash
brew install --cask mat
open -a "Memory Analyzer" heap-dumps/snapshot.hprof
```

### GC Logging

GC logs are automatically generated with timestamps in current directory (format: `gc-<timestamp>.log`).

### Continuous Memory Monitoring with JFR

Java Flight Recorder (JFR) provides continuous heap monitoring during profiling tests. See [ADR 0005](../../docs/adr/0005-jfr-continuous-memory-monitoring.md) for tool selection rationale.

**Why JFR:** Heap dump snapshots (used for MAT analysis) miss continuous growth trends between snapshots. JFR captures heap usage throughout entire test duration, generating memory-over-time graphs that detect linear growth patterns.

**Automated workflow** (recommended):
```bash
./tools/profiling/profile-memory-leak.sh
```

The script automatically:
1. Starts JFR recording when application starts
2. Runs load test and takes periodic heap dumps
3. Generates memory usage graph from JFR recording
4. Outputs both graph (for trend analysis) and heap dumps (for MAT analysis)

**Manual JFR commands:**

Start recording:
```bash
jcmd $(jps | grep PlayRun | awk '{print $1}') JFR.start name=memory-test settings=profile duration=30m filename=recording.jfr
```

Generate memory graph from recording:
```bash
jfr print --events jdk.GCHeapSummary recording.jfr | python3 tools/profiling/jfr-to-memory-graph.py memory-graph.png
```

The graph shows:
- Continuous heap usage over time (every GC event)
- Linear regression growth rate (MB/min)
- Assessment: LEAK DETECTED (>5 MB/min), Potential leak (>1 MB/min), or Stable

**Critical:** Use JFR graphs to validate memory leak fixes locally before Railway deployment. Heap dump snapshots alone are insufficient (see [Issue #171](https://github.com/SkatemapApp/skatemap-live/issues/171)).

### CPU/Allocation Profiling with async-profiler

Download async-profiler:
```bash
ASYNC_PROFILER_VERSION=3.0
wget https://github.com/async-profiler/async-profiler/releases/download/v${ASYNC_PROFILER_VERSION}/async-profiler-${ASYNC_PROFILER_VERSION}-macos.tar.gz
tar xzf async-profiler-${ASYNC_PROFILER_VERSION}-macos.tar.gz
```

Generate allocation flame graph (60 second sample):
```bash
./async-profiler-${ASYNC_PROFILER_VERSION}-macos/bin/asprof -d 60 -e alloc -f flamegraph-alloc.html $(jps | grep PlayRun | awk '{print $1}')
```

Generate CPU flame graph (60 second sample):
```bash
./async-profiler-${ASYNC_PROFILER_VERSION}-macos/bin/asprof -d 60 -e cpu -f flamegraph-cpu.html $(jps | grep PlayRun | awk '{print $1}')
```

### Memory Leak Investigation Workflow

This workflow helps diagnose memory leaks by profiling the application under load and comparing heap state before/after fixes.

#### Automated Profiling (Recommended)

From repository root:
```bash
./tools/profiling/profile-memory-leak.sh
```

This script automates the entire profiling workflow (default: 30 min load + 20 min idle). Customise with environment variables:
```bash
LOAD_DURATION_MINUTES=45 IDLE_DURATION_MINUTES=30 SKATERS_PER_EVENT=20 ./tools/profiling/profile-memory-leak.sh
```

The script will:
1. Start the application
2. Run load test for specified duration
3. Take heap dump after load
4. Stop load test and wait (idle period)
5. Take heap dump after idle
6. Stop application
7. Report heap dump locations for analysis in Eclipse MAT

#### Manual Profiling

For manual step-by-step profiling:

##### Setup

Ensure heap-dumps directory exists:
```bash
mkdir -p heap-dumps
```

##### Step 1: Start Application

From `services/api` directory:
```bash
sbt run
```

Wait ~30 seconds for application to start. Verify it's running:
```bash
curl http://localhost:9000/health
```

##### Step 2: Identify JVM Process

Find the PID of the running application:
```bash
jps
```

Look for `sbt-launch.jar` in the output. Note the PID (first column).

Alternatively, find which process is listening on port 9000:
```bash
lsof -ti :9000
```

##### Step 3: Run Load Test

From repository root, in a separate terminal:
```bash
cd tools/load-testing
EVENT_ID=$(uuidgen | tr '[:upper:]' '[:lower:]')
go run ./cmd/simulate-skaters \
  --target-url http://localhost:9000 \
  --event-id $EVENT_ID \
  --skaters-per-event 10 \
  --update-interval 3s
```

Let this run for 30 minutes. Stop with Ctrl+C when complete.

##### Step 4: Take Heap Dump After Load

Replace `<PID>` with the PID from Step 2:
```bash
jcmd <PID> GC.heap_dump $(pwd)/heap-dumps/after-30min-load.hprof
```

Example with PID 12345:
```bash
jcmd 12345 GC.heap_dump $(pwd)/heap-dumps/after-30min-load.hprof
```

##### Step 5: Idle Period (Optional)

Wait 20 minutes without load to observe if memory is released by GC. Then take another heap dump:
```bash
jcmd <PID> GC.heap_dump $(pwd)/heap-dumps/after-20min-idle.hprof
```

##### Step 6: Analyse Heap Dumps with Eclipse MAT

Open heap dump in MAT:
```bash
open -a "Memory Analyzer" heap-dumps/after-30min-load.hprof
```

In MAT:
1. Run "Leak Suspects Report" (automatic on file open)
2. Use OQL to search for specific classes:
   ```sql
   SELECT * FROM org.apache.pekko.stream.impl.* WHERE @instanceof(o)
   ```
3. Look for BroadcastHub, MergeHub, Source materializations
4. Check "List objects → with incoming references" to see what holds them
5. Examine dominator tree for memory-heavy objects

##### Step 7: Compare Before/After

To verify a fix:
1. Profile baseline (without fix) using steps above
2. Switch branches to version with fix
3. Profile again with identical load test parameters
4. Compare heap dumps:
   - Object counts for leaked classes (should decrease)
   - Total heap size after idle period (should release memory)
   - Dominator tree (leaked objects should be absent)

##### Tips

- Heap dump files are large (~160MB+). Git ignores them automatically.
- Use `$(pwd)` for absolute paths to avoid "No such file or directory" errors
- The JVM interprets relative paths from its working directory, not your shell
- GC logs are written to current directory as `gc-<timestamp>.log`

## Commit Messages
- Use conventional commit format: `type: description`
- Common types: `chore:`, `fix:`, `feat:`, `refactor:`
- Use conventional commit format in pull request titles.