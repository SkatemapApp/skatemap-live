# Baseline Memory Profiling Results (Without Fix)

**Date:** 2025-11-10
**Branch:** `chore/profiling-tooling` (based on origin/master)
**Test Duration:** 30 minutes load + 20 minutes idle
**Load Parameters:** 10 skaters per event, 3s update interval

## Heap Dump Analysis (Eclipse MAT)

### Overview Statistics

| Time Point | Heap Size | Object Count | Classes |
|-----------|-----------|--------------|---------|
| 10 min    | 61.9 MB   | 1.3m         | 23.4k   |
| 20 min    | 62.8 MB   | 1.4m         | 23.2k   |
| 30 min    | 68.3 MB   | 1.5m         | 23.2k   |

**Total Growth:** 6.4 MB over 30 minutes

### MergeHub Memory Leak Confirmed

| Time Point | MergeHub$MergedSourceLogic Retained Size |
|-----------|------------------------------------------|
| 10 min    | 3.4 MB                                   |
| 20 min    | 7.1 MB                                   |
| 30 min    | 11.3 MB                                  |

**Growth Rate:** ~3.7-3.9 MB per 10 minutes (linear growth)

### Key Findings

1. **Linear Memory Leak Confirmed:** MergeHub objects are growing linearly at approximately 3.7-3.9 MB per 10 minutes
2. **Leak Source:** `org.apache.pekko.stream.scaladsl.MergeHub$MergedSourceLogic$$anon$1` objects retaining increasingly large amounts of memory
3. **Root Cause:** BroadcastHub streams are not being terminated when publishers disconnect, causing accumulated state in MergeHub
4. **Projected Impact:** At this growth rate, the application would accumulate ~22-24 MB per hour, leading to OOM after several hours of operation

### Heap Dump Files

Located in `services/api/heap-dumps/`:
- `at-10min-20251110-213010.hprof` (127 MB file)
- `at-20min-20251110-213010.hprof` (126 MB file)
- `after-30min-load-20251110-213010.hprof` (132 MB file)
- `after-20min-idle-20251110-213010.hprof` (144 MB file)

## Comparison: BroadcastHub KillSwitch Fix Results (2025-12-27)

**Branch:** `fix/broadcast-hub-killswitch`
**Test Duration:** 30 minutes load + 20 minutes idle (same parameters as baseline)

### Heap Dump Analysis Results

| Time Point | Heap Size | Object Count | MergeHub Retained |
|-----------|-----------|--------------|-------------------|
| 10 min    | 61.9 MB   | 1.3m         | 3.5 MB            |
| 20 min    | 62.9 MB   | 1.4m         | 7.1 MB            |
| 30 min    | 66.4 MB   | 1.6m         | 10.8 MB           |

**Growth Rate:** ~3.5-3.7 MB per 10 minutes (linear growth continues)

### Comparison Summary

| Metric | Baseline (No Fix) | With KillSwitch Fix | Change |
|--------|------------------|---------------------|--------|
| 10 min MergeHub retained | 3.4 MB | 3.5 MB | +0.1 MB |
| 20 min MergeHub retained | 7.1 MB | 7.1 MB | 0 MB |
| 30 min MergeHub retained | 11.3 MB | 10.8 MB | -0.5 MB |
| Growth rate (MB/10min) | 3.7-3.9 | 3.5-3.7 | No significant change |

**Conclusion:** The BroadcastHub KillSwitch fix does NOT resolve the memory leak. Growth pattern remains nearly identical.

## Root Cause Analysis

### Actual Root Cause: Per-Publish Stream Materialisation

Eclipse MAT analysis revealed **5,873 instances** of MergeHub-related objects in memory:
- `MergeHub$anon$2$anon$3`: 5,873 instances
- `GraphInterpreterShell`: 5,873 instances
- `ActorCell`: 5,873 instances

**Source:** `services/api/src/main/scala/skatemap/core/InMemoryBroadcaster.scala:45`

```scala
def publish(eventId: String, location: Location): Unit = {
  val hubData = getOrCreateHub(eventId)
  Source.single(location).runWith(hubData.sink)  // Line 45 - Creates new materialised stream every call
}
```

Every `publish()` call materialises a new `Source.single(location)` stream. With 10 skaters sending updates every 3 seconds for 30 minutes:
- 10 skaters × (30 min × 60 sec / 3 sec) = **~6,000 materialised streams**

These materialised streams accumulate in the MergeHub and are never cleaned up, causing the linear memory growth.

### Why BroadcastHub KillSwitch Didn't Fix It

The BroadcastHub KillSwitch fix (issue #142) addresses **subscriber-side cleanup** (BroadcastHub streams). However, the leak is on the **publisher-side** (MergeHub streams created during `publish()`).

The fix is correct defensive programming and should be kept, but it doesn't address the production blocker.

## Next Steps

1. **Issue #142**: Merge BroadcastHub KillSwitch fix as defensive programming (prevents subscriber-side leaks)
2. **Issue #143**: Fix per-publish stream materialisation in `InMemoryBroadcaster.scala:45` (actual production blocker fix)
3. **Issue #138**: Remains open until #143 is implemented and verified
