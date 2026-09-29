#!/usr/bin/env bash
# token 守卫的双向验证（合成数据，不依赖后端与 Redis，可随时离线运行）。
#
# 背景：`redis-degradation.sh` 的结论完全依赖「故障实例上的失败是 Redis 造成的」这一因果。
# v1 脚本因为没有校验 token 有效期，把 `ExpiredJwtException` 造成的 401 误读成
# 「Redis 不可用导致已登录用户被登出」，报告结论被迫撤回。此后守卫成了这套脚本的
# 关键前提，因此必须验证它**既能放行有效 token，也能拦下失效 token**——
# 只验证「能放行」是单向验证，等于没验证。
#
# 本测试从 `redis-degradation.sh` 里 **现场提取** 函数定义，而不是复制一份，
# 避免出现「测的代码和跑的代码不是同一份」。
#
# 用法： bash leo/perf/test/token-guard-selftest.sh
set -uo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
SCRIPT="$REPO_ROOT/leo/perf/redis-degradation.sh"
[[ -f "$SCRIPT" ]] || { echo "找不到 $SCRIPT" >&2; exit 1; }

FUNCS="$(mktemp)"
trap 'rm -f "$FUNCS"' EXIT
sed -n '/^log()/,/^fail()/p;/^token_remaining_seconds()/,/^}/p;/^assert_token_valid()/,/^}/p' "$SCRIPT" > "$FUNCS"
grep -q '^assert_token_valid()' "$FUNCS" || { echo "未能从 $SCRIPT 提取到 assert_token_valid，测试无效" >&2; exit 1; }
# shellcheck disable=SC1090
source "$FUNCS"

mk() { # mk <exp_offset_seconds>：生成一个只有 exp 有意义的合成 JWT
  python3 -c "
import base64, json, sys, time
def enc(b): return base64.urlsafe_b64encode(b).rstrip(b'=').decode()
h = enc(json.dumps({'alg':'HS512','typ':'JWT'}, separators=(',',':')).encode())
p = enc(json.dumps({'sub':'1','exp':int(time.time())+int(sys.argv[1])}, separators=(',',':')).encode())
print(h + '.' + p + '.' + enc(b'signature-' + str(sys.argv[1]).encode()))
" "$1"
}

pass=0; failed=0
chk() { # chk <期望 pass|fail> <描述> <token>
  local want="$1" desc="$2" tok="$3" rc
  ( TOKEN="$tok"; assert_token_valid "selftest" ) >/dev/null 2>&1; rc=$?
  local actual; actual=$([[ $rc -eq 0 ]] && echo pass || echo fail)
  if [[ "$want" == "$actual" ]]; then
    printf '  ✅ %s -> 期望 %s，实际 %s\n' "$desc" "$want" "$actual"; pass=$((pass+1))
  else
    printf '  ❌ %s -> 期望 %s，实际 %s\n' "$desc" "$want" "$actual"; failed=$((failed+1))
  fi
  printf '     剩余秒数=%s\n' "$(token_remaining_seconds "$tok")"
}

chk pass "exp=+300s 的有效 token"          "$(mk 300)"
chk fail "exp=-60s 的过期 token"            "$(mk -60)"
chk fail "exp=+30s（低于 120s 阈值）"       "$(mk 30)"
chk fail "完全无法解析的 token"             "not-a-jwt"

# 篡改 token 的构造必须只改签名段：否则 exp 也可能被改动，判别探针就失去了区分能力。
T_OK="$(mk 300)"; CORRUPTED="${T_OK%????}AAAA"
if [[ "$(printf '%s' "$T_OK" | cut -d. -f1,2)" == "$(printf '%s' "$CORRUPTED" | cut -d. -f1,2)" \
   && "$(printf '%s' "$T_OK" | cut -d. -f3)" != "$(printf '%s' "$CORRUPTED" | cut -d. -f3)" ]]; then
  echo "  ✅ 篡改 token 只改签名段（header.payload 不变，签名不同）"; pass=$((pass+1))
else
  echo "  ❌ 篡改 token 构造错误"; failed=$((failed+1))
fi

echo "结果: pass=$pass fail=$failed"
[[ $failed -eq 0 ]]
