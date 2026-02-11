# Load Testing Tools - Claude Code Guide

## Project Structure

```
tools/load-testing/
├── cmd/
│   ├── simulate-skaters/    - Skater simulation binary
│   └── simulate-viewers/    - Viewer simulation binary
├── internal/
│   ├── skater/              - Skater update logic
│   ├── viewer/              - WebSocket viewer logic
│   ├── metrics/             - CSV metrics writers
│   └── testutil/            - Test helpers
│       ├── subprocess.go    - Process lifecycle management
│       ├── railway.go       - Crash detection
│       └── csv.go           - CSV metrics validation
├── test/
│   ├── smoke_test.go              - Test suite setup
│   ├── event_isolation_test.go    - Event isolation smoke test
│   ├── location_expiry_test.go    - Location expiry smoke test
│   ├── websocket_timeout_test.go  - WebSocket timeout smoke test
│   ├── scale_test.go              - Scale load test (build tag: load)
│   └── stability_test.go          - Stability load test (build tag: load)
├── Makefile                 - Build and test commands
└── go.mod                   - Go dependencies
```

## Smoke Tests vs Load Tests

### Smoke Tests

Fast validation tests that verify basic functionality. Run daily in CI.

**Location:** `test/*.go` (without build tags)
**Duration:** 2-5 minutes total
**Parameters:** 1 skater per event, 15s duration
**CI Schedule:** Daily at 2 AM UTC (10-minute timeout)

**Tests:**
- `TestEventIsolation` - Event isolation verification
- `TestLocationExpiry` - Location cleanup verification
- `TestWebSocketTimeout` - WebSocket idle timeout verification

### Load Tests

Extended stability and performance tests. Run weekly in CI.

**Location:** `test/*.go` (with `//go:build load` tag)
**Duration:** 30+ minutes
**Parameters:** 5 skaters per event, 30-minute duration
**Build Tag:** `//go:build load` (requires `-tags load` flag to run)
**CI Schedule:** Weekly (Sunday 2 AM UTC, 2-hour timeout)

**Tests:**
- `TestScale` - Phased scaling pattern
- `TestStability` - 30-minute soak test

**Note:** All tests are in the same `test/` directory. Load tests use `//go:build load` tags to be excluded from default runs.

## Running Tests Locally

### Run Smoke Tests Only

```bash
cd tools/load-testing
RAILWAY_URL=<railway-url> go test ./test/... -v -timeout 10m
```

### Run Load Tests Only

```bash
cd tools/load-testing
RAILWAY_URL=<railway-url> go test -tags load ./test/... -v -timeout 2h
```

### Run All Tests (Smoke + Load)

```bash
cd tools/load-testing
RAILWAY_URL=<railway-url> go test -tags load ./test/... -v -timeout 2h
```

### Run Single Test

```bash
go test ./test/... -run TestSmokeTestSuite/TestEventIsolation -v
```

## Test Structure

All tests extend `SmokeTestSuite` which:
- Validates `RAILWAY_URL` environment variable
- Checks `/health` endpoint before running tests
- Uses Testify suite for lifecycle management

### Test Helpers

**subprocess.go:**
- `StartSkaters(t, url, events, skatersPerEvent, interval) *Process`
- `StartSkatersWithEventID(t, url, eventID, skatersPerEvent, interval) *Process`
- `StartViewers(t, url, eventIDs) *Process`
- `(*Process).Stop(t)`

**railway.go:**
- `DetectCrash(t) bool`

**csv.go:**
- `AssertNoErrors(t, csvPath)`
- `CountRecords(t, csvPath) int`
- `ExtractSkaterIDs(t, viewerCSVPath) map[string]bool`

### Constants

Server configuration constants (align with `application.conf`):
- `locationTTL = 30s`
- `cleanupInterval = 10s`

Test timing constants:
- Declared per-test file
- Only timing values are constants, not test parameters

## Test Organisation

### Build Tags for Test Separation

All tests are in the `test/` directory. Load tests use build tags for separation:

**Smoke tests** (no build tag):
- Functional tests validating specific behaviours
- Quick smoke tests (<5 minutes)
- Suitable for frequent CI execution
- Run by default: `go test ./test/...`

**Load tests** (tagged with `//go:build load`):
- Long-running (>5 minutes)
- Resource-intensive (high CPU/memory usage)
- Performance or endurance focused (scale, stability, soak tests)
- Not suitable for regular CI runs
- Excluded by default: `go test ./test/...` skips them
- Explicitly run with: `go test -tags load ./test/...`

## Adding a New Test

### Adding a Smoke Test

1. Create `test/new_test_name_test.go`:

```go
package test

import (
	"time"
	"github.com/SkatemapApp/skatemap-live/tools/load-testing/internal/testutil"
)

const (
	newTestDuration = 15 * time.Second
)

func (s *SmokeTestSuite) TestNewFeature() {
	t := s.T()

	skaters := testutil.StartSkaters(t, s.railwayURL, 1, 1, "2s")

	time.Sleep(newTestDuration)

	skaters.Stop(t)
	testutil.AssertNoErrors(t, skaters.MetricsFile)
}
```

2. Run the test:

```bash
go test ./test/... -run TestSmokeTestSuite/TestNewFeature -v
```

### Adding a Load Test

1. Create `test/new_load_test.go`:

```go
//go:build load
// +build load

package test

import (
	"time"
	"github.com/SkatemapApp/skatemap-live/tools/load-testing/internal/testutil"
)

const (
	loadTestDuration = 30 * time.Minute
)

func (s *SmokeTestSuite) TestNewLoadScenario() {
	t := s.T()

	skaters := testutil.StartSkaters(t, s.railwayURL, 1, 5, "3s")

	time.Sleep(loadTestDuration)

	skaters.Stop(t)
	testutil.AssertNoErrors(t, skaters.MetricsFile)
}
```

2. Run the test:

```bash
go test -tags load ./test/... -run TestSmokeTestSuite/TestNewLoadScenario -v
```

## Debugging Test Failures

### Check Railway Logs

```bash
railway logs --tail 100
```

### Inspect CSV Metrics

Test helpers write CSV files to `t.TempDir()`. Check test output for file paths.

### Run Single Test with Verbose Output

```bash
go test ./test/... -run TestEventIsolation -v
```

### Check Process Lifecycle

Subprocesses are automatically cleaned up via `t.Cleanup()`. If tests hang, check for:
- Missing `defer process.Stop(t)` calls
- Processes not responding to SIGTERM

## CI Integration

### Smoke Test Workflow

**File:** `.github/workflows/smoke-test.yml`
**Schedule:** Daily at 2 AM UTC
**Timeout:** 10 minutes
**Command:** `go test ./test/... -v -timeout 10m`

Runs smoke tests (event isolation, location expiry, websocket timeout) to verify basic functionality daily.

### Load Test Workflow

**File:** `.github/workflows/load-test.yml`
**Schedule:** Weekly (Sunday 2 AM UTC)
**Timeout:** 2 hours
**Command:** `go test -tags load ./test/... -v -timeout 2h`

Runs load tests (scale, stability) to validate system behaviour under sustained load weekly. The `-tags load` flag enables tests with `//go:build load` tags.

### Manual Trigger

Both workflows support manual dispatch with custom `target_url` input:

```bash
gh workflow run smoke-test.yml --repo SkatemapApp/skatemap-live -f target_url=https://custom-url.railway.app
gh workflow run load-test.yml --repo SkatemapApp/skatemap-live -f target_url=https://custom-url.railway.app
```

## Railway Integration

**Crash detection:** `DetectCrash()` optionally fetches Railway logs to check for crash indicators (OOM, exit codes). If Railway CLI fails, it logs a warning and returns false.

**Note:** Tests verify observable behaviour, not internal logs. Log parsing is brittle (logs can change anytime) and has no contract.

## Common Tasks

### Build Simulation Binaries

```bash
make build
```

Creates:
- `bin/simulate-skaters`
- `bin/simulate-viewers`

### Run Smoke Tests Locally

```bash
RAILWAY_URL=http://localhost:9000 go test ./test/... -v -timeout 10m
```

### Run Load Tests Locally

```bash
RAILWAY_URL=http://localhost:9000 go test -tags load ./test/... -v -timeout 2h
```

### Update Test Timing

Edit constants at top of test files. Example:

```go
const (
	locationTTL = 30 * time.Second  // Matches server config
	testDuration = 45 * time.Second // Test-specific timing
)
```

**Rule:** Only extract timing constants, not test scenario parameters (skater counts, intervals).

## Prerequisites

- Railway CLI: `curl -fsSL https://railway.app/install.sh | sh`
- Railway auth: `railway login` or `RAILWAY_TOKEN` env var
- Go 1.21+
- Binaries built: `make build`

## Troubleshooting

**"railway: command not found"**
- Install Railway CLI

**Test timeout**
- Increase: `go test -timeout 3h`
- Or skip long tests: `go test -short`

**Subprocess errors**
- Build binaries first: `make build`
- Check `bin/` directory exists

**Railway authentication failed**
- Run `railway login`
- Or set `RAILWAY_TOKEN` environment variable
