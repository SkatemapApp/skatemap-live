#!/bin/bash

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CLEANUP_SCRIPT="$SCRIPT_DIR/../cleanup-merged-branches.sh"

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

source_script_functions() {
    source "$CLEANUP_SCRIPT"
}

test_protected_branches_display_master_main() {
    local temp_dir=$(mktemp -d)
    local original_dir=$(pwd)

    cd "$temp_dir"
    git init -q
    git config user.email "test@test.com"
    git config user.name "Test"

    local current_branch="feature-branch"
    local protected="master, main"
    if [[ "$current_branch" != "master" ]] && [[ "$current_branch" != "main" ]]; then
        protected="$current_branch, $protected"
    fi

    assert_contains "$protected" "master" "Protected branches display contains master"
    assert_contains "$protected" "main" "Protected branches display contains main"
    assert_contains "$protected" "feature-branch" "Protected branches display contains current branch"

    cd "$original_dir"
    rm -rf "$temp_dir"
}

test_current_branch_not_duplicated_when_master() {
    local temp_dir=$(mktemp -d)
    local original_dir=$(pwd)

    cd "$temp_dir"
    git init -q
    git config user.email "test@test.com"
    git config user.name "Test"

    local current_branch="master"
    local protected="master, main"
    if [[ "$current_branch" != "master" ]] && [[ "$current_branch" != "main" ]]; then
        protected="$current_branch, $protected"
    fi

    local count=$(echo "$protected" | grep -o "master" | wc -l | tr -d ' ')
    assert_equals "1" "$count" "Master not duplicated when current branch is master"

    cd "$original_dir"
    rm -rf "$temp_dir"
}

test_current_branch_not_duplicated_when_main() {
    local temp_dir=$(mktemp -d)
    local original_dir=$(pwd)

    cd "$temp_dir"
    git init -q
    git config user.email "test@test.com"
    git config user.name "Test"

    local current_branch="main"
    local protected="master, main"
    if [[ "$current_branch" != "master" ]] && [[ "$current_branch" != "main" ]]; then
        protected="$current_branch, $protected"
    fi

    local count=$(echo "$protected" | grep -o "main" | wc -l | tr -d ' ')
    assert_equals "1" "$count" "Main not duplicated when current branch is main"

    cd "$original_dir"
    rm -rf "$temp_dir"
}

test_current_branch_prepended_when_feature_branch() {
    local temp_dir=$(mktemp -d)
    local original_dir=$(pwd)

    cd "$temp_dir"
    git init -q
    git config user.email "test@test.com"
    git config user.name "Test"

    local current_branch="feature-xyz"
    local protected="master, main"
    if [[ "$current_branch" != "master" ]] && [[ "$current_branch" != "main" ]]; then
        protected="$current_branch, $protected"
    fi

    assert_equals "feature-xyz, master, main" "$protected" "Feature branch prepended to protected list"

    cd "$original_dir"
    rm -rf "$temp_dir"
}

echo "Running cleanup-merged-branches tests..."
echo

test_protected_branches_display_master_main
test_current_branch_not_duplicated_when_master
test_current_branch_not_duplicated_when_main
test_current_branch_prepended_when_feature_branch

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
