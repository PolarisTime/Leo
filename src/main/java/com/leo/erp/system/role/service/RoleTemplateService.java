package com.leo.erp.system.role.service;

import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.system.role.domain.entity.RolePermission;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 预定义角色模板，管理员创建角色时一键应用，减少 28×7 手动配置。
 */
@Service
public class RoleTemplateService {

    private final SnowflakeIdGenerator idGenerator;

    public RoleTemplateService(SnowflakeIdGenerator idGenerator) {
        this.idGenerator = idGenerator;
    }

    public record Template(String name, String description, List<PermissionEntry> permissions) {}

    public record PermissionEntry(String resource, Set<String> actions) {}

    public List<Template> listTemplates() {
        return List.of(
                new Template("采购员", "采购订单、采购入库、供应商管理",
                        List.of(
                                new PermissionEntry("purchase-order", Set.of("read", "create", "update", "delete")),
                                new PermissionEntry("purchase-inbound", Set.of("read", "create", "update")),
                                new PermissionEntry("supplier", Set.of("read")),
                                new PermissionEntry("material", Set.of("read")),
                                new PermissionEntry("warehouse", Set.of("read"))
                        )),
                new Template("销售员", "销售订单、销售出库、客户管理",
                        List.of(
                                new PermissionEntry("sales-order", Set.of("read", "create", "update", "delete")),
                                new PermissionEntry("sales-outbound", Set.of("read", "create", "update")),
                                new PermissionEntry("customer", Set.of("read")),
                                new PermissionEntry("material", Set.of("read")),
                                new PermissionEntry("warehouse", Set.of("read")),
                                new PermissionEntry("customer-statement", Set.of("read"))
                        )),
                new Template("财务", "收付款、发票、对账单",
                        List.of(
                                new PermissionEntry("receipt", Set.of("read", "create", "update", "audit")),
                                new PermissionEntry("payment", Set.of("read", "create", "update", "audit")),
                                new PermissionEntry("customer-statement", Set.of("read", "export")),
                                new PermissionEntry("company-setting", Set.of("read"))
                        )),
                new Template("仓管", "仓库、入库出库只读",
                        List.of(
                                new PermissionEntry("warehouse", Set.of("read", "create", "update")),
                                new PermissionEntry("purchase-inbound", Set.of("read")),
                                new PermissionEntry("sales-outbound", Set.of("read")),
                                new PermissionEntry("material", Set.of("read"))
                        )),
                new Template("管理员", "全部资源全部操作",
                        List.of(new PermissionEntry("*", Set.of("*"))))
        );
    }

    public List<RolePermission> buildPermissions(Long roleId, Template template) {
        var catalog = com.leo.erp.security.permission.ResourcePermissionCatalog.ENTRIES;
        boolean allResources = template.permissions().stream().anyMatch(p -> "*".equals(p.resource()));

        if (allResources) {
            return catalog.stream()
                    .flatMap(entry -> entry.actions().stream()
                            .map(action -> buildPermission(roleId, entry.code(), action.code())))
                    .toList();
        }

        return template.permissions().stream()
                .flatMap(entry -> entry.actions().stream()
                        .map(action -> buildPermission(roleId, entry.resource(), action)))
                .toList();
    }

    private RolePermission buildPermission(Long roleId, String resourceCode, String actionCode) {
        RolePermission p = new RolePermission();
        p.setId(idGenerator.nextId());
        p.setRoleId(roleId);
        p.setResourceCode(resourceCode);
        p.setActionCode(actionCode);
        return p;
    }
}
