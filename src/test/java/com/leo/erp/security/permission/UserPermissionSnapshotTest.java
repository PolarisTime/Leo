package com.leo.erp.security.permission;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class UserPermissionSnapshotTest {

    @Test
    void shouldCreateRecord() {
        Map<String, Set<String>> permissionMap = Map.of(
                "material", Set.of("read", "create"),
                "supplier", Set.of("read")
        );
        Map<String, String> dataScope = Map.of(
                "material:read", "self",
                "material:create", "all"
        );

        UserPermissionSnapshot snapshot = new UserPermissionSnapshot(permissionMap, dataScope);

        assertThat(snapshot.permissionMap()).isEqualTo(permissionMap);
        assertThat(snapshot.dataScopeByPermission()).isEqualTo(dataScope);
    }

    @Test
    void shouldSupportEmptyMaps() {
        UserPermissionSnapshot snapshot = new UserPermissionSnapshot(Map.of(), Map.of());

        assertThat(snapshot.permissionMap()).isEmpty();
        assertThat(snapshot.dataScopeByPermission()).isEmpty();
    }

    @Test
    void shouldImplementEquals() {
        Map<String, Set<String>> pm = Map.of("material", Set.of("read"));
        Map<String, String> ds = Map.of("material:read", "self");

        UserPermissionSnapshot s1 = new UserPermissionSnapshot(pm, ds);
        UserPermissionSnapshot s2 = new UserPermissionSnapshot(pm, ds);

        assertThat(s1).isEqualTo(s2);
    }

    @Test
    void shouldImplementHashCode() {
        Map<String, Set<String>> pm = Map.of("material", Set.of("read"));
        Map<String, String> ds = Map.of("material:read", "self");

        UserPermissionSnapshot s1 = new UserPermissionSnapshot(pm, ds);
        UserPermissionSnapshot s2 = new UserPermissionSnapshot(pm, ds);

        assertThat(s1.hashCode()).isEqualTo(s2.hashCode());
    }

    @Test
    void shouldAccessPermissionMap() {
        Map<String, Set<String>> permissionMap = Map.of(
                "material", Set.of("read", "create", "update"),
                "purchase-order", Set.of("read", "audit")
        );

        UserPermissionSnapshot snapshot = new UserPermissionSnapshot(permissionMap, Map.of());

        assertThat(snapshot.permissionMap().get("material")).containsExactlyInAnyOrder("read", "create", "update");
        assertThat(snapshot.permissionMap().get("purchase-order")).containsExactlyInAnyOrder("read", "audit");
    }

    @Test
    void shouldAccessDataScope() {
        Map<String, String> dataScope = Map.of(
                "material:read", "all",
                "material:create", "department"
        );

        UserPermissionSnapshot snapshot = new UserPermissionSnapshot(Map.of(), dataScope);

        assertThat(snapshot.dataScopeByPermission().get("material:read")).isEqualTo("all");
        assertThat(snapshot.dataScopeByPermission().get("material:create")).isEqualTo("department");
    }
}
