#!/usr/bin/env bash

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  echo "请通过 source 加载 scripts/env/prod.sh。" >&2
  exit 1
fi

LEO_RUNTIME_ENV=prod

# shellcheck disable=SC1091
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/common.sh"

load_workspace_env

export LEO_RUNTIME_ENV=prod
export SPRING_PROFILES_ACTIVE=prod
export SPRING_DATASOURCE_DB="${LEO_PROD_DATASOURCE_DB:-prod}"
export SPRING_AI_MCP_SERVER_ENABLED="${SPRING_AI_MCP_SERVER_ENABLED:-false}"

set_backend_common_defaults
