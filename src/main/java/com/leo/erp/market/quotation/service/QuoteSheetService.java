package com.leo.erp.market.quotation.service;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.market.quotation.domain.entity.QuoteSheet;
import com.leo.erp.market.quotation.domain.entity.QuoteSheetBrand;
import com.leo.erp.market.quotation.domain.entity.QuoteSheetItem;
import com.leo.erp.market.quotation.domain.entity.QuoteSheetItemPrice;
import com.leo.erp.market.quotation.repository.QuoteSheetRepository;
import com.leo.erp.market.quotation.web.dto.QuoteSheetRequest;
import com.leo.erp.market.quotation.web.dto.QuoteSheetResponse;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** 比价报价单: 单据头 + 品牌 + 商品行 + 行×品牌现货价。 */
@Service
public class QuoteSheetService {

    private static final BigDecimal DEFAULT_LENGTH_PREMIUM = new BigDecimal("30");
    private static final String DEFAULT_STATUS = "报价";

    private final QuoteSheetRepository repository;
    private final SnowflakeIdGenerator snowflakeIdGenerator;

    public QuoteSheetService(QuoteSheetRepository repository, SnowflakeIdGenerator snowflakeIdGenerator) {
        this.repository = repository;
        this.snowflakeIdGenerator = snowflakeIdGenerator;
    }

    @Transactional
    public QuoteSheetResponse create(QuoteSheetRequest request) {
        validate(request);
        QuoteSheet entity = new QuoteSheet();
        long id = snowflakeIdGenerator.nextId();
        entity.setId(id);
        entity.setSheetNo(String.valueOf(id));
        apply(entity, request);
        return toResponse(repository.saveAndFlush(entity));
    }

    @Transactional
    public QuoteSheetResponse update(Long id, QuoteSheetRequest request) {
        validate(request);
        QuoteSheet entity = requireSheet(id);
        apply(entity, request);
        return toResponse(repository.saveAndFlush(entity));
    }

    @Transactional(readOnly = true)
    public QuoteSheetResponse detail(Long id) {
        return toResponse(requireSheet(id));
    }

    @Transactional(readOnly = true)
    public Page<QuoteSheetResponse> page(PageQuery query, LocalDate orderDate, Long projectId, String keyword) {
        Pageable pageable = query.toPageable("id");
        Specification<QuoteSheet> specification = (root, criteriaQuery, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.isFalse(root.get("deletedFlag")));
            if (orderDate != null) {
                predicates.add(builder.equal(root.get("orderDate"), orderDate));
            }
            if (projectId != null) {
                predicates.add(builder.equal(root.get("projectId"), projectId));
            }
            if (keyword != null && !keyword.isBlank()) {
                String like = "%" + keyword.trim() + "%";
                predicates.add(builder.or(
                        builder.like(root.get("name"), like),
                        builder.like(root.get("sheetNo"), like),
                        builder.like(root.get("projectName"), like)));
            }
            return builder.and(predicates.toArray(new Predicate[0]));
        };
        return repository.findAll(specification, pageable).map(this::toResponse);
    }

    @Transactional
    public void delete(Long id) {
        QuoteSheet entity = requireSheet(id);
        entity.setDeletedFlag(true);
        repository.save(entity);
    }

    private QuoteSheet requireSheet(Long id) {
        return repository.findByIdAndDeletedFlagFalse(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "报价单不存在"));
    }

    private void validate(QuoteSheetRequest request) {
        if (request.brands() == null || request.brands().isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "至少需要一个品牌");
        }
        if (request.items() == null || request.items().isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "至少需要一行商品");
        }
        Set<String> brandNames = new LinkedHashSet<>();
        for (QuoteSheetRequest.BrandRequest brand : request.brands()) {
            if (!brandNames.add(brand.brandName())) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "品牌重复: " + brand.brandName());
            }
        }
        Set<Integer> lineNos = new HashSet<>();
        int lineNo = 0;
        for (QuoteSheetRequest.ItemRequest item : request.items()) {
            lineNo += 1;
            if (!lineNos.add(lineNo)) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "商品行重复");
            }
            if (item.prices() != null) {
                for (QuoteSheetRequest.ItemPriceRequest price : item.prices()) {
                    if (!brandNames.contains(price.brandName())) {
                        throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                                "现货价品牌不在品牌列表中: " + price.brandName());
                    }
                }
            }
        }
    }

    private void apply(QuoteSheet entity, QuoteSheetRequest request) {
        entity.setName(request.name().trim());
        entity.setProjectId(request.projectId());
        entity.setProjectName(request.projectName());
        entity.setOrderDate(request.orderDate());
        entity.setRefDate(request.refDate());
        entity.setRefPeriod(request.refPeriod());
        entity.setLengthPremium(request.lengthPremium() == null ? DEFAULT_LENGTH_PREMIUM : request.lengthPremium());
        entity.setLocked(Boolean.TRUE.equals(request.locked()));
        entity.setStatus(request.status() == null || request.status().isBlank() ? DEFAULT_STATUS : request.status());
        entity.setRemark(request.remark());
        replaceBrands(entity, request.brands());
        replaceItems(entity, request.items());
    }

    private void replaceBrands(QuoteSheet entity, List<QuoteSheetRequest.BrandRequest> requests) {
        entity.getBrands().clear();
        int index = 0;
        for (QuoteSheetRequest.BrandRequest request : requests) {
            QuoteSheetBrand brand = new QuoteSheetBrand();
            brand.setId(snowflakeIdGenerator.nextId());
            brand.setSheet(entity);
            brand.setBrandName(request.brandName().trim());
            brand.setFreight(request.freight() == null ? BigDecimal.ZERO : request.freight());
            brand.setSortOrder(request.sortOrder() == null ? index : request.sortOrder());
            entity.getBrands().add(brand);
            index += 1;
        }
    }

    private void replaceItems(QuoteSheet entity, List<QuoteSheetRequest.ItemRequest> requests) {
        entity.getItems().clear();
        int lineNo = 0;
        for (QuoteSheetRequest.ItemRequest request : requests) {
            lineNo += 1;
            QuoteSheetItem item = new QuoteSheetItem();
            item.setId(snowflakeIdGenerator.nextId());
            item.setSheet(entity);
            item.setLineNo(lineNo);
            item.setCategory(request.category());
            item.setMaterial(request.material());
            item.setSpec(request.spec());
            item.setLength(request.length());
            item.setTon(request.ton());
            if (request.prices() != null) {
                for (QuoteSheetRequest.ItemPriceRequest priceRequest : request.prices()) {
                    QuoteSheetItemPrice price = new QuoteSheetItemPrice();
                    price.setId(snowflakeIdGenerator.nextId());
                    price.setItem(item);
                    price.setBrandName(priceRequest.brandName());
                    price.setSpotPrice(priceRequest.spotPrice());
                    item.getPrices().add(price);
                }
            }
            entity.getItems().add(item);
        }
    }

    private QuoteSheetResponse toResponse(QuoteSheet entity) {
        List<QuoteSheetResponse.BrandResponse> brands = entity.getBrands().stream()
                .map(brand -> new QuoteSheetResponse.BrandResponse(
                        brand.getId(), brand.getBrandName(), brand.getFreight(), brand.getSortOrder()))
                .toList();
        List<QuoteSheetResponse.ItemResponse> items = entity.getItems().stream()
                .map(item -> new QuoteSheetResponse.ItemResponse(
                        item.getId(), item.getLineNo(), item.getCategory(), item.getMaterial(), item.getSpec(),
                        item.getLength(), item.getTon(),
                        item.getPrices().stream()
                                .map(price -> new QuoteSheetResponse.ItemPriceResponse(
                                        price.getId(), price.getBrandName(), price.getSpotPrice()))
                                .toList()))
                .toList();
        return new QuoteSheetResponse(entity.getId(), entity.getSheetNo(), entity.getName(), entity.getProjectId(),
                entity.getProjectName(), entity.getOrderDate(), entity.getRefDate(), entity.getRefPeriod(),
                entity.getLengthPremium(), entity.isLocked(), entity.getStatus(), entity.getRemark(),
                brands, items, entity.getCreatedAt(), entity.getUpdatedAt());
    }
}
