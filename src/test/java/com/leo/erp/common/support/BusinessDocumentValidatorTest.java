package com.leo.erp.common.support;

import com.leo.erp.common.error.BusinessException;
import org.junit.jupiter.api.Test;

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
    void shouldTrimToNull() {
        assertThat(BusinessDocumentValidator.trimToNull(" A ")).isEqualTo("A");
        assertThat(BusinessDocumentValidator.trimToNull(" ")).isNull();
        assertThat(BusinessDocumentValidator.trimToNull(null)).isNull();
    }
}
