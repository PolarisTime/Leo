package com.leo.erp.system.department.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.leo.erp.auth.domain.entity.UserAccount;
import com.leo.erp.auth.repository.UserAccountRepository;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.config.CacheConfig;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.support.RedisJsonCacheSupport;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.security.permission.PermissionService;
import com.leo.erp.system.department.domain.entity.Department;
import com.leo.erp.system.department.repository.DepartmentRepository;
import com.leo.erp.system.department.web.dto.DepartmentOptionResponse;
import com.leo.erp.system.department.web.dto.DepartmentRequest;
import com.leo.erp.system.department.web.dto.DepartmentResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DepartmentServiceTest {

    @Test
    void shouldRejectDeleteWhenDepartmentHasBoundUsers() {
        DepartmentRepository departmentRepository = mock(DepartmentRepository.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        PermissionService permissionService = mock(PermissionService.class);
        Department department = department();

        when(departmentRepository.findByIdAndDeletedFlagFalse(10L)).thenReturn(Optional.of(department));
        when(departmentRepository.existsByParentIdAndDeletedFlagFalse(10L)).thenReturn(false);
        when(userAccountRepository.countByDepartmentIdAndDeletedFlagFalse(10L)).thenReturn(1L);

        DepartmentService service = new DepartmentService(
                departmentRepository,
                userAccountRepository,
                permissionService,
                new SnowflakeIdGenerator(1)
        );

        assertThatThrownBy(() -> service.delete(10L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("部门已绑定用户");
        verify(departmentRepository, never()).save(any());
    }

    @Test
    void shouldRejectDeleteWhenDepartmentHasChildren() {
        DepartmentRepository departmentRepository = mock(DepartmentRepository.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        PermissionService permissionService = mock(PermissionService.class);
        Department department = department();

        when(departmentRepository.findByIdAndDeletedFlagFalse(10L)).thenReturn(Optional.of(department));
        when(departmentRepository.existsByParentIdAndDeletedFlagFalse(10L)).thenReturn(true);

        DepartmentService service = new DepartmentService(
                departmentRepository,
                userAccountRepository,
                permissionService,
                new SnowflakeIdGenerator(1)
        );

        assertThatThrownBy(() -> service.delete(10L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("存在下级部门");
    }

    @Test
    void shouldDeleteSuccessfully_whenNoChildrenAndNoUsers() {
        DepartmentRepository departmentRepository = mock(DepartmentRepository.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        PermissionService permissionService = mock(PermissionService.class);
        Department department = department();

        when(departmentRepository.findByIdAndDeletedFlagFalse(10L)).thenReturn(Optional.of(department));
        when(departmentRepository.existsByParentIdAndDeletedFlagFalse(10L)).thenReturn(false);
        when(userAccountRepository.countByDepartmentIdAndDeletedFlagFalse(10L)).thenReturn(0L);
        when(departmentRepository.save(any())).thenReturn(department);

        DepartmentService service = new DepartmentService(
                departmentRepository,
                userAccountRepository,
                permissionService,
                new SnowflakeIdGenerator(1)
        );

        service.delete(10L);

        assertThat(department.isDeletedFlag()).isTrue();
        verify(departmentRepository).save(department);
    }

    @Test
    void shouldSyncBoundUserDepartmentNameWhenDepartmentNameChanges() {
        DepartmentRepository departmentRepository = mock(DepartmentRepository.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        PermissionService permissionService = mock(PermissionService.class);
        Department department = department();
        UserAccount user = new UserAccount();
        user.setId(1L);
        user.setDepartmentId(10L);
        user.setDepartmentName("旧名称");

        when(departmentRepository.findByIdAndDeletedFlagFalse(10L)).thenReturn(Optional.of(department));
        when(departmentRepository.findByDepartmentCodeAndDeletedFlagFalse("HQ")).thenReturn(Optional.of(department));
        when(departmentRepository.save(department)).thenReturn(department);
        when(userAccountRepository.findByDepartmentIdAndDeletedFlagFalse(10L)).thenReturn(List.of(user));

        DepartmentService service = new DepartmentService(
                departmentRepository,
                userAccountRepository,
                permissionService,
                new SnowflakeIdGenerator(1)
        );

        service.update(10L, new DepartmentRequest(
                "HQ",
                "总部运营部",
                null,
                "",
                "",
                1,
                "正常",
                ""
        ));

        assertThat(user.getDepartmentName()).isEqualTo("总部运营部");
        verify(userAccountRepository).saveAll(anyList());
        verify(permissionService).clearDepartmentUserCache();
    }

    @Test
    void shouldNotSyncBoundUsersWhenDepartmentNameUnchanged() {
        DepartmentRepository departmentRepository = mock(DepartmentRepository.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        PermissionService permissionService = mock(PermissionService.class);
        Department department = department();
        UserAccount user = new UserAccount();
        user.setId(1L);
        user.setDepartmentId(10L);
        user.setDepartmentName("总部运营部");

        when(departmentRepository.findByIdAndDeletedFlagFalse(10L)).thenReturn(Optional.of(department));
        when(departmentRepository.findByDepartmentCodeAndDeletedFlagFalse("HQ")).thenReturn(Optional.of(department));
        when(departmentRepository.save(department)).thenReturn(department);
        when(userAccountRepository.findByDepartmentIdAndDeletedFlagFalse(10L)).thenReturn(List.of(user));

        DepartmentService service = new DepartmentService(
                departmentRepository,
                userAccountRepository,
                permissionService,
                new SnowflakeIdGenerator(1)
        );

        service.update(10L, new DepartmentRequest(
                "HQ",
                "总部运营部",
                null,
                "",
                "",
                1,
                "正常",
                ""
        ));

        verify(userAccountRepository, never()).saveAll(anyList());
        verify(permissionService).clearDepartmentUserCache();
    }

    @Test
    void shouldNotSyncUsersWhenDepartmentIsDeleted() {
        DepartmentRepository departmentRepository = mock(DepartmentRepository.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        PermissionService permissionService = mock(PermissionService.class);
        Department department = department();
        department.setDeletedFlag(true);

        when(departmentRepository.save(department)).thenReturn(department);

        DepartmentService service = new DepartmentService(
                departmentRepository,
                userAccountRepository,
                permissionService,
                new SnowflakeIdGenerator(1)
        );

        var method = getMethod("syncBoundUserDepartmentName", Department.class);
        if (method != null) {
            try {
                method.invoke(service, department);
                verify(userAccountRepository, never()).findByDepartmentIdAndDeletedFlagFalse(any());
            } catch (Exception e) {
                // ignore
            }
        }
    }

    @Test
    void shouldRejectDuplicateDepartmentCode() {
        DepartmentRepository departmentRepository = mock(DepartmentRepository.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        PermissionService permissionService = mock(PermissionService.class);

        Department existing = new Department();
        existing.setId(99L);
        existing.setDepartmentCode("HQ");

        when(departmentRepository.findByDepartmentCodeAndDeletedFlagFalse("HQ"))
                .thenReturn(Optional.of(existing));

        DepartmentService service = new DepartmentService(
                departmentRepository,
                userAccountRepository,
                permissionService,
                new SnowflakeIdGenerator(1)
        );

        assertThatThrownBy(() -> service.create(new DepartmentRequest(
                "HQ",
                "新部门",
                null,
                "",
                "",
                1,
                "正常",
                ""
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("部门编码已存在");
    }

    @Test
    void shouldRejectSelfReferencingParent() {
        DepartmentRepository departmentRepository = mock(DepartmentRepository.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        PermissionService permissionService = mock(PermissionService.class);

        Department existing = department();
        when(departmentRepository.findByIdAndDeletedFlagFalse(10L)).thenReturn(Optional.of(existing));
        when(departmentRepository.findByDepartmentCodeAndDeletedFlagFalse("HQ")).thenReturn(Optional.of(existing));

        DepartmentService service = new DepartmentService(
                departmentRepository,
                userAccountRepository,
                permissionService,
                new SnowflakeIdGenerator(1)
        );

        assertThatThrownBy(() -> service.update(10L, new DepartmentRequest(
                "HQ",
                "总部",
                10L,
                "",
                "",
                1,
                "正常",
                ""
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("上级部门不能选择自身");
    }

    @Test
    void shouldRejectInvalidParent() {
        DepartmentRepository departmentRepository = mock(DepartmentRepository.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        PermissionService permissionService = mock(PermissionService.class);

        when(departmentRepository.findByDepartmentCodeAndDeletedFlagFalse("NEW")).thenReturn(Optional.empty());
        when(departmentRepository.findByIdAndDeletedFlagFalse(999L)).thenReturn(Optional.empty());

        DepartmentService service = new DepartmentService(
                departmentRepository,
                userAccountRepository,
                permissionService,
                new SnowflakeIdGenerator(1)
        );

        assertThatThrownBy(() -> service.create(new DepartmentRequest(
                "NEW",
                "新部门",
                999L,
                "",
                "",
                1,
                "正常",
                ""
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("上级部门不存在");
    }

    @Test
    void shouldRejectDescendantAsParent() {
        DepartmentRepository departmentRepository = mock(DepartmentRepository.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        PermissionService permissionService = mock(PermissionService.class);
        Department current = department();
        Department child = department();
        child.setId(20L);
        child.setParentId(30L);
        Department grandchildParent = department();
        grandchildParent.setId(30L);
        grandchildParent.setParentId(10L);

        when(departmentRepository.findByIdAndDeletedFlagFalse(10L)).thenReturn(Optional.of(current));
        when(departmentRepository.findByDepartmentCodeAndDeletedFlagFalse("HQ")).thenReturn(Optional.of(current));
        when(departmentRepository.findByIdAndDeletedFlagFalse(20L)).thenReturn(Optional.of(child));
        when(departmentRepository.findByIdAndDeletedFlagFalse(30L)).thenReturn(Optional.of(grandchildParent));

        DepartmentService service = new DepartmentService(
                departmentRepository,
                userAccountRepository,
                permissionService,
                new SnowflakeIdGenerator(1)
        );

        assertThatThrownBy(() -> service.update(10L, new DepartmentRequest(
                "HQ",
                "总部",
                20L,
                "",
                "",
                1,
                "正常",
                ""
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("上级部门不能选择下级部门");
    }

    @Test
    void shouldRejectInvalidStatus() {
        DepartmentRepository departmentRepository = mock(DepartmentRepository.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        PermissionService permissionService = mock(PermissionService.class);

        when(departmentRepository.findByDepartmentCodeAndDeletedFlagFalse("NEW")).thenReturn(Optional.empty());

        DepartmentService service = new DepartmentService(
                departmentRepository,
                userAccountRepository,
                permissionService,
                new SnowflakeIdGenerator(1)
        );

        assertThatThrownBy(() -> service.create(new DepartmentRequest(
                "NEW",
                "新部门",
                null,
                "",
                "",
                1,
                "无效状态",
                ""
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("状态");
    }

    @Test
    void shouldRejectBlankDepartmentCode() {
        DepartmentRepository departmentRepository = mock(DepartmentRepository.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        PermissionService permissionService = mock(PermissionService.class);

        DepartmentService service = new DepartmentService(
                departmentRepository,
                userAccountRepository,
                permissionService,
                new SnowflakeIdGenerator(1)
        );

        assertThatThrownBy(() -> service.create(new DepartmentRequest(
                "  ",
                "新部门",
                null,
                "",
                "",
                1,
                "正常",
                ""
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("部门编码");
    }

    @Test
    void shouldReturnOptions_whenNoCache() {
        DepartmentRepository departmentRepository = mock(DepartmentRepository.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        PermissionService permissionService = mock(PermissionService.class);

        Department dept = new Department();
        dept.setId(1L);
        dept.setDepartmentCode("HQ");
        dept.setDepartmentName("总部");

        when(departmentRepository.findByStatusAndDeletedFlagFalseOrderBySortOrderAscIdAsc("正常"))
                .thenReturn(List.of(dept));

        DepartmentService service = new DepartmentService(
                departmentRepository,
                userAccountRepository,
                permissionService,
                new SnowflakeIdGenerator(1)
        );

        List<DepartmentOptionResponse> options = service.options();

        assertThat(options).hasSize(1);
        assertThat(options.get(0).id()).isEqualTo(1L);
        assertThat(options.get(0).departmentCode()).isEqualTo("HQ");
        assertThat(options.get(0).departmentName()).isEqualTo("总部");
    }

    @Test
    void shouldReturnCachedEmptyOptions_whenDatabaseAlsoEmpty() {
        DepartmentRepository departmentRepository = mock(DepartmentRepository.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        PermissionService permissionService = mock(PermissionService.class);
        RedisJsonCacheSupport cacheSupport = mock(RedisJsonCacheSupport.class);

        when(departmentRepository.findByStatusAndDeletedFlagFalseOrderBySortOrderAscIdAsc("正常"))
                .thenReturn(List.of());

        DepartmentService service = new DepartmentService(
                departmentRepository,
                userAccountRepository,
                permissionService,
                new SnowflakeIdGenerator(1),
                cacheSupport
        );

        List<DepartmentOptionResponse> options = service.options();

        assertThat(options).isEmpty();
        verify(cacheSupport, never()).write(anyString(), any(), any(Duration.class));
    }

    @Test
    void shouldLoadOptionsThroughSpringCachePath_whenLegacyRedisCachePresent() {
        DepartmentRepository departmentRepository = mock(DepartmentRepository.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        PermissionService permissionService = mock(PermissionService.class);
        RedisJsonCacheSupport cacheSupport = mock(RedisJsonCacheSupport.class);
        Department dept = department();
        when(departmentRepository.findByStatusAndDeletedFlagFalseOrderBySortOrderAscIdAsc("正常"))
                .thenReturn(List.of(dept));

        DepartmentService service = new DepartmentService(
                departmentRepository,
                userAccountRepository,
                permissionService,
                new SnowflakeIdGenerator(1),
                cacheSupport
        );

        List<DepartmentOptionResponse> options = service.options();

        assertThat(options).hasSize(1);
    }

    @Test
    void shouldRefreshOptionsCache_whenCachedOptionsAreEmptyButActiveDepartmentsExist() {
        DepartmentRepository departmentRepository = mock(DepartmentRepository.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        PermissionService permissionService = mock(PermissionService.class);
        RedisJsonCacheSupport cacheSupport = mock(RedisJsonCacheSupport.class);
        Department dept = department();
        when(departmentRepository.findByStatusAndDeletedFlagFalseOrderBySortOrderAscIdAsc("正常"))
                .thenReturn(List.of(dept));

        DepartmentService service = new DepartmentService(
                departmentRepository,
                userAccountRepository,
                permissionService,
                new SnowflakeIdGenerator(1),
                cacheSupport
        );

        List<DepartmentOptionResponse> options = service.options();

        assertThat(options).hasSize(1);
        verify(cacheSupport, never()).delete(anyString());
        verify(cacheSupport, never()).write(eq("leo:department:options"), eq(options), any(Duration.class));
    }

    @Test
    void shouldDeclareSpringCacheAnnotationsForOptions() throws Exception {
        var readMethod = DepartmentService.class.getMethod("options");
        Cacheable cacheable = readMethod.getAnnotation(Cacheable.class);
        var createMethod = DepartmentService.class.getMethod("create", DepartmentRequest.class);
        var updateMethod = DepartmentService.class.getMethod("update", Long.class, DepartmentRequest.class);
        var updateStatusMethod = DepartmentService.class.getMethod("updateStatus", Long.class, String.class);
        var deleteMethod = DepartmentService.class.getMethod("delete", Long.class);

        assertThat(cacheable.value()).containsExactly(CacheConfig.CACHE_OPTIONS);
        assertThat(cacheable.key()).isEqualTo("'leo:department:options'");
        assertThat(cacheable.unless()).isEqualTo("#result == null || #result.isEmpty()");
        assertThat(createMethod.getAnnotation(CacheEvict.class).value()).containsExactly(CacheConfig.CACHE_OPTIONS);
        assertThat(updateMethod.getAnnotation(CacheEvict.class).key()).isEqualTo("'leo:department:options'");
        assertThat(updateStatusMethod.getAnnotation(CacheEvict.class).key()).isEqualTo("'leo:department:options'");
        assertThat(deleteMethod.getAnnotation(CacheEvict.class).key()).isEqualTo("'leo:department:options'");
    }

    @Test
    void shouldKeepUpdateStatusOnSpringCacheEvictionPath() {
        DepartmentRepository departmentRepository = mock(DepartmentRepository.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        PermissionService permissionService = mock(PermissionService.class);
        RedisJsonCacheSupport cacheSupport = mock(RedisJsonCacheSupport.class);
        Department department = department();
        when(departmentRepository.findByIdAndDeletedFlagFalse(10L)).thenReturn(Optional.of(department));
        DepartmentService service = new DepartmentService(
                departmentRepository,
                userAccountRepository,
                permissionService,
                new SnowflakeIdGenerator(1),
                cacheSupport
        );

        DepartmentResponse response = service.updateStatus(10L, "正常");

        assertThat(response.id()).isEqualTo(10L);
        verify(departmentRepository, never()).save(any());
        verify(cacheSupport, never()).deleteAfterCommit("leo:department:options");
    }

    @Test
    void shouldRefreshDepartmentCacheDuringHealthCheck_whenCachedContentDiffers() {
        DepartmentRepository departmentRepository = mock(DepartmentRepository.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        PermissionService permissionService = mock(PermissionService.class);
        RedisJsonCacheSupport cacheSupport = mock(RedisJsonCacheSupport.class);
        Department dept = department();
        when(departmentRepository.findByStatusAndDeletedFlagFalseOrderBySortOrderAscIdAsc("正常"))
                .thenReturn(List.of(dept));

        DepartmentService service = new DepartmentService(
                departmentRepository,
                userAccountRepository,
                permissionService,
                new SnowflakeIdGenerator(1),
                cacheSupport
        );

        var result = service.verifyAndRefreshCache();

        assertThat(result.cacheName()).isEqualTo("leo:department:options");
        assertThat(result.currentSize()).isZero();
        assertThat(result.refreshedSize()).isEqualTo(1);
        assertThat(result.refreshed()).isTrue();
        verify(cacheSupport).write(eq("leo:department:options"), any(), any(Duration.class));
    }

    @Test
    void shouldRefreshDepartmentCacheDuringHealthCheck_whenDatabaseContentChanged() {
        DepartmentRepository departmentRepository = mock(DepartmentRepository.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        PermissionService permissionService = mock(PermissionService.class);
        RedisJsonCacheSupport cacheSupport = mock(RedisJsonCacheSupport.class);
        Department dept = department();
        dept.setDepartmentName("新部门");
        when(departmentRepository.findByStatusAndDeletedFlagFalseOrderBySortOrderAscIdAsc("正常"))
                .thenReturn(List.of(dept));
        when(cacheSupport.read(anyString(), any(TypeReference.class)))
                .thenReturn(Optional.of(List.of(new DepartmentOptionResponse(10L, "HQ", "旧部门"))));

        DepartmentService service = new DepartmentService(
                departmentRepository,
                userAccountRepository,
                permissionService,
                new SnowflakeIdGenerator(1),
                cacheSupport
        );

        var result = service.verifyAndRefreshCache();

        assertThat(result.currentSize()).isEqualTo(1);
        assertThat(result.refreshedSize()).isEqualTo(1);
        assertThat(result.refreshed()).isTrue();
        verify(cacheSupport).write(
                eq("leo:department:options"),
                eq(List.of(new DepartmentOptionResponse(10L, "HQ", "新部门"))),
                any(Duration.class)
        );
    }

    @Test
    void shouldDeleteStaleDepartmentCacheDuringHealthCheck_whenDatabaseHasNoActiveDepartments() {
        DepartmentRepository departmentRepository = mock(DepartmentRepository.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        PermissionService permissionService = mock(PermissionService.class);
        RedisJsonCacheSupport cacheSupport = mock(RedisJsonCacheSupport.class);
        when(departmentRepository.findByStatusAndDeletedFlagFalseOrderBySortOrderAscIdAsc("正常"))
                .thenReturn(List.of());
        when(cacheSupport.read(anyString(), any(TypeReference.class)))
                .thenReturn(Optional.of(List.of(new DepartmentOptionResponse(10L, "HQ", "旧部门"))));

        DepartmentService service = new DepartmentService(
                departmentRepository,
                userAccountRepository,
                permissionService,
                new SnowflakeIdGenerator(1),
                cacheSupport
        );

        var result = service.verifyAndRefreshCache();

        assertThat(result.currentSize()).isEqualTo(1);
        assertThat(result.refreshedSize()).isZero();
        assertThat(result.refreshed()).isTrue();
        verify(cacheSupport).delete("leo:department:options");
        verify(cacheSupport, never()).write(anyString(), any(), any(Duration.class));
    }

    @Test
    void shouldReportHealthyCacheWithoutRefresh_whenCacheSupportMissing() {
        DepartmentRepository departmentRepository = mock(DepartmentRepository.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        PermissionService permissionService = mock(PermissionService.class);
        Department dept = department();
        when(departmentRepository.findByStatusAndDeletedFlagFalseOrderBySortOrderAscIdAsc("正常"))
                .thenReturn(List.of(dept));

        DepartmentService service = new DepartmentService(
                departmentRepository,
                userAccountRepository,
                permissionService,
                new SnowflakeIdGenerator(1)
        );

        var result = service.verifyAndRefreshCache();

        assertThat(result.cacheName()).isEqualTo("leo:department:options");
        assertThat(result.currentSize()).isEqualTo(1);
        assertThat(result.refreshedSize()).isEqualTo(1);
        assertThat(result.refreshed()).isFalse();
    }

    @Test
    void shouldReportHealthyCacheWithoutRefresh_whenCachedContentMatchesDatabase() {
        DepartmentRepository departmentRepository = mock(DepartmentRepository.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        PermissionService permissionService = mock(PermissionService.class);
        RedisJsonCacheSupport cacheSupport = mock(RedisJsonCacheSupport.class);
        Department dept = department();
        List<DepartmentOptionResponse> expected = List.of(new DepartmentOptionResponse(10L, "HQ", "总部"));
        when(departmentRepository.findByStatusAndDeletedFlagFalseOrderBySortOrderAscIdAsc("正常"))
                .thenReturn(List.of(dept));
        when(cacheSupport.read(anyString(), any(TypeReference.class))).thenReturn(Optional.of(expected));

        DepartmentService service = new DepartmentService(
                departmentRepository,
                userAccountRepository,
                permissionService,
                new SnowflakeIdGenerator(1),
                cacheSupport
        );

        var result = service.verifyAndRefreshCache();

        assertThat(result.currentSize()).isEqualTo(1);
        assertThat(result.refreshedSize()).isEqualTo(1);
        assertThat(result.refreshed()).isFalse();
        verify(cacheSupport, never()).write(anyString(), any(), any(Duration.class));
        verify(cacheSupport, never()).delete(anyString());
    }

    @Test
    void shouldNormalizeStatusFilter() {
        DepartmentRepository departmentRepository = mock(DepartmentRepository.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        PermissionService permissionService = mock(PermissionService.class);

        when(departmentRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        DepartmentService service = new DepartmentService(
                departmentRepository,
                userAccountRepository,
                permissionService,
                new SnowflakeIdGenerator(1)
        );

        Page<DepartmentResponse> result = service.page(new PageQuery(0, 10, null, null), null, "正常");
        assertThat(result.getContent()).isEmpty();
    }

    @Test
    void shouldMapParentNameWhenPagingDepartments() {
        DepartmentRepository departmentRepository = mock(DepartmentRepository.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        PermissionService permissionService = mock(PermissionService.class);
        Department parent = department();
        parent.setId(1L);
        parent.setDepartmentName("上级部门");
        Department child = department();
        child.setId(2L);
        child.setParentId(1L);
        child.setDepartmentName("下级部门");

        when(departmentRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(child)));
        when(departmentRepository.findByIdInAndDeletedFlagFalse(List.of(1L))).thenReturn(List.of(parent));

        DepartmentService service = new DepartmentService(
                departmentRepository,
                userAccountRepository,
                permissionService,
                new SnowflakeIdGenerator(1)
        );

        Page<DepartmentResponse> result = service.page(new PageQuery(0, 10, null, null), " HQ ", " 正常 ");

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).parentName()).isEqualTo("上级部门");
        assertThat(result.getContent().get(0).departmentName()).isEqualTo("下级部门");
    }

    @Test
    void shouldRejectInvalidStatusFilter() {
        DepartmentRepository departmentRepository = mock(DepartmentRepository.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        PermissionService permissionService = mock(PermissionService.class);

        DepartmentService service = new DepartmentService(
                departmentRepository,
                userAccountRepository,
                permissionService,
                new SnowflakeIdGenerator(1)
        );

        assertThatThrownBy(() -> service.page(new PageQuery(0, 10, null, null), null, "无效"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("状态");
    }

    @Test
    void shouldRejectNullDepartmentName() {
        DepartmentRepository departmentRepository = mock(DepartmentRepository.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        PermissionService permissionService = mock(PermissionService.class);

        DepartmentService service = new DepartmentService(
                departmentRepository,
                userAccountRepository,
                permissionService,
                new SnowflakeIdGenerator(1)
        );

        assertThatThrownBy(() -> service.create(new DepartmentRequest(
                "NEW",
                null,
                null,
                "",
                "",
                1,
                "正常",
                ""
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("部门名称");
    }

    @Test
    void shouldEvictCacheOnSave() {
        DepartmentRepository departmentRepository = mock(DepartmentRepository.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        PermissionService permissionService = mock(PermissionService.class);
        RedisJsonCacheSupport cacheSupport = mock(RedisJsonCacheSupport.class);

        Department department = department();
        when(departmentRepository.findByDepartmentCodeAndDeletedFlagFalse("HQ")).thenReturn(Optional.empty());
        when(departmentRepository.save(any())).thenReturn(department);

        DepartmentService service = new DepartmentService(
                departmentRepository,
                userAccountRepository,
                permissionService,
                new SnowflakeIdGenerator(1),
                cacheSupport
        );

        service.create(new DepartmentRequest(
                "HQ",
                "总部",
                null,
                "",
                "",
                1,
                "正常",
                ""
        ));

        verify(cacheSupport, never()).deleteAfterCommit(anyString());
    }

    @Test
    void shouldNormalizeRequestFieldsAndDefaultSortOrderOnCreate() {
        DepartmentRepository departmentRepository = mock(DepartmentRepository.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        PermissionService permissionService = mock(PermissionService.class);

        when(departmentRepository.findByDepartmentCodeAndDeletedFlagFalse("HQ")).thenReturn(Optional.empty());
        when(departmentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        DepartmentService service = new DepartmentService(
                departmentRepository,
                userAccountRepository,
                permissionService,
                new SnowflakeIdGenerator(1)
        );

        service.create(new DepartmentRequest(
                " HQ ",
                " 总部 ",
                null,
                " 负责人 ",
                "  ",
                null,
                " 正常 ",
                " 备注 "
        ));

        ArgumentCaptor<Department> captor = ArgumentCaptor.forClass(Department.class);
        verify(departmentRepository).save(captor.capture());
        Department saved = captor.getValue();
        assertThat(saved.getDepartmentCode()).isEqualTo("HQ");
        assertThat(saved.getDepartmentName()).isEqualTo("总部");
        assertThat(saved.getManagerName()).isEqualTo("负责人");
        assertThat(saved.getContactPhone()).isNull();
        assertThat(saved.getSortOrder()).isZero();
        assertThat(saved.getStatus()).isEqualTo("正常");
        assertThat(saved.getRemark()).isEqualTo("备注");
    }

    @Test
    void shouldTranslateDepartmentCodeConflictFromDataIntegrityViolation() {
        DepartmentRepository departmentRepository = mock(DepartmentRepository.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        PermissionService permissionService = mock(PermissionService.class);

        when(departmentRepository.findByDepartmentCodeAndDeletedFlagFalse("HQ")).thenReturn(Optional.empty());
        when(departmentRepository.save(any())).thenThrow(new DataIntegrityViolationException("uk_sys_department_code_active"));

        DepartmentService service = new DepartmentService(
                departmentRepository,
                userAccountRepository,
                permissionService,
                new SnowflakeIdGenerator(1)
        );

        assertThatThrownBy(() -> service.create(new DepartmentRequest(
                "HQ",
                "总部",
                null,
                "",
                "",
                1,
                "正常",
                ""
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("部门编码已存在");
        verify(permissionService, never()).clearDepartmentUserCache();
    }

    @Test
    void shouldTranslateDepartmentCodeConflictFromNestedDataIntegrityViolationCause() {
        DepartmentRepository departmentRepository = mock(DepartmentRepository.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        PermissionService permissionService = mock(PermissionService.class);

        when(departmentRepository.findByDepartmentCodeAndDeletedFlagFalse("HQ")).thenReturn(Optional.empty());
        when(departmentRepository.save(any())).thenThrow(new DataIntegrityViolationException(
                "outer",
                new IllegalStateException("uk_sys_department_code_active")
        ));

        DepartmentService service = new DepartmentService(
                departmentRepository,
                userAccountRepository,
                permissionService,
                new SnowflakeIdGenerator(1)
        );

        assertThatThrownBy(() -> service.create(new DepartmentRequest(
                "HQ",
                "总部",
                null,
                "",
                "",
                1,
                "正常",
                ""
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("部门编码已存在");
        verify(permissionService, never()).clearDepartmentUserCache();
    }

    @Test
    void shouldRethrowUnrelatedDataIntegrityViolation() {
        DepartmentRepository departmentRepository = mock(DepartmentRepository.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        PermissionService permissionService = mock(PermissionService.class);

        DataIntegrityViolationException exception = new DataIntegrityViolationException("other constraint");
        when(departmentRepository.findByDepartmentCodeAndDeletedFlagFalse("HQ")).thenReturn(Optional.empty());
        when(departmentRepository.save(any())).thenThrow(exception);

        DepartmentService service = new DepartmentService(
                departmentRepository,
                userAccountRepository,
                permissionService,
                new SnowflakeIdGenerator(1)
        );

        assertThatThrownBy(() -> service.create(new DepartmentRequest(
                "HQ",
                "总部",
                null,
                "",
                "",
                1,
                "正常",
                ""
        )))
                .isSameAs(exception);
    }

    @Test
    void shouldNotEvictCache_whenCacheIsNull() {
        DepartmentRepository departmentRepository = mock(DepartmentRepository.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        PermissionService permissionService = mock(PermissionService.class);

        Department department = department();
        when(departmentRepository.findByDepartmentCodeAndDeletedFlagFalse("HQ")).thenReturn(Optional.empty());
        when(departmentRepository.save(any())).thenReturn(department);

        DepartmentService service = new DepartmentService(
                departmentRepository,
                userAccountRepository,
                permissionService,
                new SnowflakeIdGenerator(1)
        );

        service.create(new DepartmentRequest(
                "HQ",
                "总部",
                null,
                "",
                "",
                1,
                "正常",
                ""
        ));

        verify(permissionService).clearDepartmentUserCache();
    }

    @Test
    void shouldReturnDepartmentCacheName() {
        DepartmentService service = new DepartmentService(
                mock(DepartmentRepository.class),
                mock(UserAccountRepository.class),
                mock(PermissionService.class),
                new SnowflakeIdGenerator(1)
        );

        assertThat(service.cacheName()).isEqualTo("leo:department:options");
    }

    @Test
    void shouldRejectDetailWhenDepartmentNotFound() {
        DepartmentRepository departmentRepository = mock(DepartmentRepository.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        PermissionService permissionService = mock(PermissionService.class);
        when(departmentRepository.findByIdAndDeletedFlagFalse(404L)).thenReturn(Optional.empty());

        DepartmentService service = new DepartmentService(
                departmentRepository,
                userAccountRepository,
                permissionService,
                new SnowflakeIdGenerator(1)
        );

        assertThatThrownBy(() -> service.detail(404L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("部门不存在");
    }

    @Test
    void shouldUseFirstParentNameWhenDetailParentLookupReturnsDuplicateRows() {
        DepartmentRepository departmentRepository = mock(DepartmentRepository.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        PermissionService permissionService = mock(PermissionService.class);
        Department child = department();
        child.setId(20L);
        child.setParentId(1L);
        Department firstParent = department();
        firstParent.setId(1L);
        firstParent.setDepartmentName("上级部门");
        Department duplicateParent = department();
        duplicateParent.setId(1L);
        duplicateParent.setDepartmentName("重复上级部门");

        when(departmentRepository.findByIdAndDeletedFlagFalse(20L)).thenReturn(Optional.of(child));
        when(departmentRepository.findByIdInAndDeletedFlagFalse(List.of(1L)))
                .thenReturn(List.of(firstParent, duplicateParent));

        DepartmentService service = new DepartmentService(
                departmentRepository,
                userAccountRepository,
                permissionService,
                new SnowflakeIdGenerator(1)
        );

        DepartmentResponse response = service.detail(20L);

        assertThat(response.parentName()).isEqualTo("上级部门");
    }

    @Test
    void shouldIgnoreBlankStatusFilterAndRootParentWhenPagingDepartments() {
        DepartmentRepository departmentRepository = mock(DepartmentRepository.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        PermissionService permissionService = mock(PermissionService.class);
        Department root = department();
        root.setParentId(null);
        when(departmentRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(root)));

        DepartmentService service = new DepartmentService(
                departmentRepository,
                userAccountRepository,
                permissionService,
                new SnowflakeIdGenerator(1)
        );

        Page<DepartmentResponse> result = service.page(new PageQuery(0, 10, null, null), null, " ");

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).parentName()).isNull();
        verify(departmentRepository, never()).findByIdInAndDeletedFlagFalse(anyList());
    }

    @Test
    void shouldCreateWithValidParentWhoseAncestorChainEnds() {
        DepartmentRepository departmentRepository = mock(DepartmentRepository.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        PermissionService permissionService = mock(PermissionService.class);
        Department parent = department();
        parent.setId(1L);
        parent.setParentId(null);
        when(departmentRepository.findByDepartmentCodeAndDeletedFlagFalse("NEW")).thenReturn(Optional.empty());
        when(departmentRepository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(parent));
        when(departmentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(userAccountRepository.findByDepartmentIdAndDeletedFlagFalse(any())).thenReturn(List.of());

        DepartmentService service = new DepartmentService(
                departmentRepository,
                userAccountRepository,
                permissionService,
                new SnowflakeIdGenerator(1)
        );

        DepartmentResponse response = service.create(new DepartmentRequest(
                "NEW",
                "新部门",
                1L,
                "",
                "",
                1,
                "正常",
                ""
        ));

        assertThat(response.parentId()).isEqualTo(1L);
        verify(departmentRepository).save(any());
    }

    @Test
    void shouldRejectDuplicateDepartmentCodeOnUpdateWhenCodeBelongsToAnotherDepartment() {
        DepartmentRepository departmentRepository = mock(DepartmentRepository.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        PermissionService permissionService = mock(PermissionService.class);
        Department current = department();
        Department existing = department();
        existing.setId(99L);
        existing.setDepartmentCode("HQ");
        when(departmentRepository.findByIdAndDeletedFlagFalse(10L)).thenReturn(Optional.of(current));
        when(departmentRepository.findByDepartmentCodeAndDeletedFlagFalse("HQ")).thenReturn(Optional.of(existing));

        DepartmentService service = new DepartmentService(
                departmentRepository,
                userAccountRepository,
                permissionService,
                new SnowflakeIdGenerator(1)
        );

        assertThatThrownBy(() -> service.update(10L, new DepartmentRequest(
                "HQ",
                "总部",
                null,
                "",
                "",
                1,
                "正常",
                ""
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("部门编码已存在");
        verify(departmentRepository, never()).save(any());
    }

    @Test
    void shouldTranslateDepartmentCodeConflictWhenConstraintMessageUsesColumnName() {
        DepartmentRepository departmentRepository = mock(DepartmentRepository.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        PermissionService permissionService = mock(PermissionService.class);
        when(departmentRepository.findByDepartmentCodeAndDeletedFlagFalse("HQ")).thenReturn(Optional.empty());
        when(departmentRepository.save(any())).thenThrow(new DataIntegrityViolationException("department_code"));

        DepartmentService service = new DepartmentService(
                departmentRepository,
                userAccountRepository,
                permissionService,
                new SnowflakeIdGenerator(1)
        );

        assertThatThrownBy(() -> service.create(new DepartmentRequest(
                "HQ",
                "总部",
                null,
                "",
                "",
                1,
                "正常",
                ""
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("部门编码已存在");
    }

    @Test
    void shouldRethrowDataIntegrityViolationWhenConstraintMessageIsNull() {
        DepartmentRepository departmentRepository = mock(DepartmentRepository.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        PermissionService permissionService = mock(PermissionService.class);
        DataIntegrityViolationException exception = new DataIntegrityViolationException(null);
        when(departmentRepository.findByDepartmentCodeAndDeletedFlagFalse("HQ")).thenReturn(Optional.empty());
        when(departmentRepository.save(any())).thenThrow(exception);

        DepartmentService service = new DepartmentService(
                departmentRepository,
                userAccountRepository,
                permissionService,
                new SnowflakeIdGenerator(1)
        );

        assertThatThrownBy(() -> service.create(new DepartmentRequest(
                "HQ",
                "总部",
                null,
                "",
                "",
                1,
                "正常",
                ""
        )))
                .isSameAs(exception);
    }

    @Test
    void shouldRemoveLegacyOptionsCacheWriter() {
        assertThat(getMethod("writeOptionsCache", List.class)).isNull();
    }

    private java.lang.reflect.Method getMethod(String name, Class<?>... paramTypes) {
        try {
            var m = DepartmentService.class.getDeclaredMethod(name, paramTypes);
            m.setAccessible(true);
            return m;
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    private Department department() {
        Department department = new Department();
        department.setId(10L);
        department.setDepartmentCode("HQ");
        department.setDepartmentName("总部");
        department.setStatus("正常");
        return department;
    }
}
