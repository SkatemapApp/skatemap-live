#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

validate_path() {
  local path="$1"
  local description="$2"

  if [[ "$path" =~ \.\. ]]; then
    echo "ERROR: Invalid $description path (contains ..): $path"
    exit 1
  fi

  if [[ "$path" =~ ^/ ]] && [[ "$description" != "start command" ]]; then
    echo "ERROR: Invalid $description path (absolute path not allowed): $path"
    exit 1
  fi
}

extract_toml_value() {
  local key="$1"
  local file="$2"
  local value
  set +e
  value=$(grep -E "^[[:space:]]*${key}[[:space:]]*=" "$file" | sed -E 's/.*= "(.*)".*/\1/')
  set -e
  echo "$value"
}

echo "Validating railway.toml configuration..."

if [[ ! -f railway.toml ]]; then
  echo "ERROR: railway.toml not found"
  exit 1
fi

DOCKERFILE_PATH=$(extract_toml_value "dockerfilePath" "railway.toml")
if [[ -z "$DOCKERFILE_PATH" ]]; then
  echo "ERROR: dockerfilePath not found in railway.toml"
  exit 1
fi

validate_path "$DOCKERFILE_PATH" "dockerfile"

if [[ ! -f "$DOCKERFILE_PATH" ]]; then
  echo "ERROR: Dockerfile not found at $DOCKERFILE_PATH"
  exit 1
fi
echo "✓ Dockerfile exists: $DOCKERFILE_PATH"

START_COMMAND=$(extract_toml_value "startCommand" "railway.toml")
if [[ -z "$START_COMMAND" ]]; then
  echo "ERROR: startCommand not found in railway.toml"
  exit 1
fi

if [[ "$START_COMMAND" == "/app/docker-entrypoint.sh" ]]; then
  ENTRYPOINT_SOURCE="services/api/docker-entrypoint.sh"
  validate_path "$ENTRYPOINT_SOURCE" "entrypoint"

  if [[ ! -f "$ENTRYPOINT_SOURCE" ]]; then
    echo "ERROR: Entrypoint script not found at $ENTRYPOINT_SOURCE"
    exit 1
  fi
  echo "✓ Entrypoint script exists: $ENTRYPOINT_SOURCE"
else
  echo "WARNING: startCommand is $START_COMMAND (not /app/docker-entrypoint.sh)"
fi

HEALTHCHECK_PATH=$(extract_toml_value "healthcheckPath" "railway.toml")
if [[ -z "$HEALTHCHECK_PATH" ]]; then
  echo "ERROR: healthcheckPath not found in railway.toml"
  exit 1
fi

ROUTES_FILE="services/api/src/main/resources/routes"
if [[ ! -f "$ROUTES_FILE" ]]; then
  echo "ERROR: Routes file not found at $ROUTES_FILE"
  exit 1
fi

if ! grep -qF "GET     ${HEALTHCHECK_PATH} " "$ROUTES_FILE"; then
  echo "ERROR: healthcheckPath $HEALTHCHECK_PATH not found in $ROUTES_FILE"
  exit 1
fi
echo "✓ Healthcheck path exists in routes: $HEALTHCHECK_PATH"

echo "✓ railway.toml validation passed"
