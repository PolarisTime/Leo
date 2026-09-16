package com.leo.erp.market.quotation.web;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResourceVersionPreconditionTest {

    @Test
    void parse_canonicalHeader_returnsVersion() {
        assertThat(ResourceVersionPrecondition.parse("3", null, true)).isEqualTo(3L);
    }

    @Test
    void parse_legacyIfMatchWeakEtag_returnsVersion() {
        assertThat(ResourceVersionPrecondition.parse(null, "W/\"7\"", true)).isEqualTo(7L);
    }

    @Test
    void parse_missingVersion_whenRequired_throws428() {
        assertThatThrownBy(() -> ResourceVersionPrecondition.parse(null, null, true))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.PRECONDITION_REQUIRED);
    }

    @Test
    void parse_missingVersion_whenNotRequired_returnsNull() {
        assertThat(ResourceVersionPrecondition.parse(null, null, false)).isNull();
    }

    @Test
    void parse_conflictingHeaders_rejected() {
        assertThatThrownBy(() -> ResourceVersionPrecondition.parse("3", "\"4\"", true))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不一致");
    }

    @Test
    void parse_invalidCanonicalValue_rejected() {
        assertThatThrownBy(() -> ResourceVersionPrecondition.parse("abc", null, true))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    void parse_zeroVersion_isValid() {
        assertThat(ResourceVersionPrecondition.parse("0", null, true)).isZero();
    }
}
