#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEPLOY_DIR="$(cd "$SCRIPT_DIR/../deploy" && pwd)"

[[ -f "$DEPLOY_DIR/.env.dev" ]] && {
  set -a
  . "$DEPLOY_DIR/.env.dev"
  set +a
}

usage() {
  printf 'Usage: %s\n' "${0##*/}" >&2
  exit 2
}

[[ $# -eq 0 ]] || usage

node_is_supported() {
  local executable="$1"

  "$executable" -e '
    const major = Number(process.versions.node.split(".")[0])
    process.exit(major >= 18 ? 0 : 1)
  ' >/dev/null 2>&1
}

activate_development_node() {
  local requested_version="${TRADING_NODE_VERSION:-23.11.0}"
  local candidate

  export NVM_DIR="${NVM_DIR:-$HOME/.nvm}"
  if [[ -s "$NVM_DIR/nvm.sh" ]]; then
    set +u
    source "$NVM_DIR/nvm.sh"
    set -u

    if nvm use --silent "$requested_version" >/dev/null 2>&1; then
      printf 'Using Node.js %s for frontend development.\n' "$(node --version)"
      return 0
    fi
  fi

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
      hash -r
      printf 'Using Node.js %s from %s for frontend development.\n' \
        "$(node --version)" "$(dirname "$candidate")"
      return 0
    fi
  done

  printf 'Frontend development requires Node.js 18 or newer.\n' >&2
  return 127
}

cd "$SCRIPT_DIR"

activate_development_node || exit $?
exec env REACT_APP_POLYGON_API_KEY="${REACT_APP_POLYGON_API_KEY:-${POLYGON_API_KEY:-}}" \
  npm start
