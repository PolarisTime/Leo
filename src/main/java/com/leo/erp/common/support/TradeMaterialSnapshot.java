package com.leo.erp.common.support;

public record TradeMaterialSnapshot(Long materialId, String materialCode, Boolean batchNoEnabled) {

    public TradeMaterialSnapshot(String materialCode, Boolean batchNoEnabled) {
        this(null, materialCode, batchNoEnabled);
    }
}
