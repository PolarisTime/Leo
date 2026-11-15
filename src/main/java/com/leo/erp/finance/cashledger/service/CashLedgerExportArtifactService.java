package com.leo.erp.finance.cashledger.service;

import com.leo.erp.common.export.ExportArtifact;
import com.leo.erp.common.export.ExportArtifactDraft;
import com.leo.erp.common.export.ExportArtifactKinds;
import com.leo.erp.common.export.ExportArtifactStore;
import com.leo.erp.common.export.ExportConcurrencyGuard;
import com.leo.erp.common.web.dto.FileDownloadResponse;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/**
 * 资金流水导出产物的统一生成入口（资源化端点与兼容旧端点共用）。
 *
 * <p>并发闸门与行数上限两道「保命」防线保持不变：闸门限制同时导出数（429），
 * {@link CashLedgerService#MAX_EXPORT_ROWS} 限制单次导出规模（422）。</p>
 */
@Service
public class CashLedgerExportArtifactService {

    private static final String GUARD_LABEL = "cash-ledger-export";

    private final CashLedgerService cashLedgerService;
    private final ExportConcurrencyGuard exportConcurrencyGuard;
    private final ExportArtifactStore artifactStore;

    public CashLedgerExportArtifactService(CashLedgerService cashLedgerService,
                                           ExportConcurrencyGuard exportConcurrencyGuard,
                                           ExportArtifactStore artifactStore) {
        this.cashLedgerService = cashLedgerService;
        this.exportConcurrencyGuard = exportConcurrencyGuard;
        this.artifactStore = artifactStore;
    }

    /** 生成导出文件（不落缓存，供兼容旧端点使用）。 */
    public FileDownloadResponse generate(Long settlementCompanyId,
                                         LocalDate startDate,
                                         LocalDate endDate,
                                         String counterpartyType,
                                         Long counterpartyId,
                                         String flowType,
                                         String keyword) {
        return exportConcurrencyGuard.execute(GUARD_LABEL, () -> cashLedgerService.exportExcel(
                settlementCompanyId, startDate, endDate,
                counterpartyType, counterpartyId, flowType, keyword));
    }

    /** 生成并登记导出产物（供资源端点使用）。 */
    public ExportArtifact create(Long settlementCompanyId,
                                 LocalDate startDate,
                                 LocalDate endDate,
                                 String counterpartyType,
                                 Long counterpartyId,
                                 String flowType,
                                 String keyword,
                                 Long ownerUserId) {
        FileDownloadResponse file = generate(
                settlementCompanyId, startDate, endDate,
                counterpartyType, counterpartyId, flowType, keyword);
        return artifactStore.put(new ExportArtifactDraft(
                ExportArtifactKinds.CASH_LEDGER_EXPORT,
                null,
                null,
                ownerUserId,
                file.filename(),
                file.contentType().toString(),
                file.content()
        ));
    }
}
