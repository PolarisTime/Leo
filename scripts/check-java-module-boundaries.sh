#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
REPOSITORY_ROOT="$(cd -- "$SCRIPT_DIR/.." && pwd -P)"
VERIFIER_CLASS="com.leo.erp.ModuleBoundaryVerifier"
VERIFIER_SOURCE="$SCRIPT_DIR/ModuleBoundaryVerifier.java"
TEMPORARY_DIRECTORY=""

handle_error() {
  local -r exit_code="$?"
  local -r line_number="$1"
  printf 'Architecture boundary check failed at line %s (exit %s).\n' \
    "$line_number" "$exit_code" >&2
  exit "$exit_code"
}

cleanup() {
  if [[ -n "$TEMPORARY_DIRECTORY" && -d "$TEMPORARY_DIRECTORY" ]]; then
    rm -rf -- "$TEMPORARY_DIRECTORY"
  fi
}

usage() {
  printf 'Usage: %s [--write-baseline]\n' "${BASH_SOURCE[0]}"
}

trap 'handle_error "$LINENO"' ERR
trap cleanup EXIT

for command_name in java javac mvn; do
  if ! command -v "$command_name" >/dev/null 2>&1; then
    printf 'Required command is unavailable: %s\n' "$command_name" >&2
    exit 2
  fi
done

VERIFIER_ARGS=()
case "${1:-}" in
  "")
    if [[ $# -ne 0 ]]; then
      usage >&2
      exit 2
    fi
    ;;
  --write-baseline)
    if [[ $# -ne 1 ]]; then
      usage >&2
      exit 2
    fi
    VERIFIER_ARGS=(--refreeze)
    ;;
  -h|--help)
    usage
    exit 0
    ;;
  *)
    printf 'Unknown option: %s\n' "$1" >&2
    usage >&2
    exit 2
    ;;
esac

TEMPORARY_DIRECTORY="$(mktemp -d)"
readonly TEMPORARY_DIRECTORY
CLASSPATH_FILE="$TEMPORARY_DIRECTORY/runtime-classpath.txt"
readonly CLASSPATH_FILE
VERIFIER_CLASSES="$TEMPORARY_DIRECTORY/classes"
readonly VERIFIER_CLASSES

(
  cd -- "$REPOSITORY_ROOT"
  bash "$SCRIPT_DIR/maven.sh" -B -ntp -DskipTests -Parchitecture-verification compile dependency:build-classpath \
    "-Dmdep.outputFile=$CLASSPATH_FILE"
)

if [[ ! -s "$CLASSPATH_FILE" ]]; then
  printf 'Maven did not produce a runtime classpath file: %s\n' "$CLASSPATH_FILE" >&2
  exit 2
fi

RUNTIME_CLASSPATH="$(<"$CLASSPATH_FILE")"
readonly RUNTIME_CLASSPATH
mkdir -- "$VERIFIER_CLASSES"
javac \
  -proc:none \
  -cp "$REPOSITORY_ROOT/target/classes:$RUNTIME_CLASSPATH" \
  -d "$VERIFIER_CLASSES" \
  "$VERIFIER_SOURCE"
(
  cd -- "$REPOSITORY_ROOT"
  java \
    -cp "$VERIFIER_CLASSES:$REPOSITORY_ROOT/target/classes:$RUNTIME_CLASSPATH" \
    "$VERIFIER_CLASS" \
    ${VERIFIER_ARGS[@]+"${VERIFIER_ARGS[@]}"}
)
