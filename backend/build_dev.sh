#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEPLOY_DIR="$(cd "$SCRIPT_DIR/../deploy" && pwd)"
USE_PROD_DB=0

load_env_file() {
  local env_file="$1"
  local line
  local key
  local value

  while IFS= read -r line || [[ -n "$line" ]]; do
    line="${line%$'\r'}"
    [[ -n "$line" ]] || continue
    [[ "$line" =~ ^[[:space:]]*# ]] && continue
    [[ "$line" == *=* ]] || continue

    key="${line%%=*}"
    value="${line#*=}"
    key="${key#"${key%%[![:space:]]*}"}"
    key="${key%"${key##*[![:space:]]}"}"

    export "$key=$value"
  done < "$env_file"
}

[[ -f "$DEPLOY_DIR/.env.dev" ]] && load_env_file "$DEPLOY_DIR/.env.dev"

usage() {
  printf 'Usage: %s [--db-prod]\n' "${0##*/}" >&2
  exit 2
}

[[ $# -le 1 ]] || usage
case "${1:-}" in
  '')
    ;;
  --db-prod)
    USE_PROD_DB=1
    ;;
  *)
    usage
    ;;
esac

java_is_25() {
  local version

  version="$("$1" -version 2>&1)" || return 1
  grep -Eq 'version "25(\.|")' <<< "$version"
}

activate_java_25() {
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

  printf 'Java 25 is required. Set JAVA_HOME to a Java 25 installation.\n' >&2
  return 127
}

resolve_firebase_credentials() {
  local credentials_file
  local credentials_dir

  credentials_file="${FIREBASE_CREDENTIALS_PATH:-$DEPLOY_DIR/firebase-service-account.json}"
  if [[ ! -f "$credentials_file" ]]; then
    printf 'Firebase credentials were not found: %s\n' "$credentials_file" >&2
    return 1
  fi

  credentials_dir="$(cd "$(dirname "$credentials_file")" && pwd)" \
    || return 1
  printf '%s/%s\n' "$credentials_dir" "$(basename "$credentials_file")"
}

require_production_config() {
  local variable

  for variable in DB_KIND JDBC_URL DB_USERNAME DB_PASSWORD FRONTEND_ORIGIN HTTP_PORT FIREBASE_DB_URL FIREBASE_PROJECT_ID FINNHUB_API_URL POLYGON_API_URL ALPHAVANTAGE_API_URL; do
    if [[ -z "${!variable:-}" ]]; then
      printf 'Missing required --db-prod configuration: %s\n' "$variable" >&2
      return 1
    fi
  done
}

cd "$SCRIPT_DIR"

activate_java_25 || exit $?

if [[ $USE_PROD_DB -eq 1 ]]; then
  firebase_credentials_path=''

  require_production_config || exit $?
  firebase_credentials_path="$(resolve_firebase_credentials)" || exit $?
  printf 'WARNING: Development mode is using the production database and real external clients.\n'
  exec env \
    FIREBASE_CREDENTIALS_PATH="$firebase_credentials_path" \
    FRONTEND_ORIGIN="$FRONTEND_ORIGIN" \
    HTTP_PORT="$HTTP_PORT" \
    ./mvnw -Pdev-output clean compile quarkus:dev -Ddebug
fi

if [[ ! -f "$SCRIPT_DIR/src/dev/resources/firebase.json" ]]; then
  firebase_credentials_path="$(resolve_firebase_credentials)" || exit $?
  exec env FIREBASE_CREDENTIALS_PATH="$firebase_credentials_path" \
    ./mvnw -Pdev,dev-output clean compile quarkus:dev -Ddebug
fi

exec ./mvnw -Pdev,dev-output clean compile quarkus:dev -Ddebug
