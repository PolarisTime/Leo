package com.leo.erp.auth.service;

import com.leo.erp.common.config.RedisTuningProperties;
import com.leo.erp.auth.config.AuthProperties;
import com.leo.erp.auth.domain.entity.UserAccount;
import com.leo.erp.auth.domain.enums.UserStatus;
import com.leo.erp.auth.repository.UserAccountRepository;
import com.leo.erp.auth.repository.UserRoleRepository;
import com.leo.erp.auth.mapper.UserAccountAdminMapper;
import com.leo.erp.auth.web.dto.LoginNameAvailabilityResponse;
import com.leo.erp.auth.web.dto.TotpEnableRequest;
import com.leo.erp.auth.web.dto.TotpSetupResponse;
import com.leo.erp.auth.web.dto.UserAccountCreateResponse;
import com.leo.erp.auth.web.dto.UserAccountAdminResponse;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.security.jwt.AuthenticatedUserCacheService;
import com.leo.erp.system.department.domain.entity.Department;
import com.leo.erp.system.department.repository.DepartmentRepository;
import com.leo.erp.system.norule.service.SystemSwitchService;
import com.leo.erp.system.role.domain.entity.RoleSetting;
import com.leo.erp.system.role.repository.RoleSettingRepository;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class UserAccountAdminServiceTest {

    private static UserAccountAdminService createService(
            UserAccountRepository repository,
            com.leo.erp.common.support.SnowflakeIdGenerator idGenerator,
            org.springframework.security.crypto.password.PasswordEncoder passwordEncoder,
            UserAccountAdminMapper mapper,
            TotpService totpService,
            UserRoleBindingService userRoleBindingService,
            com.leo.erp.security.permission.PermissionService permissionService,
            AuthProperties authProperties,
            SystemSwitchService systemSwitchService,
            AuthenticatedUserCacheService authenticatedUserCacheService,
            com.leo.erp.system.dashboard.service.DashboardSummaryService dashboardSummaryService,
            com.leo.erp.common.support.RedisJsonCacheSupport redisJsonCacheSupport,
            DepartmentRepository departmentRepository) {
        UserAccountValidationService validationService = new UserAccountValidationService(
                repository, departmentRepository, redisJsonCacheSupport, systemSwitchService);
        UserAccountCacheService cacheService = new UserAccountCacheService(
                redisJsonCacheSupport, authenticatedUserCacheService, dashboardSummaryService, permissionService);
        return new UserAccountAdminService(repository, idGenerator, passwordEncoder, mapper,
                totpService, userRoleBindingService, permissionService, validationService, cacheService, null, null, null);
    }

    private static UserAccountAdminService createServiceWithConflictRepo(
            UserAccountRepository repository,
            com.leo.erp.common.support.SnowflakeIdGenerator idGenerator,
            org.springframework.security.crypto.password.PasswordEncoder passwordEncoder,
            UserAccountAdminMapper mapper,
            TotpService totpService,
            UserRoleBindingService userRoleBindingService,
            com.leo.erp.security.permission.PermissionService permissionService,
            AuthProperties authProperties,
            SystemSwitchService systemSwitchService,
            AuthenticatedUserCacheService authenticatedUserCacheService,
            com.leo.erp.system.dashboard.service.DashboardSummaryService dashboardSummaryService,
            com.leo.erp.common.support.RedisJsonCacheSupport redisJsonCacheSupport,
            DepartmentRepository departmentRepository,
            com.leo.erp.system.role.repository.RoleConflictRepository roleConflictRepository) {
        UserAccountValidationService validationService = new UserAccountValidationService(
                repository, departmentRepository, redisJsonCacheSupport, systemSwitchService);
        UserAccountCacheService cacheService = new UserAccountCacheService(
                redisJsonCacheSupport, authenticatedUserCacheService, dashboardSummaryService, permissionService);
        return new UserAccountAdminService(repository, idGenerator, passwordEncoder, mapper,
                totpService, userRoleBindingService, permissionService, validationService, cacheService,
                roleConflictRepository, null, null);
    }

    private UserAccountAdminService createServiceWithAdminGuard(
            UserAccountRepository repository,
            UserAccountAdminMapper mapper,
            UserRoleBindingService userRoleBindingService,
            com.leo.erp.security.permission.PermissionService permissionService,
            DepartmentRepository departmentRepository,
            RoleSettingRepository roleSettingRepository,
            UserRoleRepository userRoleRepository) {
        UserAccountValidationService validationService = new UserAccountValidationService(
                repository, departmentRepository, null, null);
        UserAccountCacheService cacheService = new UserAccountCacheService(
                null, authenticatedUserCacheService(), null, permissionService);
        return new UserAccountAdminService(repository, new FixedIdGenerator(100L), new StubPasswordEncoder(), mapper,
                null, userRoleBindingService, permissionService, validationService, cacheService,
                null, roleSettingRepository, userRoleRepository);
    }

    @Test
    void shouldReturnDerivedPermissionSummaryInsteadOfStoredValue() {
        UserAccount entity = new UserAccount();
        entity.setId(1L);
        entity.setLoginName("tester");
        entity.setUserName("测试用户");
        entity.setMobile("13800000000");
        entity.setPermissionSummary("旧摘要");
        entity.setLastLoginDate(LocalDateTime.of(2026, 4, 25, 12, 0));
        entity.setStatus(UserStatus.NORMAL);
        entity.setTotpEnabled(Boolean.FALSE);

        RoleSetting role = new RoleSetting();
        role.setId(11L);
        role.setRoleName("采购专员");
        role.setRoleCode("PURCHASER");
        role.setStatus("正常");

        UserAccountAdminService service = createService(
                userAccountRepository(entity),
                null,
                null,
                mapper(),
                null,
                new StubUserRoleBindingService(List.of(role)),
                new StubPermissionService("商品资料-查看、采购订单-编辑"),
                new AuthProperties(),
                null,
                authenticatedUserCacheService(),
                null,
                null,
                null
        );

        UserAccountAdminResponse response = service.detail(1L);

        assertThat(response.permissionSummary()).isEqualTo("商品资料-查看、采购订单-编辑");
        assertThat(response.roleNames()).containsExactly("采购专员");
    }

    @Test
    void shouldRejectInvalidStatusWhenSavingUser() {
        RoleSetting role = new RoleSetting();
        role.setId(11L);
        role.setRoleName("采购专员");
        role.setRoleCode("PURCHASER");
        role.setStatus("正常");

        UserAccountAdminService service = createService(
                repositoryForWrite(),
                new FixedIdGenerator(100L),
                new StubPasswordEncoder(),
                mapper(),
                null,
                new StubUserRoleBindingService(List.of(role)),
                new StubPermissionService(""),
                authProperties(),
                null,
                authenticatedUserCacheService(),
                null,
                null,
                departmentRepository()
        );

        assertThatThrownBy(() -> service.create(new com.leo.erp.auth.web.dto.UserAccountAdminRequest(
                "tester",
                null,
                "测试用户",
                "13800000000",
                10L,
                List.of("全部数据"), null,
                "全部数据",
                "",
                "INVALID",
                ""
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("用户状态不合法");
    }

    @Test
    void shouldUseRequestPasswordWhenProvided() {
        RoleSetting role = new RoleSetting();
        role.setId(11L);
        role.setRoleName("采购专员");
        role.setRoleCode("PURCHASER");
        role.setStatus("正常");
        AtomicReference<UserAccount> savedUser = new AtomicReference<>();

        UserAccountAdminService service = createService(
                repositoryForWrite(savedUser),
                new FixedIdGenerator(100L),
                new StubPasswordEncoder(),
                mapper(),
                null,
                new StubUserRoleBindingService(List.of(role)),
                new StubPermissionService(""),
                new AuthProperties(),
                null,
                authenticatedUserCacheService(),
                null,
                null,
                departmentRepository()
        );

        UserAccountCreateResponse response = service.create(new com.leo.erp.auth.web.dto.UserAccountAdminRequest(
                "tester",
                "Init@123",
                "测试用户",
                "13800000000",
                10L,
                List.of("全部数据"), null,
                "全部数据",
                "",
                "正常",
                ""
        ));

        assertThat(savedUser.get()).isNotNull();
        assertThat(savedUser.get().getPasswordHash()).isEqualTo("encoded:Init@123");
        assertThat(savedUser.get().getDepartmentId()).isEqualTo(10L);
        assertThat(savedUser.get().getDepartmentName()).isEqualTo("总部");
        assertThat(response.initialPassword()).isEqualTo("Init@123");
    }

    @Test
    void shouldGenerateRandomPasswordWhenCreatePasswordIsBlank() {
        RoleSetting role = new RoleSetting();
        role.setId(11L);
        role.setRoleName("采购专员");
        role.setRoleCode("PURCHASER");
        role.setStatus("正常");

        AtomicReference<UserAccount> savedUser = new AtomicReference<>();

        UserAccountAdminService service = createService(
                repositoryForWrite(savedUser),
                new FixedIdGenerator(100L),
                new StubPasswordEncoder(),
                mapper(),
                null,
                new StubUserRoleBindingService(List.of(role)),
                new StubPermissionService(""),
                new AuthProperties(),
                null,
                authenticatedUserCacheService(),
                null,
                null,
                departmentRepository()
        );

        UserAccountCreateResponse response = service.create(new com.leo.erp.auth.web.dto.UserAccountAdminRequest(
                "tester",
                null,
                "测试用户",
                "13800000000",
                10L,
                List.of("全部数据"), null,
                "全部数据",
                "",
                "正常",
                ""
        ));

        assertThat(savedUser.get()).isNotNull();
        assertThat(response.initialPassword()).hasSize(8);
        assertThat(response.initialPassword()).matches(".*[A-Z].*");
        assertThat(response.initialPassword()).matches(".*[a-z].*");
        assertThat(response.initialPassword()).matches(".*\\d.*");
        assertThat(savedUser.get().getPasswordHash()).isEqualTo("encoded:" + response.initialPassword());
    }

    @Test
    void shouldMarkNewUserForTotpSetupWhenSwitchEnabled() {
        RoleSetting role = new RoleSetting();
        role.setId(11L);
        role.setRoleName("采购专员");
        role.setRoleCode("PURCHASER");
        role.setStatus("正常");
        AtomicReference<UserAccount> savedUser = new AtomicReference<>();

        UserAccountAdminService service = createService(
                repositoryForWrite(savedUser),
                new FixedIdGenerator(100L),
                new StubPasswordEncoder(),
                mapper(),
                null,
                new StubUserRoleBindingService(List.of(role)),
                new StubPermissionService(""),
                authProperties(),
                enabledTotpSwitchService(),
                authenticatedUserCacheService(),
                null,
                null,
                departmentRepository()
        );

        service.create(new com.leo.erp.auth.web.dto.UserAccountAdminRequest(
                "tester",
                "Init@123",
                "测试用户",
                "13800000000",
                10L,
                List.of("全部数据"), null,
                "全部数据",
                "",
                "正常",
                ""
        ));

        assertThat(savedUser.get()).isNotNull();
        assertThat(savedUser.get().getRequireTotpSetup()).isTrue();
    }

    @Test
    void shouldAllowCreateWhenDeletedUserAlreadyUsesLoginName() {
        RoleSetting role = new RoleSetting();
        role.setId(11L);
        role.setRoleName("采购专员");
        role.setRoleCode("PURCHASER");
        role.setStatus("正常");

        UserAccount existing = new UserAccount();
        existing.setId(99L);
        existing.setLoginName("test");
        existing.setDeletedFlag(true);

        AtomicReference<UserAccount> savedUser = new AtomicReference<>();
        UserAccountAdminService service = createService(
                repositoryForWriteWithExistingLoginName(existing, savedUser),
                new FixedIdGenerator(100L),
                new StubPasswordEncoder(),
                mapper(),
                null,
                new StubUserRoleBindingService(List.of(role)),
                new StubPermissionService(""),
                authProperties(),
                null,
                authenticatedUserCacheService(),
                null,
                null,
                departmentRepository()
        );

        assertThatCode(() -> service.create(new com.leo.erp.auth.web.dto.UserAccountAdminRequest(
                "test",
                "Init@123",
                "测试用户",
                "13800000000",
                10L,
                List.of("全部数据"), null,
                "全部数据",
                "",
                "正常",
                ""
        ))).doesNotThrowAnyException();

        assertThat(savedUser.get()).isNotNull();
        assertThat(savedUser.get().getLoginName()).isEqualTo("test");
    }

    @Test
    void shouldReportLoginNameUnavailableForExistingUser() {
        UserAccount existing = new UserAccount();
        existing.setId(99L);
        existing.setLoginName("test");

        UserAccountAdminService service = createService(
                repositoryWithExistingLoginName(existing),
                new FixedIdGenerator(100L),
                new StubPasswordEncoder(),
                mapper(),
                null,
                new StubUserRoleBindingService(List.of()),
                new StubPermissionService(""),
                authProperties(),
                null,
                authenticatedUserCacheService(),
                null,
                null,
                null
        );

        LoginNameAvailabilityResponse response = service.checkLoginNameAvailability("test", null);

        assertThat(response.available()).isFalse();
        assertThat(response.message()).isEqualTo("登录账号已存在");
    }

    @Test
    void shouldAllowCurrentUserToReuseOwnLoginName() {
        UserAccount existing = new UserAccount();
        existing.setId(99L);
        existing.setLoginName("test");

        UserAccountAdminService service = createService(
                repositoryWithExistingLoginName(existing),
                new FixedIdGenerator(100L),
                new StubPasswordEncoder(),
                mapper(),
                null,
                new StubUserRoleBindingService(List.of()),
                new StubPermissionService(""),
                authProperties(),
                null,
                authenticatedUserCacheService(),
                null,
                null,
                null
        );

        LoginNameAvailabilityResponse response = service.checkLoginNameAvailability("test", 99L);

        assertThat(response.available()).isTrue();
        assertThat(response.message()).isNull();
    }

    @Test
    void shouldUpdateUserWithNewDetails() {
        UserAccount existing = new UserAccount();
        existing.setId(42L);
        existing.setLoginName("old-name");
        existing.setUserName("旧名字");
        existing.setDepartmentId(10L);
        existing.setStatus(UserStatus.NORMAL);

        RoleSetting role = new RoleSetting();
        role.setId(11L);
        role.setRoleName("采购专员");
        role.setRoleCode("PURCHASER");
        role.setStatus("正常");

        AtomicReference<UserAccount> savedUser = new AtomicReference<>();
        UserAccountAdminService service = createService(
                repositoryForUpdate(existing, savedUser),
                new FixedIdGenerator(100L),
                new StubPasswordEncoder(),
                mapper(),
                null,
                new StubUserRoleBindingService(List.of(role)),
                new StubPermissionService("采购订单-编辑"),
                authProperties(),
                null,
                authenticatedUserCacheService(),
                null,
                null,
                departmentRepository()
        );

        UserAccountAdminResponse response = service.update(42L, new com.leo.erp.auth.web.dto.UserAccountAdminRequest(
                "new-name",
                null,
                "新名字",
                "13900000000",
                10L,
                List.of("采购专员"), null,
                "本部门",
                "",
                "正常",
                "备注"
        ));

        assertThat(response.loginName()).isEqualTo("new-name");
        assertThat(response.userName()).isEqualTo("新名字");
        assertThat(savedUser.get()).isNotNull();
        assertThat(savedUser.get().getLoginName()).isEqualTo("new-name");
    }

    @Test
    void shouldPreserveRolesWhenUpdatePayloadOmitsRoleFields() {
        UserAccount existing = new UserAccount();
        existing.setId(42L);
        existing.setLoginName("admin");
        existing.setUserName("系统管理员");
        existing.setDepartmentId(1L);
        existing.setStatus(UserStatus.NORMAL);

        RoleSetting adminRole = adminRole();
        StubUserRoleBindingService roleBindingService = new StubUserRoleBindingService(List.of(adminRole));
        AtomicReference<UserAccount> savedUser = new AtomicReference<>();
        UserAccountAdminService service = createService(
                repositoryForUpdate(existing, savedUser),
                new FixedIdGenerator(100L),
                new StubPasswordEncoder(),
                mapper(),
                null,
                roleBindingService,
                new StubPermissionService("全部权限"),
                authProperties(),
                null,
                authenticatedUserCacheService(),
                null,
                null,
                departmentRepository()
        );

        service.update(42L, new com.leo.erp.auth.web.dto.UserAccountAdminRequest(
                "admin",
                null,
                "系统管理员",
                "",
                10L,
                null, null,
                "全部数据",
                "",
                "正常",
                ""
        ));

        assertThat(savedUser.get()).isNotNull();
        assertThat(savedUser.get().getDepartmentId()).isEqualTo(10L);
        assertThat(roleBindingService.replaceCalled()).isFalse();
    }

    @Test
    void shouldResolveRolesByNameWhenRoleIdsAreProvidedAsEmptyList() {
        UserAccount existing = new UserAccount();
        existing.setId(42L);
        existing.setLoginName("tester");
        existing.setUserName("测试用户");
        existing.setDepartmentId(10L);
        existing.setStatus(UserStatus.NORMAL);

        RoleSetting role = role("PURCHASER", "采购专员", 11L);
        StubUserRoleBindingService roleBindingService = new StubUserRoleBindingService(List.of(role));
        AtomicReference<UserAccount> savedUser = new AtomicReference<>();
        UserAccountAdminService service = createService(
                repositoryForUpdate(existing, savedUser),
                new FixedIdGenerator(100L),
                new StubPasswordEncoder(),
                mapper(),
                null,
                roleBindingService,
                new StubPermissionService("采购权限"),
                authProperties(),
                null,
                authenticatedUserCacheService(),
                null,
                null,
                departmentRepository()
        );

        service.update(42L, new com.leo.erp.auth.web.dto.UserAccountAdminRequest(
                "tester",
                null,
                "测试用户",
                "",
                10L,
                List.of("采购专员"), List.of(),
                "本部门",
                "",
                "正常",
                ""
        ));

        assertThat(savedUser.get()).isNotNull();
        assertThat(roleBindingService.replaceCalled()).isTrue();
        assertThat(roleBindingService.replacedRoles()).containsExactly(role);
    }

    @Test
    void shouldAllowUpdatingEntityWithoutIdWhenRolePayloadIsOmitted() {
        UserAccount existing = new UserAccount();
        existing.setId(null);
        existing.setLoginName("legacy");
        existing.setUserName("旧用户");
        existing.setDepartmentId(10L);
        existing.setStatus(UserStatus.NORMAL);

        RoleSetting role = role("PURCHASER", "采购专员", 11L);
        AtomicReference<UserAccount> savedUser = new AtomicReference<>();
        UserAccountAdminService service = createService(
                repositoryForUpdate(existing, savedUser),
                new FixedIdGenerator(100L),
                new StubPasswordEncoder(),
                mapper(),
                null,
                new StubUserRoleBindingService(List.of(role)),
                new StubPermissionService("采购权限"),
                authProperties(),
                null,
                authenticatedUserCacheService(),
                null,
                null,
                departmentRepository()
        );

        UserAccountAdminResponse response = service.update(42L, new com.leo.erp.auth.web.dto.UserAccountAdminRequest(
                "legacy",
                null,
                "旧用户",
                "",
                10L,
                null, null,
                "本部门",
                "",
                "正常",
                ""
        ));

        assertThat(response.id()).isNull();
        assertThat(savedUser.get()).isNotNull();
    }

    @Test
    void shouldRejectRemovingLastActiveAdminRole() {
        UserAccount existing = adminUser();
        StubUserRoleBindingService roleBindingService = new StubUserRoleBindingService(List.of(adminRole())) {
            @Override
            public List<RoleSetting> resolveRoles(java.util.Collection<String> roleIdentifiers) {
                return List.of();
            }
        };
        UserAccountAdminService service = createServiceWithAdminGuard(
                repositoryForUpdate(existing, new AtomicReference<>()),
                mapper(),
                roleBindingService,
                new StubPermissionService(""),
                departmentRepository(),
                roleSettingRepository(adminRole()),
                userRoleRepositoryWithOtherActiveAdmins(0)
        );

        assertThatThrownBy(() -> service.update(42L, new com.leo.erp.auth.web.dto.UserAccountAdminRequest(
                "admin",
                null,
                "系统管理员",
                "",
                10L,
                List.of(), null,
                "本人",
                "",
                "正常",
                ""
        ))).isInstanceOf(BusinessException.class)
                .hasMessageContaining("至少保留一个正常状态的系统管理员");
    }

    @Test
    void shouldRejectDisablingLastActiveAdmin() {
        UserAccount existing = adminUser();
        UserAccountAdminService service = createServiceWithAdminGuard(
                repositoryForUpdate(existing, new AtomicReference<>()),
                mapper(),
                new StubUserRoleBindingService(List.of(adminRole())),
                new StubPermissionService(""),
                departmentRepository(),
                roleSettingRepository(adminRole()),
                userRoleRepositoryWithOtherActiveAdmins(0)
        );

        assertThatThrownBy(() -> service.update(42L, new com.leo.erp.auth.web.dto.UserAccountAdminRequest(
                "admin",
                null,
                "系统管理员",
                "",
                10L,
                null, List.of(11L),
                "全部数据",
                "",
                "禁用",
                ""
        ))).isInstanceOf(BusinessException.class)
                .hasMessageContaining("至少保留一个正常状态的系统管理员");
    }

    @Test
    void shouldRejectDeletingLastActiveAdmin() {
        UserAccount existing = adminUser();
        UserAccountAdminService service = createServiceWithAdminGuard(
                repositoryForDelete(existing, new AtomicReference<>()),
                mapper(),
                new StubUserRoleBindingService(List.of(adminRole())),
                new StubPermissionService(""),
                null,
                roleSettingRepository(adminRole()),
                userRoleRepositoryWithOtherActiveAdmins(0)
        );

        assertThatThrownBy(() -> service.delete(42L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("至少保留一个正常状态的系统管理员");
    }

    @Test
    void shouldDeleteUserAndEvictCaches() {
        UserAccount existing = new UserAccount();
        existing.setId(42L);
        existing.setLoginName("to-delete");
        existing.setDepartmentId(10L);

        AtomicReference<UserAccount> savedUser = new AtomicReference<>();
        UserAccountAdminService service = createService(
                repositoryForDelete(existing, savedUser),
                new FixedIdGenerator(100L),
                new StubPasswordEncoder(),
                mapper(),
                null,
                new StubUserRoleBindingService(List.of()),
                new StubPermissionService(""),
                authProperties(),
                null,
                authenticatedUserCacheService(),
                null,
                null,
                null
        );

        service.delete(42L);

        assertThat(savedUser.get()).isNotNull();
        assertThat(savedUser.get().isDeletedFlag()).isTrue();
    }

    @Test
    void shouldAllowDeletingUserWhenResolvedRolesAreEmptyOrHaveNullCode() {
        AtomicReference<UserAccount> emptyRoleSavedUser = new AtomicReference<>();
        UserAccountAdminService emptyRoleService = createService(
                repositoryForDelete(user("empty-role", 50L), emptyRoleSavedUser),
                new FixedIdGenerator(100L),
                new StubPasswordEncoder(),
                mapper(),
                null,
                new StubUserRoleBindingService(List.of()),
                new StubPermissionService(""),
                authProperties(),
                null,
                authenticatedUserCacheService(),
                null,
                null,
                null
        );
        AtomicReference<UserAccount> nullCodeSavedUser = new AtomicReference<>();
        UserAccountAdminService nullCodeService = createService(
                repositoryForDelete(user("null-code", 51L), nullCodeSavedUser),
                new FixedIdGenerator(100L),
                new StubPasswordEncoder(),
                mapper(),
                null,
                new StubUserRoleBindingService(List.of(role(null, "无编码角色", 11L))),
                new StubPermissionService(""),
                authProperties(),
                null,
                authenticatedUserCacheService(),
                null,
                null,
                null
        );

        emptyRoleService.delete(50L);
        nullCodeService.delete(51L);

        assertThat(emptyRoleSavedUser.get().isDeletedFlag()).isTrue();
        assertThat(nullCodeSavedUser.get().isDeletedFlag()).isTrue();
    }

    @Test
    void shouldTreatNullRoleAsNonAdminRole() throws Exception {
        UserAccountAdminService service = createService(
                repositoryForNotFound(),
                new FixedIdGenerator(100L),
                new StubPasswordEncoder(),
                mapper(),
                null,
                new StubUserRoleBindingService(List.of()),
                new StubPermissionService(""),
                authProperties(),
                null,
                authenticatedUserCacheService(),
                null,
                null,
                null
        );
        Method method = UserAccountAdminService.class.getDeclaredMethod("isAdminRole", RoleSetting.class);
        method.setAccessible(true);

        assertThat(method.invoke(service, new Object[]{null})).isEqualTo(false);
    }

    @Test
    void shouldThrowWhenDeletingNonExistentUser() {
        UserAccountAdminService service = createService(
                repositoryForNotFound(),
                new FixedIdGenerator(100L),
                new StubPasswordEncoder(),
                mapper(),
                null,
                new StubUserRoleBindingService(List.of()),
                new StubPermissionService(""),
                authProperties(),
                null,
                authenticatedUserCacheService(),
                null,
                null,
                null
        );

        assertThatThrownBy(() -> service.delete(999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("用户不存在");
    }

    @Test
    void shouldSetup2faAndReturnQrCode() {
        UserAccount existing = new UserAccount();
        existing.setId(42L);
        existing.setLoginName("tester");

        AtomicReference<UserAccount> savedUser = new AtomicReference<>();
        StubTotpService totpService = new StubTotpService();

        UserAccountAdminService service = createService(
                repositoryForWrite(existing, savedUser),
                new FixedIdGenerator(100L),
                new StubPasswordEncoder(),
                mapper(),
                totpService,
                new StubUserRoleBindingService(List.of()),
                new StubPermissionService(""),
                authProperties(),
                null,
                authenticatedUserCacheService(),
                null,
                null,
                null
        );

        TotpSetupResponse response = service.setup2fa(42L);

        assertThat(response.qrCodeBase64()).isNotBlank();
        assertThat(response.secret()).isEqualTo("test-secret");
        assertThat(savedUser.get().getTotpSecret()).isEqualTo("encrypted:test-secret");
        assertThat(savedUser.get().getTotpEnabled()).isFalse();
    }

    @Test
    void shouldEnable2faWhenValidCodeProvided() {
        UserAccount existing = new UserAccount();
        existing.setId(42L);
        existing.setLoginName("tester");
        existing.setTotpSecret("encrypted-secret");

        AtomicReference<UserAccount> savedUser = new AtomicReference<>();
        StubTotpService totpService = new StubTotpService();
        totpService.setVerifyResult(true);

        UserAccountAdminService service = createService(
                repositoryForWrite(existing, savedUser),
                new FixedIdGenerator(100L),
                new StubPasswordEncoder(),
                mapper(),
                totpService,
                new StubUserRoleBindingService(List.of()),
                new StubPermissionService(""),
                authProperties(),
                null,
                authenticatedUserCacheService(),
                null,
                null,
                null
        );

        UserAccountAdminResponse response = service.enable2fa(42L, new TotpEnableRequest("123456"));

        assertThat(response.totpEnabled()).isTrue();
        assertThat(savedUser.get().getTotpEnabled()).isTrue();
        assertThat(savedUser.get().getRequireTotpSetup()).isFalse();
    }

    @Test
    void shouldRejectEnable2faWhenNoSecret() {
        UserAccount existing = new UserAccount();
        existing.setId(42L);
        existing.setTotpSecret(null);

        UserAccountAdminService service = createService(
                userAccountRepository(existing),
                new FixedIdGenerator(100L),
                new StubPasswordEncoder(),
                mapper(),
                new StubTotpService(),
                new StubUserRoleBindingService(List.of()),
                new StubPermissionService(""),
                authProperties(),
                null,
                authenticatedUserCacheService(),
                null,
                null,
                null
        );

        assertThatThrownBy(() -> service.enable2fa(42L, new TotpEnableRequest("123456")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("请先生成2FA密钥");
    }

    @Test
    void shouldRejectEnable2faWhenCodeInvalid() {
        UserAccount existing = new UserAccount();
        existing.setId(42L);
        existing.setTotpSecret("encrypted-secret");

        StubTotpService totpService = new StubTotpService();
        totpService.setVerifyResult(false);

        UserAccountAdminService service = createService(
                userAccountRepository(existing),
                new FixedIdGenerator(100L),
                new StubPasswordEncoder(),
                mapper(),
                totpService,
                new StubUserRoleBindingService(List.of()),
                new StubPermissionService(""),
                authProperties(),
                null,
                authenticatedUserCacheService(),
                null,
                null,
                null
        );

        assertThatThrownBy(() -> service.enable2fa(42L, new TotpEnableRequest("000000")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("验证码错误或已过期");
    }

    @Test
    void shouldDisable2faAndClearSecret() {
        UserAccount existing = new UserAccount();
        existing.setId(42L);
        existing.setLoginName("tester");
        existing.setTotpSecret("encrypted-secret");
        existing.setTotpEnabled(Boolean.TRUE);

        AtomicReference<UserAccount> savedUser = new AtomicReference<>();
        UserAccountAdminService service = createService(
                repositoryForWrite(existing, savedUser),
                new FixedIdGenerator(100L),
                new StubPasswordEncoder(),
                mapper(),
                new StubTotpService(),
                new StubUserRoleBindingService(List.of()),
                new StubPermissionService(""),
                authProperties(),
                null,
                authenticatedUserCacheService(),
                null,
                null,
                null
        );

        UserAccountAdminResponse response = service.disable2fa(42L);

        assertThat(response.totpEnabled()).isFalse();
        assertThat(savedUser.get().getTotpSecret()).isNull();
        assertThat(savedUser.get().getTotpEnabled()).isFalse();
    }

    @Test
    void shouldRejectConflictingRoles() {
        RoleSetting roleA = new RoleSetting();
        roleA.setId(11L);
        roleA.setRoleName("采购专员");
        roleA.setRoleCode("PURCHASER");
        roleA.setStatus("正常");

        RoleSetting roleB = new RoleSetting();
        roleB.setId(12L);
        roleB.setRoleName("销售专员");
        roleB.setRoleCode("SALESMAN");
        roleB.setStatus("正常");

        com.leo.erp.system.role.domain.entity.RoleConflict conflict =
                new com.leo.erp.system.role.domain.entity.RoleConflict();
        conflict.setRoleId(11L);
        conflict.setConflictRoleId(12L);

        com.leo.erp.system.role.repository.RoleConflictRepository conflictRepo =
                roleConflictRepository(List.of(conflict));

        UserAccountAdminService service = createServiceWithConflictRepo(
                repositoryForWrite(),
                new FixedIdGenerator(100L),
                new StubPasswordEncoder(),
                mapper(),
                null,
                new StubUserRoleBindingService(List.of(roleA, roleB)),
                new StubPermissionService(""),
                authProperties(),
                null,
                authenticatedUserCacheService(),
                null,
                null,
                departmentRepository(),
                conflictRepo
        );

        assertThatThrownBy(() -> service.create(new com.leo.erp.auth.web.dto.UserAccountAdminRequest(
                "tester",
                "Init@123",
                "测试用户",
                "13800000000",
                10L,
                List.of("采购专员", "销售专员"), null,
                "全部数据",
                "",
                "正常",
                ""
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("互斥");
    }

    @Test
    void shouldAllowMultipleRolesWhenConflictRepositoryReturnsEmpty() {
        RoleSetting roleA = role("PURCHASER", "采购专员", 11L);
        RoleSetting roleB = role("SALESMAN", "销售专员", 12L);
        AtomicReference<UserAccount> savedUser = new AtomicReference<>();
        UserAccountAdminService service = createServiceWithConflictRepo(
                repositoryForWrite(savedUser),
                new FixedIdGenerator(100L),
                new StubPasswordEncoder(),
                mapper(),
                null,
                new StubUserRoleBindingService(List.of(roleA, roleB)),
                new StubPermissionService(""),
                authProperties(),
                null,
                authenticatedUserCacheService(),
                null,
                null,
                departmentRepository(),
                roleConflictRepository(List.of())
        );

        service.create(new com.leo.erp.auth.web.dto.UserAccountAdminRequest(
                "tester",
                "Init@123",
                "测试用户",
                "13800000000",
                10L,
                List.of("采购专员", "销售专员"), null,
                "全部数据",
                "",
                "正常",
                ""
        ));

        assertThat(savedUser.get()).isNotNull();
    }

    @Test
    void shouldResolveRolesByIdsWhenProvided() {
        RoleSetting role = new RoleSetting();
        role.setId(11L);
        role.setRoleName("采购专员");
        role.setRoleCode("PURCHASER");
        role.setStatus("正常");

        AtomicReference<UserAccount> savedUser = new AtomicReference<>();
        UserAccountAdminService service = createService(
                repositoryForWrite(savedUser),
                new FixedIdGenerator(100L),
                new StubPasswordEncoder(),
                mapper(),
                null,
                new StubUserRoleBindingService(List.of(role)),
                new StubPermissionService(""),
                authProperties(),
                null,
                authenticatedUserCacheService(),
                null,
                null,
                departmentRepository()
        );

        UserAccountCreateResponse response = service.create(new com.leo.erp.auth.web.dto.UserAccountAdminRequest(
                "tester",
                "Init@123",
                "测试用户",
                "13800000000",
                10L,
                null, List.of(11L),
                "全部数据",
                "",
                "正常",
                ""
        ));

        assertThat(response).isNotNull();
        assertThat(savedUser.get()).isNotNull();
    }

    @Test
    void shouldHandleDataIntegrityViolationAsLoginNameConflict() {
        RoleSetting role = new RoleSetting();
        role.setId(11L);
        role.setRoleName("采购专员");
        role.setRoleCode("PURCHASER");
        role.setStatus("正常");

        UserAccountAdminService service = createService(
                repositoryWithDataIntegrityViolation("sys_user_login_name_key"),
                new FixedIdGenerator(100L),
                new StubPasswordEncoder(),
                mapper(),
                null,
                new StubUserRoleBindingService(List.of(role)),
                new StubPermissionService(""),
                authProperties(),
                null,
                authenticatedUserCacheService(),
                null,
                null,
                departmentRepository()
        );

        assertThatThrownBy(() -> service.create(new com.leo.erp.auth.web.dto.UserAccountAdminRequest(
                "tester",
                "Init@123",
                "测试用户",
                "13800000000",
                10L,
                List.of("采购专员"), null,
                "全部数据",
                "",
                "正常",
                ""
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("登录账号已存在");
    }

    @Test
    void shouldHandleLoginNameConflictWhenConstraintMessageOnlyContainsLoginName() {
        RoleSetting role = role("PURCHASER", "采购专员", 11L);
        UserAccountAdminService service = createService(
                repositoryWithDataIntegrityViolation("unique_login_name_index"),
                new FixedIdGenerator(100L),
                new StubPasswordEncoder(),
                mapper(),
                null,
                new StubUserRoleBindingService(List.of(role)),
                new StubPermissionService(""),
                authProperties(),
                null,
                authenticatedUserCacheService(),
                null,
                null,
                departmentRepository()
        );

        assertThatThrownBy(() -> service.create(new com.leo.erp.auth.web.dto.UserAccountAdminRequest(
                "tester",
                "Init@123",
                "测试用户",
                "13800000000",
                10L,
                List.of("采购专员"), null,
                "全部数据",
                "",
                "正常",
                ""
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("登录账号已存在");
    }

    @Test
    void shouldPageUsersWithoutStatusFilter() {
        UserAccount entity = new UserAccount();
        entity.setId(7L);
        entity.setLoginName("tester");
        entity.setUserName("测试用户");
        entity.setDepartmentName("总部");
        entity.setStatus(UserStatus.NORMAL);
        RoleSetting role = role("PURCHASER", "采购专员", 11L);
        UserAccountAdminService service = createService(
                repositoryForPage(entity),
                new FixedIdGenerator(100L),
                new StubPasswordEncoder(),
                mapper(),
                null,
                new StubUserRoleBindingService(List.of(role)),
                new StubPermissionService("采购权限"),
                authProperties(),
                null,
                authenticatedUserCacheService(),
                null,
                null,
                null
        );

        var page = service.page(PageQuery.of(0, 20, null, null), "test", null);

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().getFirst().roleNames()).containsExactly("采购专员");
        assertThat(page.getContent().getFirst().permissionSummary()).isEqualTo("采购权限");
    }

    @Test
    void shouldPageUsersWithBlankStatusFilterAsUnfiltered() {
        UserAccount entity = new UserAccount();
        entity.setId(9L);
        entity.setLoginName("blank-status");
        entity.setUserName("空白状态");
        entity.setStatus(UserStatus.NORMAL);
        UserAccountAdminService service = createService(
                repositoryForPage(entity),
                new FixedIdGenerator(100L),
                new StubPasswordEncoder(),
                mapper(),
                null,
                new StubUserRoleBindingService(List.of()),
                new StubPermissionService(""),
                authProperties(),
                null,
                authenticatedUserCacheService(),
                null,
                null,
                null
        );

        var page = service.page(PageQuery.of(0, 20, null, null), "", " ");

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().getFirst().loginName()).isEqualTo("blank-status");
    }

    @Test
    void shouldPageUsersWithStatusFilter() {
        UserAccount entity = new UserAccount();
        entity.setId(8L);
        entity.setLoginName("disabled");
        entity.setUserName("禁用用户");
        entity.setStatus(UserStatus.DISABLED);
        UserAccountAdminService service = createService(
                repositoryForPage(entity),
                new FixedIdGenerator(100L),
                new StubPasswordEncoder(),
                mapper(),
                null,
                new StubUserRoleBindingService(List.of()),
                new StubPermissionService(""),
                authProperties(),
                null,
                authenticatedUserCacheService(),
                null,
                null,
                null
        );

        var page = service.page(PageQuery.of(0, 20, null, null), "", "禁用");

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().getFirst().status()).isEqualTo("禁用");
    }

    @Test
    void shouldPropagateDataIntegrityViolationWhenItIsNotLoginNameConflict() {
        RoleSetting role = role("PURCHASER", "采购专员", 11L);
        UserAccountAdminService service = createService(
                repositoryWithDataIntegrityViolation("sys_user_mobile_key"),
                new FixedIdGenerator(100L),
                new StubPasswordEncoder(),
                mapper(),
                null,
                new StubUserRoleBindingService(List.of(role)),
                new StubPermissionService(""),
                authProperties(),
                null,
                authenticatedUserCacheService(),
                null,
                null,
                departmentRepository()
        );

        assertThatThrownBy(() -> service.create(new com.leo.erp.auth.web.dto.UserAccountAdminRequest(
                "tester",
                "Init@123",
                "测试用户",
                "13800000000",
                10L,
                List.of("采购专员"), null,
                "全部数据",
                "",
                "正常",
                ""
        )))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    void shouldPropagateDataIntegrityViolationWhenMessageIsNull() {
        RoleSetting role = role("PURCHASER", "采购专员", 11L);
        UserAccountAdminService service = createService(
                repositoryWithDataIntegrityViolationException(new org.springframework.dao.DataIntegrityViolationException(null)),
                new FixedIdGenerator(100L),
                new StubPasswordEncoder(),
                mapper(),
                null,
                new StubUserRoleBindingService(List.of(role)),
                new StubPermissionService(""),
                authProperties(),
                null,
                authenticatedUserCacheService(),
                null,
                null,
                departmentRepository()
        );

        assertThatThrownBy(() -> service.create(new com.leo.erp.auth.web.dto.UserAccountAdminRequest(
                "tester",
                "Init@123",
                "测试用户",
                "13800000000",
                10L,
                List.of("采购专员"), null,
                "全部数据",
                "",
                "正常",
                ""
        )))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    void shouldIgnoreNonMatchingRoleConflictRecords() {
        RoleSetting roleA = role("PURCHASER", "采购专员", 11L);
        RoleSetting roleB = role("SALESMAN", "销售专员", 12L);
        com.leo.erp.system.role.domain.entity.RoleConflict unrelated =
                new com.leo.erp.system.role.domain.entity.RoleConflict();
        unrelated.setRoleId(99L);
        unrelated.setConflictRoleId(12L);
        AtomicReference<UserAccount> savedUser = new AtomicReference<>();
        UserAccountAdminService service = createServiceWithConflictRepo(
                repositoryForWrite(savedUser),
                new FixedIdGenerator(100L),
                new StubPasswordEncoder(),
                mapper(),
                null,
                new StubUserRoleBindingService(List.of(roleA, roleB)),
                new StubPermissionService(""),
                authProperties(),
                null,
                authenticatedUserCacheService(),
                null,
                null,
                departmentRepository(),
                roleConflictRepository(List.of(unrelated))
        );

        service.create(new com.leo.erp.auth.web.dto.UserAccountAdminRequest(
                "tester",
                "Init@123",
                "测试用户",
                "13800000000",
                10L,
                List.of("采购专员", "销售专员"), null,
                "全部数据",
                "",
                "正常",
                ""
        ));

        assertThat(savedUser.get()).isNotNull();
    }

    @Test
    void shouldAllowUpdatingAdminWhenAnotherActiveAdminExists() {
        UserAccount existing = adminUser();
        StubUserRoleBindingService roleBindingService = new StubUserRoleBindingService(List.of(adminRole()));
        AtomicReference<UserAccount> savedUser = new AtomicReference<>();
        UserAccountAdminService service = createServiceWithAdminGuard(
                repositoryForUpdate(existing, savedUser),
                mapper(),
                roleBindingService,
                new StubPermissionService(""),
                departmentRepository(),
                roleSettingRepository(adminRole()),
                userRoleRepositoryWithOtherActiveAdmins(2)
        );

        service.update(42L, new com.leo.erp.auth.web.dto.UserAccountAdminRequest(
                "admin",
                null,
                "系统管理员",
                "",
                10L,
                null, List.of(11L),
                "全部数据",
                "",
                "禁用",
                ""
        ));

        assertThat(savedUser.get()).isNotNull();
    }

    @Test
    void shouldAllowDeletingAdminWhenAdminRoleSettingIsMissing() {
        UserAccount existing = adminUser();
        AtomicReference<UserAccount> savedUser = new AtomicReference<>();
        UserAccountAdminService service = createServiceWithAdminGuard(
                repositoryForDelete(existing, savedUser),
                mapper(),
                new StubUserRoleBindingService(List.of(adminRole())),
                new StubPermissionService(""),
                null,
                roleSettingRepository(null),
                userRoleRepositoryWithOtherActiveAdmins(0)
        );

        service.delete(42L);

        assertThat(savedUser.get()).isNotNull();
        assertThat(savedUser.get().isDeletedFlag()).isTrue();
    }

    @Test
    void shouldAllowDeletingAdminWhenAdminGuardRepositoriesAreNotConfigured() {
        UserAccount existing = adminUser();
        AtomicReference<UserAccount> savedUser = new AtomicReference<>();
        UserAccountAdminService service = createService(
                repositoryForDelete(existing, savedUser),
                new FixedIdGenerator(100L),
                new StubPasswordEncoder(),
                mapper(),
                null,
                new StubUserRoleBindingService(List.of(adminRole())),
                new StubPermissionService(""),
                authProperties(),
                null,
                authenticatedUserCacheService(),
                null,
                null,
                null
        );

        service.delete(42L);

        assertThat(savedUser.get()).isNotNull();
        assertThat(savedUser.get().isDeletedFlag()).isTrue();
    }

    private UserAccountRepository repositoryForUpdate(UserAccount existing, AtomicReference<UserAccount> savedUser) {
        return (UserAccountRepository) Proxy.newProxyInstance(
                UserAccountRepository.class.getClassLoader(),
                new Class[]{UserAccountRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByIdAndDeletedFlagFalse" -> Optional.of(existing);
                    case "findByLoginNameAndDeletedFlagFalse", "findByLoginName" -> Optional.empty();
                    case "existsByLoginNameAndDeletedFlagFalse", "existsByLoginName" -> false;
                    case "save" -> {
                        savedUser.set((UserAccount) args[0]);
                        yield args[0];
                    }
                    case "toString" -> "UserAccountRepositoryUpdateStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private UserAccountRepository repositoryForPage(UserAccount entity) {
        return (UserAccountRepository) Proxy.newProxyInstance(
                UserAccountRepository.class.getClassLoader(),
                new Class[]{UserAccountRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findAll" -> {
                        executeUserAccountSpec(args[0]);
                        yield new PageImpl<>(List.of(entity), Pageable.unpaged(), 1);
                    }
                    case "toString" -> "UserAccountRepositoryPageStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void executeUserAccountSpec(Object specificationArg) {
        Specification<UserAccount> specification = (Specification<UserAccount>) specificationArg;
        Root<UserAccount> root = (Root<UserAccount>) mock(Root.class);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder criteriaBuilder = mock(CriteriaBuilder.class);
        specification.toPredicate(root, query, criteriaBuilder);
    }

    private UserAccountRepository repositoryForDelete(UserAccount existing, AtomicReference<UserAccount> savedUser) {
        return (UserAccountRepository) Proxy.newProxyInstance(
                UserAccountRepository.class.getClassLoader(),
                new Class[]{UserAccountRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByIdAndDeletedFlagFalse" -> Optional.of(existing);
                    case "save" -> {
                        savedUser.set((UserAccount) args[0]);
                        yield args[0];
                    }
                    case "toString" -> "UserAccountRepositoryDeleteStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private UserAccountRepository repositoryForNotFound() {
        return (UserAccountRepository) Proxy.newProxyInstance(
                UserAccountRepository.class.getClassLoader(),
                new Class[]{UserAccountRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByIdAndDeletedFlagFalse" -> Optional.empty();
                    case "toString" -> "UserAccountRepositoryNotFoundStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private UserAccountRepository repositoryForWrite(UserAccount existing, AtomicReference<UserAccount> savedUser) {
        return (UserAccountRepository) Proxy.newProxyInstance(
                UserAccountRepository.class.getClassLoader(),
                new Class[]{UserAccountRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByIdAndDeletedFlagFalse" -> Optional.of(existing);
                    case "findByLoginNameAndDeletedFlagFalse", "findByLoginName" -> Optional.empty();
                    case "existsByLoginNameAndDeletedFlagFalse", "existsByLoginName" -> false;
                    case "save" -> {
                        savedUser.set((UserAccount) args[0]);
                        yield args[0];
                    }
                    case "toString" -> "UserAccountRepositoryWriteExistingStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private UserAccountRepository repositoryWithDataIntegrityViolation(String constraintMessage) {
        return repositoryWithDataIntegrityViolationException(
                new org.springframework.dao.DataIntegrityViolationException("Duplicate entry for key " + constraintMessage)
        );
    }

    private UserAccountRepository repositoryWithDataIntegrityViolationException(
            org.springframework.dao.DataIntegrityViolationException exception) {
        return (UserAccountRepository) Proxy.newProxyInstance(
                UserAccountRepository.class.getClassLoader(),
                new Class[]{UserAccountRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByIdAndDeletedFlagFalse" -> Optional.empty();
                    case "findByLoginNameAndDeletedFlagFalse", "findByLoginName" -> Optional.empty();
                    case "existsByLoginNameAndDeletedFlagFalse", "existsByLoginName" -> false;
                    case "save" -> {
                        throw exception;
                    }
                    case "toString" -> "UserAccountRepositoryConflictStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private com.leo.erp.system.role.repository.RoleConflictRepository roleConflictRepository(
            java.util.List<com.leo.erp.system.role.domain.entity.RoleConflict> conflicts) {
        return (com.leo.erp.system.role.repository.RoleConflictRepository) Proxy.newProxyInstance(
                com.leo.erp.system.role.repository.RoleConflictRepository.class.getClassLoader(),
                new Class[]{com.leo.erp.system.role.repository.RoleConflictRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findConflictsByRoleIds" -> conflicts;
                    case "toString" -> "RoleConflictRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private static final class StubTotpService extends TotpService {
        private boolean verifyResult = false;

        private StubTotpService() {
            super(new com.leo.erp.security.totp.TotpProperties("test", null), null, null);
        }

        @Override
        public String generateSecret() {
            return "test-secret";
        }

        @Override
        public String encryptSecret(String plainSecret) {
            return "encrypted:" + plainSecret;
        }

        @Override
        public String decryptSecret(String encryptedSecret) {
            return "decrypted-secret";
        }

        @Override
        public boolean verifyCode(String secret, String code) {
            return verifyResult;
        }

        @Override
        public byte[] generateQrCodeImage(String secret, String loginName) {
            return "fake-qr-bytes".getBytes();
        }

        void setVerifyResult(boolean result) {
            this.verifyResult = result;
        }
    }

    private UserAccountRepository userAccountRepository(UserAccount entity) {
        return (UserAccountRepository) Proxy.newProxyInstance(
                UserAccountRepository.class.getClassLoader(),
                new Class[]{UserAccountRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByIdAndDeletedFlagFalse" -> Optional.of(entity);
                    case "toString" -> "UserAccountRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private UserAccountRepository repositoryForWrite() {
        return repositoryForWrite(new AtomicReference<>());
    }

    private UserAccountRepository repositoryForWrite(AtomicReference<UserAccount> savedUser) {
        return (UserAccountRepository) Proxy.newProxyInstance(
                UserAccountRepository.class.getClassLoader(),
                new Class[]{UserAccountRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByIdAndDeletedFlagFalse", "findByLoginNameAndDeletedFlagFalse", "findByLoginName" -> Optional.empty();
                    case "existsByLoginNameAndDeletedFlagFalse", "existsByLoginName" -> false;
                    case "save" -> {
                        savedUser.set((UserAccount) args[0]);
                        yield args[0];
                    }
                    case "toString" -> "UserAccountRepositoryWriteStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private UserAccountRepository repositoryWithExistingLoginName(UserAccount existingUser) {
        return (UserAccountRepository) Proxy.newProxyInstance(
                UserAccountRepository.class.getClassLoader(),
                new Class[]{UserAccountRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByIdAndDeletedFlagFalse" -> Optional.empty();
                    case "findByLoginNameAndDeletedFlagFalse" ->
                            existingUser.isDeletedFlag() ? Optional.empty() : Optional.of(existingUser);
                    case "findByLoginName" -> Optional.of(existingUser);
                    case "existsByLoginNameAndDeletedFlagFalse" -> false;
                    case "existsByLoginName" -> true;
                    case "toString" -> "UserAccountRepositoryExistingLoginStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private UserAccountRepository repositoryForWriteWithExistingLoginName(UserAccount existingUser, AtomicReference<UserAccount> savedUser) {
        return (UserAccountRepository) Proxy.newProxyInstance(
                UserAccountRepository.class.getClassLoader(),
                new Class[]{UserAccountRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByIdAndDeletedFlagFalse" -> Optional.empty();
                    case "findByLoginNameAndDeletedFlagFalse" ->
                            existingUser.isDeletedFlag() ? Optional.empty() : Optional.of(existingUser);
                    case "findByLoginName" -> Optional.of(existingUser);
                    case "existsByLoginNameAndDeletedFlagFalse" -> false;
                    case "existsByLoginName" -> true;
                    case "save" -> {
                        savedUser.set((UserAccount) args[0]);
                        yield args[0];
                    }
                    case "toString" -> "UserAccountRepositoryWriteExistingLoginStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private DepartmentRepository departmentRepository() {
        Department department = new Department();
        department.setId(10L);
        department.setDepartmentCode("HQ");
        department.setDepartmentName("总部");
        department.setStatus("正常");
        return (DepartmentRepository) Proxy.newProxyInstance(
                DepartmentRepository.class.getClassLoader(),
                new Class[]{DepartmentRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByIdAndDeletedFlagFalse" ->
                            Long.valueOf(10L).equals(args[0]) ? Optional.of(department) : Optional.empty();
                    case "toString" -> "DepartmentRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private UserAccount adminUser() {
        UserAccount existing = new UserAccount();
        existing.setId(42L);
        existing.setLoginName("admin");
        existing.setUserName("系统管理员");
        existing.setDepartmentId(10L);
        existing.setStatus(UserStatus.NORMAL);
        return existing;
    }

    private UserAccount user(String loginName, Long id) {
        UserAccount user = new UserAccount();
        user.setId(id);
        user.setLoginName(loginName);
        user.setUserName("测试用户");
        user.setDepartmentId(10L);
        user.setStatus(UserStatus.NORMAL);
        return user;
    }

    private RoleSetting adminRole() {
        return role("ADMIN", "系统管理员", 11L);
    }

    private RoleSetting role(String roleCode, String roleName, Long id) {
        RoleSetting role = new RoleSetting();
        role.setId(id);
        role.setRoleName(roleName);
        role.setRoleCode(roleCode);
        role.setStatus("正常");
        return role;
    }

    private RoleSettingRepository roleSettingRepository(RoleSetting role) {
        return (RoleSettingRepository) Proxy.newProxyInstance(
                RoleSettingRepository.class.getClassLoader(),
                new Class[]{RoleSettingRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByRoleCodeAndDeletedFlagFalse" -> Optional.ofNullable(role);
                    case "toString" -> "RoleSettingRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private UserRoleRepository userRoleRepositoryWithOtherActiveAdmins(long count) {
        return (UserRoleRepository) Proxy.newProxyInstance(
                UserRoleRepository.class.getClassLoader(),
                new Class[]{UserRoleRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "countUsersByRoleIdAndStatusExcludingUserId" -> count;
                    case "toString" -> "UserRoleRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private UserAccountAdminMapper mapper() {
        return new UserAccountAdminMapper() {
            @Override
            public UserAccountAdminResponse toResponse(UserAccount entity) {
                return new UserAccountAdminResponse(
                        entity.getId(),
                        entity.getLoginName(),
                        entity.getUserName(),
                        entity.getMobile(),
                        entity.getDepartmentId(),
                        entity.getDepartmentName(),
                        List.of(), null,
                        entity.getDataScope(),
                        entity.getPermissionSummary(),
                        entity.getLastLoginDate(),
                        fromStatus(entity.getStatus()),
                        entity.getRemark(),
                        Boolean.TRUE.equals(entity.getTotpEnabled())
                );
            }
        };
    }

    private static class StubUserRoleBindingService extends UserRoleBindingService {

        private final List<RoleSetting> roles;
        private boolean replaceCalled;
        private java.util.Collection<RoleSetting> replacedRoles;

        private StubUserRoleBindingService(List<RoleSetting> roles) {
            super(null, null, null);
            this.roles = roles;
        }

        @Override
        public List<RoleSetting> resolveRolesForUser(Long userId) {
            return roles;
        }

        @Override
        public List<RoleSetting> resolveRoles(java.util.Collection<String> roleIdentifiers) {
            return roles;
        }

        @Override
        public List<RoleSetting> resolveRolesByIds(java.util.Collection<Long> roleIds) {
            return roles;
        }

        @Override
        public void replaceUserRoles(Long userId, java.util.Collection<RoleSetting> roles) {
            this.replaceCalled = true;
            this.replacedRoles = roles;
        }

        @Override
        public void replaceUserRolesWithinCurrentPrincipalBounds(Long userId, java.util.Collection<RoleSetting> roles) {
            replaceUserRoles(userId, roles);
        }

        boolean replaceCalled() {
            return replaceCalled;
        }

        java.util.Collection<RoleSetting> replacedRoles() {
            return replacedRoles;
        }
    }

    private static final class StubPermissionService extends com.leo.erp.security.permission.PermissionService {

        private final String summary;

        private StubPermissionService(String summary) {
            this.summary = summary;
        }

        @Override
        public String getPermissionSummaryForRoles(java.util.Collection<RoleSetting> roles) {
            return summary;
        }

        @Override
        public void evictCache(Long userId) {
        }

        @Override
        public void evictDepartmentUserCache(Long departmentId) {
        }

        @Override
        public void clearDepartmentUserCache() {
        }

        @Override
        public void evictAllCache() {
        }
    }

    private static final class FixedIdGenerator extends com.leo.erp.common.support.SnowflakeIdGenerator {

        private final long id;

        private FixedIdGenerator(long id) {
            this.id = id;
        }

        @Override
        public synchronized long nextId() {
            return id;
        }
    }

    private static final class StubPasswordEncoder implements org.springframework.security.crypto.password.PasswordEncoder {

        @Override
        public String encode(CharSequence rawPassword) {
            return "encoded:" + rawPassword;
        }

        @Override
        public boolean matches(CharSequence rawPassword, String encodedPassword) {
            return encodedPassword.equals(encode(rawPassword));
        }
    }

    private AuthProperties authProperties() {
        AuthProperties properties = new AuthProperties();
        properties.getUser().setDefaultPassword("test-pass");
        return properties;
    }

    private AuthenticatedUserCacheService authenticatedUserCacheService() {
        return new AuthenticatedUserCacheService(null, null, null, null, new RedisTuningProperties()) {
            @Override
            public void evict(Long userId) {
            }
        };
    }

    private SystemSwitchService enabledTotpSwitchService() {
        return new SystemSwitchService(null) {
            @Override
            public boolean shouldForceUserTotpOnFirstLogin() {
                return true;
            }
        };
    }
}
