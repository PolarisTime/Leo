package com.leo.erp.market.quotation.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 比价项目参与品牌：V220 起只承载运费（品牌清单以项目资料 {@code allowed_brands} 为准）。
 *
 * <p>{@code categories} 列保留但不再读写——启用品种已归并到
 * {@code md_project.quote_brand_categories}，读接口按品牌名从项目资料取。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "mk_quote_project_brand")
public class QuoteProjectBrand {

    @Id
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "config_id", nullable = false)
    private QuoteProjectConfig config;

    @Column(name = "brand_name", nullable = false, length = 64)
    private String brandName;

    @Column(name = "freight", nullable = false, precision = 10, scale = 2)
    private BigDecimal freight;

    /** 启用品种列表(逗号分隔); 空表示全部启用。 */
    @Column(name = "categories")
    private String categories;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;
}
