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