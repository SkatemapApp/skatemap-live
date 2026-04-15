#!/usr/bin/env bats

setup() {
  REPO_ROOT="$(cd "$BATS_TEST_DIRNAME/../../.." && pwd)"
  TEST_LABEL="test=docker-entrypoint-$$"
}

teardown() {
  docker ps -aq -f "label=$TEST_LABEL" | xargs -r docker rm -f >/dev/null 2>&1 || true
  docker images -q "skatemap-test:*" | xargs -r docker rmi -f >/dev/null 2>&1 || true
}

build_test_image() {
  local image_name="$1"
  local dockerfile_content="$2"
  local dockerfile="$BATS_TEST_DIRNAME/Dockerfile.$image_name"

  printf '%s\n' "$dockerfile_content" >"$dockerfile"
  docker build -f "$dockerfile" -t "skatemap-test:$image_name" "$REPO_ROOT" >/dev/null 2>&1
  local build_result=$?
  rm -f "$dockerfile"
  return $build_result
}

run_container() {
  local image="$1"
  shift
  docker run -d --label "$TEST_LABEL" "$@" "skatemap-test:$image"
}

@test "entrypoint exits with error when APPLICATION_SECRET is missing" {
  build_test_image "no-secret" 'FROM eclipse-temurin:21-jre
WORKDIR /app
RUN mkdir -p bin
RUN echo "#!/bin/sh" > bin/skatemap-live && chmod +x bin/skatemap-live
COPY services/api/docker-entrypoint.sh /app/
RUN chmod +x /app/docker-entrypoint.sh
ENTRYPOINT ["/app/docker-entrypoint.sh"]'

  run sh -c "docker run --rm --label $TEST_LABEL skatemap-test:no-secret 2>&1; exit \$?"

  [[ "$output" =~ "ERROR: APPLICATION_SECRET environment variable is required" ]]
  [ "$status" -eq 1 ]
}

@test "entrypoint starts application with OpenTelemetry agent present" {
  build_test_image "with-agent" 'FROM eclipse-temurin:21-jre
WORKDIR /app
RUN mkdir -p bin
RUN echo "#!/bin/sh" > bin/skatemap-live && \
    echo "echo Application started" >> bin/skatemap-live && \
    echo "sleep 2" >> bin/skatemap-live && \
    chmod +x bin/skatemap-live
RUN echo "mock-otel-agent-jar" > /app/opentelemetry-javaagent.jar
COPY services/api/docker-entrypoint.sh /app/
RUN chmod +x /app/docker-entrypoint.sh
ENTRYPOINT ["/app/docker-entrypoint.sh"]'

  container_id=$(run_container "with-agent" -e APPLICATION_SECRET="test-secret")
  sleep 3
  run sh -c "docker logs $container_id 2>&1"
  docker rm -f "$container_id" >/dev/null 2>&1

  [[ "$output" =~ "Application started" ]]
  [[ ! "$output" =~ "WARNING: OpenTelemetry agent not found" ]]
}

@test "entrypoint starts application without OpenTelemetry agent" {
  build_test_image "no-agent" 'FROM eclipse-temurin:21-jre
WORKDIR /app
RUN mkdir -p bin
RUN echo "#!/bin/sh" > bin/skatemap-live && \
    echo "echo Application started" >> bin/skatemap-live && \
    echo "sleep 2" >> bin/skatemap-live && \
    chmod +x bin/skatemap-live
COPY services/api/docker-entrypoint.sh /app/
RUN chmod +x /app/docker-entrypoint.sh
ENTRYPOINT ["/app/docker-entrypoint.sh"]'

  container_id=$(run_container "no-agent" -e APPLICATION_SECRET="test-secret")
  sleep 3
  run sh -c "docker logs $container_id 2>&1"
  docker rm -f "$container_id" >/dev/null 2>&1

  [[ "$output" =~ "WARNING: OpenTelemetry agent not found at /app/opentelemetry-javaagent.jar" ]]
  [[ "$output" =~ "Application started" ]]
}

@test "entrypoint logs OTEL configuration in debug mode" {
  build_test_image "debug" 'FROM eclipse-temurin:21-jre
WORKDIR /app
RUN mkdir -p bin
RUN echo "#!/bin/sh" > bin/skatemap-live && \
    echo "sleep 2" >> bin/skatemap-live && \
    chmod +x bin/skatemap-live
RUN echo "mock-otel-agent-jar" > /app/opentelemetry-javaagent.jar
COPY services/api/docker-entrypoint.sh /app/
RUN chmod +x /app/docker-entrypoint.sh
ENTRYPOINT ["/app/docker-entrypoint.sh"]'

  container_id=$(run_container "debug" \
    -e APPLICATION_SECRET="test-secret" \
    -e DEBUG="1" \
    -e OTEL_SERVICE_NAME="test-service" \
    -e OTEL_EXPORTER_OTLP_ENDPOINT="https://api.honeycomb.io" \
    -e OTEL_EXPORTER_OTLP_PROTOCOL="http/protobuf")
  sleep 3
  run sh -c "docker logs $container_id 2>&1"
  docker rm -f "$container_id" >/dev/null 2>&1

  [[ "$output" =~ "DEBUG: OTEL configuration:" ]]
  [[ "$output" =~ "Agent: /app/opentelemetry-javaagent.jar" ]]
  [[ "$output" =~ "Service: test-service" ]]
  [[ "$output" =~ "Endpoint: https://api.honeycomb.io" ]]
  [[ "$output" =~ "Protocol: http/protobuf" ]]
}
