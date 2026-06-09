package com.leo.erp.common.support;

import com.leo.erp.common.error.BusinessException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BusinessDocumentValidatorTest {

    @Test
    void shouldAllowStatusInAllowedSet() {
        BusinessDocumentValidator.requireStatusIn(" 已审核 ", Set.of("已审核"), "状态不合法");
    }

    @Test
    void shouldThrowWhenStatusNotAllowed() {
        assertThatThrownBy(() -> BusinessDocumentValidator.requireStatusIn("草稿", Set.of("已审核"), "状态不合法"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("状态不合法");
    }

    @Test
    void shouldAllowSameTextAfterTrim() {
        BusinessDocumentValidator.requireSameText(" 客户A ", "客户A", "客户不一致");
    }

    @Test
    void shouldTreatNullTextAsEmpty() {
        BusinessDocumentValidator.requireSameText(null, "", "客户不一致");
    }

    @Test
    void shouldThrowWhenTextMismatch() {
        assertThatThrownBy(() -> BusinessDocumentValidator.requireSameText("客户A", "客户B", "客户不一致"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("客户不一致");
    }

    @Test
    void shouldAllowSameOptionalCode() {
        BusinessDocumentValidator.requireSameOptionalCode(" C001 ", "C001", "编码不一致");
    }

    @Test
    void shouldSkipOptionalCodeWhenOneSideBlank() {
        BusinessDocumentValidator.requireSameOptionalCode(null, "C001", "编码不一致");
        BusinessDocumentValidator.requireSameOptionalCode("C001", " ", "编码不一致");
    }

    @Test
    void shouldThrowWhenOptionalCodeMismatch() {
        assertThatThrownBy(() -> BusinessDocumentValidator.requireSameOptionalCode("C001", "C002", "编码不一致"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("编码不一致");
    }

    @Test
    void shouldAllowSameInteger() {
        BusinessDocumentValidator.requireSameInteger(1, 1, "整数不一致");
        BusinessDocumentValidator.requireSameInteger(null, null, "整数不一致");
    }

    @Test
    void shouldThrowWhenIntegerMismatch() {
        assertThatThrownBy(() -> BusinessDocumentValidator.requireSameInteger(1, 2, "整数不一致"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("整数不一致");
    }

    @Test
    void shouldAllowSameDecimalIgnoringScale() {
        BusinessDocumentValidator.requireSameDecimal(new BigDecimal("1.0"), new BigDecimal("1.00"), "数值不一致");
    }

    @Test
    void shouldThrowWhenDecimalMismatchOrMissing() {
        assertThatThrownBy(() -> BusinessDocumentValidator.requireSameDecimal(new BigDecimal("1.0"), new BigDecimal("2.0"), "数值不一致"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("数值不一致");
        assertThatThrownBy(() -> BusinessDocumentValidator.requireSameDecimal(null, new BigDecimal("1.0"), "数值不一致"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("数值不一致");
    }

    @Test
    void shouldValidateSourceFieldMessage() {
        assertThatThrownBy(() -> BusinessDocumentValidator.requireSameSourceText(
                "A", "B", 2, "来源销售订单明细", "物料编码"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("第2行来源销售订单明细物料编码与请求不一致");
        assertThatThrownBy(() -> BusinessDocumentValidator.requireSameSourceInteger(
                1, 2, 3, "来源采购订单明细", "每捆支数"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("第3行来源采购订单明细每捆支数与请求不一致");
        assertThatThrownBy(() -> BusinessDocumentValidator.requireSameSourceDecimal(
                new BigDecimal("1.0"), new BigDecimal("2.0"), 4, "来源采购订单明细", "单价"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("第4行来源采购订单明细单价与请求不一致");
    }

    @Test
    void shouldNormalizeText() {
        assertThat(BusinessDocumentValidator.normalizeText(" A ")).isEqualTo("A");
        assertThat(BusinessDocumentValidator.normalizeText(null)).isEmpty();
    }

    @Test
    void shouldTrimToNull() {
        assertThat(BusinessDocumentValidator.trimToNull(" A ")).isEqualTo("A");
        assertThat(BusinessDocumentValidator.trimToNull(" ")).isNull();
        assertThat(BusinessDocumentValidator.trimToNull(null)).isNull();
    }
}
