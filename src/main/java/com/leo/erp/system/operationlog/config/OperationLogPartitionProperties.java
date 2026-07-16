package com.leo.erp.system.operationlog.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "leo.observability.operation-log")
public class OperationLogPartitionProperties {

    @Min(1)
    private int retentionMonths = 12;

    @Min(1)
    private int partitionLookaheadMonths = 12;

    public int getRetentionMonths() {
        return retentionMonths;
    }

    public void setRetentionMonths(int retentionMonths) {
        this.retentionMonths = retentionMonths;
    }

    public int getPartitionLookaheadMonths() {
        return partitionLookaheadMonths;
    }

    public void setPartitionLookaheadMonths(int partitionLookaheadMonths) {
        this.partitionLookaheadMonths = partitionLookaheadMonths;
    }
}
