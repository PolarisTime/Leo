package com.leo.erp.master.material.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.master.material.domain.MaterialTypes;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 商品长度 ↔ 定尺主数据校验的边界测试：
 * 命中/不命中、米数归一、{@code -} 放行、附加费用跳过、更新未改动放行、主数据为空放行。
 */
class MaterialLengthGuardTest {

    private final MaterialLengthGuard guard = new MaterialLengthGuard(
            TestLengthSpecCatalog.of("9米", "12米"));

    @Test
    void rejectsLengthMissingFromMasterData() {
        assertThatThrownBy(() -> guard.validate(MaterialTypes.PHYSICAL, "10米", null))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.VALIDATION_ERROR))
                .hasMessage("长度「10米」不在定尺主数据中，请先在定尺加价维护中添加");
    }

    @Test
    void acceptsExactLengthName() {
        assertThatCode(() -> guard.validate(MaterialTypes.PHYSICAL, "12米", null)).doesNotThrowAnyException();
        assertThatCode(() -> guard.validate(MaterialTypes.PHYSICAL, " 9米 ", null)).doesNotThrowAnyException();
    }

    @Test
    void acceptsLengthMatchedByMeterNumber() {
        // 与行情匹配同口径：非精确写法按米数归一后仍命中
        assertThatCode(() -> guard.validate(MaterialTypes.PHYSICAL, "12 米", null)).doesNotThrowAnyException();
        assertThatCode(() -> guard.validate(MaterialTypes.PHYSICAL, "12", null)).doesNotThrowAnyException();
        assertThatThrownBy(() -> guard.validate(MaterialTypes.PHYSICAL, "112米", null))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void allowsBlankLengthAndDash() {
        // 卷材/无定尺用 '-' 表示；空长度表示未填写，交给必填规则
        assertThatCode(() -> guard.validate(MaterialTypes.PHYSICAL, "-", null)).doesNotThrowAnyException();
        assertThatCode(() -> guard.validate(MaterialTypes.PHYSICAL, "  ", null)).doesNotThrowAnyException();
        assertThatCode(() -> guard.validate(MaterialTypes.PHYSICAL, null, null)).doesNotThrowAnyException();
    }

    @Test
    void skipsExpenseMaterial() {
        assertThatCode(() -> guard.validate(MaterialTypes.EXPENSE, "10米", null)).doesNotThrowAnyException();
    }

    @Test
    void skipsUnchangedLengthOnUpdate() {
        // 定尺被禁用后，老商品改备注不能被拦：长度与库里原值相同即放行
        assertThatCode(() -> guard.validate(MaterialTypes.PHYSICAL, "8米", "8米")).doesNotThrowAnyException();
        assertThatCode(() -> guard.validate(MaterialTypes.PHYSICAL, " 8米 ", "8米")).doesNotThrowAnyException();
        // 换了长度就要按现行主数据校验
        assertThatThrownBy(() -> guard.validate(MaterialTypes.PHYSICAL, "10米", "8米"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void allowsAnyLengthWhenMasterDataEmpty() {
        MaterialLengthGuard emptyGuard = new MaterialLengthGuard(TestLengthSpecCatalog.empty());
        assertThatCode(() -> emptyGuard.validate(MaterialTypes.PHYSICAL, "10米", null))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsDisabledOnlyLength() {
        // 主数据里只剩 9米（12米 被禁用）：12米 的新增/改长度必须拒绝
        MaterialLengthGuard onlyNine = new MaterialLengthGuard(TestLengthSpecCatalog.of("9米"));
        assertThatThrownBy(() -> onlyNine.validate(MaterialTypes.PHYSICAL, "12米", null))
                .isInstanceOf(BusinessException.class);
        assertThatCode(() -> onlyNine.validate(MaterialTypes.PHYSICAL, "9米", null)).doesNotThrowAnyException();
    }
}
