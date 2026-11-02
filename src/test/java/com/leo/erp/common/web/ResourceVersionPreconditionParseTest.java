package com.leo.erp.common.web;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link ResourceVersionPrecondition#parse} 的口径：只认规范头 {@code X-Resource-Version}。
 *
 * <p>迁移说明：本类原属 {@code market.quotation.web} 的同名转发壳（连同 {@code If-Match}
 * 兼容别名的用例）。别名下线后转发壳一并删除，用例迁到 common 并只保留规范头语义；</p>
 *
 * <p>与 {@link ResourceVersionPreconditionVerifyTest} 的分工：parse 负责 428（缺头即拒），
 * verify 负责 412（给了但对不上）。</p>
 */
class ResourceVersionPreconditionParseTest {

    @Test
    void parse_canonicalHeader_returnsVersion() {
        assertThat(ResourceVersionPrecondition.parse("3", true)).isEqualTo(3L);
    }

    @Test
    void parse_missingVersion_whenRequired_throwsPreconditionRequired() {
        assertThatThrownBy(() -> ResourceVersionPrecondition.parse(null, true))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.PRECONDITION_REQUIRED);
    }

    @Test
    void parse_missingVersion_whenNotRequired_returnsNull() {
        assertThat(ResourceVersionPrecondition.parse(null, false)).isNull();
    }

    @Test
    void parse_blankVersion_isTreatedAsMissing() {
        assertThat(ResourceVersionPrecondition.parse("   ", false)).isNull();
        assertThatThrownBy(() -> ResourceVersionPrecondition.parse("   ", true))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.PRECONDITION_REQUIRED);
    }

    @Test
    void parse_invalidCanonicalValue_rejected() {
        assertThatThrownBy(() -> ResourceVersionPrecondition.parse("abc", true))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    void parse_weakEtagSyntax_isRejectedAsInvalidCanonicalValue() {
        // 旧兼容别名接受 W/"3" 形态; 规范头必须是纯十进制, 弱验证器语法在此属非法值。
        assertThatThrownBy(() -> ResourceVersionPrecondition.parse("W/\"3\"", true))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    void parse_zeroVersion_isValid() {
        assertThat(ResourceVersionPrecondition.parse("0", true)).isZero();
    }
}
