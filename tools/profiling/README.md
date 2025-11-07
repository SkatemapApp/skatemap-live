# Memory Leak Profiling Tools

Automated tools for diagnosing memory leaks in the Skatemap Live application.

## profile-memory-leak.sh

Automated script that profiles the application under load and captures heap dumps for analysis.

### Usage

Basic usage with defaults (30 min load + 20 min idle):
```bash
./profile-memory-leak.sh
```

Customised parameters:
```bash
LOAD_DURATION_MINUTES=45 \
IDLE_DURATION_MINUTES=30 \
SKATERS_PER_EVENT=20 \
UPDATE_INTERVAL=1s \
./profile-memory-leak.sh
```

### Environment Variables

- `LOAD_DURATION_MINUTES` - Duration to run load test (default: 30)
- `IDLE_DURATION_MINUTES` - Duration to wait after stopping load (default: 20)
- `SKATERS_PER_EVENT` - Number of simulated skaters (default: 10)
- `UPDATE_INTERVAL` - Interval between location updates (default: 3s)
- `TARGET_URL` - Application URL (default: http://localhost:9000)

### What It Does

1. Starts the application (`sbt run`)
2. Waits for health check to pass
3. Identifies the JVM process PID
4. Starts load test simulator
5. Waits for specified load duration
6. Takes first heap dump (after load)
7. Stops load test
8. Waits for specified idle duration
9. Takes second heap dump (after idle)
10. Stops application
11. Reports heap dump locations

### Output

Heap dumps are saved to `services/api/heap-dumps/` with timestamps:
- `after-{N}min-load-{timestamp}.hprof`
- `after-{N}min-idle-{timestamp}.hprof`

Logs are saved to `/tmp/`:
- `sbt-run-{timestamp}.log`
- `load-test-{timestamp}.log`

### Analysing Results

Open heap dumps in Eclipse MAT:
```bash
open -a "Memory Analyzer" services/api/heap-dumps/after-30min-load-*.hprof
```

See `services/api/CLAUDE.md` for heap dump analysis workflow.

## Prerequisites

- Eclipse MAT installed: `brew install --cask mat`
- Go installed (for load test simulator)
- JDK with `jcmd` available
- Port 9000 available
