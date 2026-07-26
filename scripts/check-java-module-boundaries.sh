#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
REPOSITORY_ROOT="$(cd -- "$SCRIPT_DIR/.." && pwd -P)"
SCANNER="$SCRIPT_DIR/check_java_module_boundaries.py"
SOURCE_ROOT="$REPOSITORY_ROOT/src/main/java"
BASELINE="$REPOSITORY_ROOT/config/java-module-boundaries-baseline.json"
PYTHON_COMMAND="${LEO_ARCHITECTURE_PYTHON:-python3}"

handle_error() {
  local -r exit_code="$?"
  local -r line_number="$1"
  printf 'Architecture boundary wrapper failed at line %s (exit %s).\n' \
    "$line_number" "$exit_code" >&2
  exit "$exit_code"
}

usage() {
  printf 'Usage: %s [--write-baseline | --self-test]\n' "${BASH_SOURCE[0]}"
}

trap 'handle_error "$LINENO"' ERR

if ! command -v "$PYTHON_COMMAND" >/dev/null 2>&1; then
  printf 'Required Python command is unavailable: %s\n' "$PYTHON_COMMAND" >&2
  exit 2
fi
if [[ ! -r "$SCANNER" ]]; then
  printf 'Architecture scanner is not readable: %s\n' "$SCANNER" >&2
  exit 2
fi

case "${1:-}" in
  "")
    if [[ $# -ne 0 ]]; then
      usage >&2
      exit 2
    fi
    exec "$PYTHON_COMMAND" "$SCANNER" \
      --source-root "$SOURCE_ROOT" \
      --baseline "$BASELINE"
    ;;
  --write-baseline)
    if [[ $# -ne 1 ]]; then
      usage >&2
      exit 2
    fi
    exec "$PYTHON_COMMAND" "$SCANNER" \
      --source-root "$SOURCE_ROOT" \
      --baseline "$BASELINE" \
      --write-baseline
    ;;
  --self-test)
    if [[ $# -ne 1 ]]; then
      usage >&2
      exit 2
    fi
    exec "$PYTHON_COMMAND" "$SCANNER" --self-test
    ;;
  -h|--help)
    usage
    ;;
  *)
    printf 'Unknown option: %s\n' "$1" >&2
    usage >&2
    exit 2
    ;;
esac
