# ADR 0006: Observability Strategy with OpenTelemetry

## Status
Accepted

## Context

Skatemap Live is a real-time GPS streaming service running as a single JVM instance on Railway PaaS. The system handles multiple concurrent skating events, receiving location updates via REST endpoints and broadcasting them to viewers through WebSockets using Pekko Streams.

### Current State

The system has basic observability through OpenTelemetry auto-instrumentation:
- **Auto-instrumentation deployed:** OpenTelemetry Java agent (v2.24.0) attached via `-javaagent` flag
- **What we get for free:** HTTP request/response traces (route, status, duration), JVM runtime metrics (heap, GC, threads)
- **Export:** OTLP to Honeycomb when configured (optional, controlled by environment variables)
- **OTLP protocol configuration:** Added in PR #212 to support HTTP endpoints (Honeycomb requires `http/protobuf` protocol)

### Operational Gaps

Critical operational questions remain unanswerable:
- **Active system state:** How many events are active? How many locations per event? How many active WebSocket connections?
- **Performance visibility:** What's the publish rate? End-to-end latency from HTTP POST to WebSocket delivery?
- **Resource trends:** Are cleanup services keeping up? Is memory growing unbounded?

These gaps became operationally significant during memory leak investigations, which required heap dump analysis because no domain metrics existed. The agent traces HTTP requests but doesn't expose event count, location count, or hub count.

## Decision

**Implement OpenTelemetry instrumentation for unified observability (traces + metrics), exporting to Honeycomb.**

### Implementation Approach

**Phase 1: Leverage Auto-Instrumentation (Complete)**
- OpenTelemetry Java agent provides HTTP/JVM baseline
- Validates OTLP export pipeline to Honeycomb
- Establishes infrastructure visibility foundation
- OTLP protocol configuration support (PR #212)

**Phase 2: Manual Instrumentation for Business Context (Planned)**
- Add custom spans for business operations:
  - `location.validate` - validation logic
  - `location.store` - InMemoryLocationStore write
  - `broadcast.initiate` - InMemoryBroadcaster publish
- Enrich spans with attributes: `event.id`, `skater.id`, `subscriber.count`
- Build `TracedFuture` utility for Scala Future context propagation

**Phase 3: Application Metrics (Planned)**
- Instrument domain counters/gauges:
  - `events.active` (gauge) - active event count from TrieMap size
  - `locations.received.total` (counter) - location publish rate
  - `websocket.connections.active` (gauge) - active subscriber count per event
  - `broadcast.duration` (histogram) - fan-out timing (aggregate, not per-subscriber)
  - `cleanup.duration` (histogram) - cleanup service timing
- Export via same OTLP pipeline (traces + metrics unified)

### What We Trace vs What We Meter

**Trace (request flows):**
```
HTTP PUT /skatingEvents/{eventId}/skaters/{skaterId}
├─ span: http.server.request (auto-instrumented)
├─ span: location.validate (manual)
├─ span: location.store (manual)
└─ span: broadcast.initiate (manual)
   └─ attributes: event.id, subscriber.count
```

**Meter (volumes and aggregates):**
- Connection counts, throughput rates, latency distributions
- Cleanup service performance
- Per-event active subscriber gauge

**DO NOT trace:**
- Individual WebSocket message deliveries to subscribers
- Synchronous in-memory operations (TrieMap lookups, validation logic)
- Cleanup service iterations (use histogram for aggregate timing)

### Critical Decision: WebSocket Fan-Out Instrumentation

**The problem:** Each location update broadcasts to N subscribers (200+ viewers per event). Should we create a span for each delivery?

**Option A: Trace every delivery**
- 1 location update → 200+ spans (one per subscriber)
- Load test scenario (2 events, 5 skaters, 30 minutes, 200 subscribers): ~2.7M spans
- Hypothetical realistic event (50 skaters, 200 viewers, 2 hours): ~9.5M spans (~47% of monthly quota in single event)
- Blows through Honeycomb free tier in one event

**Option B: Don't trace deliveries (CHOSEN)**
- 1 location update → 3 spans (HTTP, validate, store, broadcast)
- Use metrics histogram for aggregate fan-out duration

**Rationale:**

The fan-out is **synchronous in-memory** via Pekko Streams BroadcastHub:
```scala
subscribers.foreach(_.send(loc))  // This is a for-loop, not distributed operation
```

Per-delivery spans would measure method call overhead (~0.5ms) with no network or I/O variance. Individual delivery timing provides zero actionable insight—if broadcast is slow, the aggregate timing (histogram) tells us enough:

- "Broadcast to 200 subscribers took 100ms" → we have a problem
- We don't need to know which specific subscriber was slow (they all run sequentially)

**When we WOULD trace deliveries:** If broadcast becomes async (Kafka, message queue, true distributed subscribers), then yes—network boundaries introduce real latency variation worth tracing.

## Rationale

### Why OpenTelemetry

**Industry standard:**
- CNCF project, widely adopted
- Portable instrumentation (not locked to Honeycomb—can switch backends via config)
- Strong ecosystem and tooling support

**High-cardinality support:**
- Honeycomb handles `event.id`, `skater.id` as span attributes (Prometheus labels would explode)
- Critical for debugging: "Show me traces WHERE event.id='X' AND error=true"

**Unified traces + metrics:**
- Single SDK, single export pipeline (OTLP)
- Correlated data in Honeycomb (query both in same UI)

**Integration challenges accepted:**
- OTel SDK integration with Play Framework/Scala (not just Spring Boot auto-config)
- Context propagation through Scala Futures (ThreadLocal challenge)
- Manual instrumentation required for business context

### Why Honeycomb (vs Prometheus/Zipkin)

**No operational overhead:**
- SaaS, not self-hosted (aligns with Railway deployment philosophy)
- Free tier sufficient for current scale (with smart instrumentation)

**Superior querying:**
- High-cardinality exploration ("show me P95 latency WHERE event.id='X' AND skater.id='Y'")
- BubbleUp, heatmaps for debugging (vs PromQL time-series aggregation)

**Unified backend:**
- Traces AND metrics in one place (vs Prometheus for metrics + Zipkin for traces)
- Single query language, single UI

### Why Not Prometheus

Prometheus came up as a cost-optimisation strategy (free, self-hosted). Here's why we're not using it:

**Cost isn't a concern yet:**
- Current scale fits within Honeycomb free tier (20M events/month)
- When quota becomes a concern, we'll re-evaluate (not prematurely optimise)

**Operational overhead:**
- Requires self-hosting (Prometheus server, Grafana, storage management)
- Contradicts Railway deployment philosophy (SaaS, minimal maintenance)

**Different use case:**
- Prometheus excels at: infrastructure metrics, low-cardinality aggregation, cost-conscious production
- Our need: high-cardinality debugging, trace correlation, exploratory analysis

**Future flexibility:**
- OTel instrumentation is portable—can dual-export later (Prometheus for volume, Honeycomb for debugging)
- Not locked into Honeycomb; OTLP works with any compatible backend

## Consequences

### What We Gain

**Operational visibility:**
- Answer: "How many active events?" (gauge metric)
- Answer: "Why is this request slow?" (distributed trace)
- Answer: "What's P95 latency for event X?" (Honeycomb query)

**Debugging capability:**
- Trace request flow: HTTP → validate → store → broadcast
- Correlate slow requests with specific events/skaters
- Identify bottlenecks without heap dumps

**Portable instrumentation:**
- OTel SDK not tied to Honeycomb (can switch to Datadog, Grafana Cloud, etc.)
- Standard semantic conventions (not vendor-specific)

### What We Lose / Accept

**Integration complexity:**
- Play Framework lacks first-class OTel support (vs Spring Boot auto-config)
- Manual context propagation through Scala Futures (ThreadLocal challenge)
- More integration work than Kamon (Scala-native observability library)

**Free tier limits:**
- 20M events/month ceiling (requires sampling if exceeded)
- 60-day retention (vs unlimited with self-hosted Prometheus)

**Incomplete visibility (initially):**
- Auto-instrumentation alone misses business context
- Manual instrumentation required for domain metrics
- End-to-end WebSocket latency not measured (viewer simulation exists but not instrumented)

**Silent degradation risk:**
- Misconfigured OTLP endpoint → telemetry lost with no application error
- Must verify trace ingestion in Honeycomb post-deployment

### Technical Challenges

**Scala Future context propagation:**

OTel context is ThreadLocal—doesn't survive Scala Future boundaries. Requires explicit capture/restore:
```scala
// Pattern: TracedFuture utility
object TracedFuture {
  def traced[A](name: String)(block: => Future[A])
                (implicit tracer: Tracer, ec: ExecutionContext): Future[A] = {
    val context = Context.current()
    val span = tracer.spanBuilder(name).startSpan()

    Future { context.makeCurrent() }
      .flatMap { _ => block }
      .andThen {
        case Success(_) => span.end()
        case Failure(e) =>
          span.recordException(e)
          span.end()
      }
  }
}
```

**Memory overhead:**
- Per-event hub buffers (128 × ~48 bytes per event)
- Span storage in Honeycomb counts against quota
- Metrics data points also count as events

**Configuration validation:**
- OTEL environment variables optional (agent loads but exports nothing if unset)
- No startup failure if export misconfigured
- Requires post-deployment verification

## Alternatives Considered

### Kamon (Scala-Native Observability)

**Pros:**
- Built for Pekko/Play from the ground up
- Context propagation works automatically (no ThreadLocal pain)
- Smaller learning curve for Scala developers

**Cons:**
- Less portable (Kamon-specific, smaller ecosystem)
- Less industry adoption vs OTel
- Smaller community and tooling ecosystem

**Decision:** OTel chosen for portability and industry standardisation, despite harder integration.

### Cinnamon (Lightbend Telemetry)

**Pros:**
- Deep Pekko instrumentation (actor metrics, stream backpressure)
- Built-in exporters (Prometheus, Datadog)
- Auto-instrumentation for Pekko primitives

**Cons:**
- **Commercial product** (requires Lightbend subscription, not free)
- Vendor lock-in (proprietary instrumentation)
- Overkill for simple stream topology (queue → broadcast hub)
- Less portable than OTel
- Originally built for Akka (Pekko support may lag)

**Decision:** Rejected due to cost and vendor lock-in. OTel provides sufficient visibility for our streaming architecture without Pekko-specific actor debugging.

### Micrometer + Prometheus

**Pros:**
- Free (self-hosted Prometheus)
- Spring-native (zero-config for basic metrics)
- Proven at scale

**Cons:**
- No distributed tracing (need separate tool: Zipkin/Jaeger)
- High-cardinality limitations (can't use event IDs as labels safely)
- Requires self-hosting (contradicts "no maintenance" goal)

**Decision:** Rejected due to operational overhead and lack of unified traces + metrics.

### Grafana Cloud (Managed Prometheus)

**Pros:**
- Free tier (10K series, 50GB logs, 14-day retention)
- Managed Prometheus + Grafana dashboards
- No self-hosting overhead
- OTel can export metrics via OTLP

**Cons:**
- Cardinality limits (10K series on free tier)
- Event IDs as metric labels would hit limits quickly
- Separate trace backend needed (Tempo included but separate quota)
- Less flexible querying than Honeycomb for high-cardinality exploration

**Decision:** Rejected in favour of Honeycomb. While Grafana Cloud eliminates self-hosting concerns, it retains Prometheus's cardinality constraints. Honeycomb's architecture better suits our high-cardinality use case (event.id, skater.id as attributes). If quota becomes a concern, Grafana Cloud would be the fallback before self-hosting Prometheus.

**When to reconsider:** If Honeycomb quota is exceeded (>20M events/month) or ongoing costs become prohibitive, Grafana Cloud is the logical fallback before self-hosting Prometheus.

### Custom Instrumentation

**Pros:**
- Complete control
- Zero dependencies

**Cons:**
- Reinventing the wheel
- No ecosystem/tooling
- Maintenance burden

**Decision:** Not seriously considered—focus is on using industry-standard tools.

### Hybrid (OTel Traces + Prometheus Metrics)

**Approach:** Dual-export via OTel Collector—traces to Honeycomb, metrics to Prometheus.

**Pros:**
- Saves Honeycomb quota (metrics don't count against it)
- Free metrics storage (Prometheus)

**Cons:**
- Two backends to manage (Honeycomb + Prometheus)
- Requires running OTel Collector (adds complexity)
- Splits observability (queries across two systems)

**Decision:** Rejected for now. Keep it simple (Honeycomb-only). Revisit if free tier becomes limiting.

## Comparison Matrix

| Factor | Honeycomb | Grafana Cloud | Prometheus (Self-Hosted) | Hybrid (HC+Prom) |
|--------|-----------|---------------|--------------------------|------------------|
| **Setup Time** | < 1 hour | < 1 hour | 2-4 hours | 4-6 hours |
| **Operational Overhead** | None (SaaS) | None (SaaS) | High (self-host) | Medium (Collector) |
| **High-Cardinality Support** | Excellent | Limited (10K series) | Poor (label explosion) | Mixed |
| **Free Tier** | 20M events/month, 60d retention | 10K series, 14d retention | Unlimited (self-host) | Varies |
| **Traces + Metrics Unified** | Yes (single backend) | Separate quotas (Tempo) | No (need Zipkin/Jaeger) | No (split backends) |
| **Query Flexibility** | Excellent (BubbleUp, heatmaps) | Good (PromQL, Grafana) | Good (PromQL) | Good |
| **Cardinality Limits** | None (event.id OK) | 10K series (event.id not viable) | Practical limits | Depends on backend |
| **Cost (Demo)** | $0 | $0 | $0 (own infra) | $0 |
| **Cost Scaling** | Paid tiers beyond 20M | Paid tiers beyond 10K | Infrastructure costs | Infrastructure + paid |
| **Integration Complexity** | Low (OTLP direct) | Low (OTLP direct) | Medium (exporters) | High (Collector setup) |
| **Best For** | High-cardinality debugging | Dashboard-first teams | Cost-conscious production | Volume + deep debug |

## Open Questions

### Sampling Strategy

**Question:** If load testing exceeds free tier, what sampling rate?

**Options:**
- **Head-based sampling:** 10% of all traces (configured in SDK)
  - Pro: Simple configuration
  - Con: Miss rare errors (1% failure rate → might never see them)

- **Tail-based sampling:** Keep 100% of errors, sample successes
  - Pro: Better error visibility
  - Con: Requires OTel Collector (contradicts "no local infra")

**Approach:** Start 100%, measure event consumption during load test, implement head-based if needed. Document trade-offs.

### Metrics Cardinality

**Question:** Should `event.id` be a metric label or span attribute?

**Decision:**
- **Span attributes:** Yes (high cardinality OK in Honeycomb traces)
- **Metric labels:** No (use aggregate metrics without event.id label)

**Rationale:** Traces answer "what happened to this specific event", metrics answer "what's overall system health". Honeycomb handles high-cardinality attributes, but metric labels should stay low-cardinality for efficient aggregation.

### End-to-End Latency Measurement

**Current gap:** Time from HTTP PUT to WebSocket delivery not measured.

**Approach:** Load testing infrastructure includes viewer simulation with reception timestamps—instrument this path to validate delivery latency.

**Status:** Not yet prioritised.

## Implementation Notes

### Phase 2: Manual Instrumentation Tasks

1. **Inject OTel SDK into Play App**
   - Add OTel SDK dependencies (Scala/Java interop):
   ```scala
   // build.sbt
   libraryDependencies ++= Seq(
     "io.opentelemetry" % "opentelemetry-api" % "1.32.0",
     "io.opentelemetry" % "opentelemetry-sdk" % "1.32.0"
   )
   ```
   - Configure `GlobalOpenTelemetry` or DI-based Tracer
   - Verify SDK coexists with agent

2. **Build TracedFuture Utility**
   - Context capture/restore wrapper for Scala Futures (see Technical Challenges section for implementation pattern)
   - Test with simple async operation
   - Verify parent-child span relationship

3. **Instrument Controller Layer**
   - Enrich HTTP span with `event.id`, `skater.id` attributes
   - Handle Play's `Action.async` context

4. **Instrument Service Layer**
   - Wrap `LocationService.process()` in span
   - Add validation span
   - Store operation span (prepares for future DB integration)

5. **Instrument Broadcast Initiation**
   - Span for `InMemoryBroadcaster.publish()`
   - Attributes: `event.id`, `subscriber.count`
   - Stop before fan-out (don't trace individual deliveries)

### Phase 3: Metrics Tasks

1. **Define Metrics**
   - Counter: `locations.received.total`
   - Gauge: `events.active` (TrieMap size)
   - Gauge: `websocket.connections.active`
   - Histogram: `broadcast.duration`
   - Histogram: `cleanup.duration`

2. **Implement Metrics**
   - Use OTel Metrics SDK (same export pipeline)
   - Inject `Meter` into services
   - Instrument at key points (controller, broadcast, cleanup)

3. **Verify Export**
   - Metrics appear in Honeycomb
   - Sparse gauges (every 60s) vs dense traces

### Load Test Validation

**Scenario:** 2 events, 5 skaters per event, 30 minutes (current stability test)

**Measure:**
- Honeycomb event consumption (spans + metric data points)
- Calculate: "At this rate, how many events per month?"
- Verify: "Does this fit within 20M free tier?"

**Decision point:** If exceeds quota, implement sampling. Document trade-offs.

## Success Criteria

**Operational questions answered:**
- "How many active events?" → `events.active` gauge
- "What's P95 latency?" → Honeycomb query on HTTP spans
- "Why was this request slow?" → Distributed trace showing timing breakdown

**No heap dumps required for:**
- Detecting memory leaks (unbounded gauge growth)
- Understanding system state (event/hub counts visible)

**Documentation complete:**
- ADR captures rationale with data-driven decisions
- TracedFuture pattern documented for Play/Scala integration
- Load test results inform sampling decision

## References

- [System Design Documentation](../system-design/system-design.md)
- [ADR 0001: Railway Platform Choice](0001-railway-platform-choice.md)
- [OpenTelemetry Documentation](https://opentelemetry.io/docs/)
- [Honeycomb Documentation](https://docs.honeycomb.io/)
- [PR #212: OTLP Protocol Configuration Support](https://github.com/SkatemapApp/skatemap-live/pull/212)
