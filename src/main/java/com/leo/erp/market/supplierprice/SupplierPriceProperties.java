package com.leo.erp.market.supplierprice;

import com.leo.erp.common.config.ResourceVersionRequiredProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 供应商品牌基价加价规则的写接口并发配置。
 *
 * <p>与 {@code leo.market.quotation.require-resource-version} 同语义: 默认要求
 * {@code X-Resource-Version} 前置条件头, 缺少时返回 428, 避免无条件覆盖他人的规则。</p>
 */
@ConfigurationProperties(prefix = "leo.market.supplier-price")
public class SupplierPriceProperties extends ResourceVersionRequiredProperties {
}
