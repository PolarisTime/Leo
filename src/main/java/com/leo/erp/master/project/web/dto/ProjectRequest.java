package com.leo.erp.master.project.web.dto;

import com.leo.erp.common.support.ValidationMessages;
import com.leo.erp.common.validation.SnowflakeId;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record ProjectRequest(
        @NotBlank(message = "项目编码不能为空")
        @Size(max = 64, message = "项目编码长度不能超过64")
        String projectCode,
        @NotBlank(message = "项目名称不能为空")
        @Size(max = 200, message = "项目名称长度不能超过200")
        String projectName,
        @Size(max = 100, message = "项目简称长度不能超过100")
        String projectNameAbbr,
        @Size(max = 255, message = "项目地址长度不能超过255")
        String projectAddress,
        @Size(max = 32, message = "项目经理长度不能超过32")
        String projectManager,
        @Positive(message = "客户ID必须为正整数")
        @SnowflakeId Long customerId,
        @NotBlank(message = "客户编码不能为空")
        String customerCode,
        @Positive(message = "结算主体ID必须为正整数")
        @SnowflakeId Long settlementCompanyId,
        @Size(max = 128, message = "结算主体名称长度不能超过128")
        String settlementCompanyName,
        @NotBlank(message = ValidationMessages.STATUS_REQUIRED)
        @Size(max = 32, message = "状态长度不能超过32")
        String status,
        /** 网价浮动方向: ADD加价/SUBTRACT减价; 空表示不浮动。 */
        String priceFloatMode,
        /** 网价固定浮动幅度(元/吨), 非负。 */
        @PositiveOrZero(message = "网价浮动幅度不能为负")
        BigDecimal priceFloatValue,
        /** 默认取价数据源: MYSTEEL/STEELX。 */
        String quoteSource,
        /** 默认取价地区(西本城市中文名)。 */
        @Size(max = 32, message = "取价地区长度不能超过32")
        String quoteRegion,
        /** 允许的品牌列表; 空列表或 null 表示不限制。 */
        @Size(max = 100, message = "允许品牌最多 100 项")
        List<@NotBlank(message = "允许品牌不能为空") @Size(max = 64, message = "允许品牌内容过长") String> allowedBrands,
        /** 允许的材质列表; 空列表或 null 表示不限制。 */
        @Size(max = 100, message = "允许材质最多 100 项")
        List<@NotBlank(message = "允许材质不能为空") @Size(max = 64, message = "允许材质内容过长") String> allowedMaterials,
        /** 允许品牌不匹配时的处理方式: OFF关闭校验/WARN仅提醒/BLOCK阻断; 空表示按 WARN 处理。 */
        String allowedBrandSeverity,
        /** 允许材质不匹配时的处理方式: OFF关闭校验/WARN仅提醒/BLOCK阻断; 空表示按 WARN 处理。 */
        String allowedMaterialSeverity,
        /** 取价基准: DELIVERY送货日期当天/ORDER报单日期之后第N个交易日/MANUAL每次手工选择; 空表示 DELIVERY。 */
        String priceDateBasis,
        /** 报单日基准的偏移交易日数(0..30), 仅 ORDER 有意义; 空表示 0。 */
        @PositiveOrZero(message = "取价偏移交易日数不能为负")
        Integer priceDateOffsetDays,
        /** 基准日休市时的位移方向: PREV往前/NEXT延后(DELIVERY与ORDER有效); 空表示 PREV。 */
        String priceClosedSettleDirection,
        /** 基准日休市位移的交易日数(0..30; 0 仅 ORDER+NEXT 有效); 空表示 1。 */
        @PositiveOrZero(message = "休市位移交易日数不能为负")
        Integer priceClosedSettleDays,
        /** 时段策略: AUTO当日最新/FIXED固定时段/MANUAL每次手工选择; 空表示 AUTO。 */
        String pricePeriodPolicy,
        /** 固定取价时段(上午/中午/下午), 仅 FIXED 时必填。 */
        String pricePeriodFixed,
        @Size(max = 255, message = "备注长度不能超过255")
        String remark,
        /** 参与报单比价的商品键列表(类别|材质|规格|长度); 空列表表示全部商品可选。 */
        @Size(max = 500, message = "参与比价的商品最多 500 项")
        List<String> quoteProducts,
        /** 品牌 → 启用品种; 缺键或空数组表示该品牌全部品种。 */
        @Size(max = 100, message = "品牌品种配置最多 100 项")
        Map<String, List<String>> quoteBrandCategories,
        /** 12米定尺加价(元/吨); 空表示未配置(取价时回退定尺主数据默认值)。 */
        @DecimalMin(value = "0", message = "12米定尺加价不能为负")
        @Digits(integer = 12, fraction = 2, message = "12米定尺加价最多 12 位整数 2 位小数")
        BigDecimal quoteLengthPremium,
        /** 是否允许 HRB400 商品按 HRB400E 价格代取; 空表示 false。 */
        Boolean quoteHrb400eFallback,
        /** 指定品牌列表(仅报单比价展示徽标)。 */
        @Size(max = 100, message = "指定品牌最多 100 项")
        List<String> quoteDesignatedBrands
) {

    /**
     * 复制一份、只替换客户与结算主体快照（其余字段原样保留）。
     *
     * <p>归一化与主体补全要让"客户/结算主体"取服务端权威值，而其余字段必须逐字保留。
     * 用一个方法收口，避免每新增一个字段就要改四处长参数构造（漏改一处就是静默丢字段）。</p>
     */
    public ProjectRequest withCustomerAndSettlement(Long customerId,
                                                    String customerCode,
                                                    Long settlementCompanyId,
                                                    String settlementCompanyName) {
        return new ProjectRequest(
                projectCode, projectName, projectNameAbbr, projectAddress, projectManager,
                customerId, customerCode, settlementCompanyId, settlementCompanyName,
                status, priceFloatMode, priceFloatValue, quoteSource, quoteRegion,
                allowedBrands, allowedMaterials, allowedBrandSeverity, allowedMaterialSeverity,
                priceDateBasis, priceDateOffsetDays, priceClosedSettleDirection, priceClosedSettleDays,
                pricePeriodPolicy, pricePeriodFixed, remark,
                quoteProducts, quoteBrandCategories, quoteLengthPremium, quoteHrb400eFallback,
                quoteDesignatedBrands);
    }

    public ProjectRequest(String projectCode,
                          String projectName,
                          String projectNameAbbr,
                          String projectAddress,
                          String projectManager,
                          String customerCode,
                          String status,
                          String remark) {
        this(projectCode, projectName, projectNameAbbr, projectAddress, projectManager,
                null, customerCode, null, null, status, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, remark,
                null, null, null, null, null);
    }

    /**
     * 兼容旧调用方: 未携带取价数据源。
     *
     * @deprecated 生产代码零调用（LSP 核实，仅测试便捷构造使用）；测试夹具统一改用规范构造器后删除。
     */
    @Deprecated(forRemoval = true)
    public ProjectRequest(String projectCode,
                          String projectName,
                          String projectNameAbbr,
                          String projectAddress,
                          String projectManager,
                          Long customerId,
                          String customerCode,
                          Long settlementCompanyId,
                          String settlementCompanyName,
                          String status,
                          String priceFloatMode,
                          BigDecimal priceFloatValue,
                          String remark) {
        this(projectCode, projectName, projectNameAbbr, projectAddress, projectManager,
                customerId, customerCode, settlementCompanyId, settlementCompanyName,
                status, priceFloatMode, priceFloatValue, null, null, null, null, null, null,
                null, null, null, null, null, null, remark, null, null, null, null, null);
    }

    /**
     * 兼容旧调用方: 未携带网价浮动。
     *
     * @deprecated 生产代码零调用（LSP 核实，仅测试便捷构造使用）；测试夹具统一改用规范构造器后删除。
     */
    @Deprecated(forRemoval = true)
    public ProjectRequest(String projectCode,
                          String projectName,
                          String projectNameAbbr,
                          String projectAddress,
                          String projectManager,
                          Long customerId,
                          String customerCode,
                          Long settlementCompanyId,
                          String settlementCompanyName,
                          String status,
                          String remark) {
        this(projectCode, projectName, projectNameAbbr, projectAddress, projectManager,
                customerId, customerCode, settlementCompanyId, settlementCompanyName,
                status, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, remark, null, null, null, null, null);
    }

    /**
     * 兼容旧调用方: 未携带允许品牌/材质的处理方式。
     *
     * @deprecated 生产代码零调用（LSP 核实，仅测试便捷构造使用）；测试夹具统一改用规范构造器后删除。
     */
    @Deprecated(forRemoval = true)
    public ProjectRequest(String projectCode,
                          String projectName,
                          String projectNameAbbr,
                          String projectAddress,
                          String projectManager,
                          Long customerId,
                          String customerCode,
                          Long settlementCompanyId,
                          String settlementCompanyName,
                          String status,
                          String priceFloatMode,
                          BigDecimal priceFloatValue,
                          String quoteSource,
                          String quoteRegion,
                          List<String> allowedBrands,
                          List<String> allowedMaterials,
                          String remark) {
        this(projectCode, projectName, projectNameAbbr, projectAddress, projectManager,
                customerId, customerCode, settlementCompanyId, settlementCompanyName,
                status, priceFloatMode, priceFloatValue, quoteSource, quoteRegion,
                allowedBrands, allowedMaterials, null, null,
                null, null, null, null, null, null, remark, null, null, null, null, null);
    }
}
