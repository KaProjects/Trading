#!/usr/bin/env bash

set -uo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MODE='prod'
MODE_SET=0
USE_PROD_DB=0
USE_NATIVE=0
USE_LOW_MEMORY=0

usage() {
  printf 'Usage: %s [dev [--db-prod] | [prod] [--native] [--low-memory]]\n' \
    "${0##*/}" >&2
}

for argument in "$@"; do
  case "$argument" in
    dev|prod)
      if [[ $MODE_SET -eq 1 ]]; then
        usage
        exit 2
      fi
      MODE="$argument"
      MODE_SET=1
      ;;
    --db-prod)
      USE_PROD_DB=1
      ;;
    --native)
      USE_NATIVE=1
      ;;
    --low-memory)
      USE_LOW_MEMORY=1
      ;;
    *)
      usage
      exit 2
      ;;
  esac
done

if [[ ( $USE_PROD_DB -eq 1 && "$MODE" != 'dev' ) \
    || ( $USE_NATIVE -eq 1 && "$MODE" != 'prod' ) \
    || ( $USE_LOW_MEMORY -eq 1 && "$MODE" != 'prod' ) ]]; then
  usage
  exit 2
fi

MODULE_NAMES=('backend' 'frontend')
MODULE_LABELS=('BACKEND' 'FRONTEND')

USE_TTY_PROGRESS=0
if command -v script >/dev/null 2>&1 \
    && script -q /dev/null true </dev/null >/dev/null 2>&1; then
  USE_TTY_PROGRESS=1
fi

LOG_DIR="$(mktemp -d "${TMPDIR:-/tmp}/trading-build.XXXXXX")"
MODULE_LOGS=()
MODULE_PIDS=()
MODULE_STATUSES=()
dashboard_rendered=0
dashboard_rows=0
dashboard_width=0
cursor_hidden=0

show_cursor() {
  if [[ $cursor_hidden -eq 1 ]]; then
    printf '\033[?25h'
    cursor_hidden=0
  fi
}

children_of() {
  local parent_pid="$1"

  if command -v pgrep >/dev/null 2>&1; then
    pgrep -P "$parent_pid" 2>/dev/null || true
  else
    ps -eo pid=,ppid= 2>/dev/null \
      | awk -v parent_pid="$parent_pid" '$2 == parent_pid { print $1 }'
  fi
}

collect_process_tree() {
  local root_pid="$1"
  local child_pid

  for child_pid in $(children_of "$root_pid"); do
    collect_process_tree "$child_pid"
  done
  printf '%s\n' "$root_pid"
}

terminate_process_tree() {
  local root_pid="$1"
  local process_ids
  local process_id
  local attempt
  local processes_alive

  [[ -n "$root_pid" ]] || return
  process_ids="$(collect_process_tree "$root_pid")"

  for process_id in $process_ids; do
    kill -TERM "$process_id" 2>/dev/null || true
  done

  for ((attempt = 0; attempt < 20; attempt++)); do
    processes_alive=0
    for process_id in $process_ids; do
      if kill -0 "$process_id" 2>/dev/null; then
        processes_alive=1
        break
      fi
    done
    [[ $processes_alive -eq 0 ]] && return
    sleep 0.1
  done

  for process_id in $process_ids; do
    kill -KILL "$process_id" 2>/dev/null || true
  done
}

cleanup() {
  show_cursor
  rm -rf "$LOG_DIR"
}

stop_children() {
  local index

  trap - INT TERM
  for index in "${!MODULE_PIDS[@]}"; do
    terminate_process_tree "${MODULE_PIDS[$index]}"
  done
  for index in "${!MODULE_PIDS[@]}"; do
    wait "${MODULE_PIDS[$index]}" 2>/dev/null || true
  done
  exit 130
}

trap cleanup EXIT
trap stop_children INT TERM

clean_log_stream() {
  tr '\r\t' '\n ' \
    | sed $'s/\\^D\010\010//g' \
    | tr -d '\004\010' \
    | sed $'s|\033\\[[0-9;?]*[ -/]*[@-~]||g' \
    | sed '/^[[:space:]]*$/d'
}

clean_log() {
  clean_log_stream < "$1"
}

recent_clean_log() {
  local file="$1"
  local lines="$2"

  tail -c 131072 "$file" | clean_log_stream | tail -n "$lines"
}

terminal_width() {
  local size
  local width

  size="$(stty size < /dev/tty 2>/dev/null || true)"
  width="${size##* }"
  if [[ ! "$width" =~ ^[0-9]+$ ]] || [[ $width -lt 20 ]]; then
    width="${COLUMNS:-120}"
  fi
  if [[ ! "$width" =~ ^[0-9]+$ ]] || [[ $width -lt 20 ]]; then
    width=120
  fi
  printf '%s' "$width"
}

terminal_height() {
  local size
  local height
  local minimum_height

  minimum_height=$(( ${#MODULE_NAMES[@]} * 3 ))
  size="$(stty size < /dev/tty 2>/dev/null || true)"
  height="${size%% *}"
  if [[ ! "$height" =~ ^[0-9]+$ ]] || [[ $height -lt $minimum_height ]]; then
    height="${LINES:-24}"
  fi
  if [[ ! "$height" =~ ^[0-9]+$ ]] || [[ $height -lt $minimum_height ]]; then
    height=24
  fi
  printf '%s' "$height"
}

render_line() {
  local content="$1"
  local width="$2"
  local color="${3:-}"

  printf '\033[2K\r'
  [[ -n "$color" ]] && printf '%s' "$color"
  printf '%-*.*s' "$width" "$width" "$content"
  [[ -n "$color" ]] && printf '\033[0m'
  printf '\n'
}

module_title() {
  local index="$1"
  local status="${MODULE_STATUSES[$index]}"

  if [[ -z "$status" ]]; then
    printf '%s [RUNNING]' "${MODULE_LABELS[$index]}"
  elif [[ $status -eq 0 ]]; then
    printf '%s [FINISHED]' "${MODULE_LABELS[$index]}"
  else
    printf '%s [FAILED]' "${MODULE_LABELS[$index]}"
  fi
}

render_dashboard() {
  local width
  local height
  local render_width
  local module_count
  local available_log_lines
  local base_panel_lines
  local extra_lines
  local panel_lines
  local separator
  local title
  local status
  local color
  local red=''
  local line
  local index
  local line_index
  local lines=()

  module_count=${#MODULE_NAMES[@]}
  if [[ ! -t 1 ]]; then
    printf -v separator '%*s' 80 ''
    separator="${separator// /-}"
    for index in "${!MODULE_NAMES[@]}"; do
      printf '%s\n' "$(module_title "$index")"
      clean_log "${MODULE_LOGS[$index]}"
      if [[ $index -lt $((module_count - 1)) ]]; then
        printf '%s\n' "$separator"
      fi
    done
    return
  fi

  width="$(terminal_width)"
  height="$(terminal_height)"
  render_width=$((width - 1))
  # Leave the terminal's final row unused so the last newline cannot scroll.
  available_log_lines=$((height - (module_count * 2)))
  [[ $available_log_lines -lt $module_count ]] \
    && available_log_lines=$module_count
  base_panel_lines=$((available_log_lines / module_count))
  extra_lines=$((available_log_lines % module_count))
  printf -v separator '%*s' "$render_width" ''
  separator="${separator// /-}"

  if [[ -t 1 ]]; then
    red=$'\033[31m'
  fi

  if [[ $dashboard_rendered -eq 0 \
      || $dashboard_rows -ne $height \
      || $dashboard_width -ne $width ]]; then
    printf '\033[2J\033[H'
  else
    printf '\033[H'
  fi

  for index in "${!MODULE_NAMES[@]}"; do
    panel_lines=$base_panel_lines
    [[ $index -lt $extra_lines ]] && panel_lines=$((panel_lines + 1))
    title="$(module_title "$index")"
    status="${MODULE_STATUSES[$index]}"
    color=''
    if [[ -n "$status" && $status -ne 0 ]]; then
      color="$red"
    fi
    render_line "$title" "$render_width" "$color"

    lines=()
    while IFS= read -r line; do
      lines[${#lines[@]}]="$line"
    done < <(recent_clean_log "${MODULE_LOGS[$index]}" "$panel_lines")

    for ((line_index = 0; line_index < panel_lines; line_index++)); do
      line="${lines[$line_index]-}"
      if [[ "$line" == *'ERROR:'* ]]; then
        render_line "$line" "$render_width" "$red"
      else
        render_line "$line" "$render_width"
      fi
    done

    if [[ $index -lt $((module_count - 1)) ]]; then
      render_line "$separator" "$render_width"
    fi
  done

  dashboard_rendered=1
  dashboard_rows=$height
  dashboard_width=$width
}

all_finished() {
  local index

  for index in "${!MODULE_NAMES[@]}"; do
    [[ -n "${MODULE_STATUSES[$index]}" ]] || return 1
  done
  return 0
}

any_failed() {
  local index
  local status

  for index in "${!MODULE_NAMES[@]}"; do
    status="${MODULE_STATUSES[$index]}"
    if [[ -n "$status" && $status -ne 0 ]]; then
      return 0
    fi
  done
  return 1
}

for index in "${!MODULE_NAMES[@]}"; do
  module="${MODULE_NAMES[$index]}"
  module_script="$ROOT_DIR/$module/build_deploy.sh"
  if [[ ! -x "$module_script" ]]; then
    printf 'Module launcher is missing or not executable: %s\n' \
      "$module_script" >&2
    exit 127
  fi

  log_file="$LOG_DIR/$module.log"
  : > "$log_file"
  MODULE_LOGS[$index]="$log_file"
  MODULE_STATUSES[$index]=''
  module_args=("$MODE")
  if [[ "$module" == 'backend' && $USE_PROD_DB -eq 1 ]]; then
    module_args+=('--db-prod')
  fi
  if [[ "$module" == 'backend' && $USE_NATIVE -eq 1 ]]; then
    module_args+=('--native')
  fi
  if [[ "$module" == 'backend' && $USE_LOW_MEMORY -eq 1 ]]; then
    module_args+=('--low-memory')
  fi

  (
    trap '' INT
    cd "$ROOT_DIR/$module" || exit 1
    if [[ "$MODE" == 'prod' && $USE_TTY_PROGRESS -eq 1 ]]; then
      script -q /dev/null env BUILDKIT_PROGRESS=tty \
        ./build_deploy.sh "${module_args[@]}"
    else
      BUILDKIT_PROGRESS=plain ./build_deploy.sh "${module_args[@]}"
    fi
  ) >"$log_file" 2>&1 &
  MODULE_PIDS[$index]=$!
done

if [[ -t 1 ]]; then
  printf '\033[?25l'
  cursor_hidden=1
fi

while ! all_finished; do
  for index in "${!MODULE_NAMES[@]}"; do
    if [[ -z "${MODULE_STATUSES[$index]}" ]] \
        && ! kill -0 "${MODULE_PIDS[$index]}" 2>/dev/null; then
      wait "${MODULE_PIDS[$index]}"
      MODULE_STATUSES[$index]=$?
    fi
  done

  if any_failed || { [[ "$MODE" == 'dev' ]] && ! all_finished; }; then
    completed=0
    for index in "${!MODULE_NAMES[@]}"; do
      [[ -n "${MODULE_STATUSES[$index]}" ]] && completed=1
    done
    if [[ $completed -eq 1 ]]; then
      for index in "${!MODULE_NAMES[@]}"; do
        if [[ -z "${MODULE_STATUSES[$index]}" ]]; then
          terminate_process_tree "${MODULE_PIDS[$index]}"
          wait "${MODULE_PIDS[$index]}" 2>/dev/null
          MODULE_STATUSES[$index]=$?
        fi
      done
    fi
  fi

  if [[ -t 1 ]]; then
    render_dashboard
  fi
  all_finished || sleep 0.2
done

render_dashboard
show_cursor

exit_status=0
for index in "${!MODULE_NAMES[@]}"; do
  status="${MODULE_STATUSES[$index]}"
  if [[ $status -ne 0 ]]; then
    exit_status=1
  fi
done

if [[ $exit_status -ne 0 ]]; then
  printf 'Build/deploy failed. See the module output above.\n' >&2
fi
exit "$exit_status"
