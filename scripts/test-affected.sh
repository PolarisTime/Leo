#!/usr/bin/env bash
#
# 增量测试：按 Git 变更集映射 JUnit 测试类，本地只跑相关测试。
#
# 设计原则：宁可多选、不可漏测。任何无法可靠判断影响面的变更都回退全量测试；
# 纯文档/脚本变更明确打印“跳过（无相关测试）”，绝不伪装成“测试通过”。
# CI 仍然执行全量 `mvn package`，本脚本只用于本地快速回归。
#
# 文档：leo/docs/incremental-testing.md
#
# 退出码：
#   0 测试通过；或明确跳过（无变更 / 无相关测试）。跳过不等于“测试通过”
#   1 测试失败（Maven 退出码透传）
#   2 参数或环境错误
#   3 仅命中真实库用例（*PostgresTest），默认不跑，本次未验证任何测试

set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
REPOSITORY_ROOT="$(cd -- "$SCRIPT_DIR/.." && pwd -P)"
DEFAULT_MAX_CLASSES="${LEO_AFFECTED_MAX_CLASSES:-40}"

BASELINE_REF=""
BASELINE_FLAG=""
LIST_ONLY=0
FORCE_FULL=0
MAX_CLASSES="$DEFAULT_MAX_CLASSES"

TEMPORARY_DIRECTORY=""
MAVEN_LOG=""

usage() {
  cat <<'EOF'
用法: bash scripts/test-affected.sh [选项]

按 Git 变更集映射 JUnit 测试类，只执行相关测试；无法可靠映射时回退全量。

选项:
  --base <ref>       变更基线。默认 git merge-base HEAD origin/main（无 origin/main 时用上游分支）
  --since <ref>      同 --base（别名）
  --list             只打印变更集、映射结果与决策，不执行 Maven
  --full             忽略映射，直接执行全量测试
  --max-classes <N>  选中测试类数阈值，超过则回退全量（默认 40；也可用 LEO_AFFECTED_MAX_CLASSES）
  -h, --help         显示本帮助

示例:
  bash scripts/test-affected.sh --list
  bash scripts/test-affected.sh
  bash scripts/test-affected.sh --base 8e5c6a09 --list
  bash scripts/test-affected.sh --full
EOF
}

log() { printf '[test-affected] %s\n' "$*"; }
warn() { printf '[test-affected] 警告: %s\n' "$*" >&2; }
die() {
  printf '[test-affected] 错误: %s\n' "$*" >&2
  exit 2
}

cleanup() {
  if [[ -n "$TEMPORARY_DIRECTORY" && -d "$TEMPORARY_DIRECTORY" ]]; then
    rm -rf -- "$TEMPORARY_DIRECTORY"
  fi
  return 0
}

handle_error() {
  local -r exit_code="$?"
  local -r line_number="$1"
  printf '[test-affected] 脚本在第 %s 行异常退出（exit %s）\n' "$line_number" "$exit_code" >&2
  exit "$exit_code"
}

trap cleanup EXIT
trap 'handle_error "$LINENO"' ERR

while (($# > 0)); do
  case "$1" in
    --base|--since)
      (($# >= 2)) || die "$1 需要 <ref> 参数"
      [[ -z "$BASELINE_REF" ]] || die "--base 与 --since 只能指定一个"
      BASELINE_REF="$2"
      BASELINE_FLAG="$1"
      shift 2
      ;;
    --list)
      LIST_ONLY=1
      shift
      ;;
    --full)
      FORCE_FULL=1
      shift
      ;;
    --max-classes)
      (($# >= 2)) || die "--max-classes 需要数字参数"
      MAX_CLASSES="$2"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      die "未知参数: $1（--help 查看用法）"
      ;;
  esac
done

[[ "$MAX_CLASSES" =~ ^[0-9]+$ ]] || die "--max-classes 必须是正整数: $MAX_CLASSES"
((MAX_CLASSES > 0)) || die "--max-classes 必须大于 0"

cd -- "$REPOSITORY_ROOT"
git rev-parse --git-dir >/dev/null 2>&1 || die "不是 Git 仓库: $REPOSITORY_ROOT"
[[ -f pom.xml ]] || die "找不到 pom.xml，请在 leo 后端仓库根目录执行"
[[ -x scripts/maven.sh ]] || die "找不到可执行的 scripts/maven.sh（Maven 包装器）"
[[ -d src/test/java ]] || die "找不到 src/test/java"

TEMPORARY_DIRECTORY="$(mktemp -d)"
readonly TEMPORARY_DIRECTORY

# ---------------------------------------------------------------------------
# 基线解析：默认 merge-base HEAD origin/main；显式 --base 也先取 merge-base，
# 避免把基线分支上不属于本分支的提交差异算进变更集。
# ---------------------------------------------------------------------------
if [[ -n "$BASELINE_REF" ]]; then
  base_ref="$BASELINE_REF"
  base_label="$BASELINE_FLAG $BASELINE_REF"
else
  if git rev-parse --verify --quiet 'origin/main^{commit}' >/dev/null; then
    base_ref="origin/main"
    base_label="默认 merge-base HEAD origin/main"
  elif git rev-parse --verify --quiet '@{upstream}^{commit}' >/dev/null; then
    base_ref="@{upstream}"
    base_label="默认 merge-base HEAD @{upstream}"
  else
    die "无法确定默认基线：origin/main 与上游分支都不存在，请显式传 --base <ref>"
  fi
fi

base_commit="$(git rev-parse --verify --quiet "${base_ref}^{commit}")" \
  || die "无法解析基线 ref: $base_ref"
if resolved_base="$(git merge-base HEAD "$base_commit" 2>/dev/null)" && [[ -n "$resolved_base" ]]; then
  base_commit="$resolved_base"
else
  warn "基线 $base_ref 与 HEAD 没有共同祖先，直接使用该提交作为基线"
fi

# ---------------------------------------------------------------------------
# 变更集分类
# ---------------------------------------------------------------------------
declare -a changed_main=()
declare -a changed_tests=()
declare -a changed_build=()
declare -a changed_noncode=()
declare -a changed_unknown=()
declare -a fallback_reasons=()
declare -a mapped_report=()
declare -a direct_tests=()
declare -A selected=()

changed_total=0

is_runnable_test_name() {
  # 仓库约定：只有 *Test.java 是可执行 JUnit 类；其余 *.java（support/基类）改动一律回退全量。
  [[ "$1" == *Test ]]
}

map_main_class() {
  local -r class_name="$1"
  local -r changed_path="$2"
  local -a hits=()
  local test_file test_name

  # 1) 命名映射：Foo.java -> Foo*Test.java（含 FooTest / FooServiceTest 这类前缀命中）
  while IFS= read -r test_file; do
    [[ -n "$test_file" ]] || continue
    test_name="${test_file##*/}"
    test_name="${test_name%.java}"
    hits+=("$test_name")
  done < <(find src/test/java -type f -name "${class_name}*Test.java" -print 2>/dev/null | LC_ALL=C sort)

  # 2) 引用映射：测试源码中出现 Foo 一词的测试类（grep -rlw，保守多选）
  while IFS= read -r test_file; do
    [[ -n "$test_file" ]] || continue
    test_name="${test_file##*/}"
    test_name="${test_name%.java}"
    if is_runnable_test_name "$test_name"; then
      hits+=("$test_name")
    else
      fallback_reasons+=("测试 support/基类 $test_file 引用了 $class_name，影响面无法枚举")
    fi
  done < <(grep -rlwF --include='*.java' -e "$class_name" src/test/java 2>/dev/null | LC_ALL=C sort || true)

  local -A seen_hits=()
  local -a unique=()
  local name
  for name in "${hits[@]}"; do
    [[ -n "${seen_hits[$name]:-}" ]] && continue
    seen_hits["$name"]=1
    unique+=("$name")
  done

  if ((${#unique[@]} == 0)); then
    fallback_reasons+=("主代码 $changed_path（类 $class_name）未映射到任何测试")
    return 0
  fi

  for name in "${unique[@]}"; do
    if [[ -z "${selected[$name]:-}" ]]; then
      selected["$name"]=1
    fi
  done
  local joined
  joined="$(printf '%s, ' "${unique[@]}")"
  joined="${joined%, }"
  mapped_report+=("$changed_path -> $joined")
}

classify_path() {
  local -r path="$1"
  local -r is_deleted="$2"
  local -r old_path="${3:-}"

  changed_total=$((changed_total + 1))
  case "$path" in
    pom.xml)
      changed_build+=("$path")
      fallback_reasons+=("pom.xml 变更（构建/测试配置，影响面无法枚举）")
      ;;
    .mvn/*|mvnw|mvnw.cmd|lombok.config)
      changed_build+=("$path")
      fallback_reasons+=("构建配置变更: $path")
      ;;
    config/*)
      changed_build+=("$path")
      fallback_reasons+=("静态检查/构建配置变更: $path")
      ;;
    src/main/resources/*|src/test/resources/*)
      changed_build+=("$path")
      fallback_reasons+=("资源或数据库迁移变更: $path")
      ;;
    src/main/java/*.java)
      local class_name="${path##*/}"
      class_name="${class_name%.java}"
      changed_main+=("$path")
      map_main_class "$class_name" "$path"
      # 重命名：旧类名仍可能被测试引用/有 OldTest，一并保守映射
      if [[ -n "$old_path" && "$old_path" == src/main/java/*.java ]]; then
        local old_class="${old_path##*/}"
        old_class="${old_class%.java}"
        if [[ "$old_class" != "$class_name" ]]; then
          map_main_class "$old_class" "$old_path"
        fi
      fi
      ;;
    src/main/java/*)
      changed_main+=("$path")
      fallback_reasons+=("主代码目录存在非 Java 文件变更: $path")
      ;;
    src/test/java/*.java)
      local test_name="${path##*/}"
      test_name="${test_name%.java}"
      changed_tests+=("$path")
      if [[ "$is_deleted" == "1" ]]; then
        fallback_reasons+=("测试文件被删除（无法定向执行，保守回退）: $path")
      elif is_runnable_test_name "$test_name"; then
        if [[ -z "${selected[$test_name]:-}" ]]; then
          selected["$test_name"]=1
          direct_tests+=("$test_name")
        fi
      else
        fallback_reasons+=("测试公共基类/工具变更: $path")
      fi
      ;;
    src/test/java/*)
      changed_tests+=("$path")
      fallback_reasons+=("测试目录存在非 Java 文件变更: $path")
      ;;
    docs/*|scripts/*|.github/*|archive/*|*.md|.gitignore|.gitattributes|.editorconfig|LICENSE)
      changed_noncode+=("$path")
      ;;
    *)
      changed_unknown+=("$path")
      fallback_reasons+=("未分类路径（保守回退全量）: $path")
      ;;
  esac
}

while IFS=$'\t' read -r status path1 path2; do
  [[ -n "${status:-}" ]] || continue
  case "$status" in
    R*|C*)
      classify_path "$path2" 0 "$path1"
      ;;
    D)
      classify_path "$path1" 1 ""
      ;;
    U)
      changed_total=$((changed_total + 1))
      changed_unknown+=("$path1")
      fallback_reasons+=("存在未合并冲突文件: $path1")
      ;;
    A|M|T)
      classify_path "$path1" 0 ""
      ;;
    *)
      changed_total=$((changed_total + 1))
      changed_unknown+=("$path1")
      fallback_reasons+=("未知 Git 状态 $status: $path1")
      ;;
  esac
done < <(git diff --name-status -M --no-ext-diff "$base_commit" --)

while IFS= read -r untracked_path; do
  [[ -n "$untracked_path" ]] || continue
  classify_path "$untracked_path" 0 ""
done < <(git ls-files --others --exclude-standard)

# ---------------------------------------------------------------------------
# 决策
# ---------------------------------------------------------------------------
declare -a regular_tests=()
declare -a postgres_tests=()
if ((${#selected[@]} > 0)); then
  while IFS= read -r name; do
    [[ -n "$name" ]] || continue
    if [[ "$name" == *PostgresTest ]]; then
      postgres_tests+=("$name")
    else
      regular_tests+=("$name")
    fi
  done < <(printf '%s\n' "${!selected[@]}" | LC_ALL=C sort)
fi

decision=""
decision_reasons=()
if ((FORCE_FULL)); then
  decision="full"
  decision_reasons+=("--full 指定强制全量")
elif ((${#fallback_reasons[@]} > 0)); then
  decision="full"
  decision_reasons=("${fallback_reasons[@]}")
elif ((changed_total == 0)); then
  decision="skip-no-changes"
elif ((${#selected[@]} == 0)); then
  decision="skip-non-code"
elif ((${#selected[@]} > MAX_CLASSES)); then
  decision="full"
  decision_reasons+=("选中测试类 ${#selected[@]} 个，超过阈值 $MAX_CLASSES（LEO_AFFECTED_MAX_CLASSES/--max-classes 可调）")
elif ((${#regular_tests[@]} == 0)); then
  decision="postgres-only"
else
  decision="incremental"
fi

print_path_list() {
  local -r title="$1"
  shift
  local -a paths=("$@")
  ((${#paths[@]} > 0)) || return 0
  local limit=30
  local index=0
  local path
  log "$title（${#paths[@]} 个）:"
  for path in "${paths[@]}"; do
    if ((index >= limit)); then
      log "  ... 其余 $(( ${#paths[@]} - limit )) 个省略"
      break
    fi
    log "  $path"
    index=$((index + 1))
  done
}

# 头部信息
log "仓库: $REPOSITORY_ROOT"
log "基线: ${base_commit:0:8}（$base_label）"
if ((changed_total == 0)); then
  log "变更文件: 0 个（工作区与基线一致）"
else
  log "变更文件: $changed_total 个（主代码 ${#changed_main[@]} / 测试代码 ${#changed_tests[@]} / 构建资源 ${#changed_build[@]} / 非代码 ${#changed_noncode[@]} / 未分类 ${#changed_unknown[@]}）"
  print_path_list "主代码变更" "${changed_main[@]}"
  print_path_list "测试代码变更" "${changed_tests[@]}"
  print_path_list "构建/资源/迁移变更（回退全量）" "${changed_build[@]}"
  print_path_list "非代码变更（不触发测试）" "${changed_noncode[@]}"
  print_path_list "未分类变更" "${changed_unknown[@]}"
fi

if ((${#mapped_report[@]} > 0)); then
  log "映射结果:"
  report_limit=30
  index=0
  for line in "${mapped_report[@]}"; do
    if ((index >= report_limit)); then
      log "  ... 其余 $(( ${#mapped_report[@]} - report_limit )) 条省略"
      break
    fi
    log "  $line"
    index=$((index + 1))
  done
fi
if ((${#direct_tests[@]} > 0)); then
  log "直接变更的测试类: $(printf '%s, ' "${direct_tests[@]}" | sed 's/, $//')"
fi

case "$decision" in
  skip-no-changes)
    log "跳过（无变更）：工作区与基线一致，没有需要运行的测试。"
    log "注意：本次没有执行任何测试，跳过不等于“测试通过”。"
    exit 0
    ;;
  skip-non-code)
    log "跳过（无相关测试）：本次变更只涉及文档/脚本/CI/归档等非代码文件，没有需要运行的测试。"
    log "注意：本次没有执行任何测试，跳过不等于“测试通过”。"
    exit 0
    ;;
  postgres-only)
    log "决策: 跳过执行（仅命中真实库用例，增量默认不跑）"
    for name in "${postgres_tests[@]}"; do
      log "  真实库用例（未执行）: $name"
    done
    joined_postgres="$(printf '%s,' "${postgres_tests[@]}")"
    joined_postgres="${joined_postgres%,}"
    log "注意：这不是“测试通过”，本次未验证任何测试。涉及数据库/SQL/迁移/仓储口径的改动必须按 docs/real-db-test-gate.md 单独执行："
    log "  LEO_TEST_POSTGRES=true bash scripts/maven.sh -B -ntp -Dtest=$joined_postgres test"
    if ((LIST_ONLY)); then
      exit 0
    fi
    exit 3
    ;;
  full)
    log "决策: 回退全量测试"
    for reason in "${decision_reasons[@]}"; do
      log "  回退原因: $reason"
    done
    if ((${#selected[@]} > 0)); then
      log "（已完成映射的测试类 ${#selected[@]} 个，但将执行全量）"
    fi
    if ((LIST_ONLY)); then
      log "计划执行: bash scripts/maven.sh -B -ntp test"
      log "（--list 仅打印，不执行）"
      exit 0
    fi
    ;;
  incremental)
    log "决策: 定向执行增量测试"
    log "选中测试类: ${#selected[@]} 个（其中过滤掉真实库用例 ${#postgres_tests[@]} 个）"
    for name in "${regular_tests[@]}"; do
      log "  - $name"
    done
    if ((${#postgres_tests[@]} > 0)); then
      log "过滤掉的真实库用例（默认不跑，涉及 DB/SQL 改动请按 docs/real-db-test-gate.md 单独执行）:"
      for name in "${postgres_tests[@]}"; do
        log "  - $name"
      done
    fi
    test_list="$(printf '%s,' "${regular_tests[@]}")"
    test_list="${test_list%,}"
    if ((LIST_ONLY)); then
      log "计划执行: bash scripts/maven.sh -B -ntp -Dtest=$test_list -Dsurefire.failIfNoSpecifiedTests=false test"
      log "（--list 仅打印，不执行）"
      exit 0
    fi
    ;;
  *)
    die "内部错误：未知决策 $decision"
    ;;
esac

# ---------------------------------------------------------------------------
# 执行 Maven 并汇总
# ---------------------------------------------------------------------------
run_maven_and_report() {
  local -r mode_label="$1"
  shift
  local -a maven_args=("$@")
  MAVEN_LOG="$TEMPORARY_DIRECTORY/maven.log"
  : > "$MAVEN_LOG"

  log "执行: bash scripts/maven.sh ${maven_args[*]}"
  local -r start_seconds="$SECONDS"
  local status=0
  # 临时摘掉 ERR trap：Maven 失败是预期分支，需要自己捕获退出码并打印尾部日志，
  # 不能让 trap 先一步以“脚本异常”退出。
  trap - ERR
  set +e
  bash "$SCRIPT_DIR/maven.sh" "${maven_args[@]}" 2>&1 | tee "$MAVEN_LOG"
  status="${PIPESTATUS[0]}"
  set -e
  trap 'handle_error "$LINENO"' ERR
  local -r elapsed="$((SECONDS - start_seconds))"

  local summary_line=""
  summary_line="$(grep -E '^\[(INFO|ERROR)\] Tests run: [0-9]+, Failures: [0-9]+, Errors: [0-9]+, Skipped: [0-9]+$' \
    "$MAVEN_LOG" | tail -n 1 || true)"
  local summary_text="未能解析 surefire 汇总（见上方 Maven 输出）"
  local skipped=0
  if [[ -n "$summary_line" ]]; then
    summary_text="${summary_line#*] }"
    skipped="$(awk 'match($0, /Skipped: [0-9]+/) { print substr($0, RSTART + 9, RLENGTH - 9) }' <<<"$summary_line")"
    [[ -n "$skipped" ]] || skipped=0
  fi

  log "结果: $summary_text"
  if ((skipped > 0)); then
    warn "本次有 $skipped 个用例被跳过（@Disabled 或真实库用例）；跳过不代表通过。"
  fi
  log "耗时: $mode_label ${elapsed}s（全量参考约 54s，见 docs/incremental-testing.md）"

  if ((status != 0)); then
    printf '[test-affected] 失败: Maven 退出码 %s\n' "$status" >&2
    printf '%s\n' '--- Maven 输出尾部 ---' >&2
    tail -n 80 "$MAVEN_LOG" >&2 || true
    exit "$status"
  fi
  if [[ -z "$summary_line" ]]; then
    warn "未找到 surefire 汇总行；Maven 构建成功，但无法报告用例数。"
  fi
  exit 0
}

if [[ "$decision" == "full" ]]; then
  run_maven_and_report "全量" -B -ntp test
else
  run_maven_and_report "定向" -B -ntp "-Dtest=$test_list" -Dsurefire.failIfNoSpecifiedTests=false test
fi
