#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "Validating railway.toml configuration..."

if [[ ! -f railway.toml ]]; then
  echo "ERROR: railway.toml not found"
  exit 1
fi

DOCKERFILE_PATH=$(grep -E '^\s*dockerfilePath\s*=' railway.toml | sed -E 's/.*= "(.*)".*/\1/' || true)
if [[ -z "$DOCKERFILE_PATH" ]]; then
  echo "ERROR: dockerfilePath not found in railway.toml"
  exit 1
fi

if [[ ! -f "$DOCKERFILE_PATH" ]]; then
  echo "ERROR: Dockerfile not found at $DOCKERFILE_PATH"
  exit 1
fi
echo "✓ Dockerfile exists: $DOCKERFILE_PATH"

START_COMMAND=$(grep -E '^\s*startCommand\s*=' railway.toml | sed -E 's/.*= "(.*)".*/\1/' || true)
if [[ -z "$START_COMMAND" ]]; then
  echo "ERROR: startCommand not found in railway.toml"
  exit 1
fi

if [[ "$START_COMMAND" == "/app/docker-entrypoint.sh" ]]; then
  ENTRYPOINT_SOURCE="services/api/docker-entrypoint.sh"
  if [[ ! -f "$ENTRYPOINT_SOURCE" ]]; then
    echo "ERROR: Entrypoint script not found at $ENTRYPOINT_SOURCE"
    exit 1
  fi
  echo "✓ Entrypoint script exists: $ENTRYPOINT_SOURCE"
else
  echo "WARNING: startCommand is $START_COMMAND (not /app/docker-entrypoint.sh)"
fi

HEALTHCHECK_PATH=$(grep -E '^\s*healthcheckPath\s*=' railway.toml | sed -E 's/.*= "(.*)".*/\1/' || true)
if [[ -z "$HEALTHCHECK_PATH" ]]; then
  echo "ERROR: healthcheckPath not found in railway.toml"
  exit 1
fi

ROUTES_FILE="services/api/src/main/resources/routes"
if [[ ! -f "$ROUTES_FILE" ]]; then
  echo "ERROR: Routes file not found at $ROUTES_FILE"
  exit 1
fi

if ! grep -qE "GET\s*${HEALTHCHECK_PATH}\s" "$ROUTES_FILE"; then
  echo "ERROR: healthcheckPath $HEALTHCHECK_PATH not found in $ROUTES_FILE"
  exit 1
fi
echo "✓ Healthcheck path exists in routes: $HEALTHCHECK_PATH"

echo "✓ railway.toml validation passed"
