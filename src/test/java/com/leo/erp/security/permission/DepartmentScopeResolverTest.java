package com.leo.erp.security.permission;

import com.leo.erp.auth.domain.entity.UserAccount;
import com.leo.erp.auth.repository.UserAccountRepository;
import com.leo.erp.system.department.domain.entity.Department;
import com.leo.erp.system.department.repository.DepartmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DepartmentScopeResolverTest {

    private UserAccountRepository userAccountRepository;
    private DepartmentRepository departmentRepository;

    @BeforeEach
    void setUp() {
        userAccountRepository = (UserAccountRepository) Proxy.newProxyInstance(
                UserAccountRepository.class.getClassLoader(),
                new Class[]{UserAccountRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByIdAndDeletedFlagFalse" -> Optional.of(userAccount(1L, 10L));
                    case "findByDepartmentIdAndDeletedFlagFalse" -> List.of(userAccount(2L, 10L), userAccount(3L, 10L));
                    case "toString" -> "UserAccountRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        departmentRepository = (DepartmentRepository) Proxy.newProxyInstance(
                DepartmentRepository.class.getClassLoader(),
                new Class[]{DepartmentRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByIdAndDeletedFlagFalse" -> Optional.of(department(10L, "技术部", null, "正常"));
                    case "findByStatusAndDeletedFlagFalseOrderBySortOrderAscIdAsc" -> List.of(
                            department(10L, "技术部", null, "正常"),
                            department(20L, "前端组", 10L, "正常"),
                            department(30L, "后端组", 10L, "正常")
                    );
                    case "toString" -> "DepartmentRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    @Test
    void shouldReturnNull_whenScopeIsAll() {
        DepartmentScopeResolver resolver = new DepartmentScopeResolver(Optional.empty(), Optional.empty());
        assertThat(resolver.getOwnerUserIds(1L, ResourcePermissionCatalog.SCOPE_ALL)).isNull();
    }

    @Test
    void shouldReturnNull_whenUserIdIsNull() {
        DepartmentScopeResolver resolver = new DepartmentScopeResolver(Optional.empty(), Optional.empty());
        assertThat(resolver.getOwnerUserIds(null, "any")).isNull();
    }

    @Test
    void shouldReturnSelf_whenScopeIsSelf() {
        DepartmentScopeResolver resolver = new DepartmentScopeResolver(Optional.empty(), Optional.empty());
        Set<Long> userIds = resolver.getOwnerUserIds(1L, ResourcePermissionCatalog.SCOPE_SELF);
        assertThat(userIds).containsExactly(1L);
    }

    @Test
    void shouldReturnDepartmentUserIds_whenScopeIsDepartment() {
        DepartmentScopeResolver resolver = new DepartmentScopeResolver(
                Optional.of(userAccountRepository), Optional.of(departmentRepository));

        Set<Long> userIds = resolver.getOwnerUserIds(1L, ResourcePermissionCatalog.SCOPE_DEPARTMENT);

        assertThat(userIds).contains(1L, 2L, 3L);
    }

    @Test
    void shouldReturnSelf_whenDepartmentUserNotFound() {
        UserAccountRepository emptyRepo = (UserAccountRepository) Proxy.newProxyInstance(
                UserAccountRepository.class.getClassLoader(),
                new Class[]{UserAccountRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByIdAndDeletedFlagFalse" -> Optional.empty();
                    case "findByDepartmentIdAndDeletedFlagFalse" -> List.of();
                    case "toString" -> "EmptyUserAccountRepoStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        DepartmentScopeResolver resolver = new DepartmentScopeResolver(
                Optional.of(emptyRepo), Optional.of(departmentRepository));

        Set<Long> userIds = resolver.getOwnerUserIds(1L, ResourcePermissionCatalog.SCOPE_DEPARTMENT);

        assertThat(userIds).containsExactly(1L);
    }

    @Test
    void shouldReturnSelf_whenDepartmentIsInactive() {
        DepartmentRepository inactiveDeptRepo = (DepartmentRepository) Proxy.newProxyInstance(
                DepartmentRepository.class.getClassLoader(),
                new Class[]{DepartmentRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByIdAndDeletedFlagFalse" -> Optional.of(department(10L, "技术部", null, "禁用"));
                    case "findByStatusAndDeletedFlagFalseOrderBySortOrderAscIdAsc" -> List.of();
                    case "toString" -> "InactiveDeptRepoStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        DepartmentScopeResolver resolver = new DepartmentScopeResolver(
                Optional.of(userAccountRepository), Optional.of(inactiveDeptRepo));

        Set<Long> userIds = resolver.getOwnerUserIds(1L, ResourcePermissionCatalog.SCOPE_DEPARTMENT);

        assertThat(userIds).containsExactly(1L);
    }

    @Test
    void shouldReturnSelf_whenUserAccountRepositoryNotInjected() {
        DepartmentScopeResolver resolver = new DepartmentScopeResolver(
                Optional.empty(), Optional.of(departmentRepository));

        Set<Long> userIds = resolver.getOwnerUserIds(1L, ResourcePermissionCatalog.SCOPE_DEPARTMENT);

        assertThat(userIds).containsExactly(1L);
    }

    @Test
    void shouldCacheDepartmentUsers() {
        DepartmentScopeResolver resolver = new DepartmentScopeResolver(
                Optional.of(userAccountRepository), Optional.of(departmentRepository));

        Set<Long> first = resolver.getOwnerUserIds(1L, ResourcePermissionCatalog.SCOPE_DEPARTMENT);
        Set<Long> second = resolver.getOwnerUserIds(1L, ResourcePermissionCatalog.SCOPE_DEPARTMENT);

        assertThat(first).isEqualTo(second);
        assertThat(first).contains(1L, 2L, 3L);
    }

    @Test
    void shouldEvictDepartmentUserCache() {
        DepartmentScopeResolver resolver = new DepartmentScopeResolver(
                Optional.of(userAccountRepository), Optional.of(departmentRepository));

        resolver.getOwnerUserIds(1L, ResourcePermissionCatalog.SCOPE_DEPARTMENT);
        resolver.evictDepartmentUserCache(10L);
        Set<Long> result = resolver.getOwnerUserIds(1L, ResourcePermissionCatalog.SCOPE_DEPARTMENT);

        assertThat(result).contains(1L, 2L, 3L);
    }

    @Test
    void shouldIgnoreEvict_whenDepartmentIdIsNull() {
        DepartmentScopeResolver resolver = new DepartmentScopeResolver(
                Optional.of(userAccountRepository), Optional.of(departmentRepository));
        resolver.evictDepartmentUserCache(null);
    }

    @Test
    void shouldClearAllCache() {
        DepartmentScopeResolver resolver = new DepartmentScopeResolver(
                Optional.of(userAccountRepository), Optional.of(departmentRepository));

        resolver.getOwnerUserIds(1L, ResourcePermissionCatalog.SCOPE_DEPARTMENT);
        resolver.clearDepartmentUserCache();
        Set<Long> result = resolver.getOwnerUserIds(1L, ResourcePermissionCatalog.SCOPE_DEPARTMENT);

        assertThat(result).contains(1L, 2L, 3L);
    }

    @Test
    void shouldCollectDescendantDepartments() {
        DepartmentRepository deepRepo = (DepartmentRepository) Proxy.newProxyInstance(
                DepartmentRepository.class.getClassLoader(),
                new Class[]{DepartmentRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByIdAndDeletedFlagFalse" -> Optional.of(department(10L, "总部", null, "正常"));
                    case "findByStatusAndDeletedFlagFalseOrderBySortOrderAscIdAsc" -> List.of(
                            department(10L, "总部", null, "正常"),
                            department(20L, "研发部", 10L, "正常"),
                            department(30L, "前端组", 20L, "正常"),
                            department(40L, "后端组", 20L, "正常"),
                            department(50L, "财务部", 10L, "正常")
                    );
                    case "toString" -> "DeepDeptRepoStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        UserAccountRepository selfRepo = (UserAccountRepository) Proxy.newProxyInstance(
                UserAccountRepository.class.getClassLoader(),
                new Class[]{UserAccountRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByIdAndDeletedFlagFalse" -> Optional.of(userAccount(1L, 20L));
                    case "findByDepartmentIdAndDeletedFlagFalse" -> List.of();
                    case "toString" -> "SelfUserAccountRepoStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        DepartmentScopeResolver resolver = new DepartmentScopeResolver(
                Optional.of(selfRepo), Optional.of(deepRepo));

        Set<Long> userIds = resolver.getOwnerUserIds(1L, ResourcePermissionCatalog.SCOPE_DEPARTMENT);

        assertThat(userIds).contains(1L);
    }

    @Test
    void shouldCollectWithoutDepartmentRepository() {
        DepartmentScopeResolver resolver = new DepartmentScopeResolver(
                Optional.of(userAccountRepository), Optional.empty());

        Set<Long> userIds = resolver.getOwnerUserIds(1L, ResourcePermissionCatalog.SCOPE_DEPARTMENT);

        assertThat(userIds).contains(1L, 2L, 3L);
    }

    private UserAccount userAccount(Long id, Long departmentId) {
        UserAccount user = new UserAccount();
        user.setId(id);
        user.setDepartmentId(departmentId);
        return user;
    }

    private Department department(Long id, String name, Long parentId, String status) {
        Department dept = new Department();
        dept.setId(id);
        dept.setDepartmentName(name);
        dept.setParentId(parentId);
        dept.setStatus(status);
        return dept;
    }
}
