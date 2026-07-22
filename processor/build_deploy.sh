#!/bin/sh
set -eu

project_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
image="trader-processor:v1.2"
env_file="${project_dir}/envs.json"
cert_file="${project_dir}/cert.json"

for required_file in "${env_file}" "${cert_file}"; do
    if [ ! -f "${required_file}" ]; then
        printf 'Missing required file: %s\n' "${required_file}" >&2
        exit 1
    fi
done

docker build --tag "${image}" "${project_dir}"

docker run --detach \
    --mount "type=bind,source=${env_file},target=/workdir/envs.json,readonly" \
    --mount "type=bind,source=${cert_file},target=/workdir/cert.json,readonly" \
    "${image}"
