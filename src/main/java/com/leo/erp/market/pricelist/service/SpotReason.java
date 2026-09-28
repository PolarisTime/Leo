package com.leo.erp.market.pricelist.service;

/**
 * 比价单现货价未匹配原因(契约 4.5 R1.2)。
 */
public enum SpotReason {

    /** 该品牌在报价时刻之前没有任何生效版本(mk_quote_sheet 读时推导)。 */
    NO_LIST_AT_TIME,
    /** 有生效版本, 但没有匹配 (category, material, spec, length) 的条目。 */
    NO_ITEM,
    /** 命中条目, 但条目 price 为 NULL(不报价)。 */
    NO_PRICE
}
