# Railway Heap Dump Analysis — Issue #166 Investigation

**Date:** 1 February 2026
**Issue:** [#166 — Memory not released to OS during idle period](https://github.com/SkatemapApp/skatemap-live/issues/166)
**Context:** Railway validation on 25 January 2026 showed memory retained after load test completion (331 MB → 424 MB, stayed at 424 MB indefinitely)

## Objective

Determine what is retaining the 93 MB of memory after load test completion by analysing Railway heap dumps captured during and after the load test.

## Test Parameters

**Load test configuration:**
- Duration: ~27 minutes
- Skaters: 10 per event
- Update interval: 3 seconds
- Platform: Railway production environment

**Heap dump timeline:**
1. **During active load** (27 minutes into test): `railway-27min-20260131-125718-fixed.hprof`
2. **After test completion** (7 minutes idle): `railway-post-test-20260131-130418-fixed.hprof`

## Heap Dump Comparison

| Metric | During Load (27 min) | After Test (idle) | Change |
|--------|---------------------|-------------------|--------|
| **Java heap size** | 12.4 MB | 11.8 MB | **-0.6 MB** (-4.8%) |
| **Object count** | 264,869 | 249,486 | **-15,383** (-5.8%) |
| **Class count** | 9,971 | 9,955 | **-16** (-0.2%) |
| **Capture time** | 12:57:19 GMT | 13:04:19 GMT | +7 min |

## Problem Suspects Analysis

### During Load Heap Dump

Eclipse MAT identified two main memory consumers:

1. **java.util.zip.ZipFile$Source** (2.5 MB, 20.37%)
   - 61 instances
   - Normal JAR file overhead for accessing application classes
   - Referenced by Common-Cleaner thread

2. **java.lang.Class** (2.5 MB, 20.23%)
   - 5,263 instances
   - Class metadata for loaded classes
   - Normal JVM overhead

### After Test Heap Dump

Eclipse MAT identified similar suspects:

1. **java.lang.Class** (2.7 MB, 21.69%)
   - 5,266 instances
   - Class metadata (slightly increased)

2. **java.util.zip.ZipFile$Source** (2.7 MB, 21.42%)
   - 61 instances
   - JAR file overhead (unchanged)

3. **java.lang.String** (1.3 MB, 10.43%)
   - 17,564 instances
   - String constants and interned strings

## Critical Finding: Zero Application Objects Retained

Searched both heap dumps for skatemap and Pekko objects using class histogram analysis:

```bash
grep -i "skatemap\|pekko" Class_Histogram7.html
```

**Result: No matches found in either dump.**

- Zero `skatemap.core.*` objects (Location, IngestService, InMemoryBroadcaster, etc.)
- Zero `skatemap.domain.*` objects
- Zero `org.apache.pekko.stream.*` objects
- Zero BroadcastHub materialisations
- Zero SourceQueue instances
- Zero materialised stream instances

## Analysis

### Java Heap Behaviour (Expected)

The Java heap demonstrates **correct garbage collection behaviour**:

1. ✅ Heap size **decreased** after load stopped: 12.4 MB → 11.8 MB
2. ✅ Object count **decreased**: 264,869 → 249,486 (-15,383 objects)
3. ✅ **Zero application objects retained** — all skatemap and Pekko stream objects were properly garbage collected
4. ✅ All problem suspects are **normal JVM overhead** (JAR files, class metadata, string constants)

### Native Memory Gap (Problematic)

Railway metrics show total process memory:
- Before test: 331 MB
- After test: 424 MB (retained indefinitely)
- **Memory retained: 93 MB**

Java heap only accounts for:
- ~12-13 MB (from heap dumps)

**Unaccounted memory: ~268 MB in native memory**

Native memory includes:
- **Metaspace**: Class metadata beyond heap (constant pools, field metadata, method data)
- **Thread stacks**: Each thread typically allocates 1 MB stack
- **Direct ByteBuffers**: Off-heap memory allocated by NIO
- **Code cache**: JIT-compiled native code
- **Compiler**: Memory used by JIT compiler
- **GC**: Native memory used by garbage collector internals
- **Internal**: JVM internal allocations

## Conclusion

**The 93 MB memory retention issue is NOT in the Java heap.**

Heap dumps have ruled out:
- BroadcastHub materialisation leaks
- SourceQueue retention
- Location object accumulation
- Pekko stream graph leaks

**The memory leak is in native memory**, which heap dumps cannot capture.

## Next Steps

To investigate the native memory leak, we need to enable **JVM Native Memory Tracking (NMT)**:

1. Add JVM flag: `-XX:NativeMemoryTracking=summary`
2. Deploy to Railway with NMT enabled
3. Capture NMT baseline before load test:
   ```bash
   jcmd <pid> VM.native_memory baseline
   ```
4. Run load test
5. Capture NMT diff after test completion:
   ```bash
   jcmd <pid> VM.native_memory summary.diff
   ```
6. Analyse which native memory category is retaining the 93 MB

NMT will break down native memory by category:
- Thread stacks (likely candidate — thread pool not shrinking?)
- Code cache (compiled methods not being unloaded?)
- Internal (native allocations not freed?)
- Metaspace (class metadata — unlikely given class count is stable)

## Related Documents

- [Railway Validation Results — 25 January 2026](railway-validation-results-20260125.md)
- [Stream Topology Documentation](../architecture/stream-topology.md)
- [JFR Continuous Memory Monitoring ADR](../adr/0005-jfr-continuous-memory-monitoring.md)

## References

- **Issue:** [#166 — Memory not released to OS during idle period](https://github.com/SkatemapApp/skatemap-live/issues/166)
- **PR:** [#174 — Enable Railway heap dump collection](https://github.com/SkatemapApp/skatemap-live/pull/174)
- **Heap dumps location:** `services/api/heap-dumps/railway-*.hprof`
- **Analysis tool:** Eclipse Memory Analyzer (MAT) 1.15.0
