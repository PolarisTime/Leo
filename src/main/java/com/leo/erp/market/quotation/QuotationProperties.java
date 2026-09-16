package com.leo.erp.market.quotation;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 报单比价乐观并发配置。
 */
@ConfigurationProperties(prefix = "leo.market.quotation")
public class QuotationProperties {

    /**
     * 写接口是否强制要求资源版本前置条件头。
     * <p>默认开启: 缺少 {@code X-Resource-Version}(兼容别名 {@code If-Match})时返回 428,
     * 避免无条件覆盖。仅在灰度/兼容旧客户端时可临时关闭。</p>
     */
    private boolean requireResourceVersion = true;

    public boolean isRequireResourceVersion() {
        return requireResourceVersion;
    }

    public void setRequireResourceVersion(boolean requireResourceVersion) {
        this.requireResourceVersion = requireResourceVersion;
    }
}
