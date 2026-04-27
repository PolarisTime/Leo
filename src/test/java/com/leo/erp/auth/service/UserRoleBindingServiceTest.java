package com.leo.erp.auth.service;

import com.leo.erp.auth.domain.entity.UserRole;
import com.leo.erp.auth.repository.UserRoleRepository;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.system.role.domain.entity.RoleSetting;
import com.leo.erp.system.role.repository.RoleSettingRepository;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserRoleBindingServiceTest {

    @Test
    void shouldResolveRolesByRoleCodeOrRoleNameAndNormalizeStoredNames() {
        RoleSetting admin = role("ADMIN", "系统管理员", 11L);
        RoleSetting finance = role("FINANCE_MANAGER", "财务主管", 12L);
        UserRoleBindingService service = new UserRoleBindingService(
                userRoleRepository(null, null),
                roleSettingRepository(List.of(admin), List.of(finance)),
                new FixedIdGenerator(100L)
        );

        List<RoleSetting> roles = service.resolveRoles(List.of("ADMIN", "财务主管"));

        assertThat(roles).extracting(RoleSetting::getId).containsExactly(11L, 12L);
        assertThat(service.joinRoleNames(roles)).isEqualTo("系统管理员,财务主管");
    }

    @Test
    void shouldReplaceUserRolesWithResolvedBindings() {
        AtomicLong deletedUserId = new AtomicLong(-1L);
        AtomicReference<List<UserRole>> savedBindings = new AtomicReference<>(List.of());
        UserRoleBindingService service = new UserRoleBindingService(
                userRoleRepository(deletedUserId, savedBindings),
                roleSettingRepository(List.of(), List.of()),
                new FixedIdGenerator(101L, 102L)
        );

        service.replaceUserRoles(9L, List.of(
                role("ADMIN", "系统管理员", 11L),
                role("FINANCE_MANAGER", "财务主管", 12L)
        ));

        assertThat(deletedUserId.get()).isEqualTo(9L);
        assertThat(savedBindings.get())
                .extracting(UserRole::getId, UserRole::getUserId, UserRole::getRoleId)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(101L, 9L, 11L),
                        org.assertj.core.groups.Tuple.tuple(102L, 9L, 12L)
                );
    }

    @Test
    void shouldRejectUnknownRoles() {
        UserRoleBindingService service = new UserRoleBindingService(
                userRoleRepository(null, null),
                roleSettingRepository(List.of(), List.of()),
                new FixedIdGenerator(100L)
        );

        assertThatThrownBy(() -> service.resolveRoles(List.of("不存在的角色")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("角色不存在");
    }

    @Test
    void shouldResolveCurrentRoleNamesFromBindings() {
        AtomicReference<List<UserRole>> savedBindings = new AtomicReference<>(List.of());
        RoleSetting finance = role("FINANCE_MANAGER", "财务主管", 12L);
        finance.setStatus("正常");
        UserRoleBindingService service = new UserRoleBindingService(
                userRoleRepositoryWithBindings(List.of(binding(12L, 9L)), savedBindings),
                roleSettingRepositoryWithIds(List.of(finance)),
                new FixedIdGenerator(100L)
        );

        List<RoleSetting> roles = service.resolveRolesForUser(9L);

        assertThat(roles).extracting(RoleSetting::getRoleName).containsExactly("财务主管");
    }

    @Test
    void shouldReturnEmptyWhenNoRoleBindingExists() {
        UserRoleBindingService service = new UserRoleBindingService(
                userRoleRepositoryWithBindings(List.of(), null),
                roleSettingRepositoryWithIds(List.of()),
                new FixedIdGenerator(100L)
        );

        assertThat(service.resolveRolesForUser(9L)).isEmpty();
    }

    private UserRoleRepository userRoleRepository(AtomicLong deletedUserId, AtomicReference<List<UserRole>> savedBindings) {
        return (UserRoleRepository) Proxy.newProxyInstance(
                UserRoleRepository.class.getClassLoader(),
                new Class[]{UserRoleRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "deleteByUserIdAndDeletedFlagFalse" -> {
                        if (deletedUserId != null) {
                            deletedUserId.set((Long) args[0]);
                        }
                        yield null;
                    }
                    case "flush" -> null;
                    case "saveAll" -> {
                        if (savedBindings != null) {
                            savedBindings.set(new ArrayList<>((Collection<UserRole>) args[0]));
                        }
                        yield args[0];
                    }
                    case "toString" -> "UserRoleRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private UserRoleRepository userRoleRepositoryWithBindings(List<UserRole> bindings, AtomicReference<List<UserRole>> savedBindings) {
        return (UserRoleRepository) Proxy.newProxyInstance(
                UserRoleRepository.class.getClassLoader(),
                new Class[]{UserRoleRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByUserIdAndDeletedFlagFalse" -> bindings;
                    case "saveAll" -> {
                        if (savedBindings != null) {
                            savedBindings.set(new ArrayList<>((Collection<UserRole>) args[0]));
                        }
                        yield args[0];
                    }
                    case "deleteByUserIdAndDeletedFlagFalse" -> null;
                    case "flush" -> null;
                    case "toString" -> "UserRoleRepositoryBindingStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private RoleSettingRepository roleSettingRepository(List<RoleSetting> codeMatches, List<RoleSetting> nameMatches) {
        return (RoleSettingRepository) Proxy.newProxyInstance(
                RoleSettingRepository.class.getClassLoader(),
                new Class[]{RoleSettingRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByRoleCodeInAndDeletedFlagFalse" -> codeMatches;
                    case "findByRoleNameInAndDeletedFlagFalse" -> nameMatches;
                    case "toString" -> "RoleSettingRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private RoleSettingRepository roleSettingRepositoryWithIds(List<RoleSetting> idMatches) {
        return (RoleSettingRepository) Proxy.newProxyInstance(
                RoleSettingRepository.class.getClassLoader(),
                new Class[]{RoleSettingRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByIdInAndDeletedFlagFalse" -> idMatches;
                    case "findByRoleCodeInAndDeletedFlagFalse", "findByRoleNameInAndDeletedFlagFalse" -> List.of();
                    case "toString" -> "RoleSettingRepositoryIdStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private RoleSetting role(String roleCode, String roleName, Long id) {
        RoleSetting role = new RoleSetting();
        role.setId(id);
        role.setRoleCode(roleCode);
        role.setRoleName(roleName);
        role.setStatus("正常");
        return role;
    }

    private UserRole binding(Long roleId, Long userId) {
        UserRole binding = new UserRole();
        binding.setId(roleId * 10);
        binding.setRoleId(roleId);
        binding.setUserId(userId);
        return binding;
    }

    private static final class FixedIdGenerator extends SnowflakeIdGenerator {

        private final long[] ids;
        private int index;

        private FixedIdGenerator(long... ids) {
            this.ids = ids;
        }

        @Override
        public synchronized long nextId() {
            return ids[index++];
        }
    }
}
