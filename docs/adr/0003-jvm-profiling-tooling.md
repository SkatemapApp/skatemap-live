# ADR 0003: async-profiler and Eclipse MAT for JVM Profiling

## Status

Proposed

## Context

Skatemap Live requires profiling tools to diagnose performance and memory issues in the Scala/Pekko application. Issue #138 identified a critical memory leak (15-18 MB/min linear growth) caused by Pekko stream materializations not being properly terminated. This investigation revealed the need for standard tooling to:

- Analyse heap dumps to identify memory leaks and reference chains
- Profile CPU usage and allocation patterns
- Diagnose Pekko stream and actor lifecycle issues
- Verify fixes work before production deployment

The application stack is:
- Scala 2.13
- Play Framework 3.0.9
- Pekko Streams/Actors 1.1.2

Requirements for profiling tools:
- Work locally on developer machines (macOS/Linux)
- Free/open source with strong community support
- Industry-standard tools (team learning transfers to other projects)
- Capable of answering "why isn't this object being garbage collected?"
- Suitable for both one-off investigations and future BAU diagnostics

## Decision

Use **async-profiler** for CPU and allocation profiling, and **Eclipse Memory Analyser Tool (MAT)** for heap dump analysis and memory leak detection.

### Workflow

1. **Local development profiling:**
   - Add JVM flags to `build.sbt` for GC logging and heap dump generation
   - Run application with load testing
   - Capture heap dumps using `jcmd` or automatic generation on OOM
   - Analyse heap dumps in Eclipse MAT to identify leaks
   - Use async-profiler for CPU/allocation flame graphs when needed

2. **Heap dump analysis (primary use case):**
   - Eclipse MAT provides "Leak Suspects Report" to automatically identify retention issues
   - Dominator tree shows what objects hold the most memory
   - OQL queries search for specific classes (e.g., BroadcastHub instances)
   - Reference chains show what's preventing garbage collection

3. **CPU/allocation profiling (secondary use case):**
   - async-profiler generates flame graphs showing hot paths
   - Low overhead (~1-2% CPU) allows production use if needed
   - Standard flame graph format used by Netflix, LinkedIn, and others

## Consequences

### Benefits

**Industry Standard Tools**
- Eclipse MAT is the de facto standard for JVM heap analysis
- async-profiler is widely used (Netflix, LinkedIn) for modern JVM profiling
- Team learning and knowledge transfers to other Scala/Java projects
- Large communities provide documentation, tutorials, and support

**Free and Open Source**
- No licensing costs
- No vendor lock-in
- Can be used across entire team without budget approval

**Effective for Memory Leak Diagnosis**
- MAT's leak suspects report automatically identifies likely leaks
- Reference tracking answers "why isn't this BroadcastHub being GC'd?"
- Heap dump analysis is offline, no performance impact on running application
- Can compare heap dumps before/after fixes to verify resolution

**Suitable for Pekko/Akka Applications**
- MAT can inspect Pekko actor internals, stream graphs, and materializations
- async-profiler works well with concurrent Scala applications
- No special instrumentation or framework-specific tooling required

**Simple Integration**
- async-profiler: Single native library, generates HTML flame graphs
- MAT: Desktop application, opens `.hprof` heap dump files
- JVM flags in `build.sbt` enable heap dumps and GC logging
- No application code changes required

### Trade-offs

**Learning Curve**
- Eclipse MAT UI requires familiarity with dominators, shallow/retained size
- async-profiler is CLI-based (less interactive than commercial GUIs)
- Team primarily familiar with Scala, not JVM internals
- Mitigation: Well-documented tools with extensive tutorials available

**No Real-Time GUI Profiling**
- MAT analyses static heap dumps, not live application state
- async-profiler requires CLI invocation, not always-on monitoring
- Cannot see objects accumulating in real-time like commercial profilers
- Mitigation: Heap dumps can be captured at any point; acceptable for investigation workflow

**Heap Dump File Size**
- Heap dumps can be multi-GB for applications with large heaps
- Requires disk space and time to generate/transfer/analyse
- Mitigation: Use `-live` flag to dump only reachable objects; store locally

**No Built-In Pekko/Akka Visualization**
- Generic JVM tools, not Pekko-specific like Lightbend Telemetry
- Need to understand Pekko internal class names to query heap dumps
- No pre-built dashboards for actor counts, stream throughput, etc.
- Mitigation: Pekko internals are inspectable; monitoring tools (Kamon) address different use case

## Alternatives Considered

### Pyroscope (Continuous Profiling Platform)

**Evaluated:** Modern continuous profiling platform with JVM support via async-profiler backend.

**Rejected because:**
- Allocation profiling shows "where objects are created", not "why they're retained"
- For Issue #138, we already know where BroadcastHubs are created; need to know why they're not GC'd
- Requires separate Pyroscope server (Docker container or cloud service)
- More complex infrastructure for one-off investigations
- Better suited for production continuous profiling, not local debugging

**When to reconsider:** If we need ongoing production profiling with time-series allocation views and team has capacity to run Pyroscope infrastructure.

### VisualVM (JDK Built-in Tool)

**Evaluated:** Free heap dump analyser and profiler bundled with JDK.

**Rejected because:**
- Dated UI compared to Eclipse MAT
- Smaller community and less active development
- MAT's leak suspects report is more powerful for leak detection
- async-profiler provides better CPU profiling than VisualVM's sampler

**When to reconsider:** If Eclipse MAT proves too heavyweight or team prefers JDK-bundled tools.

### JProfiler / YourKit (Commercial Profilers)

**Evaluated:** Best-in-class commercial JVM profilers with excellent GUIs.

**Rejected because:**
- Commercial licensing (£400-600 per developer)
- Team-wide adoption requires budget approval and per-seat licenses
- Knowledge/expertise doesn't transfer as broadly (fewer users than free tools)
- Overkill for current needs; free tools are sufficient

**When to reconsider:** If free tools prove insufficient for complex profiling scenarios, or if budget becomes available and team wants best-in-class GUI experience.

### Lightbend Telemetry (Cinnamon)

**Evaluated:** Commercial Akka/Pekko-specific monitoring and instrumentation platform.

**Rejected because:**
- Commercial product with enterprise pricing (thousands per year)
- Designed for production monitoring (actor counts, stream throughput), not leak debugging
- Wouldn't answer "why is this BroadcastHub not being GC'd?" - shows metrics, not references
- Vendor lock-in to Lightbend ecosystem
- Different problem space: ongoing observability vs. one-off debugging

**When to reconsider:** If we need production Pekko-specific observability (actor metrics, stream backpressure) and have enterprise budget.

### Kamon (Open Source Akka/Pekko Instrumentation)

**Evaluated:** Free/open-source alternative to Lightbend Telemetry for Akka/Pekko monitoring.

**Rejected because:**
- Designed for production monitoring (exports metrics to Prometheus/Grafana), not debugging
- Provides telemetry data (actor message counts, stream rates), not heap analysis
- Different problem space: ongoing observability vs. memory leak investigation
- Wouldn't help diagnose Issue #138's "why isn't this object GC'd?" question

**When to reconsider:** If we need production Pekko-specific monitoring without Lightbend costs. Kamon complements (not replaces) MAT/async-profiler.

## Implementation Notes

### Setup Instructions

Add to `services/api/build.sbt`:
```scala
run / javaOptions ++= Seq(
  "-Xlog:gc*=debug:file=gc-%t.log:time,level,tags",
  "-XX:+HeapDumpOnOutOfMemoryError",
  "-XX:HeapDumpPath=./heap-dumps/"
)
```

Create heap dumps directory:
```bash
mkdir -p services/api/heap-dumps
```

Generate heap dump on-demand:
```bash
jcmd $(jps | grep PlayRun | awk '{print $1}') GC.heap_dump heap-dumps/snapshot.hprof
```

Analyse in Eclipse MAT:
```bash
brew install --cask mat
open -a "Memory Analyzer" heap-dumps/snapshot.hprof
```

Use async-profiler for CPU/allocation profiling:
```bash
ASYNC_PROFILER_VERSION=3.0
wget https://github.com/async-profiler/async-profiler/releases/download/v${ASYNC_PROFILER_VERSION}/async-profiler-${ASYNC_PROFILER_VERSION}-macos.tar.gz
tar xzf async-profiler-${ASYNC_PROFILER_VERSION}-macos.tar.gz
./async-profiler-${ASYNC_PROFILER_VERSION}-macos/bin/asprof -d 60 -e alloc -f flamegraph.html $(jps | grep PlayRun | awk '{print $1}')
```

### Documentation

Additional setup and usage documentation will be added to `services/api/CLAUDE.md` or separate profiling guide as needed.

## References

- Issue #138: Critical memory leak investigation
- [Eclipse MAT](https://eclipse.dev/mat/)
- [async-profiler](https://github.com/async-profiler/async-profiler)
- [Brendan Gregg's Flame Graphs](https://www.brendangregg.com/flamegraphs.html)
