package com.leo.erp.market.lengthpremium.web.dto;

import java.time.LocalDateTime;

/**
 * 定尺加价响应。{@code id} 为雪花 ID，按全局约定序列化为十进制字符串。
 *
 * <p>审计时间 {@code createdAt}/{@code updatedAt} 来自 {@code AbstractAuditableEntity}。
 * 日期格式**不在这里声明**：{@code JacksonConfig} 已注册全局
 * {@code IsoLocalDateTimeSerializer}（带时区偏移的 ISO-8601），
 * 局部再写 {@code @JsonFormat} 只会造成两种格式并存。</p>
 *
 * <p>前端列表的「更新时间」列依赖这两个字段；缺失时该列只能显示占位符。</p>
 */
public record LengthPremiumResponse(
        Long id,
        String lengthName,
        Integer premium,
        Integer sortOrder,
        String status,
        String remark,
        /** 打印规格后缀（如 {@code *12}）；为空表示该定尺打印时不追加内容。 */
        String specSuffix,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
