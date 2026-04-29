package com.leo.erp.system.role.service;

import com.leo.erp.auth.domain.entity.UserRole;
import com.leo.erp.auth.repository.UserRoleRepository;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.persistence.Specs;
import com.leo.erp.common.service.AbstractCrudService;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.security.permission.PermissionService;
import com.leo.erp.security.permission.ResourcePermissionCatalog;
import com.leo.erp.system.dashboard.service.DashboardSummaryService;
import com.leo.erp.system.role.domain.entity.RolePermission;
import com.leo.erp.system.role.domain.entity.RoleSetting;
import com.leo.erp.system.role.repository.RolePermissionRepository;
import com.leo.erp.system.role.repository.RoleSettingRepository;
import com.leo.erp.system.role.web.dto.RolePermissionItem;
import com.leo.erp.system.role.web.dto.RoleSettingRequest;
import com.leo.erp.system.role.web.dto.RoleSettingResponse;
import com.leo.erp.system.menu.web.dto.MenuTreeResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class RoleSettingService extends AbstractCrudService<RoleSetting, RoleSettingRequest, RoleSettingResponse> {

    private static final Set<String> ALLOWED_ROLE_TYPES = Set.of("平台角色", "系统角色", "业务角色", "财务角色");
    private static final Set<String> ALLOWED_DATA_SCOPES = Set.of("全部数据", "全部", "本部门", "本人");
    private static final Set<String> ALLOWED_STATUS = StatusConstants.ALLOWED_ACTIVE_STATUS;

    private final RoleSettingRepository repository;
    private final RolePermissionRepository rolePermissionRepository;
    private final UserRoleRepository userRoleRepository;
    private final PermissionService permissionService;
    private final DashboardSummaryService dashboardSummaryService;

    @Autowired
    public RoleSettingService(RoleSettingRepository repository,
                              RolePermissionRepository rolePermissionRepository,
                              UserRoleRepository userRoleRepository,
                              SnowflakeIdGenerator idGenerator,
                              PermissionService permissionService,
                              DashboardSummaryService dashboardSummaryService) {
        super(idGenerator);
        this.repository = repository;
        this.rolePermissionRepository = rolePermissionRepository;
        this.userRoleRepository = userRoleRepository;
        this.permissionService = permissionService;
        this.dashboardSummaryService = dashboardSummaryService;
    }

    public RoleSettingService(RoleSettingRepository repository,
                              RolePermissionRepository rolePermissionRepository,
                              UserRoleRepository userRoleRepository,
                              SnowflakeIdGenerator idGenerator,
                              PermissionService permissionService) {
        this(repository, rolePermissionRepository, userRoleRepository, idGenerator, permissionService, null);
    }

    @Transactional(readOnly = true)
    public Page<RoleSettingResponse> page(PageQuery query, String keyword, String status) {
        Specification<RoleSetting> spec = Specs.<RoleSetting>notDeleted()
                .and(Specs.keywordLike(keyword, "roleCode", "roleName", "roleType"))
                .and(Specs.equalIfPresent("status", normalizeStatusFilter(status)));
        Page<RoleSetting> entityPage = repository.findAll(spec, query.toPageable("id"));
        RoleStatsSnapshot snapshot = buildStatsSnapshot(entityPage.getContent());
        List<RoleSettingResponse> responses = entityPage.getContent().stream()
                .map(entity -> toResponse(entity, snapshot))
                .toList();
        return new PageImpl<>(responses, entityPage.getPageable(), entityPage.getTotalElements());
    }

    @Override
    protected RoleSettingResponse toDetailResponse(RoleSetting entity) {
        return toResponse(entity, buildStatsSnapshot(List.of(entity)));
    }

    @Override
    protected RoleSettingResponse toSavedResponse(RoleSetting entity) {
        return toDetailResponse(entity);
    }

    @Override
    protected void validateCreate(RoleSettingRequest request) {
        if (repository.existsByRoleCodeAndDeletedFlagFalse(request.roleCode())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "角色编码已存在");
        }
    }

    @Override
    protected void validateUpdate(RoleSetting entity, RoleSettingRequest request) {
        if (!entity.getRoleCode().equals(request.roleCode())
                && repository.existsByRoleCodeAndDeletedFlagFalse(request.roleCode())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "角色编码已存在");
        }
    }

    @Override
    protected RoleSetting newEntity() {
        return new RoleSetting();
    }

    @Override
    protected void assignId(RoleSetting entity, Long id) {
        entity.setId(id);
    }

    @Override
    protected Optional<RoleSetting> findActiveEntity(Long id) {
        return repository.findByIdAndDeletedFlagFalse(id);
    }

    @Override
    protected String notFoundMessage() {
        return "角色不存在";
    }

    @Override
    protected void apply(RoleSetting entity, RoleSettingRequest request) {
        entity.setRoleCode(normalizeRoleCode(request.roleCode()));
        entity.setRoleName(normalizeRequiredValue(request.roleName(), "角色名称"));
        entity.setRoleType(normalizeAllowedValue(request.roleType(), ALLOWED_ROLE_TYPES, "角色类型"));
        entity.setDataScope(normalizeAllowedValue(request.dataScope(), ALLOWED_DATA_SCOPES, "数据范围"));
        entity.setPermissionCodes(null);
        entity.setPermissionCount(0);
        entity.setPermissionSummary(null);
        entity.setUserCount(0);
        entity.setStatus(normalizeStatus(request.status()));
        entity.setRemark(normalizeOptionalValue(request.remark()));
    }

    @Transactional(readOnly = true)
    public List<RolePermissionItem> getRolePermissions(Long roleId) {
        requireEntity(roleId);
        return rolePermissionRepository.findByRoleIdAndDeletedFlagFalse(roleId)
                .stream()
                .map(permission -> new RolePermissionItem(permission.getResourceCode(), permission.getActionCode()))
                .toList();
    }

    @Transactional
    public void saveRolePermissions(Long roleId, List<RolePermissionItem> permissions) {
        requireEntity(roleId);
        if (permissions == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "权限列表不能为空");
        }
        Set<String> seen = new LinkedHashSet<>();
        Map<String, Set<String>> actionsByResource = new LinkedHashMap<>();
        rolePermissionRepository.deleteActiveByRoleId(roleId);
        rolePermissionRepository.flush();
        for (RolePermissionItem item : permissions) {
            String rawResource = ResourcePermissionCatalog.normalizeResource(item.resource());
            String resource = ResourcePermissionCatalog.isKnownResource(rawResource)
                    ? rawResource
                    : ResourcePermissionCatalog.resolveResourceByMenuCode(item.resource()).orElse(rawResource);
            String action = ResourcePermissionCatalog.normalizeAction(item.action());
            String uniqueKey = resource + ":" + action;
            if (!seen.add(uniqueKey)) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "权限列表存在重复项");
            }
            if (!ResourcePermissionCatalog.isAllowed(resource, action)) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "存在无效的资源权限配置: " + resource + ":" + action);
            }
            actionsByResource.computeIfAbsent(resource, key -> new LinkedHashSet<>()).add(action);
        }
        actionsByResource.values().forEach(actions -> {
            if (actions.stream().anyMatch(action -> !ResourcePermissionCatalog.READ.equals(action))) {
                actions.add(ResourcePermissionCatalog.READ);
            }
        });
        List<RolePermission> toSave = new java.util.ArrayList<>(actionsByResource.values().stream().mapToInt(Set::size).sum());
        actionsByResource.forEach((resource, actions) -> actions.forEach(action -> {
            RolePermission permission = new RolePermission();
            permission.setId(nextId());
            permission.setRoleId(roleId);
            permission.setResourceCode(resource);
            permission.setActionCode(action);
            toSave.add(permission);
        }));
        if (!toSave.isEmpty()) {
            rolePermissionRepository.saveAll(toSave);
        }
        permissionService.evictAllCache();
        if (dashboardSummaryService != null) {
            dashboardSummaryService.evictAllCache();
        }
    }

    @Transactional(readOnly = true)
    public List<MenuTreeResponse> listPermissionOptions() {
        Map<String, List<ResourcePermissionCatalog.Entry>> entriesByGroup = ResourcePermissionCatalog.entries().stream()
                .collect(Collectors.groupingBy(
                        ResourcePermissionCatalog.Entry::group,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
        List<MenuTreeResponse> result = new java.util.ArrayList<>();
        int groupOrder = 1;
        for (Map.Entry<String, List<ResourcePermissionCatalog.Entry>> groupEntry : entriesByGroup.entrySet()) {
            String groupCode = "permission-group-" + groupOrder;
            List<MenuTreeResponse> children = new java.util.ArrayList<>();
            int childOrder = 1;
            for (ResourcePermissionCatalog.Entry entry : groupEntry.getValue()) {
                children.add(new MenuTreeResponse(
                        entry.menuCodes().isEmpty() ? entry.code() : entry.menuCodes().get(0),
                        entry.title(),
                        groupCode,
                        entry.pathPrefixes().isEmpty() ? null : entry.pathPrefixes().get(0),
                        null,
                        childOrder++,
                        "菜单",
                        entry.actions().stream().map(ResourcePermissionCatalog.ActionOption::code).toList(),
                        new java.util.ArrayList<>()
                ));
            }
            result.add(new MenuTreeResponse(
                    groupCode,
                    groupEntry.getKey(),
                    null,
                    null,
                    null,
                    groupOrder++,
                    "目录",
                    List.of(),
                    children
            ));
        }
        return result;
    }

    private RoleSettingResponse toResponse(RoleSetting entity, RoleStatsSnapshot snapshot) {
        List<RolePermission> permissions = snapshot.permissionsByRoleId().getOrDefault(entity.getId(), List.of());
        List<String> permissionCodes = permissions.stream()
                .sorted(Comparator.comparing(RolePermission::getResourceCode).thenComparing(RolePermission::getActionCode))
                .map(permission -> permission.getResourceCode() + ":" + permission.getActionCode())
                .toList();
        return new RoleSettingResponse(
                entity.getId(),
                entity.getRoleCode(),
                entity.getRoleName(),
                entity.getRoleType(),
                entity.getDataScope(),
                permissionCodes,
                permissionCodes.size(),
                buildPermissionSummary(permissions),
                Math.toIntExact(snapshot.userCountByRoleId().getOrDefault(entity.getId(), 0L)),
                entity.getStatus(),
                entity.getRemark()
        );
    }

    private RoleStatsSnapshot buildStatsSnapshot(List<RoleSetting> roles) {
        if (roles == null || roles.isEmpty()) {
            return new RoleStatsSnapshot(Map.of(), Map.of());
        }

        List<Long> roleIds = roles.stream()
                .map(RoleSetting::getId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (roleIds.isEmpty()) {
            return new RoleStatsSnapshot(Map.of(), Map.of());
        }

        Map<Long, List<RolePermission>> permissionsByRoleId = new LinkedHashMap<>();
        rolePermissionRepository.findByRoleIdInAndDeletedFlagFalse(roleIds).forEach(permission ->
                permissionsByRoleId.computeIfAbsent(permission.getRoleId(), key -> new java.util.ArrayList<>()).add(permission)
        );

        Map<Long, Long> userCountByRoleId = new LinkedHashMap<>();
        for (UserRole userRole : userRoleRepository.findByRoleIdInAndDeletedFlagFalse(roleIds)) {
            userCountByRoleId.merge(userRole.getRoleId(), 1L, Long::sum);
        }

        return new RoleStatsSnapshot(permissionsByRoleId, userCountByRoleId);
    }

    private String buildPermissionSummary(List<RolePermission> permissions) {
        return ResourcePermissionCatalog.buildPermissionSummary(permissions);
    }

    private String normalizeRoleCode(String value) {
        String normalized = normalizeRequiredValue(value, "角色编码");
        if (normalized.length() > 64) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "角色编码长度不能超过64");
        }
        return normalized;
    }

    private String normalizeStatus(String value) {
        String normalized = normalizeOptionalValue(value);
        if (normalized == null) {
            return "正常";
        }
        if (!ALLOWED_STATUS.contains(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "角色状态不合法");
        }
        return normalized;
    }

    private String normalizeStatusFilter(String value) {
        String normalized = normalizeOptionalValue(value);
        if (normalized == null) {
            return null;
        }
        if (!ALLOWED_STATUS.contains(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "角色状态不合法");
        }
        return normalized;
    }

    private String normalizeAllowedValue(String value, Set<String> allowedValues, String fieldName) {
        String normalized = normalizeRequiredValue(value, fieldName);
        if (!allowedValues.contains(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, fieldName + "不合法");
        }
        return normalized;
    }

    private String normalizeRequiredValue(String value, String fieldName) {
        String normalized = normalizeOptionalValue(value);
        if (normalized == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, fieldName + "不能为空");
        }
        return normalized;
    }

    private String normalizeOptionalValue(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private record RoleStatsSnapshot(
            Map<Long, List<RolePermission>> permissionsByRoleId,
            Map<Long, Long> userCountByRoleId
    ) {
    }

    @Override
    protected RoleSetting saveEntity(RoleSetting entity) {
        RoleSetting saved = repository.save(entity);
        permissionService.evictAllCache();
        if (dashboardSummaryService != null) {
            dashboardSummaryService.evictAllCache();
        }
        return saved;
    }

    @Override
    protected RoleSettingResponse toResponse(RoleSetting entity) {
        return toResponse(entity, buildStatsSnapshot(List.of(entity)));
    }
}
