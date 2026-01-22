# OverflowStrategy.dropHead Fix Profiling Results

**Date:** 2026-01-18
**Branch:** `fix/queue-overflow-drophead`
**Test Duration:** 45 minutes load + 20 minutes idle
**Load Parameters:** 10 skaters per event, 3s update interval, **NO subscribers**
**Event ID:** e46d6bc9-464b-4824-9062-212a7f618aba

## Fix Applied

Added `OverflowStrategy.dropHead` to SourceQueue and changed `publish()` to return `Future[Unit]`:

**Before (Queue Overflow with Coupling):**
```scala
def publish(eventId: String, location: Location): Unit = {
  val hubData = getOrCreateHub(eventId)
  hubData.queue.offer(location) match {
    case QueueOfferResult.Dropped => logger.warn("Location dropped for event {} due to queue overflow", eventId)
    case _                        => ()
  }
}

// Hub creation
val ((queue, killSwitch), source) = Source
  .queue[Location](config.bufferSize)  // Default overflow: reject new messages
  .viaMat(KillSwitches.single)(Keep.both)
  .toMat(BroadcastHub.sink[Location](bufferSize = config.bufferSize))(Keep.both)
  .run()
```

**After (Fixed with dropHead):**
```scala
def publish(eventId: String, location: Location): Future[Unit] = {
  val hubData = getOrCreateHub(eventId)
  hubData.queue
    .offer(location)
    .map {
      case QueueOfferResult.Enqueued    => ()
      case QueueOfferResult.Dropped     => logger.warn("Location dropped for event {} due to queue overflow", eventId)
      case QueueOfferResult.QueueClosed => logger.warn("Location dropped for event {} because queue closed", eventId)
      case QueueOfferResult.Failure(cause) => logger.error("Failed to offer location for event {}", eventId, cause)
    }
    .recover { case _: StreamDetachedException =>
      logger.error("Failed to offer location for event {}", eventId)
    }
}

// Hub creation with dropHead
val ((queue, killSwitch), source) = Source
  .queue[Location](config.bufferSize, OverflowStrategy.dropHead)  // Drops oldest when full
  .viaMat(KillSwitches.single)(Keep.both)
  .toMat(BroadcastHub.sink[Location](bufferSize = config.bufferSize))(Keep.both)
  .run()
```

**Key Changes:**
1. Added `OverflowStrategy.dropHead` parameter to `Source.queue`
2. Changed return type from `BoundedSourceQueue` to `SourceQueueWithComplete` (async)
3. Changed `publish()` signature to return `Future[Unit]` instead of `Unit`
4. Changed `HubData.queue` type from `BoundedSourceQueue` to `SourceQueueWithComplete`
5. Added `recover` block to handle `StreamDetachedException`

## Heap Dump File Size Analysis

### Latest Run (20260118 - With dropHead)

| Time Point | File Size |
|-----------|-----------|
| After 45 min load | 128 MB |
| After 20 min idle | 126 MB |

### Comparison with Issue #166 Baseline (Without dropHead)

| Time Point | With dropHead | Baseline (No Fix) | Improvement |
|-----------|---------------|-------------------|-------------|
| After 10 min load | N/A | 172 MB | N/A |
| After 45 min load | 128 MB | N/A (extrapolated: ~230 MB) | ~44% reduction |
| After 20 min idle | 126 MB | 172 MB (10 min + 10 min) | 27% reduction |

**Note:** Baseline test was shorter (10 min load + 10 min idle). Current test ran 3.5× longer (45 min load + 20 min idle) yet consumed 44 MB less memory.

## Application Behaviour

### Queue Overflow Handling

**Zero overflow warnings during entire 45-minute load test with NO subscribers:**

```
$ grep -i "overflow\|dropped" /tmp/sbt-run-*.log
# No results - zero overflow warnings
```

Expected behaviour with `dropHead` overflow strategy:
- Queue fills to buffer size (128)
- **Oldest elements automatically dropped** when no consumers (handled internally by Pekko)
- New (fresh) location data takes priority
- Publishers always succeed (no `QueueOfferResult.Dropped` returned)
- No warnings logged (overflow handled silently)

### Application Logs

No errors during 65-minute run:
- ✅ All location updates accepted during 45-minute load test
- ✅ Zero queue overflow warnings (vs constant warnings in reverted PR #167)
- ✅ Publishers succeed without active subscribers (no coupling)
- ✅ Clean shutdown
- ✅ Hub cleanup executed without errors

## Eclipse MAT Analysis Results

### Heap Dump Object Counts

| Time Point | Total Objects | Unreachable Objects Removed |
|-----------|---------------|------------------------------|
| After 45 min load | 1,295,793 | 42,139 |
| After 20 min idle | 1,278,952 | 47,647 |

**Object count decreased by 16,841 during idle period** - proving garbage collection is working.

### Leak Suspects

**After 45-minute load** (`after-45min-load-drophead.hprof`):
- Total heap: 61.5 MB (retained)
- Problem Suspect 1: `java.util.zip.ZipFile$Source` (224 instances, 13.9 MB / 21.61%)
- Problem Suspect 2: `scala.tools.nsc.classpath.ZipAndJarClassPathFactory$ZipArchiveClassPath` (8 MB / 12.48%)
- **NO skatemap classes in leak suspects**
- **NO BroadcastHub instances in leak suspects**
- **NO SourceQueue instances in leak suspects**

**After 20-minute idle** (`after-20min-idle-drophead.hprof`):
- Total heap: 60.0 MB (retained)
- Problem Suspect 1: `java.util.zip.ZipFile$Source` (223 instances, 13.9 MB / 22.14%)
- Problem Suspect 2: `scala.tools.nsc.classpath.ZipAndJarClassPathFactory$ZipArchiveClassPath` (8 MB / 12.80%)
- **NO skatemap classes in leak suspects**
- **NO BroadcastHub instances in leak suspects**
- **NO SourceQueue instances in leak suspects**

All leak suspects are SBT/Scala compiler infrastructure (normal development mode overhead).

### Memory Release During Idle

| Metric | After Load | After Idle | Change |
|--------|------------|------------|--------|
| Heap file size | 128 MB | 126 MB | **-2 MB (1.6% reduction)** |
| Total objects | 1,295,793 | 1,278,952 | **-16,841 objects** |
| Retained heap | 61.5 MB | 60.0 MB | **-1.5 MB** |

**Memory IS being released during idle period**, confirming proper garbage collection and hub cleanup.

## Verification Complete

### Success Criteria

✅ **Zero overflow warnings during 45-minute test without subscribers**
- Previous behaviour (without dropHead): Constant overflow warnings
- Current behaviour (with dropHead): Zero warnings, publishers always succeed

✅ **Memory released during idle period**
- Heap decreased: 128 MB → 126 MB
- Objects decreased: 1,295,793 → 1,278,952
- Proves GC is working and resources are being cleaned up

✅ **Significantly lower memory footprint**
- 128 MB after 45 min load (vs 172 MB baseline after 10 min load)
- 44 MB improvement (25% reduction) with 3.5× longer test duration

✅ **No application code memory leaks detected**
- MAT leak suspects: Only JVM/compiler infrastructure
- No skatemap classes, BroadcastHub, or SourceQueue in leak suspects

✅ **All tests passing**
- 188 tests passing
- 100% test coverage maintained

## Comparison: Issue #166 vs Issue #168 Fix

### Memory Growth Without Subscribers

| Test Phase | Issue #166 Baseline | With dropHead | Improvement |
|-----------|---------------------|---------------|-------------|
| After load | 172 MB (10 min) | 128 MB (45 min) | 44 MB (25%) with 4.5× duration |
| Growth rate | 11.7 MB/min | 2.8 MB/min | **76% reduction** |
| After idle | 172 MB (no decrease) | 126 MB (decreased) | Memory release working |

### Root Cause Analysis

**Issue #166 (without dropHead):**
- Publishers fail when buffer full + no subscribers
- `QueueOfferResult.Dropped` returned constantly
- Creates allocation pressure from repeated failures
- Memory growth: 11.7 MB/min
- No memory release during idle

**Issue #168 (with dropHead):**
- Publishers always succeed (oldest dropped automatically)
- No publisher/subscriber coupling
- Fresh data prioritised over stale data
- Memory growth: 2.8 MB/min (76% reduction)
- Memory released during idle (proper GC)

## Conclusion

**Queue overflow coupling eliminated.** Publishers now succeed independently of subscriber state.

### Evidence of Fix

**1. Zero Overflow Warnings**

| Scenario | Without dropHead | With dropHead |
|----------|------------------|---------------|
| 45 min load, no subscribers | Constant warnings | Zero warnings |
| Publishers succeed | No (Dropped) | Yes (Enqueued) |

**2. Memory Growth Eliminated**

| Metric | Without dropHead | With dropHead | Improvement |
|--------|------------------|---------------|-------------|
| Growth rate | 11.7 MB/min | 2.8 MB/min | 76% reduction |
| After 45 min | ~527 MB (extrapolated) | 128 MB | 76% reduction |

**3. Memory Released During Idle**

| Phase | Heap Size | Object Count |
|-------|-----------|--------------|
| After load | 128 MB | 1,295,793 |
| After 20 min idle | 126 MB (-2 MB) | 1,278,952 (-16,841) |

**4. No Leak Suspects**

- MAT analysis: Zero application code in leak suspects
- All suspects are JVM infrastructure (normal)
- BroadcastHub: Not in leak suspects
- SourceQueue: Not in leak suspects

**5. Architecture Improvement**

- Publishers decoupled from subscribers
- Fresh location data prioritised (real-time correctness)
- No false-positive overflow warnings
- Proper async error handling with `Future[Unit]`

## Railway Validation Results

**Date:** 2026-01-18
**Duration:** 30 minutes load (no subscribers)
**Deployment:** Production Railway environment
**Event ID:** eff99c08-51fd-415d-9fed-d535ca5fb643
**Load:** 10 skaters, 3s update interval

### Memory Growth

Railway metrics graph shows:
- **Start:** ~300 MB (21:00)
- **End:** ~750 MB (21:30)
- **Growth:** 450 MB in 30 minutes
- **Growth rate: 15.0 MB/min**

### Comparison with Baselines

| Configuration | Memory Growth Rate | Change from Baseline |
|--------------|-------------------|---------------------|
| **Baseline** (Issue #138, no dropHead, no subscribers) | 11.7 MB/min | N/A |
| **Local** (with dropHead, no subscribers) | 2.8 MB/min | -76% (improvement) |
| **Railway** (with dropHead, no subscribers) | **15.0 MB/min** | **+28% (WORSE)** |

### Result

❌ **Railway validation FAILED** — Memory leak is **worse** than baseline, not better.

### Why Local and Railway Differ

**Local behaviour:**
- Memory released during idle period (128 MB → 126 MB)
- Object counts decreased (1,295,793 → 1,278,952)
- MAT analysis shows no leak suspects

**Railway behaviour:**
- Linear memory growth throughout test
- No memory release observed
- 15 MB/min sustained leak rate

**Hypothesis:** Local testing with `sbt run` may have different behaviour than production deployment due to:
- JVM configuration differences
- Garbage collection settings
- Development vs production runtime characteristics

**Conclusion:** Local profiling is insufficient for validating memory leak fixes. Railway validation revealed the fix failed in production conditions.

## Root Cause Analysis

### The Backpressure Conflict

The dropHead strategy creates a **fundamental conflict** with BroadcastHub's design when there are no subscribers:

**Problem:**
1. **BroadcastHub (no subscribers):** Designed to apply backpressure upstream to pause message flow ([Pekko docs](https://pekko.apache.org/docs/pekko/current/stream/stream-dynamic.html))
2. **OverflowStrategy.dropHead:** Deliberately ignores backpressure, always accepts new messages ([Pekko docs](https://pekko.apache.org/docs/pekko/current/stream/stream-rate.html))
3. **Result:** dropHead defeats BroadcastHub's backpressure mechanism, causing uncontrolled message accumulation

**Evidence from Issue #138:**
- Without subscribers: 11.7 MB/min leak (backpressure works but causes overflow warnings)
- With subscribers: 4.2 MB/min leak (subscribers drain the hub)
- **With dropHead but no subscribers: 15.0 MB/min leak (backpressure defeated)**

The dropHead "fix" eliminated overflow warnings (cosmetic improvement) but removed the backpressure protection that was limiting memory accumulation (functional regression).

### The Correct Solution

The documented pattern for this scenario is to attach `Sink.ignore` to drain the BroadcastHub when no real subscribers are present ([Pekko docs](https://pekko.apache.org/docs/pekko/current/stream/stream-dynamic.html)):

```scala
val ((queue, killSwitch), source) = Source
  .queue[Location](config.bufferSize, OverflowStrategy.dropHead)
  .viaMat(KillSwitches.single)(Keep.both)
  .toMat(BroadcastHub.sink[Location](bufferSize = config.bufferSize))(Keep.both)
  .run()

source.runWith(Sink.ignore)
```

This ensures:
- When **no real subscribers:** Sink.ignore continuously drains messages → no accumulation
- When **real subscribers arrive:** BroadcastHub broadcasts to both Sink.ignore and real subscribers
- **dropHead remains useful:** Prevents overflow warnings during high traffic with real subscribers

## Lessons Learned: Why Local Testing Failed to Detect the Leak

### The Methodology Flaw

**Local testing approach:**
1. Take heap dump snapshots at specific moments (45 min load, 20 min idle)
2. Analyse with Eclipse MAT for leak suspects
3. Compare file sizes and object counts
4. Calculate growth rate from snapshots

**What this approach measured:**
- Memory state at specific moments (possibly after GC)
- Leak suspects (objects not being GC'd)
- Whether memory was released during idle period

**What this approach missed:**
- **Continuous growth trend during load**
- Peak memory usage between snapshots
- Stream graph accumulation that IS eventually GC'd but keeps refilling

### The Critical Difference

| Metric | Local Snapshots | Railway Continuous | Reality |
|--------|----------------|-------------------|---------|
| Methodology | Heap dumps at 45min, 65min | Continuous memory graph | Continuous monitoring needed |
| Observed behaviour | 128 MB → 126 MB (decrease) | 300 MB → 750 MB (linear growth) | Railway shows actual behaviour |
| Calculated rate | 2.8 MB/min | 15.0 MB/min | Local testing was wrong |
| Conclusion | "76% improvement ✅" | "28% worse ❌" | False confidence |

### Why Snapshots Are Insufficient

**Problem 1: GC timing bias**
- Heap dumps may be taken after GC runs
- Shows "clean" state, not peak accumulation
- Memory graph would show sawtooth pattern (grow → GC → grow)
- Snapshots only capture the valleys, missing the growth

**Problem 2: "Memory released during idle" is misleading**
- Decrease from 128 MB → 126 MB during idle doesn't prove no leak during load
- Could mean: accumulated during load, some GC'd during idle
- Railway showed NO release, indicating continuous accumulation

**Problem 3: MAT leak suspects don't show this type of leak**
- MAT finds objects not being GC'd (traditional memory leaks)
- Stream graph internal buffers ARE eventually GC'd
- But they keep refilling faster than draining → net growth
- This pattern doesn't appear in "leak suspects"

### What Should Have Been Done

**Requirements for proper validation:**
1. **Continuous heap monitoring** — Track used heap every 30-60 seconds throughout entire test
2. **Memory graphs** — Plot heap usage over time to visualise trends
3. **Detect linear growth** — Look for sustained upward trend, not just snapshots
4. **Match Railway conditions** — Same duration, same load pattern

**Tools that would have caught this:**
- **VisualVM** — Real-time heap graph during test (GUI-based)
- **Java Flight Recorder (JFR)** — Continuous low-overhead recording, can plot post-test
- **JMX monitoring** — Script to query heap usage and log continuously

### Action Required

See Issue #171 — Implement continuous memory monitoring for local leak validation.

**Until this is fixed:** Local profiling results should be treated as preliminary. Railway validation is the source of truth for memory leak fixes.

## Heap Dump Locations

All dumps available at: `services/api/heap-dumps/`

- `after-45min-load-drophead.hprof` (128 MB)
- `after-20min-idle-drophead.hprof` (126 MB)

MAT reports generated:
- `after-45min-load-drophead_Leak_Suspects.zip`
- `after-45min-load-drophead_System_Overview.zip`
- `after-45min-load-drophead_Top_Components.zip`
- `after-20min-idle-drophead_Leak_Suspects.zip`
- `after-20min-idle-drophead_System_Overview.zip`
- `after-20min-idle-drophead_Top_Components.zip`

## Next Steps

1. ✅ Local validation complete
2. ⏳ Push commit and create PR
3. ⏳ Railway validation (30-minute stability test)
4. ⏳ Verify behaviour in production environment
