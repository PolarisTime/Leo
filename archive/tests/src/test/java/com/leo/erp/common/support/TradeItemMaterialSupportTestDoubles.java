package com.leo.erp.common.support;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

public final class TradeItemMaterialSupportTestDoubles {

    private TradeItemMaterialSupportTestDoubles() {
    }

    public static void stubMaterialCodeNormalization(TradeItemMaterialSupport materialSupport) {
        when(materialSupport.normalizeMaterialCode(any(), anyInt())).thenAnswer(invocation -> {
            String materialCode = invocation.getArgument(0);
            int lineNo = invocation.getArgument(1);
            if (lineNo == 0 && (materialCode == null || materialCode.isBlank())) {
                return null;
            }
            if (materialCode == null || materialCode.trim().isEmpty()) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "第" + lineNo + "行商品编码不能为空");
            }
            return materialCode.trim();
        });
        when(materialSupport.resolveMaterial(any(), anyString(), anyInt())).thenAnswer(invocation -> {
            Long materialId = invocation.getArgument(0);
            String materialCode = invocation.getArgument(1);
            if (materialCode == null) {
                return null;
            }
            return new TradeMaterialSnapshot(materialId, materialCode.trim(), true);
        });
    }
}
