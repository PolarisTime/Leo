package com.leo.erp.search.flow.web.dto;

/**
 * 单据流关系边：由上游单据指向下游单据。
 */
public record DocumentFlowLink(
        String fromType,
        String fromId,
        String toType,
        String toId,
        String linkType
) {
}
