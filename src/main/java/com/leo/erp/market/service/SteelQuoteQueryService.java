package com.leo.erp.market.service;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.persistence.Specs;
import com.leo.erp.market.domain.entity.SteelQuote;
import com.leo.erp.market.repository.SteelQuoteRepository;
import com.leo.erp.market.web.dto.SteelQuoteResponse;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 行查明细分页查询(筛选字段白名单校验, 排序白名单由 PageSortFieldCatalog 控制)。
 */
@Service
public class SteelQuoteQueryService {

    private final SteelQuoteRepository quoteRepository;

    public SteelQuoteQueryService(SteelQuoteRepository quoteRepository) {
        this.quoteRepository = quoteRepository;
    }

    @Transactional(readOnly = true)
    public Page<SteelQuoteResponse> page(PageQuery query, LocalDate quoteDate, String period, String breed,
                                         String spec, String material, String factory, String changeDirection) {
        Pageable pageable = query.toPageable("id");
        Specification<SteelQuote> specification = buildSpecification(quoteDate, period, breed, spec, material,
                factory, changeDirection);
        return quoteRepository.findAll(specification, pageable).map(SteelQuoteResponse::from);
    }

    private Specification<SteelQuote> buildSpecification(LocalDate quoteDate, String period, String breed,
                                                         String spec, String material, String factory,
                                                         String changeDirection) {
        return (root, criteriaQuery, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(Specs.notDeletedPredicate(root, builder));
            if (quoteDate != null) {
                predicates.add(builder.equal(root.get("quoteDate"), quoteDate));
            }
            requireEqual(predicates, builder, root, "period", period);
            requireEqual(predicates, builder, root, "breed", breed);
            requireEqual(predicates, builder, root, "spec", spec);
            requireEqual(predicates, builder, root, "material", material);
            requireEqual(predicates, builder, root, "factory", factory);
            if ("up".equalsIgnoreCase(changeDirection)) {
                predicates.add(builder.like(root.get("changeVal"), "+%"));
            } else if ("down".equalsIgnoreCase(changeDirection)) {
                predicates.add(builder.like(root.get("changeVal"), "-%"));
            }
            return builder.and(predicates.toArray(new Predicate[0]));
        };
    }

    private void requireEqual(List<Predicate> predicates, jakarta.persistence.criteria.CriteriaBuilder builder,
                              jakarta.persistence.criteria.Root<SteelQuote> root, String field, String value) {
        if (value != null && !value.isBlank()) {
            predicates.add(builder.equal(root.get(field), value));
        }
    }
}
