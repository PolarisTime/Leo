package com.leo.erp.market.domain.entity;

import com.leo.erp.common.persistence.AbstractAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "mk_steel_article")
public class SteelArticle extends AbstractAuditableEntity {

    @Id
    private Long id;

    @Column(name = "article_url", nullable = false, unique = true, length = 255)
    private String articleUrl;

    @Column(name = "article_date", nullable = false)
    private LocalDate articleDate;

    @Column(name = "article_time", nullable = false, length = 8)
    private String articleTime;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "period", nullable = false, length = 8)
    private String period;

    @Column(name = "row_count", nullable = false)
    private Integer rowCount;

    @Column(name = "market", nullable = false, length = 16)
    private String market;

    @Column(name = "fetched_at", nullable = false)
    private LocalDateTime fetchedAt;
}
