package com.leo.erp.common.support;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

public final class WarehouseSelectionSupportTestDoubles {

    private WarehouseSelectionSupportTestDoubles() {
    }

    public static void stubWarehouseResolution(WarehouseSelectionSupport warehouseSelectionSupport) {
        when(warehouseSelectionSupport.resolveWarehouse(any(), any(), anyInt(), anyBoolean()))
                .thenAnswer(invocation -> {
                    Long warehouseId = invocation.getArgument(0);
                    String warehouseName = invocation.getArgument(1);
                    String normalizedName = warehouseName == null ? null : warehouseName.trim();
                    return new WarehouseSnapshot(warehouseId, null, normalizedName);
                });
    }
}
