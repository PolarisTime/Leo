package com.leo.erp.market.web;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.market.service.PricingQuoteResolver;
import com.leo.erp.master.api.ProjectPricingBasisQuery.ClosedSettleDirection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

/**
 * 取价基准解析接口的入参契约：休市位移的查询参数用全名
 * {@code priceClosedSettleDirection} / {@code priceClosedSettleDays}，非法方向必须 422 而不是静默回落。
 */
@ExtendWith(MockitoExtension.class)
class V2ProjectPricingQuoteResolutionControllerTest {

    @Mock
    private PricingQuoteResolver pricingQuoteResolver;

    private V2ProjectPricingQuoteResolutionController controller() {
        return new V2ProjectPricingQuoteResolutionController(pricingQuoteResolver);
    }

    @Test
    void 非法休市位移方向返回422() {
        assertThatThrownBy(() -> controller().resolve(
                1L, null, null, null, null, "SIDEWAYS", null, null, null, null))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.VALIDATION_ERROR))
                .hasMessage("休市位移方向仅支持 PREV(往前)/NEXT(延后)");
    }

    @Test
    void 休市位移方向与天数原样透传给解析命令() {
        controller().resolve(1L, null, null, "order", 2, "next", 0, null, null, null);

        ArgumentCaptor<PricingQuoteResolver.Command> captor =
                ArgumentCaptor.forClass(PricingQuoteResolver.Command.class);
        verify(pricingQuoteResolver).resolve(eq(1L), captor.capture());
        PricingQuoteResolver.Command command = captor.getValue();
        assertThat(command.offsetDays()).isEqualTo(2);
        assertThat(command.closedSettleDirection()).isEqualTo(ClosedSettleDirection.NEXT);
        assertThat(command.closedSettleDays()).isZero();
    }

    @Test
    void 未传休市位移参数时留空交由项目配置决定() {
        controller().resolve(1L, null, null, null, null, "  ", null, null, null, null);

        ArgumentCaptor<PricingQuoteResolver.Command> captor =
                ArgumentCaptor.forClass(PricingQuoteResolver.Command.class);
        verify(pricingQuoteResolver).resolve(eq(1L), captor.capture());
        assertThat(captor.getValue().closedSettleDirection()).isNull();
        assertThat(captor.getValue().closedSettleDays()).isNull();
    }

    @Test
    void 非法取价基准返回422() {
        assertThatThrownBy(() -> controller().resolve(
                1L, null, null, "WEEKLY", null, null, null, null, null, null))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.VALIDATION_ERROR))
                .hasMessageContaining("取价基准仅支持 DELIVERY/ORDER/MANUAL");
    }
}
