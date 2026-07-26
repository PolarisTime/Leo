package com.leo.erp.master.api;

public interface MasterDataStatisticsQuery {

    MasterDataStatistics countActiveRecords();

    record MasterDataStatistics(long materialCount, long supplierCount, long customerCount) {
    }
}
