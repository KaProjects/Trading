#!/bin/sh
set -eu

project_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
cd "${project_dir}"

if [ -n "${PYTHON:-}" ]; then
    python_bin="${PYTHON}"
elif [ -x "${project_dir}/venv/bin/python" ]; then
    python_bin="${project_dir}/venv/bin/python"
else
    python_bin="python3"
fi

exec "${python_bin}" src/main.py "$@"
