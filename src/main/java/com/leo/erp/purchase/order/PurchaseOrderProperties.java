package com.leo.erp.purchase.order;

import com.leo.erp.common.config.ResourceVersionRequiredProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 采购订单写入的乐观并发配置。
 *
 * <p>与 {@code market.quotation.QuotationProperties}、{@code sales.contract.SalesContractProperties}
 * 同一模式：写入口（{@code PUT /purchase-orders/{id}}）从 {@code X-Resource-Version}
 * 读取客户端期望版本，缺少版本返回 428、与实体 {@code @Version}
 * 不一致返回 412。采购订单有库存/结算副作用，因此默认强制（true），
 * 仅在灰度兼容旧客户端时可临时关闭。</p>
 */
@ConfigurationProperties(prefix = "leo.purchase-order")
public class PurchaseOrderProperties extends ResourceVersionRequiredProperties {
}
