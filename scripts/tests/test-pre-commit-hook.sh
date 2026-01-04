#!/bin/bash

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
HOOK_SCRIPT="$SCRIPT_DIR/../git-hooks/pre-commit"

test_count=0
pass_count=0
fail_count=0

RED='\033[0;31m'
GREEN='\033[0;32m'
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
  local temp_dir
  temp_dir=$(mktemp -d)
  local original_dir
  original_dir=$(pwd)

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

  cd "$original_dir"
  rm -rf "$temp_dir"
}

test_sbt_dir_detection_in_root() {
  local temp_dir
  temp_dir=$(mktemp -d)
  local original_dir
  original_dir=$(pwd)

  touch "$temp_dir/build.sbt"

  cd "$temp_dir"

  sbt_dir=""
  if [ -d "services/api" ] && [ -f "services/api/build.sbt" ]; then
    sbt_dir="services/api"
  elif [ -f "build.sbt" ]; then
    sbt_dir="."
  fi

  assert_equals "." "$sbt_dir" "Detects sbt project in root directory"

  cd "$original_dir"
  rm -rf "$temp_dir"
}

test_sbt_dir_detection_none_found() {
  local temp_dir
  temp_dir=$(mktemp -d)
  local original_dir
  original_dir=$(pwd)

  cd "$temp_dir"

  sbt_dir=""
  if [ -d "services/api" ] && [ -f "services/api/build.sbt" ]; then
    sbt_dir="services/api"
  elif [ -f "build.sbt" ]; then
    sbt_dir="."
  fi

  assert_equals "" "$sbt_dir" "Returns empty when no sbt project found"

  cd "$original_dir"
  rm -rf "$temp_dir"
}

test_sbt_dir_prefers_services_api() {
  local temp_dir
  temp_dir=$(mktemp -d)
  local original_dir
  original_dir=$(pwd)

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

  cd "$original_dir"
  rm -rf "$temp_dir"
}

test_hook_exits_early_with_no_staged_files() {
  local temp_dir
  temp_dir=$(mktemp -d)
  local original_dir
  original_dir=$(pwd)

  cd "$temp_dir"
  git init -q
  git config user.email "test@test.com"
  git config user.name "Test"

  touch file.txt
  git add file.txt

  set +e
  "$HOOK_SCRIPT" >/dev/null 2>&1
  local exit_code=$?
  set -e

  assert_equals "0" "$exit_code" "Hook exits with code 0 when no Scala, Go, or shell files staged"

  cd "$original_dir"
  rm -rf "$temp_dir"
}

test_hook_detects_staged_scala_files() {
  local temp_dir
  temp_dir=$(mktemp -d)
  local original_dir
  original_dir=$(pwd)

  cd "$temp_dir"
  git init -q
  git config user.email "test@test.com"
  git config user.name "Test"

  echo "object Test" >Test.scala
  git add Test.scala

  staged_scala_files=$(git diff --cached --name-only --diff-filter=ACM | grep '\.scala$' || true)

  assert_contains "$staged_scala_files" "Test.scala" "Detects staged Scala files"

  cd "$original_dir"
  rm -rf "$temp_dir"
}

test_hook_detects_staged_go_files() {
  local temp_dir
  temp_dir=$(mktemp -d)
  local original_dir
  original_dir=$(pwd)

  cd "$temp_dir"
  git init -q
  git config user.email "test@test.com"
  git config user.name "Test"

  echo "package main" >main.go
  git add main.go

  staged_go_files=$(git diff --cached --name-only --diff-filter=ACM | grep '\.go$' || true)

  assert_contains "$staged_go_files" "main.go" "Detects staged Go files"

  cd "$original_dir"
  rm -rf "$temp_dir"
}

test_hook_handles_missing_sbt() {
  local temp_dir
  temp_dir=$(mktemp -d)
  local original_dir
  original_dir=$(pwd)
  local original_path="$PATH"

  cd "$temp_dir"
  git init -q
  git config user.email "test@test.com"
  git config user.name "Test"
  touch build.sbt

  echo "object Test" >Test.scala
  git add Test.scala

  export PATH="/usr/bin:/bin"

  set +e
  local output
  output=$("$HOOK_SCRIPT" 2>&1)
  local exit_code=$?
  set -e

  export PATH="$original_path"

  cd "$original_dir"
  rm -rf "$temp_dir"

  assert_equals "0" "$exit_code" "Hook exits successfully when sbt not found"
  assert_contains "$output" "sbt not found in PATH" "Hook warns about missing sbt"
}

test_hook_handles_scalafmt_failure() {
  local temp_dir
  temp_dir=$(mktemp -d)
  local original_dir
  original_dir=$(pwd)
  local original_path="$PATH"
  local mock_bin="$temp_dir/mock-bin"

  mkdir -p "$mock_bin"
  cat >"$mock_bin/sbt" <<'MOCK_SBT'
#!/bin/bash
echo "[error] scalafmt failed: syntax error" >&2
exit 1
MOCK_SBT
  chmod +x "$mock_bin/sbt"

  cd "$temp_dir"
  git init -q
  git config user.email "test@test.com"
  git config user.name "Test"
  touch build.sbt

  echo "object Test" >Test.scala
  git add Test.scala

  export PATH="$mock_bin:$PATH"

  set +e
  "$HOOK_SCRIPT" >output.txt 2>&1
  local exit_code=$?
  local output
  output=$(cat output.txt)
  set -e

  export PATH="$original_path"

  cd "$original_dir"
  rm -rf "$temp_dir"

  assert_equals "1" "$exit_code" "Hook exits with code 1 when scalafmt fails"
  assert_contains "$output" "scalafmt failed" "Hook displays scalafmt error"
}

test_hook_handles_missing_goimports() {
  local temp_dir
  temp_dir=$(mktemp -d)
  local original_dir
  original_dir=$(pwd)
  local original_path="$PATH"

  cd "$temp_dir"
  git init -q
  git config user.email "test@test.com"
  git config user.name "Test"

  echo "package main" >main.go
  git add main.go

  export PATH="/usr/bin:/bin"

  set +e
  "$HOOK_SCRIPT" >output.txt 2>&1
  local exit_code=$?
  local output
  output=$(cat output.txt)
  set -e

  export PATH="$original_path"

  cd "$original_dir"
  rm -rf "$temp_dir"

  assert_equals "1" "$exit_code" "Hook exits with code 1 when goimports not found"
  assert_contains "$output" "goimports not found" "Hook displays error about missing goimports"
}

test_hook_formats_scala_files_integration() {
  local temp_dir
  temp_dir=$(mktemp -d)
  local original_dir
  original_dir=$(pwd)
  local original_path="$PATH"
  local mock_bin="$temp_dir/mock-bin"

  mkdir -p "$mock_bin"
  cat >"$mock_bin/sbt" <<'MOCK_SBT'
#!/bin/bash
find . -name "*.scala" -type f | while read -r file; do
    echo "// formatted" >> "$file"
done
exit 0
MOCK_SBT
  chmod +x "$mock_bin/sbt"

  cd "$temp_dir"
  git init -q
  git config user.email "test@test.com"
  git config user.name "Test"
  touch build.sbt

  echo "object Test" >Test.scala
  git add Test.scala

  export PATH="$mock_bin:$PATH"

  set +e
  "$HOOK_SCRIPT" >output.txt 2>&1
  local exit_code=$?
  local output
  output=$(cat output.txt)
  set -e

  export PATH="$original_path"

  local formatted_content
  formatted_content=$(cat Test.scala)

  cd "$original_dir"
  rm -rf "$temp_dir"

  assert_equals "0" "$exit_code" "Hook exits successfully after formatting"
  assert_contains "$output" "Formatted: Test.scala" "Hook reports formatted file"
  assert_contains "$formatted_content" "// formatted" "File was actually formatted"
}

test_hook_formats_go_files_integration() {
  local temp_dir
  temp_dir=$(mktemp -d)
  local original_dir
  original_dir=$(pwd)
  local original_path="$PATH"
  local mock_bin="$temp_dir/mock-bin"

  mkdir -p "$mock_bin"
  cat >"$mock_bin/goimports" <<'MOCK_GOIMPORTS'
#!/bin/bash
for file in "$@"; do
    if [[ "$file" != "-w" ]]; then
        echo "// formatted by goimports" >> "$file"
    fi
done
exit 0
MOCK_GOIMPORTS
  chmod +x "$mock_bin/goimports"

  cd "$temp_dir"
  git init -q
  git config user.email "test@test.com"
  git config user.name "Test"

  echo "package main" >main.go
  git add main.go

  export PATH="$mock_bin:$PATH"

  set +e
  "$HOOK_SCRIPT" >output.txt 2>&1
  local exit_code=$?
  local output
  output=$(cat output.txt)
  set -e

  export PATH="$original_path"

  local formatted_content
  formatted_content=$(cat main.go)

  cd "$original_dir"
  rm -rf "$temp_dir"

  assert_equals "0" "$exit_code" "Hook exits successfully after formatting Go files"
  assert_contains "$output" "Formatted: main.go" "Hook reports formatted Go file"
  assert_contains "$formatted_content" "// formatted by goimports" "Go file was actually formatted"
}

test_hook_detects_staged_shell_files() {
  local temp_dir
  temp_dir=$(mktemp -d)
  local original_dir
  original_dir=$(pwd)

  cd "$temp_dir"
  git init -q
  git config user.email "test@test.com"
  git config user.name "Test"

  echo "#!/bin/bash" >script.sh
  git add script.sh

  staged_sh_files=$(git diff --cached --name-only --diff-filter=ACM | grep '\.sh$' || true)

  assert_contains "$staged_sh_files" "script.sh" "Detects staged shell files"

  cd "$original_dir"
  rm -rf "$temp_dir"
}

test_hook_handles_missing_shfmt() {
  local temp_dir
  temp_dir=$(mktemp -d)
  local original_dir
  original_dir=$(pwd)
  local original_path="$PATH"

  cd "$temp_dir"
  git init -q
  git config user.email "test@test.com"
  git config user.name "Test"

  echo "#!/bin/bash" >script.sh
  git add script.sh

  export PATH="/usr/bin:/bin"

  set +e
  "$HOOK_SCRIPT" >output.txt 2>&1
  local exit_code=$?
  local output
  output=$(cat output.txt)
  set -e

  export PATH="$original_path"

  cd "$original_dir"
  rm -rf "$temp_dir"

  assert_equals "1" "$exit_code" "Hook exits with code 1 when shfmt not found"
  assert_contains "$output" "shfmt not found" "Hook displays error about missing shfmt"
}

test_hook_formats_shell_files_integration() {
  local temp_dir
  temp_dir=$(mktemp -d)
  local original_dir
  original_dir=$(pwd)
  local original_path="$PATH"
  local mock_bin="$temp_dir/mock-bin"

  mkdir -p "$mock_bin"
  cat >"$mock_bin/shfmt" <<'MOCK_SHFMT'
#!/bin/bash
for file in "$@"; do
    if [[ "$file" != "-i" ]] && [[ "$file" != "2" ]] && [[ "$file" != "-w" ]]; then
        echo "# formatted by shfmt" >> "$file"
    fi
done
exit 0
MOCK_SHFMT
  chmod +x "$mock_bin/shfmt"

  cd "$temp_dir"
  git init -q
  git config user.email "test@test.com"
  git config user.name "Test"

  echo "#!/bin/bash" >script.sh
  git add script.sh

  export PATH="$mock_bin:$PATH"

  set +e
  "$HOOK_SCRIPT" >output.txt 2>&1
  local exit_code=$?
  local output
  output=$(cat output.txt)
  set -e

  export PATH="$original_path"

  local formatted_content
  formatted_content=$(cat script.sh)

  cd "$original_dir"
  rm -rf "$temp_dir"

  assert_equals "0" "$exit_code" "Hook exits successfully after formatting shell files"
  assert_contains "$output" "Formatted: script.sh" "Hook reports formatted shell file"
  assert_contains "$formatted_content" "# formatted by shfmt" "Shell file was actually formatted"
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
test_hook_handles_missing_sbt
test_hook_handles_scalafmt_failure
test_hook_handles_missing_goimports
test_hook_formats_scala_files_integration
test_hook_formats_go_files_integration
test_hook_detects_staged_shell_files
test_hook_handles_missing_shfmt
test_hook_formats_shell_files_integration

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
