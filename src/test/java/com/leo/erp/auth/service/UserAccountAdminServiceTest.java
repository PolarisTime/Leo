package com.leo.erp.auth.service;

import com.leo.erp.auth.config.AuthProperties;
import com.leo.erp.auth.domain.entity.UserAccount;
import com.leo.erp.auth.domain.enums.UserStatus;
import com.leo.erp.auth.repository.UserAccountRepository;
import com.leo.erp.auth.mapper.UserAccountAdminMapper;
import com.leo.erp.auth.web.dto.LoginNameAvailabilityResponse;
import com.leo.erp.auth.web.dto.UserAccountCreateResponse;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.auth.web.dto.UserAccountAdminResponse;
import com.leo.erp.security.jwt.AuthenticatedUserCacheService;
import com.leo.erp.system.department.domain.entity.Department;
import com.leo.erp.system.department.repository.DepartmentRepository;
import com.leo.erp.system.norule.service.SystemSwitchService;
import com.leo.erp.system.role.domain.entity.RoleSetting;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
                totpService, userRoleBindingService, permissionService, validationService, cacheService);
    }

    @Test
    void shouldReturnDerivedPermissionSummaryInsteadOfStoredValue() {
        UserAccount entity = new UserAccount();
        entity.setId(1L);
        entity.setLoginName("tester");
        entity.setUserName("测试用户");
        entity.setMobile("13800000000");
        entity.setRoleName("采购专员");
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
                List.of("采购专员"),
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
                List.of("采购专员"),
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
                List.of("采购专员"),
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
                List.of("采购专员"),
                "全部数据",
                "",
                "正常",
                ""
        ));

        assertThat(savedUser.get()).isNotNull();
        assertThat(savedUser.get().getRequireTotpSetup()).isTrue();
    }

    @Test
    void shouldRejectCreateWhenDeletedUserAlreadyUsesLoginName() {
        RoleSetting role = new RoleSetting();
        role.setId(11L);
        role.setRoleName("采购专员");
        role.setRoleCode("PURCHASER");
        role.setStatus("正常");

        UserAccount existing = new UserAccount();
        existing.setId(99L);
        existing.setLoginName("test");
        existing.setDeletedFlag(Boolean.TRUE);

        UserAccountAdminService service = createService(
                repositoryWithExistingLoginName(existing),
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
                "test",
                "Init@123",
                "测试用户",
                "13800000000",
                10L,
                List.of("采购专员"),
                "全部数据",
                "",
                "正常",
                ""
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("登录账号已存在");
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
                    case "findByLoginNameAndDeletedFlagFalse" -> Optional.empty();
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
                        List.of(),
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

    private static final class StubUserRoleBindingService extends UserRoleBindingService {

        private final List<RoleSetting> roles;

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
        public void replaceUserRoles(Long userId, java.util.Collection<RoleSetting> roles) {
        }
    }

    private static final class StubPermissionService extends com.leo.erp.security.permission.PermissionService {

        private final String summary;

        private StubPermissionService(String summary) {
            super(null, null, null, null, null, null);
            this.summary = summary;
        }

        @Override
        public String getPermissionSummaryForRoles(java.util.Collection<RoleSetting> roles) {
            return summary;
        }

        @Override
        public void evictCache(Long userId) {
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
        return new AuthenticatedUserCacheService(null, null, null, null) {
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
