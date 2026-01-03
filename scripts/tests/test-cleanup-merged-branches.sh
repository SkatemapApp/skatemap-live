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

source_functions() {
  source "$CLEANUP_SCRIPT"
}

test_is_protected_function() {
  source_functions

  if is_protected "master" "feature"; then
    echo -e "${GREEN}✓${NC} is_protected returns true for master"
    pass_count=$((pass_count + 1))
  else
    echo -e "${RED}✗${NC} is_protected should return true for master"
    fail_count=$((fail_count + 1))
  fi
  test_count=$((test_count + 1))

  if is_protected "main" "feature"; then
    echo -e "${GREEN}✓${NC} is_protected returns true for main"
    pass_count=$((pass_count + 1))
  else
    echo -e "${RED}✗${NC} is_protected should return true for main"
    fail_count=$((fail_count + 1))
  fi
  test_count=$((test_count + 1))

  if is_protected "feature" "feature"; then
    echo -e "${GREEN}✓${NC} is_protected returns true for current branch"
    pass_count=$((pass_count + 1))
  else
    echo -e "${RED}✗${NC} is_protected should return true for current branch"
    fail_count=$((fail_count + 1))
  fi
  test_count=$((test_count + 1))

  if ! is_protected "feature" "master"; then
    echo -e "${GREEN}✓${NC} is_protected returns false for non-protected branch"
    pass_count=$((pass_count + 1))
  else
    echo -e "${RED}✗${NC} is_protected should return false for non-protected branch"
    fail_count=$((fail_count + 1))
  fi
  test_count=$((test_count + 1))
}

test_protected_branches_display_master_main() {
  source_functions
  DRY_RUN=true

  local output=$(display_and_confirm "feature-branch" "old-feature" 2>&1)

  assert_contains "$output" "master" "Protected branches display contains master"
  assert_contains "$output" "main" "Protected branches display contains main"
  assert_contains "$output" "feature-branch" "Protected branches display contains current branch"
}

test_current_branch_not_duplicated_when_master() {
  source_functions
  DRY_RUN=true

  local output=$(display_and_confirm "master" "old-feature" 2>&1)

  assert_contains "$output" "Protected: master, main" "Master not duplicated in protected list when current branch is master"
}

test_current_branch_not_duplicated_when_main() {
  source_functions
  DRY_RUN=true

  local output=$(display_and_confirm "main" "old-feature" 2>&1)

  assert_contains "$output" "Protected: master, main" "Main not duplicated in protected list when current branch is main"
}

test_current_branch_prepended_when_feature_branch() {
  source_functions
  DRY_RUN=true

  local output=$(display_and_confirm "feature-xyz" "old-feature" 2>&1)

  assert_contains "$output" "Protected: feature-xyz, master, main" "Feature branch prepended to protected list"
}

echo "Running cleanup-merged-branches tests..."
echo

test_is_protected_function
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
