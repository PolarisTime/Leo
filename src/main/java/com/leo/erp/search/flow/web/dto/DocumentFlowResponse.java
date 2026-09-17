package com.leo.erp.search.flow.web.dto;

import java.util.List;

/**
 * 通用单据流视图：以输入单号所在单据为起点，返回整个连通链路的节点与关系边。
 */
public record DocumentFlowResponse(
        String documentNo,
        List<DocumentFlowNode> nodes,
        List<DocumentFlowLink> links,
        /** 是否因节点/关系边数量达到上限而截断。 */
        boolean truncated
) {
}
