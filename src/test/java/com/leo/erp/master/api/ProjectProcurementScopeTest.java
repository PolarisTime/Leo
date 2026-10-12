package com.leo.erp.master.api;

import com.leo.erp.master.api.ProjectProcurementScopeQuery.Enforcement;
import com.leo.erp.master.api.ProjectProcurementScopeQuery.ProjectProcurementScope;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 项目采购范围的匹配判据回归。
 *
 * <p>这份判据同时被「保存时阻断」与「导入时提示」两条路径使用，所以边界必须在这里锁死：
 * 空清单=不限制、明细空值不判定、去重且保持首次出现顺序、大小写与空白归一。</p>
 */
class ProjectProcurementScopeTest {

    private static ProjectProcurementScope scope(List<String> brands,
                                                 List<String> materials,
                                                 Enforcement brandEnforcement,
                                                 Enforcement materialEnforcement) {
        return new ProjectProcurementScope(9001L, "项目甲", brands, materials,
                brandEnforcement, materialEnforcement);
    }

    @Test
    void blank_scope_restrictsNothing() {
        ProjectProcurementScope empty = scope(List.of(), List.of(), Enforcement.BLOCK, Enforcement.BLOCK);

        assertThat(empty.checksBrands()).isFalse();
        assertThat(empty.checksMaterials()).isFalse();
        assertThat(empty.blocksBrands()).isFalse();
        assertThat(empty.blocksMaterials()).isFalse();
        assertThat(empty.brandViolations(List.of("宝钢"))).isEmpty();
        assertThat(empty.materialViolations(List.of("HRB500"))).isEmpty();
    }

    @Test
    void off_enforcement_keepsListButStopsChecking() {
        ProjectProcurementScope scope = scope(List.of("沙钢"), List.of("HRB400"), Enforcement.OFF, Enforcement.OFF);

        assertThat(scope.checksBrands()).isFalse();
        assertThat(scope.checksMaterials()).isFalse();
        assertThat(scope.blocksBrands()).isFalse();
        assertThat(scope.blocksMaterials()).isFalse();
    }

    @Test
    void warn_enforcement_checksWithoutBlocking() {
        ProjectProcurementScope scope = scope(List.of("沙钢"), List.of("HRB400"), Enforcement.WARN, Enforcement.WARN);

        assertThat(scope.checksBrands()).isTrue();
        assertThat(scope.blocksBrands()).isFalse();
        assertThat(scope.brandViolations(List.of("宝钢"))).containsExactly("宝钢");
    }

    @Test
    void block_enforcement_blocks() {
        ProjectProcurementScope scope = scope(List.of("沙钢"), List.of("HRB400"), Enforcement.BLOCK, Enforcement.BLOCK);

        assertThat(scope.blocksBrands()).isTrue();
        assertThat(scope.blocksMaterials()).isTrue();
    }

    @Test
    void violations_areTrimmedDeduplicatedAndOrderPreserved() {
        ProjectProcurementScope scope = scope(List.of(" 沙钢 "), List.of("HRB400"), Enforcement.BLOCK, Enforcement.BLOCK);

        assertThat(scope.brandViolations(List.of("宝钢", " 宝钢 ", "中天", "")))
                .containsExactly("宝钢", "中天");
        assertThat(scope.materialViolations(List.of(" HRB500 ", "HRB500")))
                .containsExactly("HRB500");
    }

    @Test
    void blankAndNullActualValues_areNotViolations() {
        ProjectProcurementScope scope = scope(List.of("沙钢"), List.of("HRB400"), Enforcement.BLOCK, Enforcement.BLOCK);

        assertThat(scope.brandViolations(java.util.Arrays.asList(null, "  ", ""))).isEmpty();
        assertThat(scope.materialViolations(null)).isEmpty();
    }

    @Test
    void matchingValues_areNotViolations() {
        ProjectProcurementScope scope = scope(List.of("沙钢", "永钢"), List.of("HRB400"), Enforcement.BLOCK, Enforcement.BLOCK);

        assertThat(scope.brandViolations(List.of("沙钢", "永钢"))).isEmpty();
        assertThat(scope.materialViolations(List.of("HRB400"))).isEmpty();
    }

    @Test
    void brandScope_doesNotJudgeMaterials() {
        ProjectProcurementScope scope = scope(List.of("沙钢"), List.of(), Enforcement.BLOCK, Enforcement.BLOCK);

        assertThat(scope.brandViolations(List.of("宝钢"))).containsExactly("宝钢");
        assertThat(scope.materialViolations(List.of("HRB500"))).isEmpty();
    }

    @Test
    void enforcement_parse_isLenientOnReadPath() {
        assertThat(Enforcement.parse(null)).isEqualTo(Enforcement.WARN);
        assertThat(Enforcement.parse("  ")).isEqualTo(Enforcement.WARN);
        assertThat(Enforcement.parse("BLOCK")).isEqualTo(Enforcement.BLOCK);
        assertThat(Enforcement.parse("block")).isEqualTo(Enforcement.BLOCK);
        // 脏数据退化为提醒而不是 500: 写路径已由 isValid 拦住, 数据库还有 CHECK 约束兜底
        assertThat(Enforcement.parse("UNKNOWN")).isEqualTo(Enforcement.WARN);
    }

    @Test
    void enforcement_validity_isStrictOnWritePath() {
        assertThat(Enforcement.isValid(null)).isFalse();
        assertThat(Enforcement.isValid("")).isFalse();
        assertThat(Enforcement.isValid("OFF")).isTrue();
        assertThat(Enforcement.isValid("warn")).isTrue();
        assertThat(Enforcement.isValid("Block")).isTrue();
        assertThat(Enforcement.isValid("DISABLED")).isFalse();
    }

    @Test
    void enforcement_normalize_returnsCanonicalName() {
        assertThat(Enforcement.normalize(" block ")).isEqualTo(Enforcement.BLOCK);
    }
}
