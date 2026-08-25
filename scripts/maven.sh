#!/usr/bin/env bash

set -Eeuo pipefail

if ! command -v mvn >/dev/null 2>&1; then
  printf 'Required command is unavailable: mvn\n' >&2
  exit 127
fi

# Keep user-level Maven startup files from overriding the project JDK setup.
export MAVEN_SKIP_RC=true

exec mvn "$@"
