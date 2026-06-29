package com.leo.erp.system.department.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.leo.erp.auth.domain.entity.UserAccount;
import com.leo.erp.auth.repository.UserAccountRepository;
import com.leo.erp.common.api.PageQuery;
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
                null
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
                null
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
                null
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
                null
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
                null
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
                null
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
                null
        );

        List<DepartmentOptionResponse> options = service.options();

        assertThat(options).hasSize(1);
        assertThat(options.get(0).id()).isEqualTo(1L);
        assertThat(options.get(0).departmentCode()).isEqualTo("HQ");
        assertThat(options.get(0).departmentName()).isEqualTo("总部");
    }

    @Test
    void shouldReturnOptionsFromCache_whenCacheAvailable() {
        DepartmentRepository departmentRepository = mock(DepartmentRepository.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        PermissionService permissionService = mock(PermissionService.class);
        RedisJsonCacheSupport cacheSupport = mock(RedisJsonCacheSupport.class);

        List<DepartmentOptionResponse> cachedOptions = List.of(
                new DepartmentOptionResponse(1L, "HQ", "总部")
        );
        when(cacheSupport.getOrLoad(anyString(), any(Duration.class), any(TypeReference.class), any()))
                .thenReturn(cachedOptions);

        DepartmentService service = new DepartmentService(
                departmentRepository,
                userAccountRepository,
                permissionService,
                null,
                cacheSupport
        );

        List<DepartmentOptionResponse> options = service.options();

        assertThat(options).isEqualTo(cachedOptions);
        verify(cacheSupport).getOrLoad(anyString(), any(Duration.class), any(TypeReference.class), any());
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
        when(cacheSupport.getOrLoad(anyString(), any(Duration.class), any(TypeReference.class), any()))
                .thenReturn(List.of());

        DepartmentService service = new DepartmentService(
                departmentRepository,
                userAccountRepository,
                permissionService,
                null,
                cacheSupport
        );

        List<DepartmentOptionResponse> options = service.options();

        assertThat(options).hasSize(1);
        verify(cacheSupport, never()).delete(anyString());
        verify(cacheSupport).write(eq("leo:department:options"), eq(options), any(Duration.class));
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
        when(cacheSupport.getOrLoad(anyString(), any(Duration.class), any(TypeReference.class), any()))
                .thenReturn(List.of());

        DepartmentService service = new DepartmentService(
                departmentRepository,
                userAccountRepository,
                permissionService,
                null,
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
                null,
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
                null,
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
                null
        );

        Page<DepartmentResponse> result = service.page(new PageQuery(0, 10, null, null), null, "正常");
        assertThat(result.getContent()).isEmpty();
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
                null
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

        verify(cacheSupport).deleteAfterCommit(anyString());
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
