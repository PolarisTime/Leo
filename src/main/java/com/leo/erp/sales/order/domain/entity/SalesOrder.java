package com.leo.erp.sales.order.domain.entity;

import com.leo.erp.common.persistence.AbstractAuditableEntity;
import com.leo.erp.common.persistence.StatusAwareEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
@Table(name = "so_sales_order")
public class SalesOrder extends AbstractAuditableEntity implements StatusAwareEntity {

    @Id
    private Long id;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "owner_user_id")
    private Long ownerUserId;

    @Column(name = "order_no", nullable = false, unique = true, length = 64)
    private String orderNo;

    @Column(name = "purchase_inbound_no", length = 256)
    private String purchaseInboundNo;

    @Column(name = "purchase_order_no", length = 256)
    private String purchaseOrderNo;

    @Column(name = "customer_name", nullable = false, length = 128)
    private String customerName;

    @Column(name = "project_name", nullable = false, length = 200)
    private String projectName;

    @Column(name = "delivery_date", nullable = false)
    private LocalDate deliveryDate;

    /** 报单日期(客户报单/落单当天): 取价基准 ORDER(报单日之后第 N 个交易日)的锚点。 */
    @Column(name = "order_date", nullable = false)
    private LocalDate orderDate;

    @Column(name = "sales_name", nullable = false, length = 32)
    private String salesName;

    @Column(name = "total_weight", nullable = false, precision = 18, scale = 8)
    private BigDecimal totalWeight;

    @Column(name = "total_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "customer_code", length = 64)
    private String customerCode;

    @Column(name = "customer_id")
    private Long customerId;

    @Column(name = "project_id")
    private Long projectId;

    @Column(name = "settlement_company_id")
    private Long settlementCompanyId;

    @Column(name = "settlement_company_name", length = 128)
    private String settlementCompanyName;

    @Column(name = "remark", length = 255)
    private String remark;

    // ===== 交付核定价格规定快照(保证历史可读, 不随规则变更/删除失真) =====
    @Column(name = "price_rule_id")
    private Long priceRuleId;

    @Column(name = "price_rule_name", length = 64)
    private String priceRuleName;

    @Column(name = "price_float_mode", length = 8)
    private String priceFloatMode;

    @Column(name = "price_float_value", precision = 12, scale = 2)
    private BigDecimal priceFloatValue;

    /** 取价基准日(送货日期/报单日期/手工日期, 交付核定快照)。 */
    @Column(name = "price_target_date")
    private LocalDate priceTargetDate;

    /** 实际取价日(交易日历回退后, 交付核定快照)。 */
    @Column(name = "price_quote_date")
    private LocalDate priceQuoteDate;

    /** 实际取价时段(上午/中午/下午, 交付核定快照)。 */
    @Column(name = "price_quote_period", length = 8)
    private String priceQuotePeriod;

    /** 本次生效的取价基准(快照, 可能是单据级覆盖值)。 */
    @Column(name = "price_date_basis", length = 16)
    private String priceDateBasis;

    /** 本次生效的偏移交易日数(快照)。 */
    @Column(name = "price_date_offset_days")
    private Integer priceDateOffsetDays;

    /** 本次生效的「报单日休市按前一交易日结算」开关(快照)。 */
    @Column(name = "price_closed_settle_prev")
    private Boolean priceClosedSettlePrev;

    @OneToMany(mappedBy = "salesOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SalesOrderItem> items = new ArrayList<>();
}
