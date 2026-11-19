#!/usr/bin/env bash
set -euo pipefail

docker run --privileged --rm alpine sh -c "echo 1048576 > /proc/sys/fs/aio-max-nr"
docker compose up -d "$@"
