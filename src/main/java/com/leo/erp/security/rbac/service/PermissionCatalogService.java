package com.leo.erp.security.rbac.service;

import com.leo.erp.security.permission.PermissionCodes;
import com.leo.erp.security.rbac.domain.entity.SysPermission;
import com.leo.erp.security.rbac.repository.SysPermissionRepository;
import com.leo.erp.security.rbac.web.dto.PermissionResponse;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 权限目录同步与查询。
 *
 * <p>{@link #syncCatalog()} 在应用启动时按 {@link PermissionCodes#all()} 幂等 upsert
 * {@code sys_permission}，保证代码层 {@link PermissionCodes} 是权限码的单一来源；
 * 新增/删除常量后只需重启即可对齐数据库，不会重复写入。</p>
 */
@Service
public class PermissionCatalogService {

    private final SysPermissionRepository permissionRepository;

    public PermissionCatalogService(SysPermissionRepository permissionRepository) {
        this.permissionRepository = permissionRepository;
    }

    /**
     * 将权限码目录幂等同步到 {@code sys_permission}。
     *
     * @return 本次新增或字段发生变化的记录数；重复执行且无变化时返回 0
     */
    @Transactional
    public int syncCatalog() {
        Set<String> codes = PermissionCodes.all();
        Map<String, SysPermission> existing = permissionRepository.findAllById(codes).stream()
                .collect(Collectors.toMap(SysPermission::getCode, Function.identity(), (left, right) -> left));
        LocalDateTime now = LocalDateTime.now();
        List<SysPermission> changed = new ArrayList<>();
        for (String code : codes) {
            PermissionCodeRules.PermissionParts parts = PermissionCodeRules.parse(code);
            SysPermission entity = existing.get(code);
            if (entity == null) {
                entity = new SysPermission();
                entity.setCode(code);
                entity.setResource(parts.resource());
                entity.setAction(parts.action());
                entity.setField(parts.field());
                entity.setCreatedAt(now);
                entity.setUpdatedAt(now);
                changed.add(entity);
                continue;
            }
            if (!Objects.equals(entity.getResource(), parts.resource())
                    || !Objects.equals(entity.getAction(), parts.action())
                    || !Objects.equals(entity.getField(), parts.field())) {
                entity.setResource(parts.resource());
                entity.setAction(parts.action());
                entity.setField(parts.field());
                entity.setUpdatedAt(now);
                changed.add(entity);
            }
        }
        if (!changed.isEmpty()) {
            permissionRepository.saveAll(changed);
        }
        return changed.size();
    }

    /** 返回权限目录，按资源、动作、权限码稳定排序，供前端权限矩阵分组。 */
    @Transactional(readOnly = true)
    public List<PermissionResponse> listCatalog() {
        Sort sort = Sort.by(Sort.Order.asc("resource"), Sort.Order.asc("action"), Sort.Order.asc("code"));
        return permissionRepository.findAll(sort).stream()
                .map(permission -> new PermissionResponse(
                        permission.getCode(),
                        permission.getResource(),
                        permission.getAction(),
                        permission.getField(),
                        permission.getDescription()
                ))
                .toList();
    }
}
