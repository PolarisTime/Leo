package com.leo.erp.sales.contract;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 销售合同写入的乐观并发配置。
 */
@ConfigurationProperties(prefix = "leo.sales.contract")
public class SalesContractProperties {

    /**
     * PUT 写接口是否强制要求资源版本前置条件头。
     * <p>默认关闭(可选): 缺少 {@code X-Resource-Version}(兼容别名 {@code If-Match})时允许无条件替换;
     * 开启后缺少版本返回 428, 版本不匹配返回 412。</p>
     */
    private boolean requireResourceVersion = false;

    public boolean isRequireResourceVersion() {
        return requireResourceVersion;
    }

    public void setRequireResourceVersion(boolean requireResourceVersion) {
        this.requireResourceVersion = requireResourceVersion;
    }
}
