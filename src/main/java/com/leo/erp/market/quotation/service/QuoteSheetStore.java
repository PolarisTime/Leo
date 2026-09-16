package com.leo.erp.market.quotation.service;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.persistence.Specs;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.market.quotation.domain.entity.QuoteSheet;
import com.leo.erp.market.quotation.domain.entity.QuoteSheetBrand;
import com.leo.erp.market.quotation.domain.entity.QuoteSheetItem;
import com.leo.erp.market.quotation.domain.entity.QuoteSheetItemPrice;
import com.leo.erp.market.quotation.repository.QuoteSheetRepository;
import com.leo.erp.market.quotation.web.dto.QuoteSheetRequest;
import com.leo.erp.market.quotation.web.dto.QuoteSheetResponse;
import com.leo.erp.master.api.SupplierQuery;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 比价报价单读写(独立事务)。
 * <p>
 * 表头整体替换、表头单独更新与行级增删改均在此完成; 同单据并发写冲突由
 * {@link QuoteSheetService} 在进程内串行化并做有限重试。
 */
@Service
public class QuoteSheetStore {

    private static final BigDecimal DEFAULT_LENGTH_PREMIUM = new BigDecimal("30");
    private static final String DEFAULT_STATUS = "报价";

    private final QuoteSheetRepository repository;
    private final SnowflakeIdGenerator snowflakeIdGenerator;
    private final SupplierQuery supplierQuery;

    public QuoteSheetStore(QuoteSheetRepository repository,
                           SnowflakeIdGenerator snowflakeIdGenerator,
                           SupplierQuery supplierQuery) {
        this.repository = repository;
        this.snowflakeIdGenerator = snowflakeIdGenerator;
        this.supplierQuery = supplierQuery;
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

    /**
     * 更新报价单。
     * <ul>
     *   <li>请求未携带 brands/items(均为 null): 仅更新表头字段;</li>
     *   <li>否则保持整体替换语义(向后兼容)。</li>
     * </ul>
     */
    @Transactional
    public QuoteSheetResponse update(Long id, QuoteSheetRequest request, Long expectedVersion) {
        QuoteSheet entity = requireSheet(id);
        checkVersion(entity.getVersion(), expectedVersion);
        boolean headerOnly = request.brands() == null && request.items() == null;
        checkLockedRefChange(entity, request);
        if (headerOnly) {
            applyHeader(entity, request);
        } else {
            validate(request);
            apply(entity, request);
        }
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
            predicates.add(Specs.notDeletedPredicate(root, builder));
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

    /** 新增商品行: 追加到单据末尾, 返回新行(触发单据版本递增)。 */
    @Transactional
    public QuoteSheetResponse.ItemResponse addItem(Long sheetId, QuoteSheetRequest.ItemRequest request,
                                                   Long expectedVersion) {
        QuoteSheet sheet = requireSheet(sheetId);
        checkVersion(sheet.getVersion(), expectedVersion);
        int nextLineNo = sheet.getItems().stream()
                .map(QuoteSheetItem::getLineNo)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(0) + 1;
        Map<Long, String> supplierNames = resolveSupplierNames(List.of(request));
        QuoteSheetItem item = buildItem(sheet, request, nextLineNo, supplierNames);
        sheet.getItems().add(item);
        repository.saveAndFlush(sheet);
        return toItemResponse(item);
    }

    /** 整行替换商品字段、吨位与行×品牌现货价(触发单据版本递增)。 */
    @Transactional
    public QuoteSheetResponse.ItemResponse updateItem(Long sheetId, Long itemId,
                                                      QuoteSheetRequest.ItemRequest request,
                                                      Long expectedVersion) {
        QuoteSheet sheet = requireSheet(sheetId);
        checkVersion(sheet.getVersion(), expectedVersion);
        QuoteSheetItem item = requireItem(sheet, itemId);
        Map<Long, String> supplierNames = resolveSupplierNames(List.of(request));
        applyItem(item, request, supplierNames);
        repository.saveAndFlush(sheet);
        return toItemResponse(item);
    }

    /** 删除商品行(触发单据版本递增)。 */
    @Transactional
    public void deleteItem(Long sheetId, Long itemId, Long expectedVersion) {
        QuoteSheet sheet = requireSheet(sheetId);
        checkVersion(sheet.getVersion(), expectedVersion);
        QuoteSheetItem item = requireItem(sheet, itemId);
        sheet.getItems().remove(item);
        repository.saveAndFlush(sheet);
    }

    private QuoteSheet requireSheet(Long id) {
        return repository.findByIdAndDeletedFlagFalse(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "报价单不存在"));
    }

    private QuoteSheetItem requireItem(QuoteSheet sheet, Long itemId) {
        return sheet.getItems().stream()
                .filter(item -> itemId.equals(item.getId()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "商品行不存在"));
    }

    /** 乐观并发校验: expectedVersion 为空表示不做校验(兼容旧调用)。 */
    private void checkVersion(Long currentVersion, Long expectedVersion) {
        if (expectedVersion != null && !expectedVersion.equals(currentVersion)) {
            throw new BusinessException(ErrorCode.CONCURRENT_MODIFICATION, "数据已被他人修改，请刷新后重试");
        }
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
        for (QuoteSheetRequest.ItemRequest item : request.items()) {
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

    private void checkLockedRefChange(QuoteSheet entity, QuoteSheetRequest request) {
        if (entity.isLocked() && Boolean.TRUE.equals(request.locked())
                && request.refDate() != null && request.refPeriod() != null
                && (!entity.getRefDate().equals(request.refDate())
                    || !entity.getRefPeriod().equals(request.refPeriod()))) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "参照日期/时段已锁定, 请先解锁再修改");
        }
    }

    private void apply(QuoteSheet entity, QuoteSheetRequest request) {
        applyHeader(entity, request);
        replaceBrands(entity, request.brands());
        replaceItems(entity, request.items(), resolveSupplierNames(request.items()));
    }

    private void applyHeader(QuoteSheet entity, QuoteSheetRequest request) {
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
    }

    /** 批量解析现货价来源供应商名称, 任一不存在则拒绝。 */
    private Map<Long, String> resolveSupplierNames(List<QuoteSheetRequest.ItemRequest> items) {
        Set<Long> supplierIds = new LinkedHashSet<>();
        if (items != null) {
            for (QuoteSheetRequest.ItemRequest item : items) {
                if (item == null || item.prices() == null) {
                    continue;
                }
                for (QuoteSheetRequest.ItemPriceRequest price : item.prices()) {
                    if (price != null && price.supplierId() != null) {
                        supplierIds.add(price.supplierId());
                    }
                }
            }
        }
        Map<Long, String> names = new HashMap<>();
        for (Long supplierId : supplierIds) {
            names.put(supplierId, supplierQuery.findActiveById(supplierId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_ERROR,
                            "供应商不存在或已停用: " + supplierId))
                    .displayName());
        }
        return names;
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

    private void replaceItems(QuoteSheet entity, List<QuoteSheetRequest.ItemRequest> requests,
                              Map<Long, String> supplierNames) {
        entity.getItems().clear();
        int lineNo = 0;
        for (QuoteSheetRequest.ItemRequest request : requests) {
            lineNo += 1;
            entity.getItems().add(buildItem(entity, request, lineNo, supplierNames));
        }
    }

    private QuoteSheetItem buildItem(QuoteSheet sheet, QuoteSheetRequest.ItemRequest request, int lineNo,
                                     Map<Long, String> supplierNames) {
        QuoteSheetItem item = new QuoteSheetItem();
        item.setId(snowflakeIdGenerator.nextId());
        item.setSheet(sheet);
        item.setLineNo(lineNo);
        applyItem(item, request, supplierNames);
        return item;
    }

    private void applyItem(QuoteSheetItem item, QuoteSheetRequest.ItemRequest request,
                           Map<Long, String> supplierNames) {
        item.setCategory(request.category());
        item.setMaterial(request.material());
        item.setSpec(request.spec());
        item.setLength(request.length());
        item.setTon(request.ton());
        item.getPrices().clear();
        if (request.prices() != null) {
            for (QuoteSheetRequest.ItemPriceRequest priceRequest : request.prices()) {
                QuoteSheetItemPrice price = new QuoteSheetItemPrice();
                price.setId(snowflakeIdGenerator.nextId());
                price.setItem(item);
                price.setBrandName(priceRequest.brandName());
                price.setSpotPrice(priceRequest.spotPrice());
                price.setSupplierId(priceRequest.supplierId());
                price.setSupplierName(priceRequest.supplierId() == null
                        ? null : supplierNames.get(priceRequest.supplierId()));
                item.getPrices().add(price);
            }
        }
    }

    private QuoteSheetResponse toResponse(QuoteSheet entity) {
        List<QuoteSheetResponse.BrandResponse> brands = entity.getBrands().stream()
                .map(brand -> new QuoteSheetResponse.BrandResponse(
                        brand.getId(), brand.getBrandName(), brand.getFreight(), brand.getSortOrder()))
                .toList();
        List<QuoteSheetResponse.ItemResponse> items = entity.getItems().stream()
                .map(this::toItemResponse)
                .toList();
        return new QuoteSheetResponse(entity.getId(), entity.getSheetNo(), entity.getName(), entity.getProjectId(),
                entity.getProjectName(), entity.getOrderDate(), entity.getRefDate(), entity.getRefPeriod(),
                entity.getLengthPremium(), entity.isLocked(), entity.getStatus(), entity.getRemark(),
                brands, items, entity.getCreatedAt(), entity.getUpdatedAt(), entity.getVersion());
    }

    private QuoteSheetResponse.ItemResponse toItemResponse(QuoteSheetItem item) {
        return new QuoteSheetResponse.ItemResponse(
                item.getId(), item.getLineNo(), item.getCategory(), item.getMaterial(), item.getSpec(),
                item.getLength(), item.getTon(),
                item.getPrices().stream()
                        .map(price -> new QuoteSheetResponse.ItemPriceResponse(
                                price.getId(), price.getBrandName(), price.getSpotPrice(),
                                price.getSupplierId(), price.getSupplierName()))
                        .toList());
    }
}
