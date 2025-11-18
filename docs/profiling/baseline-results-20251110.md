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

### Next Steps

Run the same profiling test on the `fix/broadcast-hub-killswitch` branch to verify that implementing KillSwitch for BroadcastHub streams prevents this linear growth.
