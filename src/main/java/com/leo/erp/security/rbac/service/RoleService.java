package com.leo.erp.security.rbac.service;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.persistence.Specs;
import com.leo.erp.common.service.CrudOperationLogger;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.security.permission.PermissionCodes;
import com.leo.erp.security.rbac.domain.entity.SysRole;
import com.leo.erp.security.rbac.domain.entity.SysRolePermission;
import com.leo.erp.security.rbac.repository.SysRolePermissionRepository;
import com.leo.erp.security.rbac.repository.SysRoleRepository;
import com.leo.erp.security.rbac.web.dto.RoleDetailResponse;
import com.leo.erp.security.rbac.web.dto.RoleRequest;
import com.leo.erp.security.rbac.web.dto.RoleResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * RBAC0 角色服务。
 *
 * <p><strong>权限缓存策略：</strong>角色/权限变更后不维护内存权限缓存，
 * {@code RoleBasedAuthorityProvider} 每次请求实时查询数据库，因此变更在下一次请求立即生效，
 * 无需额外失效入口。</p>
 */
@Service
public class RoleService {

    private final SysRoleRepository roleRepository;
    private final SysRolePermissionRepository rolePermissionRepository;
    private final SnowflakeIdGenerator snowflakeIdGenerator;
    private final CrudOperationLogger operationLogger = CrudOperationLogger.forOwner(RoleService.class);

    public RoleService(SysRoleRepository roleRepository,
                       SysRolePermissionRepository rolePermissionRepository,
                       SnowflakeIdGenerator snowflakeIdGenerator) {
        this.roleRepository = roleRepository;
        this.rolePermissionRepository = rolePermissionRepository;
        this.snowflakeIdGenerator = snowflakeIdGenerator;
    }

    @Transactional(readOnly = true)
    public Page<RoleResponse> page(PageQuery query, String keyword, String status) {
        Specification<SysRole> spec = Specs.<SysRole>notDeleted()
                .and(Specs.keywordLike(keyword, "code", "name"))
                .and(Specs.equalIfPresent("status", normalizeOptionalStatus(status)));
        return roleRepository.findAll(spec, query.toPageable("id")).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public RoleDetailResponse detail(Long id) {
        SysRole role = requireActiveRole(id);
        return toDetail(role);
    }

    @Transactional
    public RoleResponse create(RoleRequest request) {
        String code = requireText(request.code(), "角色编码不能为空");
        if (roleRepository.existsByCodeAndDeletedFlagFalse(code)) {
            throw new BusinessException(ErrorCode.CONCURRENT_MODIFICATION, "角色编码已存在");
        }
        SysRole role = new SysRole();
        role.setId(snowflakeIdGenerator.nextId());
        role.setCode(code);
        role.setName(requireText(request.name(), "角色名称不能为空"));
        role.setDescription(trimToNull(request.description()));
        role.setBuiltin(false);
        role.setStatus(normalizeStatusOrDefault(request.status(), StatusConstants.NORMAL));
        SysRole saved = roleRepository.save(role);
        operationLogger.created(saved, saved.getId());
        return toResponse(saved);
    }

    @Transactional
    public RoleResponse update(Long id, RoleRequest request) {
        SysRole role = requireActiveRole(id);
        String nextCode = requireText(request.code(), "角色编码不能为空");
        if (role.isBuiltin()) {
            if (!role.getCode().equals(nextCode)) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "内置角色编码不允许修改");
            }
        } else if (!role.getCode().equals(nextCode) && roleRepository.existsByCodeAndDeletedFlagFalse(nextCode)) {
            throw new BusinessException(ErrorCode.CONCURRENT_MODIFICATION, "角色编码已存在");
        }
        role.setCode(nextCode);
        role.setName(requireText(request.name(), "角色名称不能为空"));
        role.setDescription(trimToNull(request.description()));
        role.setStatus(normalizeStatusOrDefault(request.status(), role.getStatus()));
        SysRole saved = roleRepository.save(role);
        operationLogger.updated(saved, id);
        return toResponse(saved);
    }

    @Transactional
    public RoleResponse updateStatus(Long id, String status) {
        SysRole role = requireActiveRole(id);
        String nextStatus = StatusConstants.normalizeActiveStatus(status, "角色状态");
        if (!nextStatus.equals(role.getStatus())) {
            role.setStatus(nextStatus);
            role = roleRepository.save(role);
        }
        return toResponse(role);
    }

    @Transactional
    public void delete(Long id) {
        SysRole role = requireActiveRole(id);
        if (role.isBuiltin()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "内置角色不允许删除");
        }
        role.setDeletedFlag(true);
        roleRepository.save(role);
        operationLogger.deleted(role, id);
    }

    @Transactional
    public RoleDetailResponse replacePermissions(Long id, List<String> permissionCodes) {
        SysRole role = requireActiveRole(id);
        Set<String> codes = normalizePermissionCodes(permissionCodes);
        if (role.isBuiltin() && !codes.contains(PermissionCodes.WILDCARD)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "内置角色必须保留全部权限通配符 *");
        }

        rolePermissionRepository.deleteByRoleId(id);
        List<SysRolePermission> links = codes.stream().map(code -> {
            SysRolePermission link = new SysRolePermission();
            link.setId(snowflakeIdGenerator.nextId());
            link.setRoleId(id);
            link.setPermissionCode(code);
            return link;
        }).toList();
        rolePermissionRepository.saveAll(links);
        operationLogger.updated(role, id);
        return toDetail(role);
    }

    private Set<String> normalizePermissionCodes(List<String> permissionCodes) {
        if (permissionCodes == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "权限集合不能为空");
        }
        Set<String> codes = new LinkedHashSet<>();
        for (String raw : permissionCodes) {
            String code = trimToNull(raw);
            if (code == null) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "权限码不能为空");
            }
            if (!PermissionCodeRules.isKnown(code)) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "非法权限码: " + code);
            }
            codes.add(code);
        }
        return codes;
    }

    private SysRole requireActiveRole(Long id) {
        if (id == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "角色ID不能为空");
        }
        return roleRepository.findByIdAndDeletedFlagFalse(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "角色不存在"));
    }

    private String normalizeOptionalStatus(String status) {
        return StatusConstants.normalizeOptionalActiveStatus(status, "角色状态");
    }

    private String normalizeStatusOrDefault(String status, String fallback) {
        if (status == null || status.isBlank()) {
            return fallback;
        }
        return StatusConstants.normalizeActiveStatus(status, "角色状态");
    }

    private String requireText(String value, String message) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, message);
        }
        return normalized;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private RoleResponse toResponse(SysRole role) {
        return new RoleResponse(
                role.getId(),
                role.getCode(),
                role.getName(),
                role.getDescription(),
                role.isBuiltin(),
                role.getStatus()
        );
    }

    private RoleDetailResponse toDetail(SysRole role) {
        List<String> permissions = rolePermissionRepository.findByRoleId(role.getId()).stream()
                .map(SysRolePermission::getPermissionCode)
                .filter(code -> code != null && !code.isBlank())
                .distinct()
                .sorted()
                .toList();
        return new RoleDetailResponse(
                role.getId(),
                role.getCode(),
                role.getName(),
                role.getDescription(),
                role.isBuiltin(),
                role.getStatus(),
                permissions
        );
    }
}
