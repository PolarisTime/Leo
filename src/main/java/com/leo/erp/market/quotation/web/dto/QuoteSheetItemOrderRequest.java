package com.leo.erp.market.quotation.web.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.leo.erp.common.json.SnowflakeIdStringDeserializer;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 商品行顺序调整请求(报价单子资源 {@code item-order} 的表示)。
 *
 * <p>语义: 按 {@code itemIds} 的先后顺序排列这些行; 未列出的行保持其原有相对顺序并排在末尾。
 * 因此请求允许只提交调用方已知的行——协同场景下其他设备新增的行不会被丢弃, 也不会让整个请求失败。</p>
 *
 * <p>雪花 ID 必须以十进制字符串传递(见 {@link SnowflakeIdStringDeserializer}), 禁止 JSON number:
 * JavaScript Number 无法精确表示 19 位雪花 ID, 误用数值会静默丢失低位。</p>
 */
public record QuoteSheetItemOrderRequest(
        @NotEmpty(message = "行顺序不能为空")
        @Size(max = 1000, message = "一次最多调整 1000 行")
        @JsonDeserialize(contentUsing = SnowflakeIdStringDeserializer.class)
        List<@NotNull(message = "行 id 不能为空") Long> itemIds
) {
}
