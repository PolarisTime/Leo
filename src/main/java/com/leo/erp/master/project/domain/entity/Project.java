package com.leo.erp.master.project.domain.entity;

import com.leo.erp.common.persistence.AbstractAuditableEntity;
import com.leo.erp.common.persistence.StatusAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Table(name = "md_project")
public class Project extends AbstractAuditableEntity implements StatusAwareEntity {

    @Id
    private Long id;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "project_code", nullable = false, length = 64)
    private String projectCode;

    @Column(name = "project_name", nullable = false, length = 200)
    private String projectName;

    @Column(name = "project_name_abbr", length = 100)
    private String projectNameAbbr;

    @Column(name = "project_address", length = 255)
    private String projectAddress;

    @Column(name = "project_manager", length = 32)
    private String projectManager;

    @Column(name = "customer_id")
    private Long customerId;

    @Column(name = "customer_code", nullable = false, length = 64)
    private String customerCode;

    @Column(name = "settlement_company_id")
    private Long settlementCompanyId;

    @Column(name = "settlement_company_name", length = 128)
    private String settlementCompanyName;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    /** 网价浮动方向: ADD加价/SUBTRACT减价; null 表示不启用网价浮动。 */
    @Column(name = "price_float_mode", length = 8)
    private String priceFloatMode;

    /** 网价固定浮动幅度(元/吨), 非负; 与 priceFloatMode 同时为空或同时有值。 */
    @Column(name = "price_float_value", precision = 12, scale = 2)
    private BigDecimal priceFloatValue;

    /** 上次交付核定使用的价格规定ID(项目级记忆)。 */
    @Column(name = "last_price_rule_id")
    private Long lastPriceRuleId;

    /** 默认取价数据源: MYSTEEL/STEELX; 空=MYSTEEL。 */
    @Column(name = "quote_source", length = 16)
    private String quoteSource;

    /** 默认取价地区(西本城市中文名); 空=杭州。 */
    @Column(name = "quote_region", length = 32)
    private String quoteRegion;

    /** 允许的品牌(逗号分隔, 来自 md_material.brand); 空表示不限制。 */
    @Column(name = "allowed_brands", length = 512)
    private String allowedBrands;

    /** 允许的材质(逗号分隔, 来自 md_material.material); 空表示不限制。 */
    @Column(name = "allowed_materials", length = 512)
    private String allowedMaterials;

    /** 允许品牌不匹配时的处理方式: OFF关闭校验/WARN仅提醒/BLOCK阻断; 空表示清单为空或按 WARN 处理。 */
    /** 取价基准: DELIVERY 送货日期当天 / ORDER 报单日期之后第 N 个交易日 / MANUAL 每次手工选择。 */
    @Column(name = "price_date_basis", nullable = false, length = 16)
    private String priceDateBasis;

    /** 报单日基准的偏移交易日数(0..30), 仅 ORDER 有意义。 */
    @Column(name = "price_date_offset_days", nullable = false)
    private Integer priceDateOffsetDays;

    /**
     * 基准日(送货日/报单日)休市时的位移方向: PREV 往前第 X 个交易日 / NEXT 延后第 X 个交易日。
     *
     * <p>与 {@link #priceClosedSettleDays} 一起构成休市结算的唯一口径; MANUAL 基准不参与。
     * 旧列 {@code price_closed_day_prev_settle} 自 V195 起不再映射到实体, 并已由 V207 删除。</p>
     */
    @Column(name = "price_closed_settle_direction", nullable = false, length = 8)
    private String priceClosedSettleDirection;

    /** 基准日休市位移的交易日数(0..30): 0 仅 ORDER+NEXT 有效(不做方向位移, 直接向后顺延取价)。 */
    @Column(name = "price_closed_settle_days", nullable = false)
    private Integer priceClosedSettleDays;

    /** 时段策略: AUTO 当日最新 / FIXED 固定 / MANUAL 每次手工选择。 */
    @Column(name = "price_period_policy", nullable = false, length = 16)
    private String pricePeriodPolicy;

    /** 固定取价时段(上午/中午/下午), 仅 FIXED 时有值。 */
    @Column(name = "price_period_fixed", length = 8)
    private String pricePeriodFixed;

    @Column(name = "allowed_brand_severity", length = 16)
    private String allowedBrandSeverity;

    /** 允许材质不匹配时的处理方式: OFF关闭校验/WARN仅提醒/BLOCK阻断; 空表示清单为空或按 WARN 处理。 */
    @Column(name = "allowed_material_severity", length = 16)
    private String allowedMaterialSeverity;

    /*
     * 以下 5 列是「报单比价配置」归并进项目资料后的唯一配置点（V220）。
     *
     * 归并前这些值分散在 mk_quote_project_config（标量）与 mk_quote_project_brand（品牌/品种）里，
     * 与项目资料的 allowed_brands 是两份互相打架的事实。现在比价配置只保留「每品牌运费」，
     * 读接口按这里投影、写接口只写运费。
     */

    /** 参与比价的商品键(逗号分隔: 类别|材质|规格|长度); 空表示全部商品可选。 */
    @Column(name = "quote_products")
    private String quoteProducts;

    /**
     * 品牌 → 启用品种(JSONB)。实体上保存原始 JSON 文本，靠 {@code ColumnTransformer} 做
     * {@code text ↔ jsonb} 转换；文本 ↔ Map 的解析统一走
     * {@link com.leo.erp.master.project.domain.QuoteBrandCategories}。
     */
    @Column(name = "quote_brand_categories", columnDefinition = "JSONB")
    @org.hibernate.annotations.ColumnTransformer(
            read = "quote_brand_categories::text",
            write = "?::jsonb")
    private String quoteBrandCategories;

    /** 12米定尺加价(元/吨); 空表示未配置, 取价时回退定尺主数据默认值。 */
    @Column(name = "quote_length_premium", precision = 12, scale = 2)
    private BigDecimal quoteLengthPremium;

    /** 是否允许 HRB400 商品按 HRB400E 价格代取。 */
    @Column(name = "quote_hrb400e_fallback", nullable = false)
    private boolean quoteHrb400eFallback = false;

    /** 指定品牌(逗号分隔, 仅在报单比价里展示徽标, 不参与校验)。 */
    @Column(name = "quote_designated_brands", length = 512)
    private String quoteDesignatedBrands;

    @Column(name = "remark", length = 255)
    private String remark;
}
