package com.leo.erp.market.supplierprice;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 供应商品牌基价加价规则的写接口并发配置。
 *
 * <p>与 {@code leo.market.quotation.require-resource-version} 同语义: 默认要求
 * {@code X-Resource-Version} 前置条件头, 缺少时返回 428, 避免无条件覆盖他人的规则。</p>
 */
@ConfigurationProperties(prefix = "leo.market.supplier-price")
public class SupplierPriceProperties {

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
