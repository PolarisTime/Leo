package com.leo.erp.sales.order.service;

import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.sales.order.repository.SalesOrderSourceCandidateQueryRepository;
import com.leo.erp.sales.order.web.dto.SalesOrderSourceCandidateResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SalesOrderSourceCandidateService 测试：委托转发与参数透传。
 */
@ExtendWith(MockitoExtension.class)
class SalesOrderSourceCandidateServiceTest {

    @Mock
    private SalesOrderSourceCandidateQueryRepository repository;

    @InjectMocks
    private SalesOrderSourceCandidateService service;

    @Test
    void page_shouldDelegateToRepositoryWithAllParameters() {
        PageQuery query = mock(PageQuery.class);
        @SuppressWarnings("unchecked")
        PageResponse<SalesOrderSourceCandidateResponse> expected = mock(PageResponse.class);
        when(repository.page("钢材", 1L, 2L, null, null, 3L, query)).thenReturn(expected);

        PageResponse<SalesOrderSourceCandidateResponse> result =
                service.page("钢材", 1L, 2L, null, null, 3L, query);

        assertThat(result).isSameAs(expected);
        verify(repository).page("钢材", 1L, 2L, null, null, 3L, query);
    }
}
