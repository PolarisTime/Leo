package com.leo.erp.market.lengthpremium.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 定尺加价写入请求。
 *
 * <p>加价允许为负（优惠），故只做合理区间限制而非非负校验；状态取值由服务层按
 * StatusConstants 白名单校验，给出 422 语义错误。</p>
 */
public record LengthPremiumRequest(
        @NotBlank(message = "定尺名称不能为空")
        @Size(max = 32, message = "定尺名称不能超过32个字符")
        String lengthName,

        @NotNull(message = "加价不能为空")
        @Min(value = -100000, message = "加价不能小于 -100000")
        @Max(value = 100000, message = "加价不能大于 100000")
        Integer premium,

        @NotNull(message = "排序号不能为空")
        @Min(value = 0, message = "排序号不能为负数")
        Integer sortOrder,

        @NotBlank(message = "状态不能为空")
        String status,

        @Size(max = 255, message = "备注不能超过255个字符")
        String remark,

        /**
         * 打印规格后缀（如 {@code *12}）：打印时追加到商品规格之后；空表示该定尺不追加。
         *
         * <p><b>写入语义与 remark 等其它字段不同（有意为之）</b>：</p>
         * <ul>
         *   <li>新建：按传入值 trim 后落库，{@code null} 即「无后缀」；</li>
         *   <li>更新：{@code null} = <b>保持原值</b>，空串 = <b>显式清空</b>。</li>
         * </ul>
         *
         * <p>原因：更新是整体替换语义，旧版维护页（未发版 / 回滚场景）只改加价时不会带本字段，
         * 若 {@code null} 按「清空」处理会静默抹掉已回填的 {@code *12}；空串是可表达的主动清空，
         * 语义足够。不要为了「与其他字段统一」把这里改回 null=清空。</p>
         */
        @Size(max = 16, message = "打印规格后缀长度不能超过16")
        String specSuffix
) {
}
