package com.leo.erp.market.pricelist.service;

/**
 * 比价单现货价未匹配原因(契约 4.6 修订 R2: 取消版本语义后的三种原因)。
 */
public enum SpotReason {

    /** 该品牌没有任何未删除的供应商价格表(需要区分"有表无条目"时用 {@link #NO_ITEM})。 */
    NO_LIST,
    /** 有价格表, 但没有匹配 (category, material, spec, length) 的条目。 */
    NO_ITEM,
    /** 命中条目, 但条目 price 为 NULL(不报价)。 */
    NO_PRICE
}
