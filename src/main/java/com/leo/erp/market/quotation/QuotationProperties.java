package com.leo.erp.market.quotation;

import com.leo.erp.common.config.ResourceVersionRequiredProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 报单比价乐观并发配置。
 */
@ConfigurationProperties(prefix = "leo.market.quotation")
public class QuotationProperties extends ResourceVersionRequiredProperties {
}
