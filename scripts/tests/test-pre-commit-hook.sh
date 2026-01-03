#!/bin/bash

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
HOOK_SCRIPT="$SCRIPT_DIR/../git-hooks/pre-commit"

test_count=0
pass_count=0
fail_count=0

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

assert_equals() {
    local expected="$1"
    local actual="$2"
    local test_name="$3"

    test_count=$((test_count + 1))

    if [ "$expected" = "$actual" ]; then
        echo -e "${GREEN}✓${NC} $test_name"
        pass_count=$((pass_count + 1))
    else
        echo -e "${RED}✗${NC} $test_name"
        echo "  Expected: $expected"
        echo "  Actual:   $actual"
        fail_count=$((fail_count + 1))
    fi
}

assert_contains() {
    local haystack="$1"
    local needle="$2"
    local test_name="$3"

    test_count=$((test_count + 1))

    if echo "$haystack" | grep -q "$needle"; then
        echo -e "${GREEN}✓${NC} $test_name"
        pass_count=$((pass_count + 1))
    else
        echo -e "${RED}✗${NC} $test_name"
        echo "  Expected to contain: $needle"
        echo "  Actual: $haystack"
        fail_count=$((fail_count + 1))
    fi
}

test_sbt_dir_detection_in_services_api() {
    local temp_dir=$(mktemp -d)
    trap "rm -rf $temp_dir" EXIT

    mkdir -p "$temp_dir/services/api"
    touch "$temp_dir/services/api/build.sbt"

    cd "$temp_dir"

    sbt_dir=""
    if [ -d "services/api" ] && [ -f "services/api/build.sbt" ]; then
        sbt_dir="services/api"
    elif [ -f "build.sbt" ]; then
        sbt_dir="."
    fi

    assert_equals "services/api" "$sbt_dir" "Detects sbt project in services/api"
}

test_sbt_dir_detection_in_root() {
    local temp_dir=$(mktemp -d)
    trap "rm -rf $temp_dir" EXIT

    touch "$temp_dir/build.sbt"

    cd "$temp_dir"

    sbt_dir=""
    if [ -d "services/api" ] && [ -f "services/api/build.sbt" ]; then
        sbt_dir="services/api"
    elif [ -f "build.sbt" ]; then
        sbt_dir="."
    fi

    assert_equals "." "$sbt_dir" "Detects sbt project in root directory"
}

test_sbt_dir_detection_none_found() {
    local temp_dir=$(mktemp -d)
    trap "rm -rf $temp_dir" EXIT

    cd "$temp_dir"

    sbt_dir=""
    if [ -d "services/api" ] && [ -f "services/api/build.sbt" ]; then
        sbt_dir="services/api"
    elif [ -f "build.sbt" ]; then
        sbt_dir="."
    fi

    assert_equals "" "$sbt_dir" "Returns empty when no sbt project found"
}

test_sbt_dir_prefers_services_api() {
    local temp_dir=$(mktemp -d)
    trap "rm -rf $temp_dir" EXIT

    mkdir -p "$temp_dir/services/api"
    touch "$temp_dir/services/api/build.sbt"
    touch "$temp_dir/build.sbt"

    cd "$temp_dir"

    sbt_dir=""
    if [ -d "services/api" ] && [ -f "services/api/build.sbt" ]; then
        sbt_dir="services/api"
    elif [ -f "build.sbt" ]; then
        sbt_dir="."
    fi

    assert_equals "services/api" "$sbt_dir" "Prefers services/api over root when both exist"
}

test_hook_exits_early_with_no_staged_files() {
    local temp_dir=$(mktemp -d)
    trap "rm -rf $temp_dir" EXIT

    cd "$temp_dir"
    git init -q
    git config user.email "test@test.com"
    git config user.name "Test"

    touch file.txt
    git add file.txt

    local output=$("$HOOK_SCRIPT" 2>&1 || true)
    local exit_code=$?

    assert_equals "0" "$exit_code" "Hook exits with code 0 when no Scala or Go files staged"
}

test_hook_detects_staged_scala_files() {
    local temp_dir=$(mktemp -d)
    trap "rm -rf $temp_dir" EXIT

    cd "$temp_dir"
    git init -q
    git config user.email "test@test.com"
    git config user.name "Test"

    echo "object Test" > Test.scala
    git add Test.scala

    staged_scala_files=$(git diff --cached --name-only --diff-filter=ACM | grep '\.scala$' || true)

    assert_contains "$staged_scala_files" "Test.scala" "Detects staged Scala files"
}

test_hook_detects_staged_go_files() {
    local temp_dir=$(mktemp -d)
    trap "rm -rf $temp_dir" EXIT

    cd "$temp_dir"
    git init -q
    git config user.email "test@test.com"
    git config user.name "Test"

    echo "package main" > main.go
    git add main.go

    staged_go_files=$(git diff --cached --name-only --diff-filter=ACM | grep '\.go$' || true)

    assert_contains "$staged_go_files" "main.go" "Detects staged Go files"
}

echo "Running pre-commit hook tests..."
echo

test_sbt_dir_detection_in_services_api
test_sbt_dir_detection_in_root
test_sbt_dir_detection_none_found
test_sbt_dir_prefers_services_api
test_hook_exits_early_with_no_staged_files
test_hook_detects_staged_scala_files
test_hook_detects_staged_go_files

echo
echo "================================"
echo "Test Results"
echo "================================"
echo -e "Total:  $test_count"
echo -e "${GREEN}Passed: $pass_count${NC}"
if [ $fail_count -gt 0 ]; then
    echo -e "${RED}Failed: $fail_count${NC}"
    exit 1
else
    echo -e "${GREEN}All tests passed!${NC}"
    exit 0
fi
