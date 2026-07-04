package com.leo.erp.auth.service;

import com.leo.erp.auth.domain.entity.UserAccount;
import com.leo.erp.auth.domain.enums.UserStatus;
import com.leo.erp.auth.repository.UserAccountRepository;
import com.leo.erp.auth.web.dto.LoginNameAvailabilityResponse;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.support.RedisJsonCacheSupport;
import com.leo.erp.system.department.domain.entity.Department;
import com.leo.erp.system.department.repository.DepartmentRepository;
import com.leo.erp.system.norule.service.SystemSwitchService;
import com.leo.erp.system.role.domain.entity.RoleSetting;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class UserAccountValidationServiceTest {

    @Test
    void normalizeLoginNameShouldTrimAndReturn() {
        UserAccountValidationService service = new UserAccountValidationService(
                null, null, null, null
        );

        assertThat(service.normalizeLoginName("  admin  ")).isEqualTo("admin");
    }

    @Test
    void normalizeLoginNameShouldThrowWhenExceeds64() {
        UserAccountValidationService service = new UserAccountValidationService(
                null, null, null, null
        );

        assertThatThrownBy(() -> service.normalizeLoginName("a".repeat(65)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void normalizeLoginNameShouldThrowWhenBlank() {
        UserAccountValidationService service = new UserAccountValidationService(
                null, null, null, null
        );

        assertThatThrownBy(() -> service.normalizeLoginName(" "))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("登录账号不能为空");
    }

    @Test
    void ensureLoginNameAvailableShouldThrowWhenAlreadyExists() {
        UserAccountRepository repository = mock(UserAccountRepository.class);
        UserAccount existing = new UserAccount();
        existing.setId(1L);
        when(repository.findByLoginNameAndDeletedFlagFalse("admin")).thenReturn(Optional.of(existing));

        UserAccountValidationService service = new UserAccountValidationService(
                repository, null, null, null
        );

        assertThatThrownBy(() -> service.ensureLoginNameAvailable("admin", 2L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void ensureLoginNameAvailableShouldAllowSameUserIdFromOwnerCacheAndRepository() {
        UserAccountRepository repository = mock(UserAccountRepository.class);
        RedisJsonCacheSupport cacheSupport = mock(RedisJsonCacheSupport.class);
        UserAccount existing = new UserAccount();
        existing.setId(10L);
        when(cacheSupport.getOrLoad(startsWith("auth:user:login-name:owner:"), any(), eq(Long.class), any()))
                .thenReturn(10L);
        when(repository.findByLoginNameAndDeletedFlagFalse("admin")).thenReturn(Optional.of(existing));

        UserAccountValidationService service = new UserAccountValidationService(
                repository, null, cacheSupport, null
        );

        service.ensureLoginNameAvailable("admin", 10L);

        verify(repository).findByLoginNameAndDeletedFlagFalse("admin");
    }

    @Test
    void ensureLoginNameAvailableShouldThrowWhenSecondRepositoryCheckFindsDifferentUser() {
        UserAccountRepository repository = mock(UserAccountRepository.class);
        RedisJsonCacheSupport cacheSupport = mock(RedisJsonCacheSupport.class);
        UserAccount existing = new UserAccount();
        existing.setId(20L);
        when(cacheSupport.getOrLoad(startsWith("auth:user:login-name:owner:"), any(), eq(Long.class), any()))
                .thenReturn(0L);
        when(repository.findByLoginNameAndDeletedFlagFalse("admin")).thenReturn(Optional.of(existing));

        UserAccountValidationService service = new UserAccountValidationService(
                repository, null, cacheSupport, null
        );

        assertThatThrownBy(() -> service.ensureLoginNameAvailable("admin", 10L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("登录账号已存在");
        verify(repository).findByLoginNameAndDeletedFlagFalse("admin");
    }

    @Test
    void resolveLoginNameAvailabilityShouldUseRedisOwnerCacheValue() {
        UserAccountRepository repository = mock(UserAccountRepository.class);
        RedisJsonCacheSupport cacheSupport = mock(RedisJsonCacheSupport.class);
        when(cacheSupport.getOrLoad(startsWith("auth:user:login-name:owner:"), any(), eq(Long.class), any()))
                .thenReturn(5L);

        UserAccountValidationService service = new UserAccountValidationService(
                repository, null, cacheSupport, null
        );

        LoginNameAvailabilityResponse response = service.resolveLoginNameAvailability("admin", 7L);

        assertThat(response.available()).isFalse();
        assertThat(response.message()).isEqualTo("登录账号已存在");
        verifyNoInteractions(repository);
    }

    @Test
    void resolveLoginNameAvailabilityShouldReturnAvailableWhenRedisOwnerCacheReturnsNull() {
        UserAccountRepository repository = mock(UserAccountRepository.class);
        RedisJsonCacheSupport cacheSupport = mock(RedisJsonCacheSupport.class);
        when(cacheSupport.getOrLoad(startsWith("auth:user:login-name:owner:"), any(), eq(Long.class), any()))
                .thenReturn(null);

        UserAccountValidationService service = new UserAccountValidationService(
                repository, null, cacheSupport, null
        );

        LoginNameAvailabilityResponse response = service.resolveLoginNameAvailability("admin", 7L);

        assertThat(response.available()).isTrue();
        assertThat(response.message()).isNull();
        verifyNoInteractions(repository);
    }

    @Test
    void loadLoginNameOwnerIdShouldLoadRepositoryResultThroughRedisWhenCacheMisses() {
        UserAccountRepository repository = mock(UserAccountRepository.class);
        RedisJsonCacheSupport cacheSupport = mock(RedisJsonCacheSupport.class);
        UserAccount existing = new UserAccount();
        existing.setId(12L);
        when(repository.findByLoginNameAndDeletedFlagFalse("cached")).thenReturn(Optional.of(existing));
        when(cacheSupport.getOrLoad(startsWith("auth:user:login-name:owner:"), any(), eq(Long.class), any()))
                .thenAnswer(invocation -> invocation.<Supplier<Long>>getArgument(3).get());

        UserAccountValidationService service = new UserAccountValidationService(
                repository, null, cacheSupport, null
        );

        Long ownerId = service.loadLoginNameOwnerId("cached");

        assertThat(ownerId).isEqualTo(12L);
        verify(cacheSupport).getOrLoad(
                eq("auth:user:login-name:owner:cached"),
                any(),
                eq(Long.class),
                any()
        );
        verify(repository).findByLoginNameAndDeletedFlagFalse("cached");
    }

    @Test
    void loadLoginNameOwnerIdShouldCacheNotFoundMarkerWhenRepositoryIsEmpty() {
        UserAccountRepository repository = mock(UserAccountRepository.class);
        RedisJsonCacheSupport cacheSupport = mock(RedisJsonCacheSupport.class);
        when(repository.findByLoginNameAndDeletedFlagFalse("missing")).thenReturn(Optional.empty());
        when(cacheSupport.getOrLoad(startsWith("auth:user:login-name:owner:"), any(), eq(Long.class), any()))
                .thenAnswer(invocation -> invocation.<Supplier<Long>>getArgument(3).get());

        UserAccountValidationService service = new UserAccountValidationService(
                repository, null, cacheSupport, null
        );

        Long ownerId = service.loadLoginNameOwnerId("missing");

        assertThat(ownerId).isZero();
        verify(repository).findByLoginNameAndDeletedFlagFalse("missing");
    }

    @Test
    void resolveLoginNameAvailabilityShouldReturnAvailableWhenNotFound() {
        UserAccountRepository repository = mock(UserAccountRepository.class);
        when(repository.findByLoginNameAndDeletedFlagFalse("newuser")).thenReturn(Optional.empty());

        UserAccountValidationService service = new UserAccountValidationService(
                repository, null, null, null
        );

        LoginNameAvailabilityResponse response = service.resolveLoginNameAvailability("newuser", null);

        assertThat(response.available()).isTrue();
    }

    @Test
    void toStatusShouldReturnNormalForNullOrChinese() {
        UserAccountValidationService service = new UserAccountValidationService(
                null, null, null, null
        );

        assertThat(service.toStatus(null)).isEqualTo(UserStatus.NORMAL);
        assertThat(service.toStatus("正常")).isEqualTo(UserStatus.NORMAL);
        assertThat(service.toStatus("禁用")).isEqualTo(UserStatus.DISABLED);
    }

    @Test
    void toStatusShouldThrowForInvalidValue() {
        UserAccountValidationService service = new UserAccountValidationService(
                null, null, null, null
        );

        assertThatThrownBy(() -> service.toStatus("invalid"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void applyDepartmentShouldSetDepartmentFields() {
        DepartmentRepository departmentRepository = mock(DepartmentRepository.class);
        Department department = new Department();
        department.setId(10L);
        department.setDepartmentName("技术部");
        department.setStatus("正常");
        when(departmentRepository.findByIdAndDeletedFlagFalse(10L)).thenReturn(Optional.of(department));

        UserAccountValidationService service = new UserAccountValidationService(
                null, departmentRepository, null, null
        );

        UserAccount entity = new UserAccount();
        service.applyDepartment(entity, 10L);

        assertThat(entity.getDepartmentId()).isEqualTo(10L);
        assertThat(entity.getDepartmentName()).isEqualTo("技术部");
    }

    @Test
    void applyDepartmentShouldThrowWhenDepartmentDisabled() {
        DepartmentRepository departmentRepository = mock(DepartmentRepository.class);
        Department department = new Department();
        department.setId(10L);
        department.setStatus("禁用");
        when(departmentRepository.findByIdAndDeletedFlagFalse(10L)).thenReturn(Optional.of(department));

        UserAccountValidationService service = new UserAccountValidationService(
                null, departmentRepository, null, null
        );

        assertThatThrownBy(() -> service.applyDepartment(new UserAccount(), 10L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void applyDepartmentShouldThrowWhenDepartmentIdIsNull() {
        DepartmentRepository departmentRepository = mock(DepartmentRepository.class);
        UserAccountValidationService service = new UserAccountValidationService(
                null, departmentRepository, null, null
        );

        assertThatThrownBy(() -> service.applyDepartment(new UserAccount(), null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("请选择所属部门");
        verifyNoInteractions(departmentRepository);
    }

    @Test
    void applyDepartmentShouldThrowWhenRepositoryIsMissing() {
        UserAccountValidationService service = new UserAccountValidationService(
                null, null, null, null
        );

        assertThatThrownBy(() -> service.applyDepartment(new UserAccount(), 10L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("部门不存在");
    }

    @Test
    void applyDepartmentShouldThrowWhenDepartmentNotFound() {
        DepartmentRepository departmentRepository = mock(DepartmentRepository.class);
        when(departmentRepository.findByIdAndDeletedFlagFalse(10L)).thenReturn(Optional.empty());
        UserAccountValidationService service = new UserAccountValidationService(
                null, departmentRepository, null, null
        );

        assertThatThrownBy(() -> service.applyDepartment(new UserAccount(), 10L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("部门不存在");
    }

    @Test
    void resolveInitialPasswordShouldReturnProvidedPassword() {
        UserAccountValidationService service = new UserAccountValidationService(
                null, null, null, null
        );

        assertThat(service.resolveInitialPassword("MyPass123")).isEqualTo("MyPass123");
    }

    @Test
    void resolveInitialPasswordShouldGenerateWhenNull() {
        UserAccountValidationService service = new UserAccountValidationService(
                null, null, null, null
        );

        String password = service.resolveInitialPassword(null);

        assertThat(password).hasSize(8);
    }

    @Test
    void resolveEffectiveDataScopeShouldReturnHighestScope() {
        UserAccountValidationService service = new UserAccountValidationService(
                null, null, null, null
        );

        RoleSetting role1 = new RoleSetting();
        role1.setDataScope("本人");
        RoleSetting role2 = new RoleSetting();
        role2.setDataScope("本部门");

        String result = service.resolveEffectiveDataScope(List.of(role1, role2));

        assertThat(result).isEqualTo("本部门");
    }

    @Test
    void resolveEffectiveDataScopeShouldNormalizeAllAliasToAllData() {
        UserAccountValidationService service = new UserAccountValidationService(
                null, null, null, null
        );

        RoleSetting role = new RoleSetting();
        role.setDataScope("全部");

        assertThat(service.resolveEffectiveDataScope(List.of(role))).isEqualTo("全部数据");
    }

    @Test
    void resolveEffectiveDataScopeShouldUseSelfForBlankDataScope() {
        UserAccountValidationService service = new UserAccountValidationService(
                null, null, null, null
        );

        RoleSetting role = new RoleSetting();
        role.setDataScope(" ");

        assertThat(service.resolveEffectiveDataScope(List.of(role))).isEqualTo("本人");
    }

    @Test
    void resolveEffectiveDataScopeShouldThrowForInvalidScope() {
        UserAccountValidationService service = new UserAccountValidationService(
                null, null, null, null
        );

        RoleSetting role = new RoleSetting();
        role.setDataScope("跨组织");

        assertThatThrownBy(() -> service.resolveEffectiveDataScope(List.of(role)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("数据范围不合法");
    }

    @Test
    void shouldRequireTotpSetupForNewUserShouldDelegate() {
        SystemSwitchService switchService = mock(SystemSwitchService.class);
        when(switchService.shouldForceUserTotpOnFirstLogin()).thenReturn(true);

        UserAccountValidationService service = new UserAccountValidationService(
                null, null, null, switchService
        );

        assertThat(service.shouldRequireTotpSetupForNewUser()).isTrue();
    }

    @Test
    void shouldRequireTotpSetupForNewUserShouldReturnFalseWhenSwitchServiceIsMissing() {
        UserAccountValidationService service = new UserAccountValidationService(
                null, null, null, null
        );

        assertThat(service.shouldRequireTotpSetupForNewUser()).isFalse();
    }

    @Test
    void shouldRequireTotpSetupForNewUserShouldReturnFalseWhenSwitchIsDisabled() {
        SystemSwitchService switchService = mock(SystemSwitchService.class);
        when(switchService.shouldForceUserTotpOnFirstLogin()).thenReturn(false);

        UserAccountValidationService service = new UserAccountValidationService(
                null, null, null, switchService
        );

        assertThat(service.shouldRequireTotpSetupForNewUser()).isFalse();
    }

    @Test
    void generateRandomInitialPasswordShouldContainRequiredCharTypes() {
        UserAccountValidationService service = new UserAccountValidationService(
                null, null, null, null
        );

        String password = service.generateRandomInitialPassword();

        assertThat(password).hasSize(8);
        assertThat(password).matches(p -> p.chars().anyMatch(Character::isUpperCase));
        assertThat(password).matches(p -> p.chars().anyMatch(Character::isLowerCase));
        assertThat(password).matches(p -> p.chars().anyMatch(Character::isDigit));
    }
}
