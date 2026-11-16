package com.leo.erp.market.quotation.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * 比价项目级配置保存请求（V220 起只写「每品牌运费 + 备注」）。
 *
 * <p><b>实际生效的字段只有</b> {@code brands[].brandName}、{@code brands[].freight} 与 {@code remark}；
 * 品牌/品种/商品键/12米加价/HRB400E 代取/指定品牌已归并到项目资料
 * （{@code md_project.quote_*}），由 {@code PUT /projects/{id}} 维护。</p>
 *
 * <p>其余字段**保留但忽略**（不报错、不落库）：老版本前端仍会整包提交它们，
 * 直接拒绝会造成一次无谓的前端发版；等前端切换完成后再删除。</p>
 */
public record QuoteProjectConfigRequest(
        @DecimalMin(value = "0", message = "12米加价不能为负") BigDecimal lengthPremium,
        Boolean hrb400eFallback,
        List<String> products,
        List<String> designatedBrands,
        @Size(max = 255, message = "备注过长") String remark,
        @Valid List<BrandRequest> brands
) {

    /** 参与品牌：当前只使用品牌名与运费（品种与排序由项目资料决定）。 */
    public record BrandRequest(
            @NotBlank(message = "品牌名称不能为空") @Size(max = 64, message = "品牌名称过长") String brandName,
            @DecimalMin(value = "0", message = "运费不能为负") BigDecimal freight,
            List<String> categories,
            Integer sortOrder
    ) {
    }
}
