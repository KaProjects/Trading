#!/bin/sh
set -u

project_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
cd "${project_dir}"

if [ -n "${PYTHON:-}" ]; then
    python_bin="${PYTHON}"
elif [ -x "${project_dir}/venv/bin/python" ]; then
    python_bin="${project_dir}/venv/bin/python"
else
    python_bin="python3"
fi

if "${python_bin}" -m pytest test "$@"; then
    printf '\nAll tests passed.\n'
else
    status=$?
    printf '\nTests failed with exit code %s.\n' "${status}" >&2
    exit "${status}"
fi
