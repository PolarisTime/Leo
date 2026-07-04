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
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OperationLogServiceTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

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
    void shouldPersistTrimmedFieldsAndDefaultOperatorFromExplicitLoginName() {
        OperationLogRepository repository = mock(OperationLogRepository.class);
        OperationLogService service = new OperationLogService(repository, mock(OperationLogMapper.class),
                new SnowflakeIdGenerator(1), null);

        OperationLogCommand command = new OperationLogCommand(
                "用户管理", "创建", " BIZ001 ", "POST", "/api/users",
                " 127.0.0.1 ", "成功", "x".repeat(300), 9L, " user-account ",
                null, null, " admin "
        );

        service.record(command);

        ArgumentCaptor<OperationLog> captor = ArgumentCaptor.forClass(OperationLog.class);
        verify(repository).save(captor.capture());
        OperationLog saved = captor.getValue();
        assertThat(saved.getOperatorId()).isNull();
        assertThat(saved.getOperatorName()).isEqualTo("admin");
        assertThat(saved.getLoginName()).isEqualTo("admin");
        assertThat(saved.getBusinessNo()).isEqualTo("BIZ001");
        assertThat(saved.getRecordId()).isEqualTo(9L);
        assertThat(saved.getModuleKey()).isEqualTo("user-account");
        assertThat(saved.getClientIp()).isEqualTo("127.0.0.1");
        assertThat(saved.getAuthType()).isEqualTo("SYSTEM");
        assertThat(saved.getRemark()).hasSize(255);
        assertThat(saved.getLogNo()).startsWith("OP");
        assertThat(saved.getOperationTime()).isNotNull();
    }

    @Test
    void shouldPersistExplicitOperatorNameAsDefaultLoginName() {
        OperationLogRepository repository = mock(OperationLogRepository.class);
        OperationLogService service = new OperationLogService(repository, mock(OperationLogMapper.class),
                new SnowflakeIdGenerator(1), null);

        OperationLogCommand command = new OperationLogCommand(
                "用户管理", "创建", null, "POST", "/api/users",
                null, "成功", null, null, null,
                null, " 管理员 ", null
        );

        service.record(command);

        ArgumentCaptor<OperationLog> captor = ArgumentCaptor.forClass(OperationLog.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getOperatorName()).isEqualTo("管理员");
        assertThat(captor.getValue().getLoginName()).isEqualTo("管理员");
        assertThat(captor.getValue().getBusinessNo()).isNull();
        assertThat(captor.getValue().getModuleKey()).isNull();
        assertThat(captor.getValue().getClientIp()).isNull();
        assertThat(captor.getValue().getRemark()).isNull();
    }

    @Test
    void shouldFallbackToPrincipalUsernameWhenUserAccountMissing() {
        OperationLogRepository repository = mock(OperationLogRepository.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        OperationLogService service = new OperationLogService(repository, mock(OperationLogMapper.class),
                new SnowflakeIdGenerator(1), userAccountRepository);

        SecurityPrincipal principal = SecurityPrincipal.authenticated(5L, "fallback", List.of());
        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(principal);
        SecurityContextHolder.getContext().setAuthentication(authentication);
        when(userAccountRepository.findByIdAndDeletedFlagFalse(5L)).thenReturn(Optional.empty());

        service.record(new OperationLogCommand(
                "用户管理", "创建", null, "POST", "/api/users",
                null, "成功", null
        ));

        ArgumentCaptor<OperationLog> captor = ArgumentCaptor.forClass(OperationLog.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getOperatorId()).isEqualTo(5L);
        assertThat(captor.getValue().getOperatorName()).isEqualTo("fallback");
        assertThat(captor.getValue().getLoginName()).isEqualTo("fallback");
        assertThat(captor.getValue().getAuthType()).isEqualTo("WEB");
    }

    @Test
    void shouldFallbackToPrincipalUsernameWhenUserAccountNameBlank() {
        OperationLogRepository repository = mock(OperationLogRepository.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        OperationLogService service = new OperationLogService(repository, mock(OperationLogMapper.class),
                new SnowflakeIdGenerator(1), userAccountRepository);

        SecurityPrincipal principal = SecurityPrincipal.authenticated(6L, "blank-name-user", List.of());
        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(principal);
        SecurityContextHolder.getContext().setAuthentication(authentication);
        UserAccount user = new UserAccount();
        user.setUserName("  ");
        when(userAccountRepository.findByIdAndDeletedFlagFalse(6L)).thenReturn(Optional.of(user));

        service.record(new OperationLogCommand(
                "用户管理", "创建", null, "POST", "/api/users",
                null, "成功", null
        ));

        ArgumentCaptor<OperationLog> captor = ArgumentCaptor.forClass(OperationLog.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getOperatorName()).isEqualTo("blank-name-user");
        assertThat(captor.getValue().getLoginName()).isEqualTo("blank-name-user");
        assertThat(captor.getValue().getAuthType()).isEqualTo("WEB");
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

        ArgumentCaptor<OperationLog> captor = ArgumentCaptor.forClass(OperationLog.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getAuthType()).isEqualTo("API_KEY");
        assertThat(captor.getValue().getOperatorName()).isEqualTo("system");
        assertThat(captor.getValue().getLoginName()).isEqualTo("system");
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

    @Test
    void shouldBuildPredicateForAllPageFilters() {
        OperationLogRepository repository = mock(OperationLogRepository.class);
        when(repository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(Page.empty());
        OperationLogService service = new OperationLogService(repository, mock(OperationLogMapper.class), null, null);
        PageFilter filter = new PageFilter(" keyword ", null,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31),
                null, null, null, "角色权限配置", " 创建 ", " 成功 ",
                null, null, 123L, null, " WEB ");

        service.page(new PageQuery(0, 20, null, null), filter);

        ArgumentCaptor<Specification<OperationLog>> captor = ArgumentCaptor.forClass(Specification.class);
        verify(repository).findAll(captor.capture(), any(org.springframework.data.domain.Pageable.class));
        executeOperationLogSpec(captor.getValue());
    }

    @Test
    void shouldBuildPredicateWithEmptyFilters() {
        OperationLogRepository repository = mock(OperationLogRepository.class);
        when(repository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(Page.empty());
        OperationLogService service = new OperationLogService(repository, mock(OperationLogMapper.class), null, null);
        PageFilter filter = new PageFilter(null, null, null, null,
                null, null, null, null, null, null,
                null, null, null, null, null);

        service.page(new PageQuery(0, 20, null, null), filter);

        ArgumentCaptor<Specification<OperationLog>> captor = ArgumentCaptor.forClass(Specification.class);
        verify(repository).findAll(captor.capture(), any(org.springframework.data.domain.Pageable.class));
        assertThat(captor.getValue().toPredicate(mock(Root.class), mock(CriteriaQuery.class), mockCriteriaBuilder()))
                .isNotNull();
    }

    @Test
    void shouldBuildPredicateForSpecificModuleName() {
        OperationLogRepository repository = mock(OperationLogRepository.class);
        when(repository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(Page.empty());
        OperationLogService service = new OperationLogService(repository, mock(OperationLogMapper.class), null, null);

        service.page(new PageQuery(0, 20, null, null),
                filter("API Key 管理", null, null, null, null));

        ArgumentCaptor<Specification<OperationLog>> captor = ArgumentCaptor.forClass(Specification.class);
        verify(repository).findAll(captor.capture(), any(org.springframework.data.domain.Pageable.class));
        executeOperationLogSpec(captor.getValue());
    }

    @Test
    void shouldDefaultNamesWhenOnlyOperatorIdIsExplicit() {
        OperationLogRepository repository = mock(OperationLogRepository.class);
        OperationLogService service = new OperationLogService(repository, mock(OperationLogMapper.class),
                new SnowflakeIdGenerator(1), null);
        OperationLogCommand command = new OperationLogCommand(
                "用户管理", "创建", " ", "POST", "/api/users",
                " ", "成功", "创建用户", 11L, " ",
                99L, null, null
        );

        service.record(command);

        ArgumentCaptor<OperationLog> captor = ArgumentCaptor.forClass(OperationLog.class);
        verify(repository).save(captor.capture());
        OperationLog saved = captor.getValue();
        assertThat(saved.getOperatorId()).isEqualTo(99L);
        assertThat(saved.getOperatorName()).isEqualTo("system");
        assertThat(saved.getLoginName()).isEqualTo("system");
        assertThat(saved.getBusinessNo()).isNull();
        assertThat(saved.getModuleKey()).isNull();
        assertThat(saved.getClientIp()).isNull();
    }

    @Test
    void shouldFallbackToPrincipalUsernameWhenUserAccountNameIsNull() {
        OperationLogRepository repository = mock(OperationLogRepository.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        OperationLogService service = new OperationLogService(repository, mock(OperationLogMapper.class),
                new SnowflakeIdGenerator(1), userAccountRepository);
        SecurityPrincipal principal = SecurityPrincipal.authenticated(7L, "null-name-user", List.of());
        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(principal);
        SecurityContextHolder.getContext().setAuthentication(authentication);
        when(userAccountRepository.findByIdAndDeletedFlagFalse(7L)).thenReturn(Optional.of(new UserAccount()));

        service.record(new OperationLogCommand(
                "用户管理", "创建", null, "POST", "/api/users",
                null, "成功", null
        ));

        ArgumentCaptor<OperationLog> captor = ArgumentCaptor.forClass(OperationLog.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getOperatorName()).isEqualTo("null-name-user");
        assertThat(captor.getValue().getLoginName()).isEqualTo("null-name-user");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void executeOperationLogSpec(Specification<OperationLog> spec) {
        Root<OperationLog> root = mock(Root.class);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder criteriaBuilder = mockCriteriaBuilder();
        Path path = mock(Path.class);
        Predicate predicate = mock(Predicate.class);
        when(root.get(any(String.class))).thenReturn(path);
        when(path.in(any(List.class))).thenReturn(predicate);

        assertThat(spec.toPredicate(root, query, criteriaBuilder)).isNotNull();

        verify(root, org.mockito.Mockito.atLeastOnce()).get(any(String.class));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private CriteriaBuilder mockCriteriaBuilder() {
        CriteriaBuilder criteriaBuilder = mock(CriteriaBuilder.class);
        Predicate predicate = mock(Predicate.class);
        when(criteriaBuilder.conjunction()).thenReturn(predicate);
        when(criteriaBuilder.equal(any(Expression.class), any())).thenReturn(predicate);
        when(criteriaBuilder.like(any(Expression.class), any(String.class))).thenReturn(predicate);
        when(criteriaBuilder.greaterThanOrEqualTo(any(Expression.class), any(LocalDateTime.class))).thenReturn(predicate);
        when(criteriaBuilder.lessThan(any(Expression.class), any(LocalDateTime.class))).thenReturn(predicate);
        when(criteriaBuilder.and(nullable(Predicate.class), nullable(Predicate.class))).thenReturn(predicate);
        when(criteriaBuilder.and(any(Predicate[].class))).thenReturn(predicate);
        when(criteriaBuilder.or(any(Predicate[].class))).thenReturn(predicate);
        return criteriaBuilder;
    }
}
