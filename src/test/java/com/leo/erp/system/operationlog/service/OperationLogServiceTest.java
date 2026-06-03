package com.leo.erp.system.operationlog.service;

import com.leo.erp.auth.domain.entity.UserAccount;
import com.leo.erp.auth.repository.UserAccountRepository;
import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.security.support.SecurityPrincipal;
import com.leo.erp.system.operationlog.domain.entity.OperationLog;
import com.leo.erp.system.operationlog.repository.OperationLogRepository;
import com.leo.erp.system.operationlog.mapper.OperationLogMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OperationLogServiceTest {

    private static PageFilter filter(String moduleName,
                                     String actionType,
                                     String resultStatus,
                                     LocalDate startDate,
                                     LocalDate endDate) {
        return new PageFilter(null, null, startDate, endDate,
                null, null, null, moduleName, actionType, resultStatus,
                null, null, null, null, null);
    }

    @Test
    void shouldAllowArbitraryModuleNameAndActionTypeFilters() {
        OperationLogRepository repository = mock(OperationLogRepository.class);
        when(repository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(Page.empty());
        OperationLogService service = new OperationLogService(repository, mock(OperationLogMapper.class), null, null);

        assertThatCode(() -> service.page(
                new PageQuery(0, 20, null, null),
                filter("API Key 管理", "禁用 API Key", null, null, null)))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldAllowMergedRoleModuleNameFilterAlias() {
        OperationLogRepository repository = mock(OperationLogRepository.class);
        when(repository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(Page.empty());
        OperationLogService service = new OperationLogService(repository, mock(OperationLogMapper.class), null, null);

        assertThatCode(() -> service.page(
                new PageQuery(0, 20, null, null),
                filter("角色权限配置", null, null, null, null)))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldRejectUnknownResultStatusFilter() {
        OperationLogService service = new OperationLogService(null, null, null, null);

        assertThatThrownBy(() -> service.page(
                new PageQuery(0, 20, null, null),
                filter(null, null, "异常", null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("resultStatus 不合法");
    }

    @Test
    void shouldRejectInvertedDateRange() {
        OperationLogService service = new OperationLogService(null, null, null, null);

        assertThatThrownBy(() -> service.page(
                new PageQuery(0, 20, null, null),
                filter(null, null, null,
                        LocalDate.of(2026, 4, 30),
                        LocalDate.of(2026, 4, 1))
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("startTime 不能晚于 endTime");
    }

    @Test
    void shouldRecordOperationLogWithExplicitOperator() {
        OperationLogRepository repository = mock(OperationLogRepository.class);
        OperationLogService service = new OperationLogService(repository, mock(OperationLogMapper.class),
                new SnowflakeIdGenerator(1), null);

        OperationLogCommand command = new OperationLogCommand(
                "用户管理", "创建", "BIZ001", "POST", "/api/users",
                "127.0.0.1", "成功", "创建用户", null, null,
                1L, "管理员", "admin"
        );
        service.record(command);

        verify(repository).save(any(OperationLog.class));
    }

    @Test
    void shouldRecordOperationLogWithSystemOperator() {
        OperationLogRepository repository = mock(OperationLogRepository.class);
        OperationLogService service = new OperationLogService(repository, mock(OperationLogMapper.class),
                new SnowflakeIdGenerator(1), null);

        SecurityContextHolder.clearContext();

        OperationLogCommand command = new OperationLogCommand(
                "用户管理", "创建", "BIZ001", "POST", "/api/users",
                "127.0.0.1", "成功", "创建用户"
        );
        service.record(command);

        verify(repository).save(any(OperationLog.class));
    }

    @Test
    void shouldRecordOperationLogWithAuthenticatedUser() {
        OperationLogRepository repository = mock(OperationLogRepository.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        OperationLogService service = new OperationLogService(repository, mock(OperationLogMapper.class),
                new SnowflakeIdGenerator(1), userAccountRepository);

        SecurityPrincipal principal = SecurityPrincipal.authenticated(1L, "testuser", java.util.List.of());
        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(principal);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        UserAccount user = new UserAccount();
        user.setUserName("测试用户");
        when(userAccountRepository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(user));

        OperationLogCommand command = new OperationLogCommand(
                "用户管理", "创建", "BIZ001", "POST", "/api/users",
                "127.0.0.1", "成功", "创建用户"
        );
        service.record(command);

        verify(repository).save(any(OperationLog.class));
    }

    @Test
    void shouldRecordOperationLogWithNullBusinessNo() {
        OperationLogRepository repository = mock(OperationLogRepository.class);
        OperationLogService service = new OperationLogService(repository, mock(OperationLogMapper.class),
                new SnowflakeIdGenerator(1), null);

        OperationLogCommand command = new OperationLogCommand(
                "用户管理", "创建", null, "POST", "/api/users",
                "127.0.0.1", "成功", "创建用户"
        );
        service.record(command);

        verify(repository).save(any(OperationLog.class));
    }

    @Test
    void shouldRecordOperationLogWithLongRemark() {
        OperationLogRepository repository = mock(OperationLogRepository.class);
        OperationLogService service = new OperationLogService(repository, mock(OperationLogMapper.class),
                new SnowflakeIdGenerator(1), null);

        String longRemark = "a".repeat(300);
        OperationLogCommand command = new OperationLogCommand(
                "用户管理", "创建", "BIZ001", "POST", "/api/users",
                "127.0.0.1", "成功", longRemark
        );
        service.record(command);

        verify(repository).save(any(OperationLog.class));
    }

    @Test
    void shouldAllowRoleSettingModuleNameAlias() {
        OperationLogRepository repository = mock(OperationLogRepository.class);
        when(repository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(Page.empty());
        OperationLogService service = new OperationLogService(repository, mock(OperationLogMapper.class), null, null);

        assertThatCode(() -> service.page(
                new PageQuery(0, 20, null, null),
                filter("角色设置", null, null, null, null)))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldAllowKeywordFilter() {
        OperationLogRepository repository = mock(OperationLogRepository.class);
        when(repository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(Page.empty());
        OperationLogService service = new OperationLogService(repository, mock(OperationLogMapper.class), null, null);

        PageFilter filter = new PageFilter("test", null, null, null,
                null, null, null, null, null, null,
                null, null, null, null, null);

        assertThatCode(() -> service.page(
                new PageQuery(0, 20, null, null), filter))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldAllowRecordIdFilter() {
        OperationLogRepository repository = mock(OperationLogRepository.class);
        when(repository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(Page.empty());
        OperationLogService service = new OperationLogService(repository, mock(OperationLogMapper.class), null, null);

        PageFilter filter = new PageFilter(null, null, null, null,
                null, null, null, null, null, null,
                null, null, 123L, null, null);

        assertThatCode(() -> service.page(
                new PageQuery(0, 20, null, null), filter))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldAllowStartDateFilter() {
        OperationLogRepository repository = mock(OperationLogRepository.class);
        when(repository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(Page.empty());
        OperationLogService service = new OperationLogService(repository, mock(OperationLogMapper.class), null, null);

        assertThatCode(() -> service.page(
                new PageQuery(0, 20, null, null),
                filter(null, null, null, LocalDate.of(2026, 1, 1), null)))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldAllowEndDateFilter() {
        OperationLogRepository repository = mock(OperationLogRepository.class);
        when(repository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(Page.empty());
        OperationLogService service = new OperationLogService(repository, mock(OperationLogMapper.class), null, null);

        assertThatCode(() -> service.page(
                new PageQuery(0, 20, null, null),
                filter(null, null, null, null, LocalDate.of(2026, 12, 31))))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldAllowDateRangeFilter() {
        OperationLogRepository repository = mock(OperationLogRepository.class);
        when(repository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(Page.empty());
        OperationLogService service = new OperationLogService(repository, mock(OperationLogMapper.class), null, null);

        assertThatCode(() -> service.page(
                new PageQuery(0, 20, null, null),
                filter(null, null, null,
                        LocalDate.of(2026, 1, 1),
                        LocalDate.of(2026, 12, 31))))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldAllowResultStatusSuccess() {
        OperationLogRepository repository = mock(OperationLogRepository.class);
        when(repository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(Page.empty());
        OperationLogService service = new OperationLogService(repository, mock(OperationLogMapper.class), null, null);

        assertThatCode(() -> service.page(
                new PageQuery(0, 20, null, null),
                filter(null, null, "成功", null, null)))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldAllowResultStatusFailure() {
        OperationLogRepository repository = mock(OperationLogRepository.class);
        when(repository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(Page.empty());
        OperationLogService service = new OperationLogService(repository, mock(OperationLogMapper.class), null, null);

        assertThatCode(() -> service.page(
                new PageQuery(0, 20, null, null),
                filter(null, null, "失败", null, null)))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldAllowAuthTypeFilter() {
        OperationLogRepository repository = mock(OperationLogRepository.class);
        when(repository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(Page.empty());
        OperationLogService service = new OperationLogService(repository, mock(OperationLogMapper.class), null, null);

        PageFilter filter = new PageFilter(null, null, null, null,
                null, null, null, null, null, null,
                "WEB", null, null, null, null);

        assertThatCode(() -> service.page(
                new PageQuery(0, 20, null, null), filter))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldRecordWithExplicitOperatorNameOnly() {
        OperationLogRepository repository = mock(OperationLogRepository.class);
        OperationLogService service = new OperationLogService(repository, mock(OperationLogMapper.class),
                new SnowflakeIdGenerator(1), null);

        OperationLogCommand command = new OperationLogCommand(
                "用户管理", "创建", "BIZ001", "POST", "/api/users",
                "127.0.0.1", "成功", "创建用户", null, null,
                null, "管理员", null
        );
        service.record(command);

        verify(repository).save(any(OperationLog.class));
    }

    @Test
    void shouldRecordWithExplicitLoginNameOnly() {
        OperationLogRepository repository = mock(OperationLogRepository.class);
        OperationLogService service = new OperationLogService(repository, mock(OperationLogMapper.class),
                new SnowflakeIdGenerator(1), null);

        OperationLogCommand command = new OperationLogCommand(
                "用户管理", "创建", "BIZ001", "POST", "/api/users",
                "127.0.0.1", "成功", "创建用户", null, null,
                null, null, "admin"
        );
        service.record(command);

        verify(repository).save(any(OperationLog.class));
    }

    @Test
    void shouldRecordWithApiKeyAuthType() {
        OperationLogRepository repository = mock(OperationLogRepository.class);
        OperationLogService service = new OperationLogService(repository, mock(OperationLogMapper.class),
                new SnowflakeIdGenerator(1), null);

        Authentication auth = mock(org.springframework.security.core.Authentication.class);
        when(auth.getDetails()).thenReturn(new com.leo.erp.security.jwt.ApiKeyAuthenticationDetails("key123", java.util.List.of(), java.util.List.of()));
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);

        OperationLogCommand command = new OperationLogCommand(
                "API管理", "调用", null, "GET", "/api/test",
                "127.0.0.1", "成功", null
        );
        service.record(command);

        verify(repository).save(any(OperationLog.class));
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    @Test
    void shouldRecordWithBlankRemark() {
        OperationLogRepository repository = mock(OperationLogRepository.class);
        OperationLogService service = new OperationLogService(repository, mock(OperationLogMapper.class),
                new SnowflakeIdGenerator(1), null);

        OperationLogCommand command = new OperationLogCommand(
                "用户管理", "创建", null, "POST", "/api/users",
                "127.0.0.1", "成功", "   "
        );
        service.record(command);

        verify(repository).save(any(OperationLog.class));
    }

    @Test
    void shouldAllowPageWithAllFilters() {
        OperationLogRepository repository = mock(OperationLogRepository.class);
        when(repository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(Page.empty());
        OperationLogService service = new OperationLogService(repository, mock(OperationLogMapper.class), null, null);

        PageFilter filter = new PageFilter("keyword", null,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31),
                null, null, null, "用户管理", "创建", "成功",
                null, null, 123L, 1L, "WEB");

        assertThatCode(() -> service.page(new PageQuery(0, 20, null, null), filter))
                .doesNotThrowAnyException();
    }
}
