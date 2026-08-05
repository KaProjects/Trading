#!/usr/bin/env bash

set -uo pipefail

BASE_URL="${BASE_URL:-http://localhost:7703}"
ROUNDS="${ROUNDS:-0}"
YEAR="${YEAR:-$(date +%Y)}"
COMPANY_ID="${COMPANY_ID:-}"
ORIGIN="${ORIGIN:-}"

if ! command -v curl >/dev/null 2>&1; then
  printf 'ERROR: curl is required.\n' >&2
  exit 127
fi

if [[ ! "$ROUNDS" =~ ^[0-9]+$ ]]; then
  printf 'ERROR: ROUNDS must be a non-negative integer.\n' >&2
  exit 2
fi

if [[ ! "$YEAR" =~ ^[0-9]{4}$ ]]; then
  printf 'ERROR: YEAR must use the YYYY format.\n' >&2
  exit 2
fi

BASE_URL="${BASE_URL%/}"

endpoints=(
  '/company/values'
  '/company/'
  '/company/lists'
  '/trade/'
  '/trade/?active=true'
  '/trade/?active=false'
  "/trade/?year=$YEAR"
  '/dividend/'
  "/dividend/?year=$YEAR"
  '/stats/company'
  "/stats/company?year=$YEAR"
  '/stats/monthly'
  '/stats/quarterly'
  '/stats/yearly'
)

if [[ -n "$COMPANY_ID" ]]; then
  endpoints+=(
    "/trade/?companyId=$COMPANY_ID"
    "/dividend/?companyId=$COMPANY_ID"
    "/stats/monthly?companyId=$COMPANY_ID"
    "/stats/quarterly?companyId=$COMPANY_ID"
    "/stats/yearly?companyId=$COMPANY_ID"
    "/research/$COMPANY_ID"
  )
fi

curl_arguments=(
  --silent
  --show-error
  --location
  --output /dev/null
  --write-out '%{http_code}'
  --connect-timeout 5
  --max-time 60
)

if [[ -n "$ORIGIN" ]]; then
  curl_arguments+=(--header "Origin: $ORIGIN")
fi

successful=0

request() {
  local endpoint="$1"
  local status

  if ! status="$(curl "${curl_arguments[@]}" "$BASE_URL$endpoint")"; then
    printf 'FAILED curl error: %s\n' "$endpoint" >&2
    return 1
  fi

  if [[ "$status" == 2?? ]]; then
    successful=$((successful + 1))
    return 0
  else
    printf 'FAILED HTTP %s: %s\n' "$status" "$endpoint" >&2
    return 1
  fi
}

if [[ $ROUNDS -eq 0 ]]; then
  printf 'Sending GET workload to %s until interrupted (%s endpoints per round).\n' \
    "$BASE_URL" "${#endpoints[@]}"
else
  printf 'Sending GET workload to %s (%s rounds, %s endpoints per round).\n' \
    "$BASE_URL" "$ROUNDS" "${#endpoints[@]}"
fi

round=1
while [[ $ROUNDS -eq 0 || $round -le $ROUNDS ]]; do
  if [[ $ROUNDS -eq 0 ]]; then
    printf 'Round %s\n' "$round"
  else
    printf 'Round %s/%s\n' "$round" "$ROUNDS"
  fi

  for endpoint in "${endpoints[@]}"; do
    if ! request "$endpoint"; then
      printf 'Stopped after %s successful requests.\n' "$successful" >&2
      exit 1
    fi
  done

  round=$((round + 1))
done

printf 'Completed: %s successful requests.\n' "$successful"
