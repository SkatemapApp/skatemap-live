# ADR 0005: Java Flight Recorder for Continuous Memory Monitoring

## Status

Proposed

## Context

Issue #171 exposed a critical gap in local profiling methodology. PR #169's dropHead fix appeared successful in local testing (2.8 MB/min memory growth) but Railway validation revealed it actually made the leak worse (15.0 MB/min, a 28% increase from the 11.7 MB/min baseline).

**The methodology failure:**

Local testing used heap dump snapshots at discrete moments (45 min load, 20 min idle). This approach:
- Only captured memory state at specific points (possibly after GC runs)
- Missed continuous growth trends between snapshots
- Showed "memory released during idle" (128 MB → 126 MB) which masked accumulation during load
- Led to false conclusion: "76% improvement ✅"

Railway's continuous memory graph immediately revealed linear growth (300 MB → 750 MB over 30 min).

**The requirement:**

We need continuous heap usage monitoring during local testing that:
- Tracks used heap every 30-60 seconds throughout entire test
- Generates memory-over-time graphs comparable to Railway metrics
- Detects linear growth patterns (e.g., 15 MB/min)
- Has low overhead (doesn't distort test results)
- Is scriptable/automatable for CI integration
- Runs on developer machines (macOS/Linux)

Without this capability, we cannot validate memory leak fixes locally and must rely on Railway for truth, which wastes time and CI resources deploying failed fixes.

## Decision

Use **Java Flight Recorder (JFR)** for continuous memory monitoring during local profiling tests.

### Workflow

1. **Start JFR recording** when beginning load test:
   ```bash
   jcmd <PID> JFR.start name=memory-test settings=profile duration=60m filename=recording.jfr
   ```

2. **Run load test** for specified duration (JFR records continuously)

3. **Stop recording** (if not using duration parameter):
   ```bash
   jcmd <PID> JFR.stop name=memory-test
   ```

4. **Extract heap metrics** from recording:
   ```bash
   jfr print --events jdk.GCHeapSummary recording.jfr > heap-metrics.txt
   ```

5. **Generate memory graph** from extracted data using script (e.g., Python/gnuplot)

6. **Compare graphs** before/after fixes to validate memory behaviour

### Integration with Existing Workflow

JFR complements (not replaces) Eclipse MAT:
- **JFR**: Continuous monitoring during test → memory trend graphs
- **MAT**: Heap dump analysis → identify what objects are leaking

Both are needed: JFR detects the leak, MAT diagnoses the cause.

## Consequences

### Benefits

**Built Into JDK**
- Available in Java 11+ (no installation required)
- Already on all developer machines
- No additional dependencies or infrastructure

**Very Low Overhead**
- Designed for production use (<1% CPU overhead)
- Won't distort memory behaviour being measured
- Can run for hours without performance impact

**Continuous Recording**
- Captures heap usage throughout entire test
- No manual snapshots needed
- Records at configurable intervals (default: every few seconds)

**Scriptable and Automatable**
- jcmd commands can be scripted
- jfr CLI tool for data extraction
- Suitable for CI integration

**Industry Standard**
- Used by Netflix, Oracle, and many enterprise Java shops
- Extensive documentation and tooling
- Team knowledge transfers to other JVM projects

**Rich Event Data**
- Captures GC events, heap summaries, allocation patterns
- Can correlate memory growth with GC behaviour
- Provides context beyond just heap numbers

### Trade-offs

**Requires Post-Processing**
- JFR produces binary `.jfr` files, not graphs
- Need script to extract heap metrics and plot
- Not as immediate as GUI tools like VisualVM

Mitigation: Create script once, reuse for all profiling tests

**Learning Curve for JFR Format**
- `.jfr` file format requires jfr CLI tool to extract
- Event types and field names need documentation
- Team must learn jfr print syntax

Mitigation: Document common queries, provide example scripts

**No Real-Time Visualization**
- Can't watch memory graph during test (only after)
- VisualVM provides real-time GUI but requires manual observation

Mitigation: Acceptable for validation workflow; graphs generated within minutes after test

**Heap Metrics Are Summary Events**
- GCHeapSummary events occur at GC time (not fixed intervals)
- Graph resolution depends on GC frequency
- May miss peaks between GC events

Mitigation: GC typically runs frequently enough for leak detection; acceptable resolution

## Alternatives Considered

### VisualVM (Real-Time GUI Monitoring)

**Evaluated:** Free GUI tool bundled with JDK that shows real-time heap graphs.

**Rejected because:**
- GUI-based, requires manual observation during entire test (30-60 min)
- Difficult to automate or integrate with CI
- Must export data manually to save graphs
- Not suitable for unattended profiling runs
- Less professional than scripted JFR approach

**When to reconsider:** If team prefers interactive debugging over automated testing, or for ad-hoc investigations where automation isn't needed.

### JMX + Custom Monitoring Script

**Evaluated:** Query JMX MBeans for heap usage, log periodically, plot with gnuplot/Python.

**Rejected because:**
- Reinventing what JFR already provides
- Requires writing custom monitoring code
- JMX polling has higher overhead than JFR's event-based approach
- Would need to handle connection management, error handling, etc.
- JFR is the modern standard; JMX is legacy approach

**When to reconsider:** If JFR proves insufficient or team wants complete control over metrics collected. JMX remains available as fallback.

### Continuous Profiling Platforms (Pyroscope, Datadog)

**Evaluated:** SaaS/self-hosted platforms for continuous profiling in production.

**Rejected because:**
- Designed for production monitoring, not local leak validation
- Requires infrastructure (server, agents, dashboards)
- Overkill for local development testing
- JFR provides same data without infrastructure overhead

**When to reconsider:** If we need production profiling infrastructure. For Issue #171's use case (validate fixes locally), JFR is sufficient.

## Implementation Notes

### Example JFR Commands

Start recording with 60-minute duration:
```bash
jcmd $(jps | grep PlayRun | awk '{print $1}') JFR.start name=memory-test settings=profile duration=60m filename=memory-test.jfr
```

Check recording status:
```bash
jcmd $(jps | grep PlayRun | awk '{print $1}') JFR.check
```

Stop recording early:
```bash
jcmd $(jps | grep PlayRun | awk '{print $1}') JFR.stop name=memory-test
```

Extract heap summary events:
```bash
jfr print --events jdk.GCHeapSummary memory-test.jfr
```

### Script Requirements

Create `tools/profiling/jfr-to-memory-graph.py` (or similar) that:
1. Parses `jfr print` output
2. Extracts timestamp and heap usage from GCHeapSummary events
3. Calculates memory growth rate (MB/min)
4. Generates graph (PNG/SVG) showing heap over time
5. Outputs growth rate and assessment (linear growth = leak)

### Integration with profile-memory-leak.sh

Update existing `tools/profiling/profile-memory-leak.sh` to:
1. Start JFR recording before load test
2. Stop JFR recording after idle period
3. Generate memory graph from JFR data
4. Compare graph to Railway metrics pattern
5. Take heap dumps at key points (for MAT analysis)

### Documentation Updates

- `services/api/CLAUDE.md`: Document JFR commands and workflow
- `docs/profiling/`: Add example JFR memory graphs showing leak patterns
- `tools/profiling/README.md`: Explain JFR vs MAT usage

## References

- Issue #171: Continuous memory monitoring requirement
- Issue #138: Memory leak parent tracking issue
- [docs/profiling/results/drophead-fix-results-20260118.md](/docs/profiling/results/drophead-fix-results-20260118.md#lessons-learned-why-local-testing-failed-to-detect-the-leak)
- [Java Flight Recorder Documentation](https://docs.oracle.com/javacomponents/jmc-5-4/jfr-runtime-guide/about.htm)
- [JDK Mission Control](https://www.oracle.com/java/technologies/jdk-mission-control.html)
