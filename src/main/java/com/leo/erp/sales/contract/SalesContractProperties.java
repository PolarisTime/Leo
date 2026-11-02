package com.leo.erp.sales.contract;

import com.leo.erp.common.config.ResourceVersionRequiredProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 销售合同写入的乐观并发配置。
 */
@ConfigurationProperties(prefix = "leo.sales.contract")
public class SalesContractProperties extends ResourceVersionRequiredProperties {

    /** 销售合同默认「不强制」：历史客户端未带版本即可整体替换，保持既有行为。 */
    public SalesContractProperties() {
        super(false);
    }
}
