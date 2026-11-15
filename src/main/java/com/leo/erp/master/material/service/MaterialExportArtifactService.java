package com.leo.erp.master.material.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.export.ExportArtifact;
import com.leo.erp.common.export.ExportArtifactDraft;
import com.leo.erp.common.export.ExportArtifactKinds;
import com.leo.erp.common.export.ExportArtifactStore;
import com.leo.erp.common.export.ExportConcurrencyGuard;
import com.leo.erp.common.web.dto.FileDownloadResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * 商品资料导出产物的统一生成入口。
 *
 * <p>资源化端点与兼容旧端点共用本类，保证同一请求生成的字节、媒体类型与文件名一致。
 * 格式归一化与错误信息保持原端点行为：{@code xlsx}（默认）/ {@code csv}，
 * 其它值返回 422「不支持的导出格式」。</p>
 */
@Service
public class MaterialExportArtifactService {

    public static final String FORMAT_XLSX = "xlsx";
    public static final String FORMAT_CSV = "csv";

    private static final MediaType CSV_MEDIA_TYPE = new MediaType("text", "csv", StandardCharsets.UTF_8);
    private static final MediaType XLSX_MEDIA_TYPE = new MediaType(
            "application",
            "vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    );
    private static final String GUARD_LABEL = "material-export";

    private final MaterialDocumentService materialDocumentService;
    private final ExportConcurrencyGuard exportConcurrencyGuard;
    private final ExportArtifactStore artifactStore;

    public MaterialExportArtifactService(MaterialDocumentService materialDocumentService,
                                         ExportConcurrencyGuard exportConcurrencyGuard,
                                         ExportArtifactStore artifactStore) {
        this.materialDocumentService = materialDocumentService;
        this.exportConcurrencyGuard = exportConcurrencyGuard;
        this.artifactStore = artifactStore;
    }

    /** 生成导出文件（不落缓存，供兼容旧端点使用）。 */
    public FileDownloadResponse generate(String keyword, String format) {
        String normalizedFormat = format == null ? FORMAT_XLSX : format.trim().toLowerCase(Locale.ROOT);
        return exportConcurrencyGuard.execute(GUARD_LABEL, () -> switch (normalizedFormat) {
            case FORMAT_XLSX -> new FileDownloadResponse(
                    "material.xlsx", XLSX_MEDIA_TYPE, materialDocumentService.exportSpreadsheet(keyword));
            case FORMAT_CSV -> new FileDownloadResponse(
                    "materials.csv", CSV_MEDIA_TYPE, materialDocumentService.exportCsv(keyword));
            default -> throw new BusinessException(ErrorCode.VALIDATION_ERROR, "不支持的导出格式: " + format);
        });
    }

    /** 生成并登记导出产物（供资源端点使用）。 */
    public ExportArtifact create(String keyword, String format, Long ownerUserId) {
        FileDownloadResponse file = generate(keyword, format);
        return artifactStore.put(new ExportArtifactDraft(
                ExportArtifactKinds.MATERIAL_EXPORT,
                null,
                null,
                ownerUserId,
                file.filename(),
                file.contentType().toString(),
                file.content()
        ));
    }

}
