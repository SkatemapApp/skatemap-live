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
│       ├── railway.go       - Railway log checking
│       └── csv.go           - CSV metrics validation
├── test/
│   ├── smoke_test.go              - Test suite setup
│   ├── event_isolation_test.go    - Event isolation test
│   ├── location_expiry_test.go    - Location expiry test
│   ├── websocket_timeout_test.go  - WebSocket timeout test
│   ├── scale_test.go              - Scale test
│   └── stability_test.go          - 30-minute stability test
├── Makefile                 - Build and test commands
└── go.mod                   - Go dependencies
```

## Running Smoke Tests

### Full Suite

```bash
cd tools/load-testing
RAILWAY_URL=<railway-url> go test ./test/... -v -timeout 2h
```

### Single Test

```bash
go test ./test/... -run TestSmokeTestSuite/TestEventIsolation -v
```

Test names:
- `TestEventIsolation`
- `TestLocationExpiry`
- `TestWebSocketTimeout`
- `TestScale`
- `TestStability`

### Skip Long Tests

```bash
go test ./test/... -short -v
```

Skips `TestStability` (30 minutes).

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
- `CheckRailwayLogs(t, pattern, expectFound)`
- `ParseCleanupTime(t) time.Time`
- `DetectCrash(t) bool`

**csv.go:**
- `AssertNoErrors(t, csvPath)`
- `AssertAverageLatency(t, csvPath, maxMs)`
- `CountRecords(t, csvPath) int`

### Constants

Server configuration constants (align with `application.conf`):
- `locationTTL = 30s`
- `cleanupInterval = 10s`

Test timing constants:
- Declared per-test file
- Only timing values are constants, not test parameters

## Adding a New Test

1. Create `test/new_test_name_test.go`:

```go
package test

import (
	"time"
	"github.com/SkatemapApp/skatemap-live/tools/load-testing/internal/testutil"
)

const (
	newTestDuration = 2 * time.Minute
)

func (s *SmokeTestSuite) TestNewFeature() {
	t := s.T()

	skaters := testutil.StartSkaters(t, s.railwayURL, 1, 3, "2s")

	// Test logic...

	testutil.AssertNoErrors(t, skaters.MetricsFile)
}
```

2. Run the test:

```bash
go test ./test/... -run TestSmokeTestSuite/TestNewFeature -v
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

Tests run in GitHub Actions via `.github/workflows/smoke-test.yml`.

Triggered by:
- Manual workflow dispatch
- Daily scheduled run (2 AM UTC)

## Railway Log Parsing

**Current approach:** Parse Railway logs using `railway logs --tail N`

**Limitation:** Brittle, temporary solution

**Future:** Replace with Prometheus metrics (Issue #26)

## Common Tasks

### Build Simulation Binaries

```bash
make build
```

Creates:
- `bin/simulate-skaters`
- `bin/simulate-viewers`

### Run All Tests Locally

```bash
RAILWAY_URL=http://localhost:9000 make smoke-test
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
