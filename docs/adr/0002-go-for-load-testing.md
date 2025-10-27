# ADR 0002: Go for Load Testing Tools

## Status

Accepted

## Context

Skatemap Live requires load testing tools to validate system behaviour under realistic conditions, particularly for extended periods (2-24 hours). The application handles concurrent WebSocket streams and location updates across multiple events, necessitating tools that can simulate:

- Multiple concurrent skaters (50+) sending location updates at regular intervals
- Multiple concurrent WebSocket viewers (15+) maintaining long-lived connections
- Realistic GPS coordinate movement patterns
- 24-hour continuous operation for stability testing
- Performance metrics collection (response times, connection stability)

The load testing tools are development/operations utilities, not production services. They need to be:
- Simple to build and deploy
- Resource-efficient (may run on developer laptops or alongside the application)
- Easy to iterate on during test development
- Stable for long-running tests without manual intervention

## Decision

Implement load testing tools in **Go** rather than Scala, Python, or Bash.

Tools will be located in `tools/load-testing/` directory, separate from the Scala API service in `services/api/`.

## Consequences

### Benefits

**Concurrency Model**
- Goroutines trivially handle 50+ concurrent skaters and 15+ WebSocket connections
- Channels provide simple coordination between concurrent operations
- No complex async/await patterns or event loop management required

**Resource Efficiency**
- Typical memory usage: ~20MB for full simulation (50 skaters + 15 viewers)
- Negligible CPU when idle between update intervals
- Can run alongside application on same machine without resource contention
- Contrast: JVM-based solution would require 256MB+ minimum

**Development Velocity**
- Build time: ~2 seconds (vs 30-40 seconds for SBT)
- Instant startup (vs 5-10 seconds for JVM)
- Fast iteration cycle during test development and debugging
- Single binary deployment (no dependency installation required)

**Operational Simplicity**
- Single static binary, no runtime dependencies
- No JVM, no Python virtual environments, no version managers
- Runs identically on macOS, Linux, Windows
- Simple to deploy to CI runners or cloud instances

**Long-Running Stability**
- Proven stability for 24-hour network operations (gorilla/websocket library)
- Simple reconnection logic for network interruptions
- No garbage collection pauses affecting timing accuracy
- Standard library HTTP client sufficient for requirements

**Independence**
- Separate build system from Scala application
- Upgrading Scala, SBT, or Play Framework doesn't affect load testing tools
- Can be deleted entirely without impacting application
- Clear architectural boundary between services and tooling

### Trade-offs

**Multi-Language Repository**
- Requires Go toolchain in CI alongside Scala/SBT
- Team needs basic Go familiarity (mitigated: straightforward for this use case)
- Separate dependency management (go.mod vs build.sbt)

**Learning Curve**
- Team primarily familiar with Scala
- Go's concurrency model differs from Scala's Futures/Actors
- Mitigation: Load testing code is simple, focused use case for learning Go

**Limited Scala Code Reuse**
- Cannot reuse domain models or validation logic from Scala codebase
- Load testing tools implement their own coordinate validation, UUID generation
- Trade-off accepted: Validation is trivial, duplication is minimal

## Alternatives Considered

### Scala + Akka

**Rejected Reasons:**
- Heavyweight: 50-100MB JAR, 256MB+ JVM memory minimum
- Slow iteration: 30-40 second rebuild cycle, 5-10 second startup
- Overkill: Akka Streams/Actors add unnecessary complexity for this use case
- Tight coupling: Changes to application dependencies could affect load testing tools

**When it would make sense:**
- If load testing required complex Scala domain logic reuse
- If team had no capacity to learn new language
- If application and testing were tightly coupled by design

### Python + asyncio

**Rejected Reasons:**
- 24-hour stability concerns: asyncio event loop complexity, reconnection edge cases
- Dependency management: virtualenv, pip, version conflicts
- Resource usage: ~150MB memory vs Go's ~20MB
- Slower startup and execution compared to compiled Go binary

**When it would make sense:**
- If generating complex graphs/visualisations during tests (matplotlib)
- If team was Python-focused rather than JVM-focused
- If testing duration was hours, not 24+ hours

### Gatling (Scala Load Testing Framework)

**Rejected Reasons:**
- Designed for HTTP load testing, not realistic behaviour simulation
- Cannot easily model "skater moving along GPS path" patterns
- Heavier than needed for simple location update simulation
- Less control over WebSocket reconnection logic

**When it would make sense:**
- If testing pure HTTP throughput rather than behaviour patterns
- If standard load testing scenarios (ramp-up, sustained load) were sufficient
- If team needed pre-built reporting and metrics dashboards

### Bash Scripts + curl

**Rejected Reasons:**
- Cannot handle 50+ concurrent operations cleanly
- No structured data handling for CSV logging
- Fragile for long-running tests (no proper error handling, reconnection)
- No WebSocket support without additional tools

**When it would make sense:**
- Quick one-off manual tests (already have scripts/realtime_updates.sh for this)
- Very simple linear scenarios
- If no programming language experience existed

## Implementation Notes

- Go tools located in `tools/load-testing/` following monorepo structure
- Standard Go project layout: `cmd/` for binaries, `internal/` for shared code
- Minimal external dependencies: primarily standard library + gorilla/websocket
- Future Bazel compatibility: flat directory structure supports build system migration

## References

- Related implementation: [Issue #107](https://github.com/SkatemapApp/skatemap-live/issues/107)
- Monorepo structure: [PR #121](https://github.com/SkatemapApp/skatemap-live/pull/121)
