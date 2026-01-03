#!/bin/bash

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
API_DIR="$REPO_ROOT/services/api"
HEAP_DUMP_DIR="$API_DIR/heap-dumps"

LOAD_DURATION_MINUTES=${LOAD_DURATION_MINUTES:-30}
IDLE_DURATION_MINUTES=${IDLE_DURATION_MINUTES:-20}
SKATERS_PER_EVENT=${SKATERS_PER_EVENT:-10}
UPDATE_INTERVAL=${UPDATE_INTERVAL:-3s}
TARGET_URL=${TARGET_URL:-http://localhost:9000}

EVENT_ID=$(uuidgen | tr '[:upper:]' '[:lower:]')
TIMESTAMP=$(date +%Y%m%d-%H%M%S)

echo "=========================================="
echo "Memory Leak Profiling Script"
echo "=========================================="
echo "Load duration: $LOAD_DURATION_MINUTES minutes"
echo "Idle duration: $IDLE_DURATION_MINUTES minutes"
echo "Skaters per event: $SKATERS_PER_EVENT"
echo "Update interval: $UPDATE_INTERVAL"
echo "Target URL: $TARGET_URL"
echo "Event ID: $EVENT_ID"
echo "Timestamp: $TIMESTAMP"
echo "=========================================="

mkdir -p "$HEAP_DUMP_DIR"

cleanup() {
  echo ""
  echo "Cleaning up..."
  if [ -n "$LOAD_PID" ] && kill -0 "$LOAD_PID" 2>/dev/null; then
    echo "Stopping load test (PID: $LOAD_PID)..."
    kill "$LOAD_PID" || true
  fi
  if [ -n "$APP_PID" ] && kill -0 "$APP_PID" 2>/dev/null; then
    echo "Stopping application (PID: $APP_PID)..."
    kill "$APP_PID" || true
  fi
}

trap cleanup EXIT INT TERM

echo ""
echo "Step 1: Starting application..."
cd "$API_DIR"
sbt run >"/tmp/sbt-run-$TIMESTAMP.log" 2>&1 &
SBT_PID=$!
echo "sbt started (PID: $SBT_PID)"

echo "Waiting for application to be ready..."
MAX_WAIT=60
WAITED=0
while [ $WAITED -lt $MAX_WAIT ]; do
  if curl -s "$TARGET_URL/health" >/dev/null 2>&1; then
    echo "Application is ready"
    break
  fi
  sleep 1
  WAITED=$((WAITED + 1))
done

if [ $WAITED -ge $MAX_WAIT ]; then
  echo "ERROR: Application failed to start within $MAX_WAIT seconds"
  exit 1
fi

APP_PID=$(lsof -ti :9000 | head -1)
if [ -z "$APP_PID" ]; then
  echo "ERROR: Could not identify application PID"
  exit 1
fi
echo "Application PID: $APP_PID"

echo ""
echo "Step 2: Starting load test for $LOAD_DURATION_MINUTES minutes..."
cd "$REPO_ROOT/tools/load-testing"
go run ./cmd/simulate-skaters \
  --target-url "$TARGET_URL" \
  --event-id "$EVENT_ID" \
  --skaters-per-event "$SKATERS_PER_EVENT" \
  --update-interval "$UPDATE_INTERVAL" >"/tmp/load-test-$TIMESTAMP.log" 2>&1 &
LOAD_PID=$!
echo "Load test started (PID: $LOAD_PID)"

HEAP_DUMP_INTERVAL=${HEAP_DUMP_INTERVAL_MINUTES:-10}
echo "Waiting $LOAD_DURATION_MINUTES minutes for load to run (heap dumps every $HEAP_DUMP_INTERVAL minutes)..."
for i in $(seq 1 "$LOAD_DURATION_MINUTES"); do
  sleep 60
  echo "  $i / $LOAD_DURATION_MINUTES minutes elapsed..."

  if [ $((i % HEAP_DUMP_INTERVAL)) -eq 0 ] && [ "$i" -lt "$LOAD_DURATION_MINUTES" ]; then
    echo "  Taking periodic heap dump at $i minutes..."
    PERIODIC_DUMP="$HEAP_DUMP_DIR/at-${i}min-$TIMESTAMP.hprof"
    if jcmd "$APP_PID" GC.heap_dump "$PERIODIC_DUMP" 2>&1 | grep -q "Heap dump file created"; then
      echo "  Heap dump saved: $PERIODIC_DUMP"
    else
      echo "  WARNING: Heap dump failed - process may have died"
    fi
  fi
done

echo ""
echo "Step 3: Taking final heap dump after load..."
HEAP_DUMP_1="$HEAP_DUMP_DIR/after-${LOAD_DURATION_MINUTES}min-load-$TIMESTAMP.hprof"
if jcmd "$APP_PID" GC.heap_dump "$HEAP_DUMP_1" 2>&1 | grep -q "Heap dump file created"; then
  echo "Heap dump saved: $HEAP_DUMP_1"
else
  echo "WARNING: Final heap dump failed - using most recent periodic dump"
  HEAP_DUMP_1=$(find "$HEAP_DUMP_DIR" -name "at-*min-$TIMESTAMP.hprof" -type f -print0 2>/dev/null | xargs -0 ls -t 2>/dev/null | head -1)
  if [ -n "$HEAP_DUMP_1" ]; then
    echo "Most recent heap dump: $HEAP_DUMP_1"
  else
    echo "ERROR: No heap dumps available"
  fi
fi

echo ""
echo "Step 4: Stopping load test..."
if kill -0 "$LOAD_PID" 2>/dev/null; then
  kill "$LOAD_PID"
  echo "Load test stopped"
fi
LOAD_PID=""

echo ""
echo "Step 5: Waiting $IDLE_DURATION_MINUTES minutes (idle period)..."
for i in $(seq 1 "$IDLE_DURATION_MINUTES"); do
  sleep 60
  echo "  $i / $IDLE_DURATION_MINUTES minutes elapsed..."
done

echo ""
echo "Step 6: Taking heap dump after idle period..."
HEAP_DUMP_2="$HEAP_DUMP_DIR/after-${IDLE_DURATION_MINUTES}min-idle-$TIMESTAMP.hprof"
if jcmd "$APP_PID" GC.heap_dump "$HEAP_DUMP_2" 2>&1 | grep -q "Heap dump file created"; then
  echo "Heap dump saved: $HEAP_DUMP_2"
else
  echo "WARNING: Idle heap dump failed - process likely died during load test"
  HEAP_DUMP_2=""
fi

echo ""
echo "Step 7: Stopping application..."
if kill -0 "$APP_PID" 2>/dev/null; then
  kill "$APP_PID"
  echo "Application stopped"
fi
APP_PID=""

echo ""
echo "=========================================="
echo "Profiling Complete"
echo "=========================================="
echo "Heap dumps:"
PERIODIC_DUMPS=$(find "$HEAP_DUMP_DIR" -name "at-*min-$TIMESTAMP.hprof" -type f -print0 2>/dev/null | xargs -0 ls -t 2>/dev/null)
if [ -n "$PERIODIC_DUMPS" ]; then
  echo "  Periodic dumps (during load):"
  while IFS= read -r dump; do
    echo "    $(basename "$dump")"
  done <<< "$PERIODIC_DUMPS"
fi
if [ -n "$HEAP_DUMP_1" ] && [ -f "$HEAP_DUMP_1" ]; then
  echo "  After load: $(basename "$HEAP_DUMP_1")"
fi
if [ -n "$HEAP_DUMP_2" ] && [ -f "$HEAP_DUMP_2" ]; then
  echo "  After idle: $(basename "$HEAP_DUMP_2")"
fi
echo ""
echo "All dumps location: $HEAP_DUMP_DIR"
echo ""
echo "Logs:"
echo "  Application: /tmp/sbt-run-$TIMESTAMP.log"
echo "  Load test:   /tmp/load-test-$TIMESTAMP.log"
echo ""
echo "Next steps:"
echo "  1. Open most recent heap dump in Eclipse MAT:"
LATEST_DUMP=$(find "$HEAP_DUMP_DIR" -name "*-$TIMESTAMP.hprof" -type f -print0 2>/dev/null | xargs -0 ls -t 2>/dev/null | head -1)
if [ -n "$LATEST_DUMP" ]; then
  echo "     open -a \"Memory Analyzer\" \"$LATEST_DUMP\""
fi
echo "  2. Run Leak Suspects Report"
echo "  3. Compare with earlier dumps to see memory growth"
echo "=========================================="
