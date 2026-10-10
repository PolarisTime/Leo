package com.leo.erp.purchase.order.web.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 采购订单响应向后兼容契约。
 *
 * <p>强制结单留痕是新增组件: 未强制结单时必须<b>不输出该键</b>。线上前端对采购订单列表使用
 * {@code z.strictObject}, 任何多出的键都会让整页列表解析失败; Jackson 对 record 的默认行为是
 * 把空组件序列化为 {@code null}, 因此这里锁死 {@code @JsonInclude(NON_NULL)} 的效果。</p>
 */
class PurchaseOrderResponseSerializationTest {

    /** 与列表响应一致: items/chargeItems 传 null, 用于验证既有键形态未被破坏。 */
    private final ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();

    private PurchaseOrderResponse response(PurchaseOrderResponse.ForceCloseInfo forceClose) {
        return new PurchaseOrderResponse(
                1L, "PO-1", null, null, "供应商", null, null, null, null,
                null, null, "完成采购", false, null, null, null,
                false, false, 0, null, null, null, forceClose
        );
    }

    @Test
    void serialization_shouldOmitForceCloseKeyWhenNotForceClosed() throws Exception {
        String json = objectMapper.writeValueAsString(response(null));

        assertThat(json).doesNotContain("forceClose");
        // 既有键不受影响: 列表页依赖 chargeItems:null 显式存在
        assertThat(json).contains("\"chargeItems\":null");
    }

    @Test
    void serialization_shouldExposeForceCloseTrailWhenForceClosed() throws Exception {
        String json = objectMapper.writeValueAsString(response(
                new PurchaseOrderResponse.ForceCloseInfo(
                        "剩余 1 件报废", 1, 9L, "系统管理员",
                        LocalDateTime.of(2026, 10, 9, 13, 50, 43))
        ));

        assertThat(json).contains("\"forceClose\":{");
        assertThat(json).contains("\"reason\":\"剩余 1 件报废\"");
        assertThat(json).contains("\"remainingQuantity\":1");
        assertThat(json).contains("\"operatorName\":\"系统管理员\"");
    }
}
