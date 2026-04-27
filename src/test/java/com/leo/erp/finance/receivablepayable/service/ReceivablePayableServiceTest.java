package com.leo.erp.finance.receivablepayable.service;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.finance.receivablepayable.repository.ReceivablePayableQueryRepository;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class ReceivablePayableServiceTest {

    @Test
    void shouldRejectUnknownDirectionFilter() {
        ReceivablePayableService service = new ReceivablePayableService(mock(ReceivablePayableQueryRepository.class));

        assertThatThrownBy(() -> service.page(new PageQuery(0, 10, "status", "desc"), "未知", null, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("direction 不合法");
    }

    @Test
    void shouldRejectUnknownCounterpartyTypeFilter() {
        ReceivablePayableService service = new ReceivablePayableService(mock(ReceivablePayableQueryRepository.class));

        assertThatThrownBy(() -> service.page(new PageQuery(0, 10, "status", "desc"), null, "未知", null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("counterpartyType 不合法");
    }
}
