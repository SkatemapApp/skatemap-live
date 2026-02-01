# Native Memory Tracking (NMT) Investigation Guide

**Context:** Issue #166 — Memory not released to OS during idle period
**Purpose:** Investigate native memory retention (93 MB) that is not visible in heap dumps

## Background

Railway heap dump analysis (1 Feb 2026) confirmed:
- Java heap is healthy: 12.4 MB → 11.8 MB (decreased during idle)
- Zero application objects retained
- Railway total memory: ~280 MB
- **Gap: ~268 MB in native memory** (metaspace, thread stacks, direct ByteBuffers, code cache)

The 93 MB memory retention is in native memory, which heap dumps cannot capture.

## NMT Overview

Native Memory Tracking is a JVM feature that tracks memory allocations outside the Java heap:

**Categories tracked:**
- **Thread**: Thread stacks (typically 1 MB per thread)
- **Code**: JIT-compiled native code
- **GC**: Garbage collector internal structures
- **Compiler**: JIT compiler memory
- **Internal**: JVM internal allocations
- **Metaspace**: Class metadata beyond heap
- **Other**: Direct ByteBuffers, native libraries

**Performance impact:** ~5-10% overhead with `summary` mode (acceptable for investigation)

## Enabling NMT

NMT has been enabled in both local and Railway deployments:

**Local (`services/api/build.sbt`):**
```scala
run / javaOptions ++= Seq(
  "-XX:NativeMemoryTracking=summary"
)
```

**Railway (`services/api/docker-entrypoint.sh`):**
```sh
exec bin/skatemap-live \
  -J-XX:NativeMemoryTracking=summary \
  -Dplay.http.secret.key="${APPLICATION_SECRET}"
```

## Capturing NMT Snapshots

### Local Testing

**1. Start application:**
```bash
cd services/api
sbt run
```

**2. Get Java process ID:**
```bash
jps | grep PlayRun
# Output: 12345 PlayRun
```

**3. Capture baseline before load test:**
```bash
jcmd 12345 VM.native_memory baseline
```

**4. Run load test:**
```bash
cd tools/load-testing
EVENT_ID=$(uuidgen | tr '[:upper:]' '[:lower:]')
go run ./cmd/simulate-skaters \
  --target-url http://localhost:9000 \
  --event-id $EVENT_ID \
  --skaters-per-event 10 \
  --update-interval 3s
```

Run for 27+ minutes to match Railway test duration.

**5. Stop load test:**
```
Ctrl+C
```

**6. Wait for idle period:**
Wait 7-10 minutes to match Railway idle period.

**7. Capture NMT diff:**
```bash
jcmd 12345 VM.native_memory summary.diff > nmt-diff-$(date +%Y%m%d-%H%M%S).txt
```

**8. Analyse diff:**
```bash
cat nmt-diff-*.txt
```

Look for categories with positive diff values indicating retained memory.

### Railway Testing

**1. Trigger Railway smoke test:**
```bash
gh workflow run smoke-test.yml
```

**2. SSH into Railway once test is running:**
```bash
railway login --browserless
railway link
railway ssh
```

**3. Get Java process ID on Railway:**
```bash
jps
# Output: 1 ProdServerStart
```

**4. Capture baseline (during active load, ~15 minutes into test):**
```bash
jcmd 1 VM.native_memory baseline
```

**5. Wait for test completion and idle period:**

Monitor test progress via GitHub Actions or Railway logs. The smoke test runs for ~27 minutes with a 10-skater load.

**6. Capture NMT diff (after test completion, ~7 minutes idle):**
```bash
jcmd 1 VM.native_memory summary.diff > /app/nmt-diff-$(date +%Y%m%d-%H%M%S).txt
```

**7. Download NMT diff from Railway:**

Exit SSH session, then from local terminal:
```bash
railway ssh "cat /app/nmt-diff-*.txt" > nmt-diff-railway-$(date +%Y%m%d-%H%M%S).txt
```

**8. Analyse locally:**
```bash
cat nmt-diff-railway-*.txt
```

## Interpreting NMT Output

### Summary Section

```
Native Memory Tracking:

Total: reserved=1234567KB, committed=891011KB

-                 Java Heap (reserved=524288KB, committed=262144KB)
                            (mmap: reserved=524288KB, committed=262144KB)

-                     Class (reserved=100000KB, committed=80000KB)
                            (classes #12345)
                            (  instance classes #11111, array classes #1234)
                            (malloc=1000KB #10000)
                            (mmap: reserved=99000KB, committed=79000KB)
```

**Key fields:**
- **reserved**: Address space reserved but not necessarily backed by physical memory
- **committed**: Physical memory actually allocated

### Diff Section

When running `summary.diff`, look for positive values indicating memory growth:

```
-                     Thread (reserved=+10240KB +10, committed=+10240KB +10)
                            (thread #20 +10)
                            (stack: reserved=10240KB +10, committed=10240KB +10)
```

This indicates:
- 10 new threads created (+10)
- Each thread stack: 1 MB (10 MB total)
- **10 MB retained in thread stacks** ← likely candidate for 93 MB leak

### Categories to Investigate

**Thread stacks (most likely candidate):**
- If thread pool does not shrink after load, each thread retains ~1 MB
- 93 MB leak ≈ 93 extra threads not cleaned up
- Check: `(thread #N +X)` — if X is large positive number, threads are leaking

**Code cache:**
- JIT-compiled methods
- Should stabilise after warmup, not grow indefinitely
- Check: `(committed=+XXXXXKB)` — should be near zero or small after idle

**Compiler:**
- JIT compiler temporary allocations
- Should be minimal during idle
- Check: `(committed=+XXXXXKB)` — should be near zero during idle

**Internal:**
- JVM internal allocations
- Requires detailed investigation if this is growing
- Check: `(malloc=+XXXXXKB)` — should stabilise

## Expected Findings

Based on Railway validation showing 93 MB retention:

**Hypothesis:** Thread pool not shrinking after load completion

**Expected NMT diff:**
```
-                     Thread (reserved=+95232KB +93, committed=+95232KB +93)
                            (thread #123 +93)
                            (stack: reserved=95232KB +93, committed=95232KB +93)
```

This would indicate 93 new threads created during load test and never cleaned up.

**If confirmed:** Investigate Pekko dispatcher configuration:
- `pekko.actor.default-dispatcher.thread-pool-executor.core-pool-size`
- `pekko.actor.default-dispatcher.thread-pool-executor.max-pool-size`
- Thread pool keep-alive time settings

**Alternative hypothesis:** Code cache or metaspace growth

If thread count is stable but committed memory grows in other categories, investigate:
- JIT compilation (code cache)
- Class loading/unloading (metaspace)
- Direct buffer allocations (Internal category)

## Next Steps After NMT Analysis

1. **Document findings** in `docs/profiling/nmt-analysis-YYYYMMDD.md`
2. **Update issue #166** with NMT results
3. **Implement fix** based on findings (e.g., thread pool configuration)
4. **Validate fix** with another Railway test showing memory returned to baseline

## References

- **Issue:** [#166 — Memory not released to OS during idle period](https://github.com/SkatemapApp/skatemap-live/issues/166)
- **Heap dump analysis:** [docs/profiling/railway-heap-dump-analysis-20260201.md](railway-heap-dump-analysis-20260201.md)
- **Oracle NMT documentation:** https://docs.oracle.com/en/java/javase/21/troubleshoot/diagnostic-tools.html#GUID-90AA2C3F-4080-4CEF-B95E-0E8C2CF5F05A
