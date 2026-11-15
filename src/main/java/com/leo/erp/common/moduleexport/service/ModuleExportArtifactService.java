package com.leo.erp.common.moduleexport.service;

import com.leo.erp.common.export.ExportArtifact;
import com.leo.erp.common.export.ExportArtifactDraft;
import com.leo.erp.common.export.ExportArtifactKinds;
import com.leo.erp.common.export.ExportArtifactStore;
import com.leo.erp.common.export.ExportConcurrencyGuard;
import com.leo.erp.common.moduleexport.web.dto.ModuleExportRequest;
import com.leo.erp.common.web.dto.FileDownloadResponse;
import org.springframework.stereotype.Service;

/**
 * 业务单据导出产物的统一生成入口。
 *
 * <p>资源化端点与兼容旧端点都走这里，保证「同一请求 → 同一字节」：</p>
 * <ul>
 *     <li>{@link #create(ModuleExportRequest, Long)}：资源端点使用，生成后写入
 *         {@link ExportArtifactStore} 供 {@code GET /{id}/content} 回读；</li>
 *     <li>{@link #generate(ModuleExportRequest)}：旧端点使用，只生成不落缓存，保持
 *         迁移期旧端点的原响应契约不变。</li>
 * </ul>
 *
 * <p>并发闸门放在生成入口而不是控制器：两种端点共用同一额度，
 * 且闸门在服务开启事务之前取额度，等待期间不占用数据库连接（见 {@link ExportConcurrencyGuard}）。</p>
 */
@Service
public class ModuleExportArtifactService {

    private static final String GUARD_LABEL = "module-xlsx";

    private final ModuleExportService moduleExportService;
    private final ExportConcurrencyGuard exportConcurrencyGuard;
    private final ExportArtifactStore artifactStore;

    public ModuleExportArtifactService(ModuleExportService moduleExportService,
                                       ExportConcurrencyGuard exportConcurrencyGuard,
                                       ExportArtifactStore artifactStore) {
        this.moduleExportService = moduleExportService;
        this.exportConcurrencyGuard = exportConcurrencyGuard;
        this.artifactStore = artifactStore;
    }

    /** 生成导出文件（不落缓存，供兼容旧端点使用）。 */
    public FileDownloadResponse generate(ModuleExportRequest request) {
        return exportConcurrencyGuard.execute(
                GUARD_LABEL,
                () -> moduleExportService.export(request.moduleKey(), request.recordIds())
        );
    }

    /** 生成并登记导出产物（供资源端点使用）。 */
    public ExportArtifact create(ModuleExportRequest request, Long ownerUserId) {
        FileDownloadResponse file = generate(request);
        return artifactStore.put(new ExportArtifactDraft(
                ExportArtifactKinds.MODULE_EXPORT,
                null,
                null,
                ownerUserId,
                file.filename(),
                file.contentType().toString(),
                file.content()
        ));
    }
}
