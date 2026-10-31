package com.leo.erp.common.web;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link ResourceVersionPrecondition#verify} 的三态语义（质量报告 P1-2）：
 * 未要求（null）→ 放行；一致 → 放行；不一致/实体无版本 → 412。
 *
 * <p>与 {@code parse(...)} 的分工：parse 负责 428（缺头即拒），verify 负责 412（给了但对不上）。
 * 两者分开的好处是「模块可灰度关闭强制」时，客户端只要带了版本仍然享受 412 保护。</p>
 */
class ResourceVersionPreconditionVerifyTest {

    @Test
    void verify_nullExpectedVersion_skipsCheck() {
        assertThatCode(() -> ResourceVersionPrecondition.verify(null, 9L, "销售订单"))
                .doesNotThrowAnyException();
        assertThatCode(() -> ResourceVersionPrecondition.verify(null, null, "销售订单"))
                .doesNotThrowAnyException();
    }

    @Test
    void verify_matchingVersion_passes() {
        assertThatCode(() -> ResourceVersionPrecondition.verify(3L, 3L, "采购订单"))
                .doesNotThrowAnyException();
    }

    @Test
    void verify_staleVersion_throws412() {
        assertThatThrownBy(() -> ResourceVersionPrecondition.verify(2L, 3L, "采购订单"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("采购订单版本已变更")
                .satisfies(ex -> org.assertj.core.api.Assertions
                        .assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.PRECONDITION_FAILED));
    }

    @Test
    void verify_entityWithoutVersion_throws412() {
        assertThatThrownBy(() -> ResourceVersionPrecondition.verify(1L, null, "销售订单"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> org.assertj.core.api.Assertions
                        .assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.PRECONDITION_FAILED));
    }
}
