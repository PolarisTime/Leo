package com.leo.erp.common.support;

public record TradeMaterialSnapshot(Long materialId, String materialCode) {

    public TradeMaterialSnapshot(String materialCode) {
        this(null, materialCode);
    }
}
