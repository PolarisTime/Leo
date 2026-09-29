#!/usr/bin/env bash
# 清理压测遗留数据：按 customerName 前缀检索并逐个删除。
#
# 压测脚本创建的客户 customerName 统一以 PERF-LOAD- 开头（含批次号），
# 本脚本默认先「预演计数」，加 --yes 才真正执行删除。
#
# 用法：
#   source tmp/perf/creds.env
#   bash leo/perf/cleanup-perf-data.sh                 # 只统计，不删除
#   bash leo/perf/cleanup-perf-data.sh --yes            # 删除全部 PERF-LOAD-* 客户
#   bash leo/perf/cleanup-perf-data.sh --yes PERF-LOAD-run20260101-120000   # 只删指定批次
set -euo pipefail

# ---- 附件清理（默认不做，需显式 --attachments）----------------------------------
# 为什么走 SQL 而不是 API：附件**没有删除接口**（只有 upload / access-url / content），
# 压测上传的附件无法通过业务接口回收。这里是**测试数据清理**（不是 schema 变更，
# 也不进 Flyway），仅删除文件名带 `perf-attach-` 标记的记录与本地文件。
#
# 用法：
#   bash leo/perf/cleanup-perf-data.sh --attachments                      # 只统计
#   bash leo/perf/cleanup-perf-data.sh --attachments --yes                # 删除全部 perf-attach-*
#   bash leo/perf/cleanup-perf-data.sh --attachments --yes 20260930-0531  # 只删指定批次
cleanup_attachments() {
  local confirm="$1" marker="${2:-}"
  local local_root="${LEO_ATTACHMENT_LOCAL_PATH:-/tmp/leo/uploads}"
  local like="perf-attach-%"
  [[ -n "$marker" ]] && like="perf-attach-${marker}%"

  [[ -n "${SPRING_DATASOURCE_PASSWORD:-}" ]] || {
    echo "附件清理需要数据库凭据：请先 source leo/scripts/env/dev.sh" >&2; exit 1; }
  command -v psql >/dev/null || { echo "缺少 psql，无法清理附件" >&2; exit 1; }

  local psql_cmd=(psql -h "${SPRING_DATASOURCE_HOST:-localhost}" -p "${SPRING_DATASOURCE_PORT:-5432}"
    -U "${SPRING_DATASOURCE_USERNAME:-leo}" -d "${SPRING_DATASOURCE_DB:-leo}" -At)
  export PGPASSWORD="$SPRING_DATASOURCE_PASSWORD"

  local ids body count
  ids="$("${psql_cmd[@]}" -c "select id || '|' || storage_path from sys_attachment where original_file_name like '$like';")"
  count="$(printf '%s\n' "$ids" | grep -c . || true)"
  if [[ "$confirm" != "--yes" ]]; then
    echo "匹配到 $count 条压测附件（文件名 $like）；加 --yes 才会真正删除"
    return 0
  fi
  [[ "$count" -gt 0 ]] || { echo "没有需要清理的压测附件"; return 0; }

  # 保护：解析出的绝对路径必须落在配置的本地存储目录内，且路径里必须含自身 id，
  # 避免「存储路径被污染 → 删掉无关文件」这种不可逆事故。
  local removed_files=0 id path abs file_dir
  while IFS='|' read -r id path; do
    [[ -n "$id" && -n "$path" ]] || continue
    case "$path" in
      local:*) rel="${path#local:}" ;;
      *) echo "跳过非本地存储附件 id=$id path=$path" >&2; continue ;;
    esac
    abs="$local_root/$rel"
    file_dir="$(dirname "$abs")"
    case "$abs" in
      "$local_root"/*) ;;
      *) echo "拒绝删除越界路径：$abs" >&2; continue ;;
    esac
    [[ "$file_dir" == *"/$id/"* || "$file_dir" == *"/$id" ]] || { echo "路径与 id 不匹配，跳过 id=$id dir=$file_dir" >&2; continue; }
    if [[ -d "$file_dir" ]]; then rm -rf -- "$file_dir" && removed_files=$((removed_files + 1)); fi
  done <<< "$ids"

  # 绑定记录由外键 ON DELETE CASCADE 随之删除，这里显式核对一下
  local bindings
  bindings="$("${psql_cmd[@]}" -c "select count(*) from sys_attachment_binding b join sys_attachment a on a.id=b.attachment_id where a.original_file_name like '$like';")"
  [[ "$bindings" == "0" ]] || { echo "仍有 $bindings 条绑定记录，请人工检查" >&2; exit 1; }

  local deleted
  deleted="$("${psql_cmd[@]}" -c "delete from sys_attachment where original_file_name like '$like' returning id;" | grep -c . || true)"
  echo "已删除 $deleted 条压测附件记录、$removed_files 个本地目录（标记 $like）"
}

if [[ "${1:-}" == "--attachments" ]]; then
  shift
  confirm="${1:-}"; [[ "$confirm" == "--yes" ]] && shift || confirm=""
  cleanup_attachments "$confirm" "${1:-}"
  exit 0
fi

PREFIX="${2:-PERF-LOAD-}"
CONFIRM="${1:-}"
BASE_URL="${LEO_PERF_BASE_URL:-http://127.0.0.1:11211/api}"

[[ -n "${LEO_PERF_LOGIN_NAME:-}" && -n "${LEO_PERF_PASSWORD:-}" ]] \
  || { echo "缺少 LEO_PERF_LOGIN_NAME / LEO_PERF_PASSWORD" >&2; exit 1; }

TOKEN="$(curl -s --max-time 10 -X POST "$BASE_URL/v2.0/auth/login" \
  -H 'Content-Type: application/json' \
  -H "X-Idempotency-Key: cleanup-$RANDOM-$RANDOM" \
  -d "{\"loginName\":\"$LEO_PERF_LOGIN_NAME\",\"password\":\"$LEO_PERF_PASSWORD\"}" \
  | jq -r '.accessToken // empty')"
[[ -n "$TOKEN" ]] || { echo "登录失败，无法清理" >&2; exit 1; }

deleted=0
page=0
while :; do
  body="$(curl -s --max-time 20 -H "Authorization: Bearer $TOKEN" \
    --get --data-urlencode "keyword=$PREFIX" --data-urlencode "page=$page" --data-urlencode "size=200" \
    "$BASE_URL/v2.0/customers")"
  ids="$(printf '%s' "$body" | jq -r '(.content // [])[].id // empty' 2>/dev/null || true)"
  [[ -z "$ids" ]] && break

  while IFS= read -r id; do
    [[ -z "$id" ]] && continue
    if [[ "$CONFIRM" == "--yes" ]]; then
      code="$(curl -s -o /dev/null -w '%{http_code}' --max-time 15 -X DELETE \
        "$BASE_URL/v2.0/customers/$id" \
        -H "Authorization: Bearer $TOKEN" \
        -H "X-Idempotency-Key: cleanup-$id-$RANDOM")"
      [[ "$code" == "204" ]] && deleted=$((deleted + 1)) || echo "删除失败 id=$id status=$code" >&2
    else
      deleted=$((deleted + 1))
    fi
  done <<< "$ids"
  page=$((page + 1))
  [[ $page -gt 100 ]] && break
done

if [[ "$CONFIRM" == "--yes" ]]; then
  echo "已删除 $deleted 条压测客户数据（前缀 $PREFIX）"
else
  echo "匹配到 $deleted 条压测客户数据（前缀 $PREFIX）；加 --yes 才会真正删除"
fi

