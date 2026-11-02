package com.leo.erp.finance.payment;

import com.leo.erp.common.config.ResourceVersionRequiredProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 付款单写入的乐观并发配置。
 *
 * <p>与 {@code purchase.order.PurchaseOrderProperties}、{@code sales.order.SalesOrderProperties}
 * 同一模式：写入口（{@code PUT /payments/{id}}）从 {@code X-Resource-Version}
 * 读取客户端期望版本，缺少版本返回 428、与实体 {@code @Version}
 * 不一致返回 412。付款单会同步资金账户与供应商台账（结算副作用），因此默认强制（true），
 * 仅在灰度兼容旧客户端时可临时关闭。</p>
 */
@ConfigurationProperties(prefix = "leo.payment")
public class PaymentProperties extends ResourceVersionRequiredProperties {
}
