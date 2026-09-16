package com.leo.erp.market.quotation.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.market.quotation.web.dto.QuoteProjectConfigRequest;
import com.leo.erp.market.quotation.web.dto.QuoteProjectConfigResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuoteProjectConfigServiceTest {

    @Mock
    private QuoteProjectConfigStore store;

    private QuoteProjectConfigService service() {
        return new QuoteProjectConfigService(store);
    }

    private QuoteProjectConfigRequest request() {
        return new QuoteProjectConfigRequest(
                new BigDecimal("30"), false, List.of(), List.of(), null,
                List.of(new QuoteProjectConfigRequest.BrandRequest("中天", new BigDecimal("30"), List.of(), 0)));
    }

    private QuoteProjectConfigResponse response() {
        return new QuoteProjectConfigResponse(88L, new BigDecimal("30"), false, List.of(), List.of(), null,
                List.of(), 0L);
    }

    @Test
    void find_delegatesToStore() {
        when(store.find(88L)).thenReturn(response());

        assertThat(service().find(88L).projectId()).isEqualTo(88L);
    }

    @Test
    void save_retriesOnOptimisticConflictThenSucceeds() {
        when(store.save(eq(88L), any(QuoteProjectConfigRequest.class), any()))
                .thenThrow(new ObjectOptimisticLockingFailureException("QuoteProjectConfig", 88L))
                .thenReturn(response());

        QuoteProjectConfigResponse result = service().save(88L, request(), null);

        assertThat(result.projectId()).isEqualTo(88L);
        verify(store, times(2)).save(eq(88L), any(QuoteProjectConfigRequest.class), any());
    }

    @Test
    void save_propagatesWhenConflictsPersist() {
        when(store.save(eq(88L), any(QuoteProjectConfigRequest.class), any()))
                .thenThrow(new ObjectOptimisticLockingFailureException("QuoteProjectConfig", 88L));

        assertThatThrownBy(() -> service().save(88L, request(), null))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
        verify(store, times(3)).save(eq(88L), any(QuoteProjectConfigRequest.class), any());
    }

    @Test
    void save_propagatesVersionConflictWithoutRetry() {
        when(store.save(eq(88L), any(QuoteProjectConfigRequest.class), any()))
                .thenThrow(new BusinessException(ErrorCode.PRECONDITION_FAILED, "项目配置版本已变更，请刷新后重试"));

        assertThatThrownBy(() -> service().save(88L, request(), 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("版本已变更");
        verify(store, times(1)).save(eq(88L), any(QuoteProjectConfigRequest.class), any());
    }
}
