package com.leo.erp.market.pricelist.web.dto;

/** 规格全集条目(只读): 固定规格行的展示与预填来源。 */
public record MaterialSpecResponse(
        String category,
        String material,
        Integer spec,
        String length,
        Integer sortOrder
) {
}
