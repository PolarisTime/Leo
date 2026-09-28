package com.leo.erp.market.pricelist.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 价格表版本创建/更新请求。
 * <p>业务校验(规格必须为正、单价不得为负、报价状态枚举、条目键必须在规格全集内、条目键重复)
 * 统一由服务层按 422 语义处理; {@code itemIds} 里的雪花 ID 必须为十进制字符串,
 * JSON number 只接受安全整数(见 {@code JacksonConfig.SnowflakeSafeLongDeserializer})。</p>
 */
public record SupplierPriceListRequest(
        @NotNull(message = "供应商不能为空") Long supplierId,
        @NotBlank(message = "品牌不能为空") @Size(max = 64, message = "品牌过长") String brandName,
        @NotNull(message = "发布时刻不能为空") LocalDateTime releasedAt,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        @Size(max = 64, message = "仓库过长") String warehouse,
        @Size(max = 255, message = "备注过长") String remark,
        @Valid List<ItemRequest> items
) {

    /** 价格条目: 单价留空(null) = 不报价, 不得写 0。 */
    public record ItemRequest(
            @Size(max = 16, message = "类别过长") String category,
            @Size(max = 16, message = "材质过长") String material,
            Integer spec,
            @Size(max = 16, message = "定尺过长") String length,
            BigDecimal price,
            String priceStatus,
            @Size(max = 255, message = "备注过长") String remark,
            Integer sortOrder
    ) {

        /** 兼容旧调用方: 未携带排序号。 */
        public ItemRequest(String category, String material, Integer spec, String length,
                           BigDecimal price, String priceStatus, String remark) {
            this(category, material, spec, length, price, priceStatus, remark, null);
        }
    }
}
