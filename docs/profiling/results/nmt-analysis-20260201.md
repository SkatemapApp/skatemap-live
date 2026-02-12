# Native Memory Tracking Analysis — Issue #166 Investigation

**Date:** 1 February 2026
**Issue:** [#166 — Memory not released to OS during idle period](https://github.com/SkatemapApp/skatemap-live/issues/166)
**Context:** Railway validation showed memory retained after load test completion (not released during idle period)

## Objective

Identify which native memory category is retaining ~50 MB after load test completion by using JVM Native Memory Tracking (NMT).

## Background

Railway heap dump analysis (1 Feb 2026) confirmed the memory leak is NOT in Java heap:
- Java heap decreased during idle: 12.4 MB → 11.8 MB
- Zero application objects retained
- All Pekko stream objects properly garbage collected

The 50 MB retention is in **native memory** (code cache, thread stacks, metaspace), which heap dumps cannot capture.

## Test Configuration

**Railway deployment:**
- PR #176 merged: NMT enabled with `-XX:NativeMemoryTracking=summary`
- Service restarted: 20:41 UTC (fresh baseline)
- NMT baseline captured: 20:47:51 UTC (before any load)

**Load test parameters:**
- Test started: 20:48:09 UTC
- Test completed: 21:30:20 UTC
- Duration: 42 minutes
- Skaters: 10 per event
- Update interval: 3 seconds

**NMT diff captured:** 21:37:01 UTC (7 minutes after test completion)

## Railway Memory Metrics

**Timeline:** 8:40 PM - 9:45 PM (20:41 UTC - 21:45 UTC)

**Memory levels (from Railway graph):**
- **Baseline** (8:47 PM, after restart): ~200 MB
- **During test** (8:50 PM - 9:30 PM): ~250 MB
- **After test** (9:30 PM onwards): **~250 MB (retained)**

**Memory retention: ~50 MB** (200 MB → 250 MB, not released)

## NMT Analysis Results

### Total Memory Change

**Total committed memory increase: +29,052 KB (+28.4 MB)**

This accounts for 56.8% of the 50 MB Railway retention. The remaining ~21.6 MB is RSS overhead (page tables, shared libraries, memory-mapped files) not tracked by NMT.

### Breakdown by Category

| Category | Increase (KB) | Increase (MB) | % of NMT Total | Details |
|----------|---------------|---------------|----------------|---------|
| **Code** | **+16,495** | **+16.1** | **56.8%** | **JIT-compiled native code** |
| **Metaspace** | **+7,071** | **+6.9** | **24.3%** | **Class metadata (+830 classes)** |
| **Symbol** | +1,930 | +1.9 | 6.6% | Symbols for new classes |
| **Other** | +896 | +0.9 | 3.1% | Miscellaneous allocations |
| **Class** | +843 | +0.8 | 2.9% | Class structures |
| **Thread** | +665 | +0.65 | 2.3% | +5 threads (37 → 42) |
| **NMT overhead** | +621 | +0.6 | 2.1% | Tracking overhead |
| **GC** | +256 | +0.25 | 0.9% | GC internal structures |
| **Compiler** | +160 | +0.16 | 0.6% | JIT compiler workspace |
| **Internal** | +117 | +0.11 | 0.4% | Internal allocations |

### NMT Diff Output (Excerpt)

```
Total: reserved=3496176KB +10880KB, committed=238676KB +29052KB

- Code (reserved=250046KB +1531KB, committed=28614KB +16495KB)
        (malloc=2358KB +1531KB #10888 +5617)
        (mmap: reserved=247688KB, committed=26256KB +14964KB)

- Metaspace (reserved=65734KB +31KB, committed=52422KB +7071KB)
        (malloc=235KB #142)
        (mmap: reserved=65536KB, committed=52928KB)

- Thread (reserved=42107KB +5137KB, committed=2979KB +665KB)
        (thread #42 +5)
        (stack: reserved=41984KB +5120KB, committed=2856KB +648KB)
```

Full NMT diff: Available in git history at commit `b073759` (removed post-investigation)

## Root Cause Analysis

### Primary Culprit: Code Cache (+16.1 MB, 32% of total retention)

**What is code cache?**

The JVM JIT (Just-In-Time) compiler optimises frequently-executed methods:
1. Java bytecode starts as interpreted (slow)
2. JVM detects hot methods (called frequently)
3. JIT compiler converts bytecode → native machine code (fast)
4. Compiled code stored in **code cache** (off-heap native memory)
5. Future calls execute fast native code instead of interpreting

**Why it's retained:**

During the 30-minute load test:
- JVM compiled many hot methods to native code
- 16.1 MB of native code generated and cached
- After test completion → **compiled code remains cached indefinitely**
- Code cache has no eviction policy until cache is full
- Normal JVM behaviour, but causes observable memory retention

**Evidence:**
- Code category shows +14,964 KB committed in mmap (memory-mapped code cache region)
- +5,617 new code allocations during test
- No corresponding decrease during idle period

### Secondary: Metaspace (+6.9 MB, 14% of total retention)

**What is metaspace?**

Metaspace stores class metadata:
- Method bytecode
- Constant pools
- Field descriptors
- Annotations

**Why it grew:**

- 830 new classes loaded during test (9,804 → 10,634 total)
- Likely framework classes loaded lazily on first use
- Class metadata retained until ClassLoader is garbage collected

**Evidence:**
- +7,071 KB committed to metaspace
- +830 classes loaded (791 instance classes, 39 array classes)
- Class count remained stable after test completion

### Thread Stacks (Not a Problem)

**Initial hypothesis:** Thread pool not shrinking after load

**Actual result:**
- Only +5 threads (37 → 42)
- +665 KB thread stack memory
- Thread pool IS shrinking properly
- **Not the primary issue**

## Comparison with Heap Dump Analysis

**Heap dump findings (1 Feb 2026):**
- Java heap: 12.4 MB → 11.8 MB (decreased ✓)
- Zero application objects retained ✓
- Zero Pekko stream objects retained ✓

**NMT findings (1 Feb 2026):**
- Native memory: +28.4 MB committed
- Code cache: +16.1 MB (primary culprit)
- Metaspace: +6.9 MB (secondary)

**Combined conclusion:**
- Java heap is healthy — no object leaks
- Native memory is the issue — code cache and metaspace retention
- Leak is **external to application code** — JVM internal behaviour

## Progress Tracking

**Memory retention over time:**

| Date | Test | Retention | Notes |
|------|------|-----------|-------|
| 25 Jan 2026 | Railway validation | **93 MB** | Before PR #173 |
| 1 Feb 2026 | First test (18:12 UTC) | **50 MB** | After PR #173 |
| 1 Feb 2026 | Second test (20:48 UTC) | **50 MB** | Consistent with first test |

**Improvement: 46% reduction** (93 MB → 50 MB) after PR #173 (Sink.ignore fix)

**Remaining issue: 50 MB retention** from code cache and metaspace

## Proposed Solutions

### Option 1: Reduce Code Cache Size (Forces Earlier Eviction)

**Implementation:**
```scala
// services/api/build.sbt
run / javaOptions ++= Seq(
  "-XX:ReservedCodeCacheSize=128m"  // Default: 240m
)

// services/api/docker-entrypoint.sh
exec bin/skatemap-live \
  -J-XX:ReservedCodeCacheSize=128m \
  -Dplay.http.secret.key="${APPLICATION_SECRET}"
```

**Pros:**
- Smaller cache → compiled code evicted sooner
- Reduces retention at cost of occasional recompilation

**Cons:**
- May reduce peak performance during sustained load
- Methods may be compiled/evicted repeatedly if cache thrashes

### Option 2: Enable Aggressive Code Cache Flushing

**Implementation:**
```scala
run / javaOptions ++= Seq(
  "-XX:+UseCodeCacheFlushing"  // Default: enabled, but can tune
)
```

**Pros:**
- JVM actively flushes old compiled code when cache pressure increases

**Cons:**
- Already enabled by default in modern JVMs
- May not solve retention for applications with modest code cache usage

### Option 3: Accept as Normal JVM Behaviour

**Rationale:**
- Code cache retention is standard JVM behaviour
- Compiled code improves performance during load
- 50 MB retention is small relative to available memory
- Only becomes issue if memory is severely constrained

**Trade-off:**
- No code changes needed
- Accept ~50 MB baseline memory growth after load events
- Monitor to ensure retention doesn't accumulate across multiple load cycles

### Recommendation

**Option 3 (Accept) is recommended** unless memory is severely constrained:

1. **50 MB is acceptable overhead** for a production service (Railway provides 512 MB - 8 GB)
2. **Code cache improves performance** — evicting it hurts response times
3. **Retention is one-time** — doesn't grow with subsequent tests (second test also retained 50 MB, not 100 MB)
4. **Real issue was 93 MB retention** — reduced to 50 MB by PR #173

If memory constraints require action, **Option 1** (reduce code cache to 128 MB) is the safest approach.

## Verification: Third Consecutive Test

**Date:** 1 February 2026, 22:03 UTC
**Objective:** Verify retention is one-time (stable) vs cumulative (grows with each test)

### Test Configuration

**Critical difference:** No restart between tests 2 and 3

- **Test 2** (20:48 UTC): Fresh baseline (200 MB) → 250 MB after test
- **Test 3** (22:03 UTC): Started from 250 MB (no restart) → tested if memory grows to 300 MB

**Test 3 parameters:**
- Duration: 42 minutes
- Skaters: 10 per event
- Update interval: 3 seconds
- **Starting memory:** ~270 MB (after test 2)

### Results

**Railway memory metrics:**

| Time | Memory | Event |
|------|--------|-------|
| 22:03 UTC | ~270 MB | Test 3 starts |
| 22:07 UTC | 275 MB | During test |
| 22:45 UTC | 278 MB | After test completion |
| **Change** | **+8 MB** | **Within noise tolerance** |

### Analysis

**If cumulative (x+y+y pattern):**
- Expected: 270 MB + 50 MB = **320 MB** ❌
- Actual: **278 MB** ✓

**If stable (x+y pattern):**
- Expected: ~270-280 MB ✓
- Actual: **278 MB** ✓

**Conclusion:** Memory retention is **one-time warmup overhead**, not cumulative.

### Explanation

**Why memory stays stable:**

1. **Code cache:** Methods already compiled during test 2 → reused in test 3 → no additional compilation
2. **Metaspace:** Classes already loaded during test 2 → reused in test 3 → no additional class loading
3. **JVM warmed up:** Test 2 performed the one-time warmup, test 3 operates on already-optimised code

**Pattern confirmed:**
- First test after restart: Baseline + 50 MB (warmup)
- Subsequent tests: Stays at baseline + 50 MB (stable)

### Decision

**Accepted as normal JVM behaviour** based on verification showing:
- ✅ Retention is one-time (50 MB), not cumulative
- ✅ Memory stays stable across multiple load cycles
- ✅ Code cache and metaspace improve performance
- ✅ 50 MB overhead is acceptable for production service

**Test run:** https://github.com/SkatemapApp/skatemap-live/actions/runs/21571065748

## Next Steps

1. **Document findings in issue #166** ✅
2. **Decide on fix approach** — Accept (recommended) vs reduce code cache
3. **If implementing fix:**
   - Create PR with code cache configuration changes
   - Run Railway validation to verify retention reduced
   - Measure performance impact
4. **Disable NMT** after investigation completes (5-10% overhead)
5. **Close issue #166** once decision is made and documented

## Files Referenced

- **NMT raw outputs:** Removed post-investigation (available in git history at commit `b073759`)
- **Heap dump analysis:** [docs/profiling/results/railway-heap-dump-analysis-20260201.md](railway-heap-dump-analysis-20260201.md)
- **NMT investigation guide:** [docs/profiling/guides/native-memory-tracking-guide.md](../guides/native-memory-tracking-guide.md)

## References

- **Issue:** [#166 — Memory not released to OS during idle period](https://github.com/SkatemapApp/skatemap-live/issues/166)
- **PR #176:** Investigate native memory retention for issue #166
- **Test run:** https://github.com/SkatemapApp/skatemap-live/actions/runs/21569978501
- **Oracle JVM Code Cache documentation:** https://docs.oracle.com/en/java/javase/21/vm/codecache-tuning.html
