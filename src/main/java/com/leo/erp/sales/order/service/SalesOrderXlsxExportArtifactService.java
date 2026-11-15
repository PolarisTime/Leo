package com.leo.erp.sales.order.service;

import com.leo.erp.common.export.ExportArtifact;
import com.leo.erp.common.export.ExportArtifactDraft;
import com.leo.erp.common.export.ExportArtifactKinds;
import com.leo.erp.common.export.ExportArtifactStore;
import com.leo.erp.common.export.ExportConcurrencyGuard;
import com.leo.erp.common.web.dto.FileDownloadResponse;
import org.springframework.stereotype.Service;

/**
 * 销售订单套打 Excel 导出产物的统一生成入口（资源化端点与兼容旧端点共用）。
 *
 * <p>导出的产物从属于销售订单：{@code parentType=sales-order}、{@code parentId=订单 id}，
 * 内容端点据此校验路径父资源与产物是否匹配，避免拿 A 订单的导出 id 去 B 订单路径下读取。</p>
 */
@Service
public class SalesOrderXlsxExportArtifactService {

    /** 产物父资源类型：销售订单。 */
    public static final String PARENT_TYPE = "sales-order";

    private static final String GUARD_LABEL = "sales-order-xlsx";

    private final SalesOrderPrintExportService salesOrderPrintExportService;
    private final ExportConcurrencyGuard exportConcurrencyGuard;
    private final ExportArtifactStore artifactStore;

    public SalesOrderXlsxExportArtifactService(SalesOrderPrintExportService salesOrderPrintExportService,
                                               ExportConcurrencyGuard exportConcurrencyGuard,
                                               ExportArtifactStore artifactStore) {
        this.salesOrderPrintExportService = salesOrderPrintExportService;
        this.exportConcurrencyGuard = exportConcurrencyGuard;
        this.artifactStore = artifactStore;
    }

    /** 生成导出文件（不落缓存，供兼容旧端点使用）。 */
    public FileDownloadResponse generate(Long orderId, SalesOrderPrintXlsxOptions options) {
        return exportConcurrencyGuard.execute(
                GUARD_LABEL,
                () -> salesOrderPrintExportService.exportSalesOrderPrint(orderId, options));
    }

    /** 生成并登记导出产物（供资源端点使用）。 */
    public ExportArtifact create(Long orderId, SalesOrderPrintXlsxOptions options, Long ownerUserId) {
        FileDownloadResponse file = generate(orderId, options);
        return artifactStore.put(new ExportArtifactDraft(
                ExportArtifactKinds.SALES_ORDER_XLSX_EXPORT,
                PARENT_TYPE,
                orderId,
                ownerUserId,
                file.filename(),
                file.contentType().toString(),
                file.content()
        ));
    }
}
