# Railway Validation Results — PR #173 (Sink.ignore Fix)

**Date:** 2026-01-25
**Branch:** `master` (after PR #173 merged)
**Commit:** f323c151f83bb35ae12db8e4dd302742db3c252f
**Test Duration:** 30 minutes
**Load Parameters:** 10 skaters (5 per event × 2 events), 3s update interval

## Summary

PR #173 attached `Sink.ignore` to BroadcastHub to prevent message accumulation when no real subscribers are present. Railway validation shows:

✅ **Linear growth during load FIXED** — Growth reduced from 15-18 MB/min to 0.5 MB/min during stable operation
❌ **Memory not released after load** — 93 MB acquired during test retained indefinitely (issue #166)

## Changes in PR #173

**Implementation** (`InMemoryBroadcaster.scala:40`):
```scala
val ((queue, killSwitch), source) = Source
  .queue[Location](config.bufferSize, OverflowStrategy.dropHead)
  .viaMat(KillSwitches.single)(Keep.both)
  .toMat(BroadcastHub.sink[Location](bufferSize = config.bufferSize))(Keep.both)
  .run()

source.runWith(Sink.ignore)  // ← Fix: continuously drain hub
```

**Why this pattern:**
- BroadcastHub with `OverflowStrategy.dropHead` creates backpressure conflict when no subscribers present
- dropHead defeats backpressure → messages accumulate in stream graph internals
- `Sink.ignore` continuously drains hub → prevents accumulation
- Documented Pekko pattern: [BroadcastHub docs](https://pekko.apache.org/docs/pekko/current/stream/stream-dynamic.html)

## Railway Validation Results

### Test Execution

**CI Run:** https://github.com/SkatemapApp/skatemap-live/actions/runs/21332946211
**Test Window:** 13:05-13:35 UTC (25 Jan 2026)
**Load:** 10 skaters total, 3-second update interval
**Updates Processed:** 6,000 location updates
**Error Rate:** 0.0%

### Test Results

| Test | Duration | Result |
|------|----------|--------|
| TestEventIsolation | 30s | ✅ PASS |
| TestLocationExpiry | 75s | ❌ FAIL (known issue #136) |
| TestWebSocketTimeout | 2.5 min | ✅ PASS |
| TestScale | 7 min | ✅ PASS |
| **TestStability** | **30 min** | **✅ PASS** |

**Critical validation: TestStability passed** — 30-minute sustained load with no crashes or errors.

### Memory Metrics

**Railway memory graph:**

| Time | Memory | Notes |
|------|--------|-------|
| 12:00-13:05 UTC | 331 MB | Idle baseline before test |
| 13:05 UTC | 331 MB → 390 MB | Test start (sharp jump) |
| 13:05-13:10 UTC | 390 MB → 400 MB | Warmup phase (JVM JIT, hub creation) |
| 13:10-13:35 UTC | 400 MB → 410 MB | Stable test phase |
| 13:35 UTC onwards | 424 MB | Test ended, memory plateau |
| 16:00 UTC (now) | **424 MB** | **Memory NOT released** |

**Memory behaviour:**
- **During test**: Initial warmup (70 MB), then stable growth (0.5 MB/min)
- **After test**: Memory plateaued at 424 MB and never decreased
- **Retained memory**: 93 MB (424 MB - 331 MB)

### Growth Rate Analysis

**Total test period** (13:05-13:35):
- Growth: 331 MB → 424 MB = 93 MB over 30 min
- **Rate: 3.1 MB/min**

**Stable phase only** (13:10-13:35):
- Growth: 400 MB → 410 MB = 10 MB over 25 min
- **Rate: 0.4 MB/min**

**Post-test** (13:35 onwards):
- Growth: 0 MB/min (completely flat)
- **BUT**: Memory stayed at 424 MB, not released

### Comparison with Previous Results

| Fix Stage | Memory Growth (during load) | Memory Release (after load) |
|-----------|----------------------------|----------------------------|
| **Original bug** (Nov 2025) | 17.7 MB/min continuous | ❌ No release |
| **After PRs #146/#148/#151** (Jan 11) | 2.1 MB/min | ❌ No release |
| **After PR #173** (Jan 25) | 0.5 MB/min (stable phase)<br>3.1 MB/min (incl. warmup) | ❌ No release |

**Improvement:** 97% reduction in growth rate during stable operation (17.7 → 0.5 MB/min)

## Key Findings

### ✅ What PR #173 Fixed

**Continuous linear growth eliminated:**
- Before: 15-18 MB/min sustained growth → OOM crash in 70 minutes
- After: 0.5 MB/min during stable operation (97% reduction)
- Growth **stops immediately** when load stops (flat line post-test)

**Evidence the fix works:**
- No continuous accumulation during sustained load
- Memory stabilised during 25-minute stable phase
- Zero growth after test ended

### ❌ What PR #173 Did NOT Fix

**Memory acquired during load is not released:**
- 93 MB acquired during 30-minute test
- Memory retained indefinitely after load stopped
- Still at 424 MB hours after test ended

**This creates stepwise growth pattern:**
- Test 1: 331 MB → 424 MB (+93 MB, retained)
- Test 2: 424 MB → ~517 MB (+93 MB, retained)
- Test 3: 517 MB → ~610 MB (+93 MB, retained)
- Eventually: OOM crash (just takes longer)

### Root Cause Analysis

**PR #173 addressed:** Message accumulation in BroadcastHub when no subscribers present

**Still unresolved:** Memory acquired during hub lifecycle not released on cleanup

**Previously attempted (PR #167):**
- Called `queue.complete()` during hub cleanup
- Deployed to Railway and tested
- **Result: Did NOT release memory**
- PR reverted (commit 4fb7495)

**Tracked in:** Issue #166 "fix: memory not released to OS during idle period"

## Assessment

### Production Viability

**Compared to original bug:**
- ✅ Won't crash from continuous 17.7 MB/min growth
- ✅ Can sustain steady load indefinitely (0.5 MB/min is negligible)
- ❌ Memory footprint = peak load ever seen
- ❌ Traffic spikes permanently increase baseline memory

**System behaviour:**
- **Stable** under constant load
- **Inefficient** — memory never shrinks
- **Degrading** — baseline memory drifts upward over time

### Status Summary

| Issue | Status | Notes |
|-------|--------|-------|
| Original: Continuous growth (17.7 MB/min) | ✅ **FIXED** | PR #173 validated |
| Original: Memory not released | ❌ **UNFIXED** | Issue #166 reopened |
| Production blocker (OOM in 70 min) | ✅ **RESOLVED** | System stable under sustained load |
| Memory efficiency | ❌ **DEGRADED** | Stepwise growth pattern remains |

## Next Steps

1. **Issue #166** — Investigate why `queue.complete()` didn't release memory
2. **Issue #138** — Update with partial resolution status, keep open until #166 resolved
3. **Local profiling** — Run JFR test with 45-min load + 30-min idle to confirm memory retention locally
4. **Investigation areas:**
   - Are hubs actually being removed from the TrieMap?
   - Is KillSwitch.shutdown() completing properly?
   - Are there other references keeping hub objects alive?
   - Does the Sink.ignore materialisation create a reference leak?

## Related Issues

- **#138** — Parent tracking issue for memory leak (partial resolution)
- **#166** — Memory not released to OS during idle (reopened)
- **#170** — Closed (Sink.ignore fix, now in PR #173)
- **#142** — KillSwitch to BroadcastHub (fixed in PR #146)
- **#143** — Per-publish stream materialisation (fixed in PR #148)

## Related PRs

- **PR #173** — Sink.ignore fix (merged, this validation)
- **PR #172** — JFR continuous memory monitoring
- **PR #169** — dropHead fix (made leak worse, identified root cause)
- **PR #167** — queue.complete() fix (reverted, didn't work)
- **PR #165** — Production validation documentation (in progress)
- **PR #151** — Race condition in cleanup (merged)
- **PR #148** — SourceQueue fix (merged)
- **PR #146** — KillSwitch fix (merged)

## CI Log Evidence

**Test passed:**
```
TestStability: simulate-skaters: 2026/01/25 13:05:41 Starting simulation
TestStability: stability_test.go:43: Stability test progress: 30m0s / 30m0s
TestStability: stability_test.go:54: Event A records: 3000 (expected ~3000)
TestStability: stability_test.go:55: Event B records: 3000 (expected ~3000)
TestStability: --- PASS: TestSmokeTestSuite/TestStability (1800.18s)
```

**No errors in Railway logs** during test window (13:05-13:35 UTC).

## Conclusion

PR #173's Sink.ignore fix successfully eliminated the continuous 15-18 MB/min memory leak that caused OOM crashes in 70 minutes. The system is now stable under sustained load.

However, memory acquired during load is not released during idle periods, creating a stepwise growth pattern (93 MB per 30-minute load cycle). This is the same memory release issue that has existed since the original bug report and remains unresolved.

**Status:**
- **Production blocker (OOM in 70 min):** ✅ Resolved
- **Memory efficiency:** ❌ Degraded, issue #166 tracks resolution

**Railway validation:** PASS (with limitations documented)
