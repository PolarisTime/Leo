package com.leo.erp.market.quotation.domain.entity;

import com.leo.erp.common.persistence.AbstractAuditableEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
@Table(name = "mk_quote_sheet")
public class QuoteSheet extends AbstractAuditableEntity {

    @Id
    private Long id;

    @Column(name = "sheet_no", nullable = false, unique = true, length = 64)
    private String sheetNo;

    @Column(name = "name", nullable = false, length = 64)
    private String name;

    @Column(name = "project_id")
    private Long projectId;

    @Column(name = "project_name", length = 200)
    private String projectName;

    @Column(name = "order_date", nullable = false)
    private LocalDate orderDate;

    @Column(name = "ref_date", nullable = false)
    private LocalDate refDate;

    @Column(name = "ref_period", nullable = false, length = 32)
    private String refPeriod;

    @Column(name = "length_premium", nullable = false, precision = 10, scale = 2)
    private BigDecimal lengthPremium;

    @Column(name = "locked", nullable = false)
    private boolean locked = false;

    @Column(name = "status", nullable = false, length = 16)
    private String status = "报价";

    @Column(name = "remark", length = 255)
    private String remark;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @OneToMany(mappedBy = "sheet", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("sortOrder ASC")
    private List<QuoteSheetBrand> brands = new ArrayList<>();

    @OneToMany(mappedBy = "sheet", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("lineNo ASC")
    private List<QuoteSheetItem> items = new ArrayList<>();
}
