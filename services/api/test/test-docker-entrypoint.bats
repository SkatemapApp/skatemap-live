#!/usr/bin/env bats

OTEL_AGENT_JAR="/app/opentelemetry-javaagent.jar"

setup() {
  REPO_ROOT="$(cd "$BATS_TEST_DIRNAME/../../.." && pwd)"
  TEST_RUN_ID="$$-$RANDOM"
  TEST_LABEL="test=docker-entrypoint-$TEST_RUN_ID"
  IMAGE_PREFIX="skatemap-test-$TEST_RUN_ID"
}

teardown() {
  docker ps -aq -f "label=$TEST_LABEL" | xargs -r docker rm -f >/dev/null 2>&1 || true
  docker images -q "$IMAGE_PREFIX:*" | xargs -r docker rmi -f >/dev/null 2>&1 || true
}

build_test_image() {
  local image_name="$1"
  local dockerfile_content="$2"
  local dockerfile="$BATS_TEST_DIRNAME/Dockerfile.$image_name"

  printf '%s\n' "$dockerfile_content" >"$dockerfile"
  docker build -f "$dockerfile" -t "$IMAGE_PREFIX:$image_name" "$REPO_ROOT" >/dev/null 2>&1
  local build_result=$?
  rm -f "$dockerfile"
  return $build_result
}

run_container() {
  local image="$1"
  shift
  docker run -d --label "$TEST_LABEL" "$@" "$IMAGE_PREFIX:$image"
}

base_dockerfile() {
  cat <<'EOF'
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN mkdir -p bin
EOF
}

mock_app_script() {
  local include_output="${1:-false}"
  if [ "$include_output" = "true" ]; then
    cat <<'EOF'
RUN echo "#!/bin/sh" > bin/skatemap-live && \
    echo "echo Application started" >> bin/skatemap-live && \
    echo "sleep 2" >> bin/skatemap-live && \
    chmod +x bin/skatemap-live
EOF
  else
    cat <<'EOF'
RUN echo "#!/bin/sh" > bin/skatemap-live && \
    echo "sleep 2" >> bin/skatemap-live && \
    chmod +x bin/skatemap-live
EOF
  fi
}

entrypoint_setup() {
  cat <<'EOF'
COPY services/api/docker-entrypoint.sh /app/
RUN chmod +x /app/docker-entrypoint.sh
ENTRYPOINT ["/app/docker-entrypoint.sh"]
EOF
}

@test "entrypoint exits with error when APPLICATION_SECRET is missing" {
  local dockerfile
  dockerfile="$(base_dockerfile)
RUN echo '#!/bin/sh' > bin/skatemap-live && chmod +x bin/skatemap-live
$(entrypoint_setup)"

  build_test_image "no-secret" "$dockerfile"
  run sh -c "docker run --rm --label $TEST_LABEL $IMAGE_PREFIX:no-secret 2>&1; exit \$?"

  [[ "$output" =~ "ERROR: APPLICATION_SECRET environment variable is required" ]] ||
    fail "Expected error message not found in output: $output"
  [ "$status" -eq 1 ] ||
    fail "Expected exit code 1, got: $status"
}

@test "entrypoint starts application with OpenTelemetry agent present" {
  local dockerfile
  dockerfile="$(base_dockerfile)
$(mock_app_script true)
RUN echo 'mock-otel-agent-jar' > $OTEL_AGENT_JAR
$(entrypoint_setup)"

  build_test_image "with-agent" "$dockerfile"
  container_id=$(run_container "with-agent" -e APPLICATION_SECRET="test-secret")
  sleep 3
  run sh -c "docker logs $container_id 2>&1"
  docker rm -f "$container_id" >/dev/null 2>&1

  [[ "$output" =~ "Application started" ]] ||
    fail "Application failed to start. Output: $output"
  [[ ! "$output" =~ "WARNING: OpenTelemetry agent not found" ]] ||
    fail "Unexpected warning about missing OTEL agent. Output: $output"
}

@test "entrypoint starts application without OpenTelemetry agent" {
  local dockerfile
  dockerfile="$(base_dockerfile)
$(mock_app_script true)
$(entrypoint_setup)"

  build_test_image "no-agent" "$dockerfile"
  container_id=$(run_container "no-agent" -e APPLICATION_SECRET="test-secret")
  sleep 3
  run sh -c "docker logs $container_id 2>&1"
  docker rm -f "$container_id" >/dev/null 2>&1

  [[ "$output" =~ "WARNING: OpenTelemetry agent not found at $OTEL_AGENT_JAR" ]] ||
    fail "Expected warning about missing OTEL agent not found. Output: $output"
  [[ "$output" =~ "Application started" ]] ||
    fail "Application failed to start without OTEL agent. Output: $output"
}

@test "entrypoint logs OTEL configuration in debug mode" {
  local dockerfile
  dockerfile="$(base_dockerfile)
$(mock_app_script false)
RUN echo 'mock-otel-agent-jar' > $OTEL_AGENT_JAR
$(entrypoint_setup)"

  build_test_image "debug" "$dockerfile"
  container_id=$(run_container "debug" \
    -e APPLICATION_SECRET="test-secret" \
    -e DEBUG="1" \
    -e OTEL_SERVICE_NAME="test-service" \
    -e OTEL_EXPORTER_OTLP_ENDPOINT="https://api.honeycomb.io" \
    -e OTEL_EXPORTER_OTLP_PROTOCOL="http/protobuf")
  sleep 3
  run sh -c "docker logs $container_id 2>&1"
  docker rm -f "$container_id" >/dev/null 2>&1

  [[ "$output" =~ "DEBUG: OTEL configuration:" ]] ||
    fail "Debug header not found. Output: $output"
  [[ "$output" =~ "Agent: $OTEL_AGENT_JAR" ]] ||
    fail "Agent path not logged. Output: $output"
  [[ "$output" =~ "Service: test-service" ]] ||
    fail "Service name not logged. Output: $output"
  [[ "$output" =~ "Endpoint: https://api.honeycomb.io" ]] ||
    fail "Endpoint not logged. Output: $output"
  [[ "$output" =~ "Protocol: http/protobuf" ]] ||
    fail "Protocol not logged. Output: $output"
}
