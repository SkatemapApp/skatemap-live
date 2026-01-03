# Load Testing Tools

Go-based load testing tools for Skatemap Live. These tools simulate realistic skater behaviour to validate system performance under load.

## Requirements

- Go 1.21 or later

## Building

From the repository root:

```bash
go build -o bin/simulate-skaters ./tools/load-testing/cmd/simulate-skaters
go build -o bin/simulate-viewers ./tools/load-testing/cmd/simulate-viewers
```

Or from the `tools/load-testing` directory:

```bash
go build -o bin/simulate-skaters ./cmd/simulate-skaters
go build -o bin/simulate-viewers ./cmd/simulate-viewers
```

## simulate-skaters

Simulates multiple concurrent skaters sending location updates to the API.

### Usage

```bash
./bin/simulate-skaters \
  --events=5 \
  --skaters-per-event=10 \
  --update-interval=3s \
  --target-url=https://skatemap-live-production.up.railway.app \
  --metrics-file=metrics.csv
```

### Options

- `--events`: Number of events to simulate (default: 1)
- `--skaters-per-event`: Number of skaters per event (default: 10)
- `--update-interval`: Interval between location updates, e.g., "3s", "1m" (default: 3s)
- `--target-url`: Target URL for the API (required)
- `--metrics-file`: Output file for metrics (default: metrics.csv)

### Examples

Basic test with 10 skaters on a single event:

```bash
./bin/simulate-skaters \
  --events=1 \
  --skaters-per-event=10 \
  --update-interval=3s \
  --target-url=https://skatemap-live-production.up.railway.app
```

Extended load test (50 skaters across 5 events, 24-hour run):

```bash
./bin/simulate-skaters \
  --events=5 \
  --skaters-per-event=10 \
  --update-interval=5s \
  --target-url=https://skatemap-live-production.up.railway.app \
  --metrics-file=load-test-24h.csv
```

### Output

The tool generates a CSV file with the following columns:

- `timestamp`: ISO 8601 timestamp of the request
- `event_id`: Event UUID
- `skater_id`: Skater UUID
- `response_time_ms`: Response time in milliseconds
- `error`: Error message (empty if successful)

### Behaviour

- Generates random UUIDs for event IDs
- Spawns concurrent goroutines for each skater
- Each skater:
  - Starts at a random location near London (51.5074°N, 0.1278°W)
  - Moves by small random increments each update (~10m)
  - Sends location updates at the specified interval
  - Sends coordinates as `{"coordinates": [longitude, latitude]}`
- Runs until interrupted with Ctrl+C
- Gracefully shuts down, flushing all metrics to the CSV file

### Performance

Typical resource usage (50 skaters, 3s interval):
- Memory: ~20MB
- CPU: Negligible when idle between updates
- Network: ~2KB per update

## simulate-viewers

Simulates multiple concurrent viewers receiving location updates via WebSocket connections.

### Usage

```bash
./bin/simulate-viewers \
  --viewers-per-event=3 \
  --events=event-1,event-2 \
  --target-url=https://skatemap-live-production.up.railway.app \
  --metrics-file=viewer-metrics.csv
```

### Options

- `--viewers-per-event`: Number of viewers per event (default: 1)
- `--events`: Comma-separated list of event IDs (required)
- `--target-url`: Target URL for the API (required)
- `--metrics-file`: Output file for metrics (default: viewer-metrics.csv)

### Examples

Basic test with 3 viewers on 2 events:

```bash
./bin/simulate-viewers \
  --viewers-per-event=3 \
  --events=123e4567-e89b-12d3-a456-426614174000,123e4567-e89b-12d3-a456-426614174001 \
  --target-url=https://skatemap-live-production.up.railway.app
```

Load test with 10 viewers per event across 5 events:

```bash
./bin/simulate-viewers \
  --viewers-per-event=10 \
  --events=event-1,event-2,event-3,event-4,event-5 \
  --target-url=https://skatemap-live-production.up.railway.app \
  --metrics-file=viewer-load-test.csv
```

### Output

The tool generates a CSV file with the following columns:

- `timestamp`: ISO 8601 timestamp of the message receipt
- `event_id`: Event ID being monitored
- `viewer_number`: Viewer number (sequential across all events)
- `message_count`: Cumulative count of messages received by this viewer
- `latency_ms`: Latency in milliseconds (receive time - server time)
- `error`: Error message (empty if successful)

### Behaviour

- Opens WebSocket connections to specified event streams
- Receives batched location updates as JSON
- Each viewer:
  - Maintains a persistent WebSocket connection
  - Receives only updates for their specific event
  - Tracks message count and latency for each batch
  - Records metrics for every received message
- Runs until interrupted with Ctrl+C
- Gracefully closes all connections and flushes metrics

### Performance

Typical resource usage (50 viewers):
- Memory: ~30MB
- CPU: Negligible
- Network: ~2KB per message batch

## Architecture

```
tools/load-testing/
├── cmd/
│   ├── simulate-skaters/    # Skater simulation CLI
│   │   └── main.go
│   └── simulate-viewers/    # Viewer simulation CLI
│       └── main.go
├── internal/
│   ├── skater/              # Skater simulation logic
│   │   └── skater.go        # Location updates, GPS movement
│   ├── viewer/              # Viewer simulation logic
│   │   └── viewer.go        # WebSocket connections, message receiving
│   └── metrics/             # CSV metrics output
│       ├── writer.go        # Skater metrics
│       └── viewer_writer.go # Viewer metrics
├── bin/                     # Compiled binaries (gitignored)
├── go.mod
└── README.md
```

## Development

Install development tools:

```bash
make install-tools
```

This installs all required development tools (goimports, etc.) as defined in `tools.go`.

Note: Ensure `~/go/bin` is in your PATH. Add to your shell profile if needed:
```bash
export PATH="$HOME/go/bin:$PATH"
```

Run tests:

```bash
go test ./...
```

Format code:

```bash
make fmt
```

## Future Enhancements

- Configurable GPS movement patterns (linear routes, circular paths)
- Real-time metrics dashboard
- Support for multiple concurrent events with different scenarios
