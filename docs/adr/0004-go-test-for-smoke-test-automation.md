# ADR 0004: Go Test Framework for Smoke Test Automation

## Status

Accepted

## Context

Skatemap Live requires automated smoke tests to validate deployment behaviour before releasing to production. Manual smoke testing (#110) successfully validated system functionality and identified critical issues (#138, #139, #136), but requires 1.5 hours of manual work per validation:

- Juggling 4-5 terminal windows
- Manually copying Event IDs between terminals
- Manually checking Railway dashboard every 5 minutes during 30-minute stability tests
- Manually parsing CSV metrics and archiving results

The smoke tests validate behaviour across 5 phases:
- Event isolation (Event A data doesn't leak to Event B)
- Location expiry (cleanup happens after 30s TTL)
- WebSocket timeout (connections survive 95s idle)
- 30-minute stability (no memory leaks or crashes)
- Scale handling (system handles increased load)

The key technical requirement is **test orchestration**: starting the existing `simulate-skaters` and `simulate-viewers` binaries as subprocesses, capturing Event IDs from stdout, passing them between processes, stopping/restarting with same IDs, and validating results from CSV metrics and Railway logs.

ADR 0002 established Go for load testing tools. This ADR addresses how to orchestrate those tools in automated tests.

## Decision

Implement smoke test automation using **`go test` with Testify suite** for test orchestration.

Tests located in `tools/load-testing/test/`, helpers in `tools/load-testing/internal/testutil/`.

## Rationale

### Test Orchestration, Not Load Generation

The existing tools (`simulate-skaters`, `simulate-viewers`) already generate load per ADR 0002. The automation requirement is orchestrating those tools: start subprocess, parse Event ID from stdout, pass to next subprocess, validate results.

### Custom CLI vs `go test`

**Custom orchestrator approach:**
- ~1000 lines of code (CLI, orchestrator, reporter, lifecycle management)
- Reinvents test framework features (setup, teardown, cleanup, reporting)
- Users learn custom CLI instead of standard tooling

**`go test` approach:**
- ~500 lines of code (helpers + tests)
- Built-in lifecycle: `SetupSuite`, `TearDownTest`, `t.Cleanup()`
- Standard tooling: `-run`, `-timeout`, `-json`, `-v`, `-short`
- Familiar to Go developers

`go test` provides everything needed without rebuilding it.

### Subprocess Orchestration is Native

Go standard library handles the required workflow naturally:

```go
cmd := exec.Command("./bin/simulate-skaters", "--events=1")
stdout, _ := cmd.StdoutPipe()
cmd.Start()

// Parse Event ID from stdout
eventID := extractEventID(bufio.NewScanner(stdout))

// Auto-cleanup
t.Cleanup(func() {
    cmd.Process.Signal(syscall.SIGTERM)
})
```

Starting processes, parsing output, signal handling, cleanup - all native.

### Testify vs Pure `go test`

**Pure `go test`:**
```go
if actual != expected {
    t.Errorf("Expected %v, got %v", expected, actual)
}
```

**Testify:**
```go
assert.Equal(t, expected, actual)
```

**Suite lifecycle:**
- Pure `go test`: Manual `TestMain` setup/teardown
- Testify: `SetupSuite()`, `TearDownTest()` methods

**Trade-off:** Adds one dependency (Testify) for cleaner test code and suite lifecycle. Testify is the de facto standard (used by Kubernetes, Docker, Terraform), MIT licensed, actively maintained.

## Alternatives Considered

### Custom CLI Orchestrator

**Structure:** `tools/smoke-testing/cmd/smoke-test/main.go`

**Rejected Reasons:**
- Reinvents test framework lifecycle (setup, teardown, assertions, reporting)
- More code to maintain (~1000 lines vs ~500 lines)
- Custom CLI vs standard `go test`
- Custom JSON reporter vs built-in `-json` flag

**When it would make sense:**
- Need very custom behaviour that test frameworks can't provide
- Non-test orchestration use case

### Pure `go test` (No Testify)

**Rejected Reasons:**
- More verbose assertions
- Manual suite setup/teardown with `TestMain`
- More boilerplate code

**Trade-off accepted:** Small dependency (Testify) for cleaner code.

**When it would make sense:**
- Zero-dependency requirement
- Testify license incompatible (it's MIT, so not applicable)

### Load Testing Tools (Locust/k6/Gatling)

See ADR 0002 for rationale on Go vs load testing frameworks. Those tools generate load; we need to orchestrate existing Go binaries. Would require external state sharing (files/Redis) for Event ID passing and don't support subprocess management natively.

## Consequences

### Benefits

**Standard Tooling**
- Run tests: `go test ./test/... -v`
- Run single test: `go test -run TestEventIsolation`
- JSON output: `go test -json`
- Timeout control: `go test -timeout 2h`
- Skip long tests: `go test -short`

**Subprocess Orchestration**
- `exec.Command()` native in Go standard library
- `t.Cleanup()` automatic resource cleanup
- Stdout/stderr piping with `cmd.StdoutPipe()`
- Signal handling (`SIGTERM`) built-in

**Less Code to Maintain**
- ~500 lines (helpers + tests) vs ~1000 lines (custom orchestrator)
- No custom CLI, reporter, or lifecycle management
- Test helpers can be unit tested themselves

**CI Integration**
- GitHub Actions has built-in Go support
- Same `go test` command works locally and in CI
- Standard test result formats

**Minimal Dependencies**
- Only adds Testify (`github.com/stretchr/testify`)
- Widely used, MIT licensed, actively maintained

### Trade-offs

**Not a Load Testing Tool**
- Cannot generate distributed load across machines
- No built-in ramp-up patterns or percentile reporting
- Trade-off accepted: We have `simulate-skaters` for load generation per ADR 0002

**One Dependency**
- Adds Testify to test code
- Trade-off accepted: Benefits (cleaner code, suite lifecycle) outweigh cost

**Railway Log Parsing**
- Currently parses Railway logs for cleanup validation and crash detection
- Brittle approach, temporary solution
- Will be replaced by Prometheus metrics (Issue #26)

## Implementation Notes

- Tests located in `tools/load-testing/test/`
- Helper packages in `tools/load-testing/internal/testutil/`
- Standard Go project layout
- Minimum Go version: 1.21 (matches existing tools per ADR 0002)

## References

- Predecessor ADR: [ADR 0002: Go for Load Testing Tools](0002-go-for-load-testing.md)
- Manual smoke test plan: `docs/testing/manual-smoke-test-plan.md`
- Related implementation: [Issue #141](https://github.com/SkatemapApp/skatemap-live/issues/141)
- Future enhancement: [Issue #26: Prometheus Metrics](https://github.com/SkatemapApp/skatemap-live/issues/26)
