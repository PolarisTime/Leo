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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
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
    void shouldRequireTotpSetupForNewUserShouldDelegate() {
        SystemSwitchService switchService = mock(SystemSwitchService.class);
        when(switchService.shouldForceUserTotpOnFirstLogin()).thenReturn(true);

        UserAccountValidationService service = new UserAccountValidationService(
                null, null, null, switchService
        );

        assertThat(service.shouldRequireTotpSetupForNewUser()).isTrue();
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
