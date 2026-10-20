#!/usr/bin/env bash
#
# test-affected.sh 自测：在临时 Git 仓库（mktemp -d）里验证增量测试脚本的
# 变更集收集、映射、回退、跳过、真实库用例过滤与执行参数。
#
# - 不接触工作区业务源码，不执行真实 Maven：临时仓库内放置 scripts/maven.sh 桩，
#   只记录收到的参数并按 MAVEN_EXIT_CODE 返回，因此自测秒级完成、不污染工作区。
# - 每个用例使用全新的临时仓库，避免用例间状态串扰。

set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
TARGET_SCRIPT="$SCRIPT_DIR/test-affected.sh"
[[ -f "$TARGET_SCRIPT" ]] || {
  printf '[selftest] 找不到 %s\n' "$TARGET_SCRIPT" >&2
  exit 2
}

TEMP_ROOT="$(mktemp -d)"
readonly TEMP_ROOT
export MAVEN_ARG_LOG="$TEMP_ROOT/maven-args.log"
export MAVEN_EXIT_CODE=0

REPO=""
INITIAL_COMMIT=""
REPO_COUNTER=0
PASSED=0
FAILED=0
CASE=""
run_out=""
run_status=0

cleanup() {
  rm -rf -- "$TEMP_ROOT"
}
trap cleanup EXIT

info() { printf '[selftest] %s\n' "$*"; }
begin_case() {
  CASE="$1"
  printf '\n=== %s ===\n' "$CASE"
  : > "$MAVEN_ARG_LOG"
  export MAVEN_EXIT_CODE=0
}
record_pass() {
  PASSED=$((PASSED + 1))
  printf '[ OK ] %s\n' "$*"
}
record_fail() {
  FAILED=$((FAILED + 1))
  printf '[FAIL] %s\n' "$*" >&2
  if [[ -n "$run_out" ]]; then
    printf '%s\n' "$run_out" | sed 's/^/       | /' >&2
  fi
}

expect_status() {
  local -r expected="$1"
  local -r description="$2"
  if [[ "$run_status" == "$expected" ]]; then
    record_pass "$description（exit $run_status）"
  else
    record_fail "$description（期望 exit $expected，实际 exit $run_status）"
  fi
}

expect_contains() {
  local -r needle="$1"
  local -r description="$2"
  if [[ "$run_out" == *"$needle"* ]]; then
    record_pass "$description"
  else
    record_fail "$description（输出缺少：$needle）"
  fi
}

expect_not_contains() {
  local -r needle="$1"
  local -r description="$2"
  if [[ "$run_out" != *"$needle"* ]]; then
    record_pass "$description"
  else
    record_fail "$description（输出不应包含：$needle）"
  fi
}

expect_maven_not_called() {
  local -r description="$1"
  if [[ ! -s "$MAVEN_ARG_LOG" ]]; then
    record_pass "$description"
  else
    record_fail "$description（Maven 桩被调用：$(tr '\n' ' ' < "$MAVEN_ARG_LOG")）"
  fi
}

expect_maven_called_with() {
  local -r needle="$1"
  local -r description="$2"
  if grep -qF -- "$needle" "$MAVEN_ARG_LOG"; then
    record_pass "$description"
  else
    record_fail "$description（Maven 参数未包含：$needle；实际：$(tr '\n' ' ' < "$MAVEN_ARG_LOG")）"
  fi
}

run_script() {
  run_status=0
  if run_out="$(cd -- "$REPO" && bash scripts/test-affected.sh "$@" 2>&1)"; then
    run_status=0
  else
    run_status=$?
  fi
}

write_file() {
  local -r path="$1"
  shift
  mkdir -p -- "$(dirname -- "$path")"
  printf '%s\n' "$@" > "$path"
}

new_repo() {
  REPO_COUNTER=$((REPO_COUNTER + 1))
  REPO="$TEMP_ROOT/repo-$REPO_COUNTER"
  mkdir -p -- "$REPO"

  write_file "$REPO/src/main/java/com/example/FooService.java" \
    'package com.example;' '' 'public class FooService {' '    public void run() {' '    }' '}'
  write_file "$REPO/src/main/java/com/example/Widget.java" \
    'package com.example;' '' 'public class Widget {' '}'
  write_file "$REPO/src/main/java/com/example/Unmapped.java" \
    'package com.example;' '' 'public class Unmapped {' '}'
  write_file "$REPO/src/main/java/com/example/DbThing.java" \
    'package com.example;' '' 'public class DbThing {' '}'

  write_file "$REPO/src/test/java/com/example/FooServiceTest.java" \
    'package com.example;' '' 'class FooServiceTest {' '    void usesFooService() {' '        FooService service = new FooService();' '        service.run();' '    }' '}'
  write_file "$REPO/src/test/java/com/example/LegacyUsageTest.java" \
    'package com.example;' '' 'class LegacyUsageTest {' '    Class<?> type = FooService.class;' '}'
  write_file "$REPO/src/test/java/com/example/UnrelatedTest.java" \
    'package com.example;' '' 'class UnrelatedTest {' '    void trivial() {' '    }' '}'
  write_file "$REPO/src/test/java/com/example/DbThingPostgresTest.java" \
    'package com.example;' '' 'class DbThingPostgresTest {' '    DbThing dbThing;' '}'
  write_file "$REPO/src/test/java/com/example/support/SharedFixture.java" \
    'package com.example.support;' '' 'class SharedFixture {' '    Class<?> widgetType = com.example.Widget.class;' '}'

  write_file "$REPO/src/main/resources/application.yml" 'spring:' '  application:' '    name: selftest'
  write_file "$REPO/src/main/resources/db/migration/V1__baseline.sql" 'SELECT 1;'
  write_file "$REPO/config/checkstyle.xml" '<module name="Checker"/>'
  write_file "$REPO/docs/guide.md" '# 自测文档' ''
  write_file "$REPO/scripts/other.sh" '#!/usr/bin/env bash' 'echo other'
  write_file "$REPO/pom.xml" '<project/>'

  write_file "$REPO/scripts/maven.sh" \
    '#!/usr/bin/env bash' \
    'set -Eeuo pipefail' \
    'printf "%s\n" "$*" >> "${MAVEN_ARG_LOG:?}"' \
    'exit "${MAVEN_EXIT_CODE:-0}"'
  chmod +x "$REPO/scripts/maven.sh"

  cp -- "$TARGET_SCRIPT" "$REPO/scripts/test-affected.sh"
  chmod +x "$REPO/scripts/test-affected.sh"

  git -C "$REPO" init -q -b main
  git -C "$REPO" config user.email 'selftest@example.invalid'
  git -C "$REPO" config user.name 'selftest'
  git -C "$REPO" add -A
  git -C "$REPO" -c commit.gpgsign=false commit -qm 'init selftest repo'
  INITIAL_COMMIT="$(git -C "$REPO" rev-parse HEAD)"
  git -C "$REPO" update-ref refs/remotes/origin/main "$INITIAL_COMMIT"
}

# ---------------------------------------------------------------------------
# 1. 无变更 -> 跳过（无变更），不执行 Maven
# ---------------------------------------------------------------------------
begin_case "无变更"
new_repo
run_script --list
expect_status 0 "无变更 --list 退出码"
expect_contains "变更文件: 0 个" "报告 0 个变更文件"
expect_contains "跳过（无变更）" "打印“跳过（无变更）”"
expect_contains "不等于" "明确提示跳过不等于测试通过"
expect_maven_not_called "--list 不执行 Maven"

# ---------------------------------------------------------------------------
# 2. 纯文档 / 纯脚本 / CI 变更 -> 跳过（无相关测试）
# ---------------------------------------------------------------------------
begin_case "纯文档变更"
new_repo
printf '\n补充说明\n' >> "$REPO/docs/guide.md"
run_script --list
expect_status 0 "纯文档 --list 退出码"
expect_contains "跳过（无相关测试）" "打印“跳过（无相关测试）”"
expect_contains "不等于“测试通过”" "明确不是测试通过"
expect_maven_not_called "纯文档变更不执行 Maven"

begin_case "纯脚本变更"
new_repo
printf '\n# 修改\n' >> "$REPO/scripts/other.sh"
run_script --list
expect_status 0 "纯脚本 --list 退出码"
expect_contains "跳过（无相关测试）" "脚本变更打印跳过"
expect_maven_not_called "脚本变更不执行 Maven"

begin_case "CI 变更"
new_repo
write_file "$REPO/.github/workflows/ci.yml" 'name: ci'
run_script --list
expect_status 0 "CI 变更 --list 退出码"
expect_contains "跳过（无相关测试）" "CI 变更打印跳过"

# ---------------------------------------------------------------------------
# 3. 测试文件直接变更 -> 直接纳入该测试类
# ---------------------------------------------------------------------------
begin_case "测试文件直接变更"
new_repo
printf '\n// touch\n' >> "$REPO/src/test/java/com/example/UnrelatedTest.java"
run_script --list
expect_status 0 "直接测试变更 --list 退出码"
expect_contains "决策: 定向执行" "直接测试变更走定向执行"
expect_contains "UnrelatedTest" "选中被直接修改的测试类"
expect_not_contains "回退全量" "直接测试变更不回退全量"
expect_maven_not_called "--list 不执行 Maven"

# ---------------------------------------------------------------------------
# 4. 主代码命名映射 + 引用映射（grep -rlw）-> 命中多个测试
# ---------------------------------------------------------------------------
begin_case "主代码映射到测试"
new_repo
printf '\n// touch\n' >> "$REPO/src/main/java/com/example/FooService.java"
run_script --list
expect_status 0 "主代码变更 --list 退出码"
expect_contains "决策: 定向执行" "主代码有映射时走定向执行"
expect_contains "FooServiceTest" "命中 Foo*Test.java 命名映射"
expect_contains "LegacyUsageTest" "命中 grep 引用映射"
expect_contains "选中测试类: 2 个" "选中 2 个测试类"
expect_not_contains "回退全量" "映射命中时不回退全量"
expect_maven_not_called "--list 不执行 Maven"

# ---------------------------------------------------------------------------
# 5. 主代码无法映射 -> 回退全量
# ---------------------------------------------------------------------------
begin_case "未映射主代码"
new_repo
printf '\n// touch\n' >> "$REPO/src/main/java/com/example/Unmapped.java"
run_script --list
expect_status 0 "未映射主代码 --list 退出码"
expect_contains "回退全量" "未映射主代码回退全量"
expect_contains "未映射到任何测试" "打印未映射原因"

# ---------------------------------------------------------------------------
# 6. 测试 support/基类被引用或变更 -> 回退全量
# ---------------------------------------------------------------------------
begin_case "测试 support 引用主代码"
new_repo
printf '\n// touch\n' >> "$REPO/src/main/java/com/example/Widget.java"
run_script --list
expect_status 0 "support 引用 --list 退出码"
expect_contains "回退全量" "support 引用时回退全量"
expect_contains "support" "打印 support 引用原因"

begin_case "测试 support 直接变更"
new_repo
printf '\n// touch\n' >> "$REPO/src/test/java/com/example/support/SharedFixture.java"
run_script --list
expect_status 0 "support 直接变更 --list 退出码"
expect_contains "回退全量" "测试 support 变更回退全量"
expect_contains "测试公共基类/工具变更" "打印测试 support 变更原因"

# ---------------------------------------------------------------------------
# 7. 构建/资源/迁移/配置/未知路径 -> 回退全量
# ---------------------------------------------------------------------------
begin_case "pom.xml 变更"
new_repo
printf '\n<!-- touch -->\n' >> "$REPO/pom.xml"
run_script --list
expect_status 0 "pom 变更 --list 退出码"
expect_contains "回退全量" "pom.xml 变更回退全量"
expect_contains "pom.xml 变更" "打印 pom.xml 回退原因"

begin_case "主资源变更"
new_repo
printf '\n# touch\n' >> "$REPO/src/main/resources/application.yml"
run_script --list
expect_contains "回退全量" "主资源变更回退全量"
expect_contains "资源或数据库迁移变更" "打印资源回退原因"

begin_case "数据库迁移变更"
new_repo
printf '\n-- touch\n' >> "$REPO/src/main/resources/db/migration/V1__baseline.sql"
run_script --list
expect_contains "回退全量" "db/migration 变更回退全量"

begin_case "静态检查配置变更"
new_repo
printf '\n' >> "$REPO/config/checkstyle.xml"
run_script --list
expect_contains "回退全量" "config 变更回退全量"
expect_contains "静态检查/构建配置变更" "打印 config 回退原因"

begin_case "未分类路径变更"
new_repo
write_file "$REPO/deploy/Dockerfile" 'FROM scratch'
run_script --list
expect_status 0 "未分类路径 --list 退出码"
expect_contains "回退全量" "未分类路径保守回退全量"
expect_contains "未分类路径" "打印未分类原因"

# ---------------------------------------------------------------------------
# 8. 选中类数超过阈值 -> 回退全量（阈值为 40）
# ---------------------------------------------------------------------------
begin_case "选中类数超过阈值"
new_repo
for index in $(seq 1 41); do
  write_file "$REPO/src/test/java/com/example/bulk/Bulk${index}Test.java" \
    'package com.example.bulk;' '' "class Bulk${index}Test {" '}'
done
run_script --list
expect_status 0 "超过阈值 --list 退出码"
expect_contains "回退全量" "选中类数超过阈值回退全量"
expect_contains "超过阈值 40" "打印阈值回退原因"
expect_contains "已完成映射的测试类 41 个" "报告选中类数"

begin_case "--max-classes 参数"
new_repo
printf '\n// touch\n' >> "$REPO/src/main/java/com/example/FooService.java"
run_script --list --max-classes 1
expect_contains "回退全量" "--max-classes 1 时回退全量"
expect_contains "超过阈值 1" "使用 --max-classes 覆盖阈值"

# ---------------------------------------------------------------------------
# 9. 真实库用例：默认过滤；仅命中时跳过并 exit 3；不得伪装通过
# ---------------------------------------------------------------------------
begin_case "仅命中真实库用例"
new_repo
printf '\n// touch\n' >> "$REPO/src/main/java/com/example/DbThing.java"
run_script --list
expect_status 0 "仅真实库用例 --list 退出码"
expect_contains "仅命中真实库用例" "识别出仅命中真实库用例"
expect_contains "DbThingPostgresTest" "列出被过滤的真实库用例"
expect_maven_not_called "--list 不执行 Maven"

run_script
expect_status 3 "仅真实库用例直接执行返回 3（未验证）"
expect_contains "未验证" "明确提示本次未验证"
expect_contains "LEO_TEST_POSTGRES=true" "给出真实库用例执行命令"
expect_maven_not_called "仅真实库用例时不执行 Maven 定向命令"

# ---------------------------------------------------------------------------
# 10. 定向执行：命令参数、真实库用例过滤、耗时输出
# ---------------------------------------------------------------------------
begin_case "定向执行参数"
new_repo
printf '\n// touch\n' >> "$REPO/src/main/java/com/example/FooService.java"
printf '\n// touch\n' >> "$REPO/src/main/java/com/example/DbThing.java"
run_script
expect_status 0 "定向执行退出码"
expect_contains "结果:" "输出 surefire 结果（桩不产生结果时也应优雅处理）"
expect_contains "耗时: 定向" "输出定向执行耗时"
expect_maven_called_with "-Dtest=FooServiceTest,LegacyUsageTest" "只把常规测试类传给 -Dtest"
expect_maven_called_with "-Dsurefire.failIfNoSpecifiedTests=false" "关闭 failIfNoSpecifiedTests"
expect_maven_called_with "-B -ntp" "透传批处理参数"
if grep -qF "DbThingPostgresTest" "$MAVEN_ARG_LOG"; then
  record_fail "真实库用例不应出现在 -Dtest 中"
else
  record_pass "真实库用例未进入 -Dtest"
fi

# ---------------------------------------------------------------------------
# 11. 测试失败 -> 退出码透传
# ---------------------------------------------------------------------------
begin_case "测试失败透传"
new_repo
printf '\n// touch\n' >> "$REPO/src/main/java/com/example/FooService.java"
export MAVEN_EXIT_CODE=1
run_script
expect_status 1 "Maven 失败时透传退出码"
expect_contains "失败: Maven 退出码 1" "打印 Maven 失败信息"
export MAVEN_EXIT_CODE=0

# ---------------------------------------------------------------------------
# 12. --full：强制全量，不传 -Dtest；--list --full 不执行
# ---------------------------------------------------------------------------
begin_case "--full 强制全量"
new_repo
run_script --list --full
expect_status 0 "--list --full 退出码"
expect_contains "回退全量测试" "--full 决策为全量"
expect_contains "计划执行: bash scripts/maven.sh -B -ntp test" "打印全量计划命令"
expect_maven_not_called "--list --full 不执行 Maven"

run_script --full
expect_status 0 "--full 执行退出码"
expect_maven_called_with "-B -ntp test" "全量执行参数"
if grep -qF -- "-Dtest=" "$MAVEN_ARG_LOG"; then
  record_fail "全量执行不应传 -Dtest"
else
  record_pass "全量执行不传 -Dtest"
fi

# ---------------------------------------------------------------------------
# 13. 已提交变更包含在默认变更集中（默认基线 merge-base HEAD origin/main）
# ---------------------------------------------------------------------------
begin_case "已提交 Java 变更"
new_repo
printf '\n// committed touch\n' >> "$REPO/src/main/java/com/example/FooService.java"
git -C "$REPO" add -A
git -C "$REPO" -c commit.gpgsign=false commit -qm 'change FooService'
run_script --list
expect_status 0 "已提交变更 --list 退出码"
expect_contains "默认 merge-base HEAD origin/main" "使用默认基线"
expect_contains "FooServiceTest" "已提交变更也能被映射"

begin_case "--since 别名"
new_repo
printf '\n// committed touch\n' >> "$REPO/src/main/java/com/example/FooService.java"
git -C "$REPO" add -A
git -C "$REPO" -c commit.gpgsign=false commit -qm 'change FooService'
run_script --list --since "$INITIAL_COMMIT"
expect_status 0 "--since --list 退出码"
expect_contains "--since $INITIAL_COMMIT" "输出使用 --since 基线"
expect_contains "FooServiceTest" "--since 同样映射变更"

# ---------------------------------------------------------------------------
# 14. 删除测试文件 -> 保守回退全量
# ---------------------------------------------------------------------------
begin_case "删除测试文件"
new_repo
rm -- "$REPO/src/test/java/com/example/UnrelatedTest.java"
run_script --list
expect_status 0 "删除测试 --list 退出码"
expect_contains "回退全量" "删除测试文件回退全量"
expect_contains "测试文件被删除" "打印删除测试原因"

# ---------------------------------------------------------------------------
# 15. 参数错误与帮助
# ---------------------------------------------------------------------------
begin_case "--help 与非法参数"
new_repo
run_script --help
expect_status 0 "--help 退出码"
expect_contains "用法:" "--help 输出用法"

run_script --base does-not-exist --list
expect_status 2 "不存在的基线退出码 2"
expect_contains "无法解析基线" "打印基线解析失败原因"

run_script --unknown-option
expect_status 2 "未知参数退出码 2"
expect_contains "未知参数" "打印未知参数提示"

run_script --max-classes 0 --list
expect_status 2 "--max-classes 0 退出码 2"

# ---------------------------------------------------------------------------
# 汇总
# ---------------------------------------------------------------------------
printf '\n===== 自测汇总 =====\n'
printf '通过: %s\n失败: %s\n' "$PASSED" "$FAILED"
if ((FAILED > 0)); then
  printf 'SELFTEST_FAILED\n' >&2
  exit 1
fi
printf 'SELFTEST_OK\n'
