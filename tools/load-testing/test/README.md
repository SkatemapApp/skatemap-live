# Smoke Test Suite

Automated integration tests for skatemap-live deployment validation.

## Running Tests

### Prerequisites

1. **Railway CLI installed:**
   ```bash
   curl -fsSL https://railway.app/install.sh | sh
   ```

2. **Railway authenticated:**
   ```bash
   railway login
   ```
   Or set `RAILWAY_TOKEN` environment variable.

3. **Simulation binaries built:**
   ```bash
   cd tools/load-testing
   make build
   ```

### All Tests

```bash
cd tools/load-testing
RAILWAY_URL=<railway-url> go test ./test/... -v -timeout 2h
```

### Single Test

```bash
RAILWAY_URL=https://... go test ./test/... -run TestSmokeTestSuite/TestEventIsolation -v
```

### Skip Long-Running Tests

```bash
go test ./test/... -short
```

Skips the 30-minute stability test during development.

### JSON Output for CI

```bash
go test ./test/... -json > smoke-test-results.json
```

## Test Descriptions

### TestEventIsolation

Verifies events are completely isolated - Event A skaters never appear in Event B viewers.

**Duration:** ~30 seconds

### TestLocationExpiry

Verifies locations expire after 30s TTL and cleanup occurs within expected timeframe.

**Duration:** ~2 minutes

### TestWebSocketTimeout

Verifies WebSocket connections survive 95s idle period and can resume receiving data.

**Duration:** ~3 minutes

### TestScale

Verifies system handles increased load gracefully (doubles skaters in Event A).

**Duration:** ~7 minutes

### TestStability

30-minute stress test with periodic crash detection. Validates no memory leaks or performance degradation.

**Duration:** 30 minutes

## Environment Variables

- `RAILWAY_URL` (required): Target Railway deployment URL
- `RAILWAY_TOKEN` (optional): Railway authentication token (alternative to `railway login`)

## Troubleshooting

### "RAILWAY_URL environment variable required"

Set the environment variable before running tests:

```bash
export RAILWAY_URL=<railway-url>
```

### "railway: command not found"

Install Railway CLI:

```bash
curl -fsSL https://railway.app/install.sh | sh
```

### Test Timeout

Increase timeout for longer runs:

```bash
go test ./test/... -timeout 3h
```

Or skip stability test:

```bash
go test ./test/... -short
```

### Subprocess Not Found

Build simulation tools first:

```bash
cd tools/load-testing
make build
```
