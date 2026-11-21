#!/usr/bin/env bash
set -euo pipefail
PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
exec bash "$PROJECT_DIR/backend/scripts/scylla-tombstone-experiment.sh" "$@"
