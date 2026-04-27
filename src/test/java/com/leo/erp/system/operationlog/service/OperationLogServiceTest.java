package com.leo.erp.system.operationlog.service;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.system.operationlog.repository.OperationLogRepository;
import com.leo.erp.system.operationlog.mapper.OperationLogMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OperationLogServiceTest {

    @Test
    void shouldAllowArbitraryModuleNameAndActionTypeFilters() {
        OperationLogRepository repository = mock(OperationLogRepository.class);
        when(repository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(Page.empty());
        OperationLogService service = new OperationLogService(repository, mock(OperationLogMapper.class), null, null);

        assertThatCode(() -> service.page(new PageQuery(0, 20, null, null), null, "API Key 管理", "禁用 API Key", null, null, null))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldAllowMergedRoleModuleNameFilterAlias() {
        OperationLogRepository repository = mock(OperationLogRepository.class);
        when(repository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(Page.empty());
        OperationLogService service = new OperationLogService(repository, mock(OperationLogMapper.class), null, null);

        assertThatCode(() -> service.page(new PageQuery(0, 20, null, null), null, "角色权限配置", null, null, null, null))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldRejectUnknownResultStatusFilter() {
        OperationLogService service = new OperationLogService(null, null, null, null);

        assertThatThrownBy(() -> service.page(new PageQuery(0, 20, null, null), null, null, null, "异常", null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("resultStatus 不合法");
    }

    @Test
    void shouldRejectInvertedDateRange() {
        OperationLogService service = new OperationLogService(null, null, null, null);

        assertThatThrownBy(() -> service.page(
                new PageQuery(0, 20, null, null),
                null,
                null,
                null,
                null,
                LocalDate.of(2026, 4, 30),
                LocalDate.of(2026, 4, 1)
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("startTime 不能晚于 endTime");
    }
}
