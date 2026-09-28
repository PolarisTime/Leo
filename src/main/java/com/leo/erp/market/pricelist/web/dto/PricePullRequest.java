package com.leo.erp.market.pricelist.web.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 价格拉取请求(可选固化动作)。
 * <p>比价单现货价的主路径是"读时自动推导", 本接口只把推导结果快照落库, 供归档/审计。</p>
 */
public record PricePullRequest(
        List<Long> supplierIds,
        List<String> brandNames,
        LocalDateTime releasedBefore,
        Boolean overwriteManual
) {

    public boolean overwriteManualEnabled() {
        return Boolean.TRUE.equals(overwriteManual);
    }
}
