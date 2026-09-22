package com.leo.erp.master.project.domain.entity;

import com.leo.erp.common.persistence.AbstractAuditableEntity;
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
public class Project extends AbstractAuditableEntity {

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

    @Column(name = "status", nullable = false, length = 16)
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

    @Column(name = "remark", length = 255)
    private String remark;
}
