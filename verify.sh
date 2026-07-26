#!/usr/bin/env bash

set -uo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$ROOT_DIR/backend"
FRONTEND_DIR="$ROOT_DIR/frontend"

STEP_NAMES=()
STEP_RESULTS=()
STEP_DURATIONS=()
PASSED=0
FAILED=0

if [[ -t 1 ]]; then
  GREEN=$'\033[32m'
  RED=$'\033[31m'
  BOLD=$'\033[1m'
  RESET=$'\033[0m'
else
  GREEN=''
  RED=''
  BOLD=''
  RESET=''
fi

java_is_25() {
  local version

  version="$("$1" -version 2>&1)" || return 1
  grep -Eq 'version "25(\.|")' <<< "$version"
}

select_java_25() {
  local candidate

  if [[ -n "${JAVA_HOME:-}" ]] \
      && [[ -x "$JAVA_HOME/bin/java" ]] \
      && java_is_25 "$JAVA_HOME/bin/java"; then
    export PATH="$JAVA_HOME/bin:$PATH"
    return 0
  fi

  if command -v java >/dev/null 2>&1 && java_is_25 "$(command -v java)"; then
    return 0
  fi

  for candidate in \
      /Library/Java/JavaVirtualMachines/graalvm-jdk-25*/Contents/Home \
      /Library/Java/JavaVirtualMachines/jdk-25*.jdk/Contents/Home \
      "$HOME"/Library/Java/JavaVirtualMachines/graalvm-jdk-25*/Contents/Home \
      "$HOME"/Library/Java/JavaVirtualMachines/jdk-25*.jdk/Contents/Home; do
    [[ -x "$candidate/bin/java" ]] || continue
    export JAVA_HOME="$candidate"
    export PATH="$JAVA_HOME/bin:$PATH"
    printf 'Using Java 25 from %s.\n' "$JAVA_HOME"
    return 0
  done

  printf 'Java 25 is required for backend checks.\n'
  return 127
}

node_is_supported() {
  local executable="$1"

  "$executable" -e '
    const major = Number(process.versions.node.split(".")[0])
    process.exit(major >= 18 ? 0 : 1)
  ' >/dev/null 2>&1
}

select_compatible_node() {
  local candidate

  if command -v node >/dev/null 2>&1 \
      && node_is_supported "$(command -v node)"; then
    return 0
  fi

  for candidate in \
      "$HOME"/.nvm/versions/node/v23.11.0/bin/node \
      "$HOME"/.nvm/versions/node/v*/bin/node \
      /opt/homebrew/bin/node \
      /usr/local/bin/node; do
    [[ -x "$candidate" ]] || continue
    if node_is_supported "$candidate"; then
      export PATH="$(dirname "$candidate"):$PATH"
      printf 'Using Node.js %s from %s.\n' \
        "$(node --version)" "$(dirname "$candidate")"
      return 0
    fi
  done

  printf 'Node.js 18 or newer is required for frontend checks.\n'
  return 127
}

run_step() {
  local name="$1"
  shift
  local started status duration result

  printf '\n%s==> %s%s\n' "$BOLD" "$name" "$RESET"
  started=$(date +%s)

  "$@"
  status=$?
  duration=$(( $(date +%s) - started ))

  if [[ $status -eq 0 ]]; then
    result='PASS'
    PASSED=$((PASSED + 1))
    printf '%sPASS%s %s (%ss)\n' "$GREEN" "$RESET" "$name" "$duration"
  else
    result='FAIL'
    FAILED=$((FAILED + 1))
    printf '%sFAIL%s %s (%ss, exit %s)\n' \
      "$RED" "$RESET" "$name" "$duration" "$status"
  fi

  STEP_NAMES+=("$name")
  STEP_RESULTS+=("$result")
  STEP_DURATIONS+=("$duration")
  return 0
}

require_backend_environment() {
  select_java_25 || return $?
  if [[ ! -x "$BACKEND_DIR/mvnw" ]]; then
    printf 'Maven wrapper is missing: %s/mvnw\n' "$BACKEND_DIR"
    return 127
  fi
}

backend_tests() {
  require_backend_environment || return $?
  cd "$BACKEND_DIR" || return 1
  ./mvnw test
}

backend_package() {
  require_backend_environment || return $?
  cd "$BACKEND_DIR" || return 1
  ./mvnw clean package -Dmaven.test.skip=true
}

require_frontend_environment() {
  select_compatible_node || return $?
  if [[ ! -x "$FRONTEND_DIR/node_modules/.bin/react-scripts" \
      || ! -x "$FRONTEND_DIR/node_modules/.bin/eslint" ]]; then
    printf 'Frontend dependencies are missing. Run:\n'
    printf '  cd %s && npm ci\n' "$FRONTEND_DIR"
    return 127
  fi
}

frontend_dependencies() {
  select_compatible_node || return $?
  cd "$FRONTEND_DIR" || return 1
  npm install
}

frontend_lint() {
  require_frontend_environment || return $?
  cd "$FRONTEND_DIR" || return 1
  ./node_modules/.bin/eslint src
}

frontend_component_tests() {
  require_frontend_environment || return $?
  cd "$FRONTEND_DIR" || return 1
  CI=true ./node_modules/.bin/react-scripts test \
    --watchAll=false \
    --coverage=false
}

frontend_build() {
  require_frontend_environment || return $?
  cd "$FRONTEND_DIR" || return 1
  REACT_APP_BACKEND_URL="${REACT_APP_BACKEND_URL:-http://127.0.0.1:9090}" \
    npm run build
}

print_summary() {
  local index result color

  printf '\n%s================ CHECK SUMMARY ================%s\n' \
    "$BOLD" "$RESET"
  for index in "${!STEP_NAMES[@]}"; do
    result="${STEP_RESULTS[$index]}"
    if [[ "$result" == 'PASS' ]]; then
      color="$GREEN"
    else
      color="$RED"
    fi
    printf '%s%-4s%s  %-42s %4ss\n' \
      "$color" "$result" "$RESET" \
      "${STEP_NAMES[$index]}" "${STEP_DURATIONS[$index]}"
  done

  printf '\nPassed: %s  Failed: %s\n' "$PASSED" "$FAILED"
}

printf '%sTrading local checks%s\n' "$BOLD" "$RESET"
printf 'Workspace: %s\n' "$ROOT_DIR"

run_step 'Backend - tests' backend_tests
run_step 'Backend - production package' backend_package
run_step 'Frontend - dependencies' frontend_dependencies
run_step 'Frontend - ESLint' frontend_lint
run_step 'Frontend - component tests' frontend_component_tests
run_step 'Frontend - production build' frontend_build

print_summary

if [[ $FAILED -gt 0 ]]; then
  exit 1
fi
