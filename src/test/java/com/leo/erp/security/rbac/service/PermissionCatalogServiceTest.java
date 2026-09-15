package com.leo.erp.security.rbac.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.leo.erp.security.permission.PermissionCodes;
import com.leo.erp.security.rbac.domain.entity.SysPermission;
import com.leo.erp.security.rbac.repository.SysPermissionRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 权限目录同步测试：首轮写入、字段解析、幂等、字段变化更新。
 */
@ExtendWith(MockitoExtension.class)
class PermissionCatalogServiceTest {

    @Mock
    private SysPermissionRepository permissionRepository;

    @InjectMocks
    private PermissionCatalogService permissionCatalogService;

    @Test
    void syncCatalog_shouldInsertEveryCatalogCodeOnFirstRun() {
        when(permissionRepository.findAllById(anyIterable())).thenReturn(List.of());

        int changed = permissionCatalogService.syncCatalog();

        assertThat(changed).isEqualTo(PermissionCodes.all().size());
        List<SysPermission> saved = captureSaved();
        assertThat(saved).extracting(SysPermission::getCode)
                .containsExactlyInAnyOrderElementsOf(PermissionCodes.all());

        SysPermission wildcard = find(saved, PermissionCodes.WILDCARD);
        assertThat(wildcard.getResource()).isEqualTo("*");
        assertThat(wildcard.getAction()).isEqualTo("*");
        assertThat(wildcard.getField()).isNull();

        SysPermission field = find(saved, "sales-orders:read:amount");
        assertThat(field.getResource()).isEqualTo("sales-orders");
        assertThat(field.getAction()).isEqualTo("read");
        assertThat(field.getField()).isEqualTo("amount");
    }

    @Test
    void syncCatalog_shouldBeIdempotentWhenNothingChanged() {
        when(permissionRepository.findAllById(anyIterable())).thenReturn(entitiesMatchingCatalog());

        int changed = permissionCatalogService.syncCatalog();

        assertThat(changed).isZero();
        verify(permissionRepository, never()).saveAll(anyIterable());
    }

    @Test
    void syncCatalog_shouldRepairChangedFields() {
        SysPermission stale = new SysPermission();
        stale.setCode("sales-orders:read");
        stale.setResource("sales-orders");
        stale.setAction("WRONG");
        when(permissionRepository.findAllById(anyIterable())).thenReturn(List.of(stale));

        permissionCatalogService.syncCatalog();

        assertThat(stale.getAction()).isEqualTo("read");
        assertThat(stale.getResource()).isEqualTo("sales-orders");
    }

    private List<SysPermission> captureSaved() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<SysPermission>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(permissionRepository).saveAll(captor.capture());
        List<SysPermission> saved = new ArrayList<>();
        captor.getValue().forEach(saved::add);
        return saved;
    }

    private List<SysPermission> entitiesMatchingCatalog() {
        Set<String> codes = PermissionCodes.all();
        List<SysPermission> entities = new ArrayList<>(codes.size());
        for (String code : codes) {
            PermissionCodeRules.PermissionParts parts = PermissionCodeRules.parse(code);
            SysPermission entity = new SysPermission();
            entity.setCode(code);
            entity.setResource(parts.resource());
            entity.setAction(parts.action());
            entity.setField(parts.field());
            entities.add(entity);
        }
        return entities;
    }

    private SysPermission find(List<SysPermission> permissions, String code) {
        return permissions.stream()
                .filter(permission -> code.equals(permission.getCode()))
                .findFirst()
                .orElseThrow();
    }
}
