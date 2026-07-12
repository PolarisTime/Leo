package com.leo.erp.common.support;

import java.util.List;

public interface WarehouseCatalog {

    List<String> listActiveWarehouseNames();

    default List<WarehouseSnapshot> listActiveWarehouses() {
        return listActiveWarehouseNames().stream()
                .map(name -> new WarehouseSnapshot(null, null, name))
                .toList();
    }
}
