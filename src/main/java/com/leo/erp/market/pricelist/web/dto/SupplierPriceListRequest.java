package com.leo.erp.market.pricelist.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 价格表创建/更新请求。
 *
 * <p>已取消版本语义(契约 4.6 修订 R2): {@code releasedAt} / {@code effectiveFrom} / {@code effectiveTo}
 * 仅为兼容保留, 不参与取版与筛选; {@code releasedAt} 缺省 = 当前时刻。</p>
 *
 * <p>{@code quotedOn} 是<b>业务报价日期</b>(用户可填, 创建时缺省 = 当天), 与系统审计列
 * {@code updatedAt}(最后修改时刻) 严格区分; 同一 (供应商, 品牌) 可直接改该日期, 不产生版本。</p>
 *
 * <p>业务校验(规格必须为正、单价不得为负、报价状态枚举、条目键必须在规格全集内、条目键重复)
 * 统一由服务层按 422 语义处理; {@code supplierId} 里的雪花 ID 必须为十进制字符串,
 * JSON number 只接受安全整数(见 {@code JacksonConfig.SnowflakeSafeLongDeserializer})。</p>
 */
public record SupplierPriceListRequest(
        @NotNull(message = "供应商不能为空") Long supplierId,
        @NotBlank(message = "品牌不能为空") @Size(max = 64, message = "品牌过长") String brandName,
        /** 兼容保留: 缺省 = 当前时刻, 不再参与取版。 */
        LocalDateTime releasedAt,
        /** 兼容保留(允许为空), 不参与取版与筛选。 */
        LocalDate effectiveFrom,
        /** 兼容保留(允许为空), 不参与取版与筛选。 */
        LocalDate effectiveTo,
        @Size(max = 64, message = "仓库过长") String warehouse,
        @Size(max = 255, message = "备注过长") String remark,
        @Valid List<ItemRequest> items,
        /**
         * 业务报价日期 {@code yyyy-MM-dd}(可空: 创建 = 当天, 更新 = 保持原值)。
         *
         * <p><b>刻意用字符串接收</b>: 若直接声明 {@code LocalDate}, Jackson 对 {@code 2026-13-45}
         * 这类非法日期会抛反序列化异常, 被全局处理器映射为 400(请求体格式错误)。本接口契约要求
         * "非法日期 422"(语义/字段校验失败), 因此先按 ISO 文本收下, 由 {@code @Pattern} 与服务层
         * 解析统一按 422 返回; 真正的 JSON 语法错误仍是 400。</p>
         */
        @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$", message = "报价日期格式必须为 yyyy-MM-dd")
        String quotedOn
) {

    /** 兼容旧调用方: 未携带业务报价日期(创建时按当天, 更新时保持原值)。 */
    public SupplierPriceListRequest(Long supplierId, String brandName, LocalDateTime releasedAt,
                                    LocalDate effectiveFrom, LocalDate effectiveTo, String warehouse,
                                    String remark, List<ItemRequest> items) {
        this(supplierId, brandName, releasedAt, effectiveFrom, effectiveTo, warehouse, remark, items, null);
    }

    /**
     * 价格条目: 单价留空(null) = 不报价, 不得写 0。
     * <p>{@code priceStatus}/{@code remark} 数据层保留(兼容历史数据), 维护页不再展示与编辑。</p>
     */
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
