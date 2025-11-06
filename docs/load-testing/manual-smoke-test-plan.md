# Manual Smoke Test Plan - Issue #110

**Purpose:** Validate deployed system functionality before automating tests

**Duration:** 1.5 hours (1 hour testing + 0.5 hour documentation)

**Issue:** https://github.com/SkatemapApp/skatemap-live/issues/110

## Prerequisites

### Environment Setup

**Get Railway deployment URL:**
```bash
cd /path/to/skatemap-live
railway status
```

Store the public URL as `$RAILWAY_URL` (e.g., `https://skatemap-live-production.up.railway.app`)

**Verify health endpoint:**
```bash
curl $RAILWAY_URL/health
```

Expected: `200 OK`

**Build simulation tools (if not already built):**
```bash
cd tools/load-testing
go build -o bin/simulate-skaters ./cmd/simulate-skaters
go build -o bin/simulate-viewers ./cmd/simulate-viewers
```

**Tools required:**
- Terminal with multiple tabs/windows
- Web browser with multiple tabs
- Access to Railway dashboard at https://railway.app
- Text editor for documentation

---

## Phase 1: Event Isolation Testing (15 minutes)

### Objective
Verify that events are completely isolated - skaters in Event A never appear in Event B viewers.

### Steps

**1.1 Start Event A Simulation (Terminal 1)**

```bash
cd tools/load-testing

./bin/simulate-skaters \
  --events=1 \
  --skaters-per-event=3 \
  --update-interval=2s \
  --target-url=$RAILWAY_URL \
  --metrics-file=event-a-skaters.csv
```

**Important:** Note the generated Event ID from the output (looks like `Generated event IDs: [550e8400-...]`)

Store as `$EVENT_A_ID`

**1.2 Start Event B Simulation (Terminal 2)**

```bash
cd tools/load-testing

./bin/simulate-skaters \
  --events=1 \
  --skaters-per-event=3 \
  --update-interval=2s \
  --target-url=$RAILWAY_URL \
  --metrics-file=event-b-skaters.csv
```

Store the generated Event ID as `$EVENT_B_ID`

**1.3 Start Event A Viewers (Terminal 3)**

```bash
cd tools/load-testing

./bin/simulate-viewers \
  --viewers-per-event=1 \
  --events=$EVENT_A_ID \
  --target-url=$RAILWAY_URL \
  --metrics-file=event-a-viewers.csv
```

Watch the output for incoming messages.

**1.4 Start Event B Viewers (Terminal 4)**

```bash
cd tools/load-testing

./bin/simulate-viewers \
  --viewers-per-event=1 \
  --events=$EVENT_B_ID \
  --target-url=$RAILWAY_URL \
  --metrics-file=event-b-viewers.csv
```

**1.5 Open Browser Viewer for Event A**

1. Open browser tab: `$RAILWAY_URL/assets/viewer.html`
2. Enter Event ID: `$EVENT_A_ID`
3. Click "Connect"
4. Verify: 3 skaters appear and update every 2 seconds

**1.6 Open Browser Viewer for Event B**

1. Open browser tab: `$RAILWAY_URL/assets/viewer.html`
2. Enter Event ID: `$EVENT_B_ID`
3. Click "Connect"
4. Verify: 3 different skaters appear

### Acceptance Criteria - Phase 1

- [ ] Event A skaters appear only in Event A viewers (browser + terminal output)
- [ ] Event B skaters appear only in Event B viewers (browser + terminal output)
- [ ] No cross-event data leakage observed
- [ ] Location updates appear within 3 seconds (2s interval + <1s network/processing)
- [ ] Browser WebSocket connections remain stable (no disconnections)
- [ ] Terminal viewer outputs show message counts increasing

---

## Phase 2: Location Expiry Testing (10 minutes)

### Objective
Verify locations expire after 30 seconds and cleanup works correctly.

### Steps

**2.1 Stop Event A Skaters**

In Terminal 1, press `Ctrl+C` to stop the Event A skater simulation.

**2.2 Observe Event A Viewer (Browser)**

Watch the Event A browser viewer and note timing:
- **T+0s:** All 3 skaters still visible
- **T+30s:** Skaters should start disappearing
- **T+40s:** All skaters should be gone (30s TTL + 10s cleanup interval maximum)

**2.3 Verify Event B Unaffected**

Check Event B browser viewer:
- [ ] Event B still shows 3 skaters
- [ ] Event B updates still flowing normally
- [ ] Event A expiry did not impact Event B

**2.4 Stop Event B Skaters**

In Terminal 2, press `Ctrl+C` to stop the Event B skater simulation.

**2.5 Observe Event B Viewer**

Wait 30-40 seconds:
- [ ] All Event B skaters disappear
- [ ] Both events now show empty state

**2.6 Verify Empty State and WebSocket Stability**

- [ ] Both browser viewers show zero skaters
- [ ] No errors in browser console (press F12 to check)
- [ ] WebSocket connections still active (check browser DevTools Network tab - WS status should be "101 Switching Protocols")
- [ ] Terminal viewers show no new messages
- [ ] **IMPORTANT:** No "connection reset by peer" errors in terminal viewer logs

### Acceptance Criteria - Phase 2

- [ ] Locations expire within 30-40 seconds after last update
- [ ] Cleanup does not affect other active events
- [ ] Empty state renders correctly in browser
- [ ] No errors logged during expiry process

**2.7 WebSocket Timeout Regression Test**

This validates PR #132 - WebSocket idle timeout fix.

**Objective:** Verify that WebSocket connections survive >75 seconds of idle time (no data flowing), and can resume receiving data through the same connection without reconnecting.

**Prerequisites:** Event A viewers still running from Phase 2 (Terminal 3), connected to `$EVENT_A_ID`

**Steps:**

1. Note the Event A ID from Phase 2: `$EVENT_A_ID` (the ID Event A viewers are currently connected to)

2. Restart Event A skaters **using the same event ID**:
   ```bash
   # Terminal 1
   cd tools/load-testing

   ./bin/simulate-skaters \
     --event-id=$EVENT_A_ID \
     --skaters-per-event=3 \
     --update-interval=2s \
     --target-url=$RAILWAY_URL \
     --metrics-file=event-a-timeout-test.csv
   ```

3. Verify Event A viewer (Terminal 3) immediately receives updates
   - [ ] Messages start flowing again in Terminal 3
   - [ ] No reconnection happened (viewer was already connected)

4. Stop Event A skaters (Ctrl+C in Terminal 1)
   - WebSocket is now idle (no data flowing)

5. **Wait 90 seconds** (beyond the old 75-second default timeout that was causing failures)
   - Time this carefully - use a timer or watch

6. Verify WebSocket connection survived the idle period:
   - [ ] Terminal 3 viewer shows no "connection reset by peer" errors
   - [ ] Terminal 3 viewer shows no disconnection messages
   - [ ] If browser viewer is still open: WebSocket still shows "101 Switching Protocols" in DevTools Network tab

7. Restart Event A skaters **with the same event ID again**:
   ```bash
   # Terminal 1
   ./bin/simulate-skaters \
     --event-id=$EVENT_A_ID \
     --skaters-per-event=3 \
     --update-interval=2s \
     --target-url=$RAILWAY_URL \
     --metrics-file=event-a-timeout-test-2.csv
   ```

8. Verify updates flow immediately through the **existing connection**:
   - [ ] Messages appear in Terminal 3 within seconds
   - [ ] No reconnection happened (connection survived the 90-second idle period)
   - [ ] Data flows through the same WebSocket connection

**Expected:** WebSocket survives >75 seconds idle period. Connections should remain stable up to 3 minutes (default timeout from PR #132). Data can resume flowing through the same connection after idle period.

**If this test fails:** Check Railway logs for "HTTP idle-timeout encountered" messages - indicates the timeout configuration was not applied correctly.

---

## Phase 3: Long-Running Stability Test (30 minutes)

### Objective
Verify system remains stable during extended operation, check for memory leaks or performance degradation.

### Steps

**3.1 Restart Both Event Simulations**

**Terminal 1 - Event A:**
```bash
cd tools/load-testing

./bin/simulate-skaters \
  --events=1 \
  --skaters-per-event=5 \
  --update-interval=3s \
  --target-url=$RAILWAY_URL \
  --metrics-file=event-a-stability.csv
```

Note the Event ID as `$EVENT_A_ID`

**Terminal 2 - Event B:**
```bash
cd tools/load-testing

./bin/simulate-skaters \
  --events=1 \
  --skaters-per-event=5 \
  --update-interval=3s \
  --target-url=$RAILWAY_URL \
  --metrics-file=event-b-stability.csv
```

Note the Event ID as `$EVENT_B_ID`

**3.2 Start Multiple Viewers**

**Terminal 3 - Event A Viewers:**
```bash
cd tools/load-testing

./bin/simulate-viewers \
  --viewers-per-event=2 \
  --events=$EVENT_A_ID \
  --target-url=$RAILWAY_URL \
  --metrics-file=event-a-stability-viewers.csv
```

**Terminal 4 - Event B Viewers:**
```bash
cd tools/load-testing

./bin/simulate-viewers \
  --viewers-per-event=2 \
  --events=$EVENT_B_ID \
  --target-url=$RAILWAY_URL \
  --metrics-file=event-b-stability-viewers.csv
```

**3.3 Open Browser Viewers**

Keep both browser viewers open (from Phase 1 or reopen):
- Browser Tab 1: Event A
- Browser Tab 2: Event B

**3.4 Monitor for 30 Minutes**

Set a timer for 30 minutes. During this time, monitor:

**Every 5 minutes, check:**

1. **Browser Performance:**
   - [ ] Tabs remain responsive (no lag when switching)
   - [ ] Location markers update smoothly
   - [ ] No console errors (F12 → Console)
   - [ ] Memory usage stable (F12 → Performance Monitor)

2. **Terminal Viewers:**
   - [ ] Message counts steadily increasing
   - [ ] No error messages appearing
   - [ ] Latency values remain reasonable (< 2000ms)

3. **Railway Metrics:**

Open Railway dashboard and check:

```bash
railway logs --tail 50
```

Monitor for:
- [ ] No error messages in logs
- [ ] No crash/restart events
- [ ] CleanupService logs showing normal operation

Railway dashboard graphs:
- [ ] Memory usage: stable, not growing linearly
- [ ] CPU usage: stable, < 80%
- [ ] Request rate: steady pattern
- [ ] No error rate spikes

**3.5 Analyse Metrics Files**

After 30 minutes, inspect the CSV files:

```bash
cd tools/load-testing

# Check skater metrics
wc -l event-a-stability.csv event-b-stability.csv

# Check for errors
grep -c "error" event-a-stability.csv event-b-stability.csv

# Check viewer metrics
tail -20 event-a-stability-viewers.csv
tail -20 event-b-stability-viewers.csv
```

**Expected:**
- CSV files contain hundreds of rows (10 skaters × 20 updates/min × 30 min = ~6000 rows per event)
- Zero or minimal errors
- Latency values remain consistent throughout

### Acceptance Criteria - Phase 3

- [ ] System runs for 30 minutes without crashes
- [ ] Memory usage remains stable (Railway metrics)
- [ ] CPU usage remains reasonable (< 80% average)
- [ ] WebSocket connections remain stable (no disconnections)
- [ ] Browser tabs remain responsive
- [ ] No error rate increase over time
- [ ] Response times consistent (check CSV metrics)
- [ ] Railway logs show no errors or warnings

---

## Phase 4: Scale Test - More Skaters (5 minutes)

### Objective
Verify system handles increased load gracefully.

### Steps

**4.1 Keep Existing Simulations Running**

Leave Phase 3 simulations running (Event A & B with 5 skaters each).

**4.2 Add Additional Skaters to Event A**

**Terminal 5 - More Event A Skaters:**
```bash
cd tools/load-testing

./bin/simulate-skaters \
  --event-id=$EVENT_A_ID \
  --skaters-per-event=5 \
  --update-interval=3s \
  --target-url=$RAILWAY_URL \
  --metrics-file=event-a-additional.csv
```

This adds 5 more skaters to the existing Event A (which already has 5 skaters from Phase 3).

**4.3 Verify Increased Load Handling**

Check Event A browser viewer:
- [ ] Now shows 10 skaters (5 original + 5 additional)
- [ ] All skaters updating smoothly
- [ ] No performance degradation

Check Event B browser viewer:
- [ ] Still shows only 5 skaters
- [ ] Event B unaffected by Event A load increase

Check Railway metrics:
- [ ] CPU usage increased but still reasonable
- [ ] Memory usage stable
- [ ] No errors

### Acceptance Criteria - Phase 4

- [ ] System handles doubled load (10 skaters in Event A)
- [ ] Event isolation maintained (Event B still shows 5 skaters)
- [ ] No performance degradation observed
- [ ] Response times remain acceptable

---

## Cleanup

**Stop all simulations:**

1. Terminal 1-5: Press `Ctrl+C` in each terminal
2. Wait 30-40 seconds for cleanup to occur
3. Verify all browser viewers show empty state

**Collect metrics files:**

```bash
cd tools/load-testing
ls -lh *.csv

# Archive results
mkdir -p test-results/$(date +%Y-%m-%d-%H%M)
mv *.csv test-results/$(date +%Y-%m-%d-%H%M)/
```

---

## Documentation Template

Create `docs/manual-test-results-YYYY-MM-DD.md` with findings:

```markdown
# Manual Smoke Test Results - [DATE]

## Test Environment
- Tester: [Your name]
- Date: [YYYY-MM-DD]
- Start time: [HH:MM]
- End time: [HH:MM]
- Deployment URL: [Railway URL]
- Railway environment: [Production/Staging]

## Test Results Summary

### Phase 1: Event Isolation ✅/❌
- Result: [PASS/FAIL]
- Notes: [Details of any issues]

### Phase 2: Location Expiry ✅/❌
- Result: [PASS/FAIL]
- TTL timing: [Actual observed timing]
- Notes: [Details]

### Phase 3: 30-Minute Stability ✅/❌
- Result: [PASS/FAIL]
- Memory: [Stable/Growing - include graph screenshot]
- CPU: [Peak %, average %]
- Notes: [Details]

### Phase 4: Scale Test ✅/❌
- Result: [PASS/FAIL]
- Maximum skaters tested: [Number]
- Notes: [Details]

## Railway Metrics

### Memory Usage
- Start: [MB]
- End: [MB]
- Peak: [MB]
- Trend: [Stable/Growing/Declining]
- Screenshot: [Link or attach]

### CPU Usage
- Average: [%]
- Peak: [%]
- Trend: [Stable/Spiking]

### Request Metrics
- Total requests during test: [Count]
- Error count: [Count]
- Error rate: [%]

## Metrics Analysis

### Skater Update Metrics
```bash
cd tools/load-testing/test-results/[date]/

# Total updates sent
wc -l event-a-stability.csv event-b-stability.csv

# Error rate
grep "error" *.csv | wc -l

# Average response time (requires CSV analysis)
```

**Findings:**
- Total updates sent: [Number]
- Errors encountered: [Number and %]
- Average response time: [ms]
- P95 response time: [ms]

### Viewer Metrics

**Findings:**
- Total messages received: [Number]
- Average latency: [ms]
- P95 latency: [ms]
- Connection drops: [Number]

## Issues Found

### Issue 1: [Title]
- Severity: [Critical/High/Medium/Low]
- Phase: [Which phase]
- Description: [What happened]
- Steps to reproduce:
  1. [Step 1]
  2. [Step 2]
- Expected behaviour: [What should happen]
- Actual behaviour: [What actually happened]
- Screenshots/logs: [Attach relevant evidence]

### Issue 2: [Title]
[Same format as Issue 1]

## Observations

### Positive Findings
- [What worked well]
- [Performance highlights]
- [Stability observations]

### Areas of Concern
- [Performance concerns]
- [Stability concerns]
- [UX issues]

### WebSocket Connection Stability
- WebSocket timeout configuration: [Confirmed from Railway env vars or default 3 minutes]
- Phase 2.7 timeout test result: [PASS/FAIL - connections survived 90s pause]
- Any "connection reset by peer" errors: [Yes/No - if yes, note timing and phase]
- Connection behaviour during Phase 3 (30-min stability): [Stable/Intermittent drops]
- Related: PR #132 fixed default 75-second timeout issue

## Recommendations

- [ ] System ready for automated load testing
- [ ] Issues need addressing before automation
- [ ] Further manual testing recommended

### Immediate Actions Required
1. [Action 1]
2. [Action 2]

### Future Improvements
1. [Improvement 1]
2. [Improvement 2]

## Appendices

### Event IDs Used
- Event A: [UUID]
- Event B: [UUID]

### Metrics Files
- `event-a-skaters.csv` - [Size, row count]
- `event-b-skaters.csv` - [Size, row count]
- `event-a-viewers.csv` - [Size, row count]
- `event-b-viewers.csv` - [Size, row count]
- `event-a-stability.csv` - [Size, row count]
- `event-b-stability.csv` - [Size, row count]

### Screenshots
1. Railway metrics at start - [Link]
2. Railway metrics at 15min - [Link]
3. Railway metrics at 30min - [Link]
4. Browser viewer Event A - [Link]
5. Browser viewer Event B - [Link]
6. Browser console (if errors) - [Link]
```

---

## Quick Reference Commands

### Check Railway Status
```bash
railway status
railway logs --tail 100
```

### Check Health
```bash
curl $RAILWAY_URL/health
```

### View Metrics Files
```bash
cd tools/load-testing
tail -f event-a-skaters.csv      # Watch real-time updates
wc -l *.csv                       # Count total rows
grep -c "\"\"" *.csv              # Count successful requests (empty error column)
```

### Generate Event ID Manually
```bash
uuidgen
```

### Access Railway Dashboard
https://railway.app → Select project → Metrics tab

---

## Notes

### Limitations of Current Tools

1. **No real-time dashboard:** Must check CSV files and Railway metrics separately.

2. **Uniform skater distribution:** All events get same number of skaters; cannot simulate realistic varied event sizes.

### Future Enhancements

- Real-time metrics dashboard
- Variable skater counts per event
- Automated health checks during test runs
