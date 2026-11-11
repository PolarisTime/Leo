package com.leo.erp.market.quotation.web.dto;

import com.leo.erp.market.quotation.domain.enums.QuoteRowType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 报价单响应。
 * <p>报单比价模块与采购/销售单据完全解耦: 响应中不含任何采购订单号、明细行、已开吨位或已采购标记。</p>
 */
public record QuoteSheetResponse(
        Long id,
        String sheetNo,
        String name,
        Long projectId,
        String projectName,
        LocalDate orderDate,
        LocalDate refDate,
        String refPeriod,
        BigDecimal lengthPremium,
        boolean locked,
        boolean specQuantityLocked,
        String status,
        String remark,
        List<BrandResponse> brands,
        List<ItemResponse> items,
         LocalDateTime createdAt,
         LocalDateTime updatedAt,
         Long version
) {

    /** 以提交后回读的权威版本覆盖当前版本, 其余字段保持不变。 */
    public QuoteSheetResponse withVersion(Long newVersion) {
        return new QuoteSheetResponse(id, sheetNo, name, projectId, projectName, orderDate, refDate, refPeriod,
                lengthPremium, locked, specQuantityLocked, status, remark,
                brands, items, createdAt, updatedAt, newVersion);
    }

    public record BrandResponse(Long id, String brandName, BigDecimal freight, Integer sortOrder) {
    }

    /**
     * 行×品牌现货价。
     *
     * @param spotSourceMaterial 该现货价**实际取自**的材质（代取时非空，如 HRB400 行取了 HRB400E 的价）；
     *                           null 表示就是本行材质的价。前端据此打「代取」角标。
     */
    public record ItemPriceResponse(Long id, String brandName, BigDecimal spotPrice,
                                    Long supplierId, String supplierName,
                                    String spotSourceMaterial) {

        /** 兼容既有调用方（不含代取来源）。 */
        public ItemPriceResponse(Long id, String brandName, BigDecimal spotPrice,
                                 Long supplierId, String supplierName) {
            this(id, brandName, spotPrice, supplierId, supplierName, null);
        }
    }

    /**
     * 商品行/隔断行。{@code locked} 仅表示行级编辑保护标记, 不再表达任何采购订单关联状态。
     */
    public record ItemResponse(Long id, Integer lineNo, QuoteRowType rowType, String category, String material,
                               Integer spec, String length, String remark, BigDecimal ton,
                               boolean locked,
                               List<ItemPriceResponse> prices) {

        /** 兼容旧调用方: 未显式传行类型与行备注。 */
        public ItemResponse(Long id, Integer lineNo, String category, String material, Integer spec,
                            String length, BigDecimal ton, List<ItemPriceResponse> prices) {
            this(id, lineNo, QuoteRowType.PRODUCT, category, material, spec, length, null, ton, false, prices);
        }

        /** 兼容旧调用方: 未携带行备注。 */
        public ItemResponse(Long id, Integer lineNo, QuoteRowType rowType, String category, String material,
                            Integer spec, String length, BigDecimal ton, List<ItemPriceResponse> prices) {
            this(id, lineNo, rowType, category, material, spec, length, null, ton, false, prices);
        }

        /** 兼容旧调用方: 未携带锁定标记。 */
        public ItemResponse(Long id, Integer lineNo, QuoteRowType rowType, String category, String material,
                            Integer spec, String length, String remark, BigDecimal ton,
                            List<ItemPriceResponse> prices) {
            this(id, lineNo, rowType, category, material, spec, length, remark, ton, false, prices);
        }
    }
}
