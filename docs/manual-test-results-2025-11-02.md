# Manual Smoke Test Results - 2025-11-02

## Test Environment
- Tester: [User]
- Date: 2025-11-02 to 2025-11-05
- Start time: ~17:55 GMT (2025-11-02)
- End time: 23:46 GMT (2025-11-05)
- Deployment URL: https://skatemap-live-production.up.railway.app
- Railway environment: Production
- Git commit: PR #132 merged (WebSocket timeout fix - 3 minutes), PR #137 merged (--event-id flag)

## Test Results Summary

### Phase 1: Event Isolation ✅
- Result: PASS
- Notes: Event isolation working correctly. Event A and Event B showed only their respective skaters. No cross-event data leakage observed.

### Phase 2: Location Expiry ❌
- Result: **FAIL - Frontend Bug (Server cleanup working correctly)**
- Server cleanup timing: **31 seconds (CORRECT)**
- Browser display timing: **~75 seconds (BUG - stale data)**
- Notes: Server cleaned up expired locations correctly at 31 seconds, but browser viewer continued showing stale data until WebSocket disconnected ~75 seconds after skaters stopped
  - Skaters stopped: 18:47:58
  - Server cleanup removed 3 locations: **18:48:29 (31s later - within expected 40s maximum)**
  - Browser still showed skaters until: ~18:49:13 (~75s later - WebSocket disconnected)
  - Root cause: Frontend viewer.html accumulates locations without expiry logic
  - Additional observations:
    - WebSocket eventually disconnected (expected idle timeout behaviour)
    - Terminal viewer simulators correctly stopped receiving immediately when skaters stopped
    - Browser only cleared display when WebSocket onclose event fired

### Phase 2.7: WebSocket Timeout Test ✅
- Result: **PASS**
- Idle period tested: 94 seconds
- Notes: WebSocket connection survived idle period and resumed data flow correctly
  - Skaters stopped: 09:00:30
  - Idle period: 94 seconds (exceeds 75 second threshold)
  - Skaters restarted: 09:02:04
  - Terminal viewer resumed messages: 09:02:07 (message 171 after message 170)
  - No "connection reset by peer" errors in terminal viewer
  - Browser showed "network connection was lost" but terminal viewer continued working (browser issue likely related to frontend bug #136)

### Phase 3: 30-Minute Stability ❌
- Result: **FAIL - Memory Leak Confirmed**
- Duration: 31 minutes (22:47:45 to 23:19:07)
- Memory: **Linear growth - 550MB increase (250MB → 800MB)**
- CPU: Stable, < 0.1 vCPU average
- Notes: Critical memory leak confirmed with linear growth pattern
  - Start memory: ~250 MB (22:45)
  - End memory: ~800 MB (23:15)
  - Growth rate: ~17.7 MB/minute
  - Load: 10 skaters (5 per event), 3s update interval
  - Requests: ~100 requests/minute steady
  - Error rate: 0.0% (perfect)
  - Response time spike at end: ~30 seconds (p99) - same issue as Phase 1
  - At this rate, would exhaust 1GB memory limit in ~70 minutes under sustained load

### Phase 4: Scale Test ❌
- Result: **FAIL - Memory Leak Continues, Not Released During Idle**
- Duration: 7 minutes (23:39:11 to 23:46:24)
- Maximum skaters tested: 15 (10 in Event A, 5 in Event B)
- Memory: Started at 800MB (carried over), ended at ~825MB
- CPU: Stable, < 0.1 vCPU average
- Notes: Critical finding - memory NOT released during 20-minute idle period between Phase 3 and Phase 4
  - Phase 3 ended: 23:19:07 at 800MB
  - Idle period: 23:19 to 23:39 (20 minutes with no activity)
  - Phase 4 started: 23:39:11 still at 800MB
  - **Leaked memory is NOT being garbage collected**
  - Phase 4 memory growth: 25MB in 7 minutes (~3.6 MB/min, but starting from already leaked 800MB)
  - Request rate increased to ~150 req/min (expected with 15 skaters vs 10)
  - Error rate: 0.0% (perfect reliability maintained)
  - Response time spike at end: ~30 seconds (same pattern as Phase 1 and Phase 3)
  - Network traffic: ~100-150 KB/s (higher than Phase 3 due to increased load)

## Railway Metrics

### Memory Usage

**Phase 1:**
- Start: ~400 MB (17:55)
- End: ~750 MB (18:18)
- Growth: 350MB in ~23 minutes (~15.2 MB/min)
- Screenshot: Provided

**Phase 3:**
- Start: ~250 MB (22:45, after redeploy)
- End: ~800 MB (23:15)
- Growth: 550MB in ~31 minutes (~17.7 MB/min)
- Peak: 800 MB
- Trend: **Linear continuous growth - MEMORY LEAK CONFIRMED**
- Screenshot: Provided

**Idle Period (between Phase 3 and Phase 4):**
- Duration: 23:19 to 23:39 (20 minutes, no activity)
- Memory at start: 800 MB
- Memory at end: 800 MB
- **CRITICAL: NO MEMORY RELEASED - Leaked memory is NOT being garbage collected**

**Phase 4:**
- Start: ~800 MB (23:39, carried over from Phase 3)
- End: ~825 MB (23:46)
- Growth: 25MB in ~7 minutes (~3.6 MB/min)
- Load: 15 skaters (50% increase over Phase 3)
- Trend: Continued growth from already leaked baseline
- Screenshot: Provided

**Analysis:** All phases show memory growth (~15-18 MB/min under 10 skaters, ~3.6 MB/min under 15 skaters starting from leaked 800MB). CRITICAL: 20-minute idle period showed NO memory release, confirming leaked memory is not being garbage collected. This is a severe memory leak that would exhaust the 1GB limit in ~70 minutes of sustained operation.

### CPU Usage

**Phase 1:**
- Average: < 0.1 vCPU (minimal)
- Peak: < 0.2 vCPU
- Trend: Stable, very low utilisation

**Phase 3:**
- Average: < 0.05 vCPU (minimal)
- Peak: ~1.0 vCPU (brief spike at startup, 22:45)
- Trend: Stable, very low utilisation
- Screenshot: Provided

**Phase 4:**
- Average: < 0.1 vCPU (minimal)
- Peak: < 0.2 vCPU
- Trend: Stable, very low utilisation despite 50% increased load
- Screenshot: Provided

**Analysis:** CPU usage remains extremely low and stable across all phases, even with increased load in Phase 4. Not a performance concern.

### Network Traffic

**Phase 1:**
- Active (18:00-18:15): Steady 60-80 KB/s
- Stopped (18:18): Dropped to ~30 KB/s

**Phase 3:**
- Active (22:45-23:15): Steady 80-90 KB/s
- Stopped (23:19): Dropped to ~10 KB/s
- Screenshot: Provided

**Phase 4:**
- Active (23:39-23:46): Steady 100-150 KB/s
- Stopped (23:46): Dropped to ~10 KB/s
- Screenshot: Provided

**Pattern:** Expected behaviour - traffic correlates with simulation activity. Phase 4 shows increased traffic (~50% higher) matching the increased skater count. Network usage stable and predictable.

### Request Metrics

**Phase 1:**
- Rate: ~95 requests/minute (steady)
- Post-Phase 1: Dropped to ~20 requests/minute (18:18)
- Total requests: ~2,000+
- Error count: 0
- Error rate: 0.0%

**Phase 3:**
- Rate: ~100 requests/minute (steady)
- Duration: 31 minutes
- Total requests: ~3,100
- Error count: 0
- Error rate: 0.0%
- All responses: 2xx (successful)
- Screenshot: Provided

**Phase 4:**
- Rate: ~150 requests/minute (steady)
- Duration: 7 minutes
- Total requests: ~1,050
- Error count: 0
- Error rate: 0.0%
- All responses: 2xx (successful)
- Screenshot: Provided

**Analysis:** Excellent reliability - zero errors across all testing phases including Phase 4 with 50% increased load. Request rate scales linearly with skater count (10 skaters: 100 req/min, 15 skaters: 150 req/min). System handles increased load without any errors.

### Response Time

**Phase 1:**
- Median (p50): < 100ms (normal)
- p95/p99: Several spikes to 20+ seconds observed around 18:10-18:15

**Phase 3:**
- Median (p50): < 100ms (normal)
- p99: **Spike to ~30 seconds at end (~23:15-23:19)**
- Screenshot: Provided

**Phase 4:**
- Median (p50): < 100ms (normal)
- p99: **Spike to ~30 seconds at end (~23:46)**
- Screenshot: Provided

**Concern:** Response time spikes to 20-30+ seconds observed in Phase 1, Phase 3, and Phase 4. All spikes occurred at test end (Phase 1: 18:10-18:15, Phase 3: 23:15-23:19, Phase 4: 23:46). Pattern strongly correlates with test stop/cleanup timing. These are extremely high for simple PUT requests and suggest a shutdown/cleanup performance issue.

## Metrics Analysis

### Skater Update Metrics

**Note:** CSV files from Phase 1 and Phase 2 were overwritten during subsequent test runs (command history reuse). Only Phase 3 and Phase 4 CSV files are available for analysis.

Phase 1 files:
- Not available (overwritten)

Phase 2 files:
- Not available (overwritten)

Phase 3 files:
- [File names pending]

Phase 4 files:
- `event-a-stability.csv` - Available for analysis
- `event-a-additional.csv` - Available for analysis
- `event-b-stability.csv` - Available for analysis

**Findings:** CSV analysis deferred - not required for smoke test validation as Railway metrics provide sufficient data for all phases.

### Viewer Metrics

**Note:** No viewer simulations were run for Phase 3 or Phase 4. Phase 1 and Phase 2 viewer CSV files were overwritten.

Phase 1 files:
- Not available (overwritten)

**Findings:** Negative latency values were observed in viewer output during Phase 1 testing (known bug tracked in #133). Browser viewers were used for observation in later phases.

## Issues Found

### Issue 1: Memory Leak - Linear Growth, No Garbage Collection
- Severity: **CRITICAL**
- Phase: All phases (Phase 1, Phase 3, Phase 4)
- Description: **CONFIRMED MEMORY LEAK** - Memory usage grows linearly during operation (~15-18 MB/min) and is NOT released during idle periods
- Steps to reproduce:
  1. Deploy fresh instance (memory starts at ~250MB)
  2. Run skater simulations with any load (tested with 6, 10, and 15 skaters)
  3. Monitor Railway memory metrics
  4. Stop simulations and observe idle period
- Expected behaviour:
  - Memory should stabilise during steady-state operation
  - Memory should be released by garbage collector during idle periods
- Actual behaviour:
  - **Phase 1**: 400MB → 750MB in 23 minutes (~15.2 MB/min growth)
  - **Phase 3**: 250MB → 800MB in 31 minutes (~17.7 MB/min growth)
  - **Idle period**: 23:19-23:39 (20 minutes) - memory stayed at 800MB, NO RELEASE
  - **Phase 4**: 800MB → 825MB in 7 minutes (~3.6 MB/min additional growth from leaked baseline)
- Assessment: **CRITICAL PRODUCTION BLOCKER**
  - Memory grows linearly at ~15-18 MB/min under typical load
  - Leaked memory is NOT garbage collected (confirmed by 20-minute idle period)
  - Would exhaust 1GB Railway limit in ~70 minutes of sustained operation
  - Likely causes: Unclosed resources (streams, connections, actors), accumulating state not being cleaned up
- Impact: Application would crash with OOM in production under sustained load
- Screenshots/logs: Railway metrics screenshots provided for all phases
- Priority: Must fix before production deployment

### Issue 2: Response Time Spikes to 20-30+ Seconds at Test Shutdown
- Severity: High
- Phase: All phases (Phase 1, Phase 3, Phase 4)
- Description: Response time spikes to 20-30+ seconds observed consistently at the end of each test phase
- Steps to reproduce:
  1. Run skater simulations for several minutes
  2. Stop simulations with Ctrl+C
  3. Observe Railway response time metrics
- Expected behaviour: PUT requests should respond in < 500ms consistently, even during shutdown
- Actual behaviour: p99 response time spikes to 20,000-30,000+ ms at test end
- Timing analysis:
  - Phase 1: Spikes around 18:10-18:15 (near Event B stop at 18:14:11)
  - Phase 3: Spike at 23:15-23:19 (test stopped at 23:19:07)
  - Phase 4: Spike at 23:46 (test stopped at 23:46:24)
- Assessment: Pattern strongly correlates with test shutdown/cleanup. Very concerning for production use as it suggests graceful shutdown may block request handling. May indicate:
  - Cleanup service blocking request threads
  - WebSocket stream shutdown blocking
  - Resource cleanup contention
- Screenshots/logs: Railway Response Time graphs show consistent pattern across all phases
- Follow-up required: Investigate application shutdown behaviour, cleanup service timing, and potential blocking operations during shutdown

### Issue 3: Poor WebSocket Error Message for Invalid Event ID
- Severity: Low (UX issue)
- Phase: Phase 1
- Description: When starting viewer with invalid event ID (typo: "k321ec76c..." instead of valid UUID "321ec76c..."), error message is unhelpful: "websocket: bad handshake"
- Steps to reproduce:
  1. Start viewer simulation with malformed/invalid event ID
  2. Observe error message
- Expected behaviour: Clear validation error like "Invalid event ID format" or "Event not found"
- Actual behaviour: Generic WebSocket handshake failure message
- Assessment: Poor user experience - difficult to diagnose typos or invalid IDs
- Tracked in: Issue #135

### Issue 4: Negative Latency Values in Viewer Simulation
- Severity: Low (tooling bug, not server issue)
- Phase: Phase 1
- Description: Viewer simulation tool reports negative latency values (e.g., -2072ms)
- Root cause: Clock skew between local machine and Railway server, or latency calculation bug in viewer tool
- Assessment: Bug in simulation tool, not the server. Does not affect smoke test validity.
- Tracked in: Issue #133

### Issue 5: Frontend Viewer Shows Stale Skater Data After Server Cleanup
- Severity: **HIGH**
- Phase: Phase 2
- Description: Browser viewer continues displaying skaters for ~75 seconds after they expire on the server, despite server cleanup working correctly at 31 seconds.
- Steps to reproduce:
  1. Open browser viewer and connect to an event
  2. Start skater simulation with 3 skaters, 2s update interval
  3. Run for several minutes to establish active display
  4. Stop skater simulation
  5. Observe browser viewer - skaters remain visible
  6. Monitor cleanup logs and browser display
- Expected behaviour:
  - Server cleanup removes expired locations within 40s (30s TTL + 10s cleanup interval)
  - Browser viewer shows skaters disappear within same timeframe
  - Or: Browser clears display when WebSocket disconnects
- Actual behaviour:
  - Skaters stopped: 18:47:58
  - Server cleanup removed locations: **18:48:29 (31 seconds - CORRECT!)**
  - Browser still showed skaters until WebSocket disconnected ~75 seconds later
  - Skaters only disappeared from browser when WebSocket closed, not when server cleaned them up
- Railway cleanup logs (actual timing):
  ```
  18:48:29 - Cleanup completed: removed 3 locations (31s after stop) ✅
  18:50:39 - Cleanup completed: removed 3 locations (from different test run)
  ```
- Assessment: Server-side cleanup is working correctly. The bug is in the **frontend viewer** (viewer.html) which accumulates location data without removing stale entries.
- Root cause: Frontend bug in viewer.html:318-346
  1. Line 324: `skaterLocations.set(location.skaterId, location)` - Only adds/updates, never removes
  2. Line 319-321: Returns early if batch is empty - doesn't process removals
  3. Line 396: `skaterLocations.clear()` - Only clears on WebSocket disconnect
  4. **No mechanism to remove skaters that stop appearing in updates**
- Contributing backend design issue:
  - EventStreamService (EventStreamService.scala:20-31) only streams location updates
  - When cleanup removes locations, nothing is published to the WebSocket stream
  - Stream goes silent when all skaters expire (groupedWithin doesn't emit empty batches)
  - Frontend never receives notification that locations were removed
- Impact: Browser viewers show stale data until WebSocket disconnects, which can be several minutes depending on idle timeout configuration
- Tracked in: Issue #136

**Technical Analysis:**

**Backend behaviour (verified correct):**
1. CleanupService runs every 10 seconds (verified from Railway startup logs)
2. Cleanup at 18:48:29 removed 3 locations correctly (31s after last update)
3. EventStreamService.scala:27-30 streams updates via groupedWithin(batchSize=100, interval=500ms)
4. When skaters expire, stream goes silent (no removal events published)

**Frontend bug (viewer.html):**
1. `skaterLocations` Map accumulates all received locations (line 324)
2. No expiry logic - skaters are never removed from Map unless WebSocket disconnects
3. Empty batches trigger early return (line 320) without processing
4. Result: Stale data persists indefinitely until connection drops

**Fix options:**
1. Frontend: Replace Map contents with each batch (treat as snapshot, not delta)
2. Frontend: Add client-side TTL to expire skaters not seen in recent batches
3. Backend: Publish removal events when cleanup deletes locations
4. Backend: Periodically send full snapshots instead of deltas only

## Observations

### Positive Findings
- Event isolation working perfectly - no cross-event data leakage (Phase 1)
- **Server-side cleanup working correctly - removed locations after 31 seconds (Phase 2)**
- Zero error rate across ALL phases (Phase 1, Phase 3, Phase 4) - perfect reliability
- CPU usage very low and stable across all phases, even with increased load
- Network traffic patterns match expected behaviour and scale linearly with load
- WebSocket connections remained stable throughout all phases (no unexpected drops)
- Request rate scales linearly with skater count (10 skaters: 100 req/min, 15 skaters: 150 req/min)
- System handles 50% load increase (Phase 4) without any functional issues

### Areas of Concern
- **CRITICAL: Memory leak confirmed - ~15-18 MB/min growth, NOT garbage collected during idle (all phases)**
- **HIGH: Response time spikes to 20-30+ seconds at test shutdown (all phases)**
- **HIGH: Frontend viewer shows stale data for ~75s after server cleanup (Phase 2)**
- **LOW: Poor error messages for invalid WebSocket connections (UX)**

### WebSocket Connection Stability
- WebSocket timeout configuration: Default 3 minutes (from PR #132)
- Phase 2.7 timeout test result: **PASS** - connections survived 94 second idle period
- Any "connection reset by peer" errors: No - none observed in Phase 1 or Phase 2.7
- Connection behaviour during Phase 1: Stable - no disconnections
- Connection behaviour during Phase 2.7: Survived 94s idle, resumed data flow without reconnection
- Related: PR #132 successfully fixed default 75-second timeout issue

## Recommendations

- [ ] System ready for automated load testing - **NO, CRITICAL ISSUES FOUND**
- [x] Issues need addressing before automation - Memory leak and response time spikes are blockers
- [ ] Further manual testing recommended - **NO, manual smoke test complete**

### Immediate Actions Required (Priority Order)
1. **CRITICAL: Investigate and fix memory leak** - System would crash with OOM in ~70 minutes under load
   - Profile application memory usage
   - Check for unclosed streams, connections, actors
   - Review state accumulation (LocationStore, BroadcastHub management)
   - Verify cleanup service is actually removing data from all data structures
2. **HIGH: Investigate response time spikes at shutdown** - 20-30+ second delays at test end
   - Review application shutdown hooks
   - Check cleanup service blocking behaviour
   - Investigate WebSocket stream shutdown
3. **HIGH: Fix frontend viewer stale data bug (viewer.html)** - Tracked in #136
   - Skaters persist ~75s after server cleanup
   - Choose fix approach (client-side TTL, snapshot mode, or removal events)

### Future Improvements (Non-Blocking)
1. Improve WebSocket error messages for invalid event IDs (tracked in #135)
2. Fix negative latency calculation in viewer simulation tool (tracked in #133)
3. Add monitoring/alerting for response time and memory usage in production

## Appendices

### Event IDs Used

**Phase 1:**
- Event A: `ed5851ad-d189-478e-9ac8-7eda46cd6a11` (started 17:55:23, stopped 18:21:08)
- Event B: `321ec76c-1b21-4f17-b4e7-860b98c71dc4` (started 17:55:46, stopped 18:14:11)

**Phase 2, 2.7, 3:**
- [Event IDs pending]

**Phase 4:**
- Event A: `08940290-20b0-4f18-bc27-4b47904d33d9` (started 23:39:11, stopped 23:46:24 - 10 skaters)
- Event B: `2a4d3853-bdf3-4fcf-b7a0-c6b8db2de917` (started 23:39:49, stopped 23:46:24 - 5 skaters)

### Metrics Files

**Phase 1:**
- `event-a-skaters.csv` - [Size, row count pending]
- `event-b-skaters.csv` - [Size, row count pending]
- `event-a-viewers.csv` - [Size, row count pending]
- `event-b-viewers.csv` - [Size, row count pending]

**Phase 3:**
- [File names pending]

**Phase 4:**
- `event-a-stability.csv` - Skater metrics (Event A, 5 skaters)
- `event-a-additional.csv` - Skater metrics (Event A, additional 5 skaters)
- `event-b-stability.csv` - Skater metrics (Event B, 5 skaters)

### Screenshots
1. Railway metrics at Phase 1 start (~17:55) - Provided
2. Railway metrics at Phase 1 end (~18:18) - Provided
3. Memory growth graph - Provided
4. Response time spikes - Provided
5. Browser viewer Event A - [Not captured]
6. Browser viewer Event B - [Not captured]
7. Browser console (if errors) - No errors

## Next Steps

1. ✅ Phase 1 complete - Event isolation validated
2. ✅ Phase 2 complete - Location expiry validated (server working, frontend bug found)
3. ✅ Phase 2.7 complete - WebSocket timeout test validated (PR #132 successful)
4. ✅ Phase 3 complete - Memory leak confirmed (550MB growth in 31 minutes)
5. ✅ Phase 4 complete - Memory NOT released during idle, continued growth with increased load
6. ✅ GitHub issue created for memory leak - #138 (CRITICAL priority)
7. ✅ GitHub issue created for response time spikes - #139 (HIGH priority)
8. ✅ Test plan and results documents ready for commit

## Issues Created

- #135 - Poor WebSocket error messages for invalid event IDs (LOW priority)
- #136 - Frontend viewer shows stale data after server cleanup (HIGH priority)
- #138 - Memory leak causes OOM crash in ~70 minutes (CRITICAL priority)
- #139 - Response time spikes to 20-30+ seconds at shutdown (HIGH priority)
