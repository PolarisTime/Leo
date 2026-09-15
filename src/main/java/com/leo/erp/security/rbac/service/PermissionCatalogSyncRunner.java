package com.leo.erp.security.rbac.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 启动时同步权限目录，保证 {@code sys_permission} 与 {@link com.leo.erp.security.permission.PermissionCodes}
 * 一致。同步失败不阻断启动，仅记录错误，等待下次重启重试（目录同步是幂等的）。
 */
@Slf4j
@Component
public class PermissionCatalogSyncRunner implements ApplicationRunner {

    private final PermissionCatalogService permissionCatalogService;

    public PermissionCatalogSyncRunner(PermissionCatalogService permissionCatalogService) {
        this.permissionCatalogService = permissionCatalogService;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            int changed = permissionCatalogService.syncCatalog();
            log.info("权限目录同步完成，新增或更新 {} 条", changed);
        } catch (RuntimeException ex) {
            log.error("权限目录同步失败，将在下次启动重试", ex);
        }
    }
}
