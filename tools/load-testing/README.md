# Load Testing Tools

Go-based load testing tools for Skatemap Live. These tools simulate realistic skater behaviour to validate system performance under load.

## Requirements

- Go 1.21 or later

## Building

From the repository root:

```bash
go build -o bin/simulate-skaters ./tools/load-testing/cmd/simulate-skaters
```

Or from the `tools/load-testing` directory:

```bash
go build -o bin/simulate-skaters ./cmd/simulate-skaters
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

## Architecture

```
tools/load-testing/
├── cmd/
│   └── simulate-skaters/    # CLI binary
│       └── main.go
├── internal/
│   ├── skater/              # Skater simulation logic
│   │   └── skater.go        # Location updates, GPS movement
│   └── metrics/             # CSV metrics output
│       └── writer.go
├── bin/                     # Compiled binaries (gitignored)
├── go.mod
└── README.md
```

## Development

Run tests:

```bash
go test ./...
```

Format code:

```bash
go fmt ./...
```

## Future Enhancements

- WebSocket viewer simulation for long-lived connections
- Configurable GPS movement patterns (linear routes, circular paths)
- Real-time metrics dashboard
- Support for multiple concurrent events with different scenarios
