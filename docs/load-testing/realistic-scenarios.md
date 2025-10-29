# Realistic Simulation Scenarios

This document defines realistic parameters for load testing simulations based on direct participation in organised skating events and research into GPS tracking applications.

## Parameters

### Concurrent Events

**Value**: 10 events

**Rationale**: In urban areas with active skating communities, multiple organised events can occur simultaneously across different locations. A typical evening might see several regular meet-ups, private group sessions, and organised rides happening at once. Ten concurrent events represents a realistic load scenario for a city-wide skating platform.

### Skaters Per Event

**Value**: Variable, 20-500+ skaters

**Distribution**:
- **Minimum** (20-50 skaters): Small group rides, weekly meet-ups
- **Typical** (100+ skaters): Regular organised events
- **Large** (500+ skaters): Major events like charity rides or holiday skates

**Rationale**: Based on direct participation in London skating events:
- Halloween skate: 2.5 hours with at least 200 skaters
- Good Friday skate: 3 hours
- Santa skate: 500+ skaters

Smaller regular meet-ups attract 20-50 participants, whilst typical organised events see 100+ skaters. Major events can attract 500 or more participants.

### Update Frequency

**Value**: 3-5 seconds per skater

**Rationale**: GPS tracking applications must balance real-time accuracy with battery consumption. Research on location tracking apps indicates:

- Update intervals of 3-5 seconds provide near real-time tracking
- Shorter intervals (1-2 seconds) significantly increase battery drain
- Longer intervals (10+ seconds) compromise tracking accuracy for fast-moving activities
- Location updates consume approximately 2KB per transmission

For skating activities where participants move at 15-25 km/h, 3-5 second intervals provide sufficient granularity to track movements whilst maintaining reasonable battery life for 2-3 hour sessions.

### Session Duration

**Value**: 30-180 minutes

**Distribution**:
- **Short** (30-60 minutes): Quick evening rides, training sessions
- **Medium** (90-120 minutes): Standard organised events
- **Long** (150-180 minutes): Extended rides, charity events

**Rationale**: Based on direct participation in organised skating events:
- Typical organised events run for 90-120 minutes
- Extended events like Halloween skate run for 2.5 hours
- Good Friday skate runs for 3 hours
- Shorter casual sessions typically last 30-60 minutes

Battery life and physical endurance naturally limit most skating sessions to under 3 hours.

### Viewer Count

**Value**: 2-5 viewers per event

**Rationale**:
- Friends and family tracking participants: 1-2 viewers
- Event organisers monitoring progress: 1-2 viewers
- Additional interested parties: 0-1 viewers

Most events will have a small number of viewers relative to participants. Organisers need to monitor event progress, whilst friends and family may track specific skaters. Large events with 500+ skaters might see slightly higher viewer counts, but the ratio remains low compared to active participants.

## Example Simulation Commands

**Note**: Replace `<target-url>` with your deployment URL (e.g., `http://localhost:9000` for local testing, or your production URL).

### Small Event (20 skaters, 45 minutes)

```bash
./bin/simulate-skaters \
  --events=1 \
  --skaters-per-event=20 \
  --update-interval=4s \
  --target-url=<target-url> \
  --metrics-file=small-event.csv
```

Stop after 45 minutes with Ctrl+C.

### Typical Event (100 skaters, 2 hours)

```bash
./bin/simulate-skaters \
  --events=1 \
  --skaters-per-event=100 \
  --update-interval=4s \
  --target-url=<target-url> \
  --metrics-file=typical-event.csv
```

Stop after 2 hours with Ctrl+C.

### Multiple Concurrent Events (10 events, varied sizes)

Simulate a realistic evening with multiple concurrent events:

```bash
./bin/simulate-skaters \
  --events=10 \
  --skaters-per-event=50 \
  --update-interval=4s \
  --target-url=<target-url> \
  --metrics-file=concurrent-events.csv
```

Note: Currently the simulator uses uniform skater counts per event. Future enhancements could support variable sizes per event.

### Large Event with Viewers (200 skaters, 5 viewers)

Start skaters:
```bash
./bin/simulate-skaters \
  --events=1 \
  --skaters-per-event=200 \
  --update-interval=3s \
  --target-url=<target-url> \
  --metrics-file=large-event-skaters.csv
```

In a separate terminal, start viewers (using event ID from skater simulator output):
```bash
./bin/simulate-viewers \
  --viewers-per-event=5 \
  --events=<event-id-from-skater-output> \
  --target-url=<target-url> \
  --metrics-file=large-event-viewers.csv
```

### Extended Load Test (Multiple events, 3 hours)

```bash
./bin/simulate-skaters \
  --events=5 \
  --skaters-per-event=100 \
  --update-interval=5s \
  --target-url=<target-url> \
  --metrics-file=extended-load-test.csv
```

Run for 3 hours to simulate extended events like Good Friday skate.

## Bandwidth Estimation

Based on 2KB per location update:

- **Single skater** (4-second interval): 0.5 KB/s = 1.8 MB/hour
- **20 skaters**: 10 KB/s = 36 MB/hour
- **100 skaters**: 50 KB/s = 180 MB/hour
- **500 skaters**: 250 KB/s = 900 MB/hour

A large 3-hour event with 500 skaters would generate approximately 2.7 GB of location update traffic.

## Acknowledgement

These parameters are plausible approximations based on direct participation in organised skating events and research into GPS tracking applications. They are not scientific measurements but represent realistic usage patterns for load testing purposes.
