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
wget https://github.com/async-profiler/async-profiler/releases/latest/download/async-profiler-3.0-macos.tar.gz
tar xzf async-profiler-3.0-macos.tar.gz
```

Generate allocation flame graph (60 second sample):
```bash
./async-profiler-3.0-macos/bin/asprof -d 60 -e alloc -f flamegraph-alloc.html $(jps | grep PlayRun | awk '{print $1}')
```

Generate CPU flame graph (60 second sample):
```bash
./async-profiler-3.0-macos/bin/asprof -d 60 -e cpu -f flamegraph-cpu.html $(jps | grep PlayRun | awk '{print $1}')
```

### Memory Leak Investigation Workflow

1. Start application: `sbt run`
2. Run load test for extended period (e.g., 30 minutes)
3. Take heap dump: `jcmd <pid> GC.heap_dump heap-dumps/after-test.hprof`
4. Open in Eclipse MAT and run "Leak Suspects Report"
5. Search for specific classes (e.g., BroadcastHub) using OQL queries
6. Examine reference chains to identify what's holding objects in memory

## Commit Messages
- Use conventional commit format: `type: description`
- Common types: `chore:`, `fix:`, `feat:`, `refactor:`
- Use conventional commit format in pull request titles.