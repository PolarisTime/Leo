package com.leo.erp.purchase.order;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 采购订单写入的乐观并发配置。
 *
 * <p>与 {@code market.quotation.QuotationProperties}、{@code sales.contract.SalesContractProperties}
 * 同一模式：写入口（{@code PUT /purchase-orders/{id}}）从 {@code X-Resource-Version}
 * （兼容 {@code If-Match}）读取客户端期望版本，缺少版本返回 428、与实体 {@code @Version}
 * 不一致返回 412。采购订单有库存/结算副作用，因此默认强制（true），
 * 仅在灰度兼容旧客户端时可临时关闭。</p>
 */
@ConfigurationProperties(prefix = "leo.purchase-order")
public class PurchaseOrderProperties {

    /**
     * PUT 写接口是否强制要求资源版本前置条件头。
     * <p>默认开启：缺少 {@code X-Resource-Version}（兼容别名 {@code If-Match}）时返回 428，
     * 避免无条件覆盖他人修改。关闭后缺少版本仍允许更新，但**带了版本照样校验 412**。</p>
     */
    private boolean requireResourceVersion = true;

    public boolean isRequireResourceVersion() {
        return requireResourceVersion;
    }

    public void setRequireResourceVersion(boolean requireResourceVersion) {
        this.requireResourceVersion = requireResourceVersion;
    }
}
