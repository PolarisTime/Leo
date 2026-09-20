package com.leo.erp.market.quotation.domain.enums;

/** 报价单明细行类型。 */
public enum QuoteRowType {
    /** 商品行: 携带商品与价格, 参与网价/现货/汇总。 */
    PRODUCT,
    /** 隔断行: 仅作视觉分组, 不携带商品与价格。 */
    SEPARATOR
}
