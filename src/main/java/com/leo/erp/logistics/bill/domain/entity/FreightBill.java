package com.leo.erp.logistics.bill.domain.entity;

import com.leo.erp.common.persistence.AbstractAuditableEntity;
import com.leo.erp.common.persistence.StatusAwareEntity;
import com.leo.erp.logistics.bill.domain.enums.FreightSettlementMode;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Getter
@Setter
@Entity
@Table(name = "lg_freight_bill")
public class FreightBill extends AbstractAuditableEntity implements StatusAwareEntity {

    @Id
    private Long id;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "bill_no", nullable = false, unique = true, length = 64)
    private String billNo;

    @Column(name = "carrier_id")
    private Long carrierId;

    @Column(name = "carrier_code", nullable = false, length = 64)
    private String carrierCode;

    @Column(name = "carrier_name", nullable = false, length = 128)
    private String carrierName;

    @Column(name = "settlement_company_id")
    private Long settlementCompanyId;

    @Column(name = "settlement_company_name", length = 128)
    private String settlementCompanyName;

    @Column(name = "vehicle_plate", length = 16)
    private String vehiclePlate;

    @Column(name = "vehicle_id")
    private Long vehicleId;

    @Column(name = "customer_name", length = 128)
    private String customerName;

    @Column(name = "project_name", length = 200)
    private String projectName;

    @Column(name = "bill_time", nullable = false)
    private LocalDate billTime;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "total_weight", nullable = false, precision = 18, scale = 8)
    private BigDecimal totalWeight;

    /**
     * 本次结算金额: {@link FreightSettlementMode#BY_TONNAGE} 时为 单价 × 吨位,
     * {@link FreightSettlementMode#FIXED} 时为固定运费金额。
     * <p>运费对账单、财务概览、导出等既有读取方都读本列, 因此它是"应付金额"的唯一落库字段。</p>
     */
    @Column(name = "total_freight", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalFreight;

    /**
     * 结算方式: 历史数据与新建默认都是 {@link FreightSettlementMode#BY_TONNAGE}。
     * <p>请求未携带该字段时保持原值(见 {@code FreightBillService#applySettlementTerms}),
     * 避免老客户端不带该字段编辑时把「固定运费」静默改回按吨位结算。</p>
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "settlement_mode", nullable = false, length = 16)
    private FreightSettlementMode settlementMode = FreightSettlementMode.BY_TONNAGE;

    /**
     * 固定运费结算金额: 仅 {@link FreightSettlementMode#FIXED} 时非空且大于 0, 并作为结算额写入
     * {@link #totalFreight}; BY_TONNAGE 时必须为空(不保留无意义的固定金额)。
     */
    @Column(name = "fixed_freight_amount", precision = 14, scale = 2)
    private BigDecimal fixedFreightAmount;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "remark", length = 255)
    private String remark;

    /** 明细行按 {@code line_no} 返回(拖拽重排后按提交数组下标重写)。 */
    @OneToMany(mappedBy = "freightBill", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo ASC, id ASC")
    private List<FreightBillItem> items = new ArrayList<>();

    @OneToMany(mappedBy = "freightBill", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<FreightBillSourceOrder> sourceOrders = new LinkedHashSet<>();
}
