# Test Parameters and Thresholds

This document defines parameter values and timeout thresholds for smoke tests vs load tests.

## Test Classification

### Smoke Tests

Daily validation that verifies basic functionality. Asks "does this work?" not "does this keep working?"

**Run frequency**: Daily (or per-deployment)
**Total suite duration**: 2-5 minutes
**Individual test duration**: 10-30 seconds (up to 60 seconds for expiry waits)

### Load Tests

Extended validation for stability and performance. Verifies system behaviour under sustained load over time.

**Run frequency**: Weekly or on-demand
**Total suite duration**: 30+ minutes

## Current Parameters

The existing test suite (`tools/load-testing/test/`) currently uses load test parameters for all tests:

- **Skaters per event**: 5
- **Update interval**: 3 seconds
- **Duration**: 30 minutes (TestStability)
- **Timeout**: 2 hours (CI workflow)

## Smoke Test Parameters

Parameters for fast, daily smoke tests:

- **Skaters per event**: 1-2
- **Update interval**: 3 seconds
- **Duration**: 10-15 seconds of streaming
- **Individual test timeout**: 5-10 minutes
- **Suite timeout**: 15-20 minutes

**Rationale**: Verify functionality, not capacity. Just long enough to observe one or two update cycles. The skater count and update interval aren't problematic—it's the durations that distinguish smoke from load tests.

## Load Test Parameters

Parameters for weekly or on-demand stability and scale validation:

- **Skaters per event**: 5
- **Update interval**: 3 seconds
- **Duration**: 30 minutes (TestStability)
- **Suite timeout**: 2 hours

**Rationale**: Validate system stability under sustained load. Detect memory leaks and performance degradation over time.

## Test Categorisation

### Smoke Tests
- Health endpoint check
- Event isolation (create event, verify isolation, tear down)
- Location expiry (short verification that mechanism triggers)
- WebSocket timeout (verify connections survive idle period)

### Load Tests
- TestStability (30-minute soak test)
- TestScale (phased scaling pattern)

## References

- Manual smoke test plan: `manual-smoke-test-plan.md`
- Realistic load scenarios: `realistic-scenarios.md`
- ADR 0004: Go test framework: `docs/adr/0004-go-test-for-smoke-test-automation.md`
- Current test implementation: `tools/load-testing/test/`
