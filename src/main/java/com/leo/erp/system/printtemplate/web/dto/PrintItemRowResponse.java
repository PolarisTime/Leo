package com.leo.erp.system.printtemplate.web.dto;

import com.leo.erp.system.printtemplate.service.PrintRecordItem;

/**
 * 打印明细行响应：service 层 PrintRecordItem 的 web 契约形态。
 */
public record PrintItemRowResponse(
        String id,
        String recordId,
        String brand,
        String category,
        String settlementMode,
        String material,
        String spec,
        String length,
        String quantity,
        String pieceWeightTon,
        String weightTon,
        String unitPrice,
        String amount
) {

    public static PrintItemRowResponse from(PrintRecordItem item) {
        return new PrintItemRowResponse(
                item.id(),
                item.recordId(),
                item.brand(),
                item.category(),
                item.settlementMode(),
                item.material(),
                item.spec(),
                item.length(),
                item.quantity(),
                item.pieceWeightTon(),
                item.weightTon(),
                item.unitPrice(),
                item.amount()
        );
    }
}
