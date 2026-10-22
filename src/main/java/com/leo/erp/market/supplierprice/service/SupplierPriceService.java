package com.leo.erp.market.supplierprice.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.market.supplierprice.domain.entity.SupplierPriceItem;
import com.leo.erp.market.supplierprice.domain.entity.SupplierPriceList;
import com.leo.erp.market.supplierprice.repository.SupplierPriceListRepository;
import com.leo.erp.market.supplierprice.web.dto.SupplierPriceGridResponse;
import com.leo.erp.market.supplierprice.web.dto.SupplierPriceListRequests;
import com.leo.erp.master.supplier.domain.entity.Supplier;
import com.leo.erp.master.supplier.repository.SupplierRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 供应商价格表（比价一手价）服务。
 *
 * <p><b>界面形态</b>：一个品牌一张表，规格做行、供应商做列。存储上是「(品牌, 供应商) 一条表头 +
 * 该供应商的规格条目」，把同一品牌下的表头并排就是列。</p>
 *
 * <p><b>不保留历史版本</b>（业务确认：旧价格表不能按旧价开货，留着只会误用）：
 * 改价直接覆盖条目，一张表头在唯一索引
 * {@code uk_supplier_price_list_supplier_brand} 下每个供应商只能有一条。</p>
 */
@Service
public class SupplierPriceService {

    /** 报价状态枚举（与库里的 CHECK 约束一致）。 */
    public static final String STATUS_NORMAL = "NORMAL";
    private static final String STATUS_OUT_OF_STOCK = "OUT_OF_STOCK";
    private static final List<String> VALID_STATUSES = List.of(
            STATUS_NORMAL, "PENDING", "BUNDLED", "NEGOTIABLE", STATUS_OUT_OF_STOCK);

    private final SupplierPriceListRepository repository;
    private final SupplierRepository supplierRepository;
    private final SnowflakeIdGenerator snowflakeIdGenerator;

    public SupplierPriceService(SupplierPriceListRepository repository,
                                SupplierRepository supplierRepository,
                                SnowflakeIdGenerator snowflakeIdGenerator) {
        this.repository = repository;
        this.supplierRepository = supplierRepository;
        this.snowflakeIdGenerator = snowflakeIdGenerator;
    }

    /**
     * 某品牌的整张价格表（列 + 行）。
     *
     * <p>行只包含**至少有一家报价**的商品：界面要「规格列满」时应由前端叠加商品资料全量规格，
     * 后端不重复维护一份规格主数据（那会变成同一口径的第二个实现）。</p>
     */
    @Transactional(readOnly = true)
    public SupplierPriceGridResponse grid(String brandName) {
        List<SupplierPriceList> lists =
                repository.findByBrandNameAndDeletedFlagFalseOrderBySupplierNameAsc(brandName);
        List<SupplierPriceGridResponse.Column> columns = lists.stream()
                .map(this::toColumn)
                .toList();
        return new SupplierPriceGridResponse(brandName, columns, toRows(lists));
    }

    /**
     * 在品牌表里增加一列（接入一家供应商）。
     *
     * <p>重复接入同一供应商返回 409：库里有唯一索引兜底，这里先探测是为了给出可读文案而不是
     * 让用户看到约束冲突堆栈。</p>
     */
    @Transactional
    public SupplierPriceGridResponse.Column addColumn(SupplierPriceListRequests.AddColumn request) {
        Supplier supplier = supplierRepository.findById(request.supplierId())
                .filter(item -> !item.isDeletedFlag())
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.NOT_FOUND, "供应商不存在或已删除: " + request.supplierId()));
        repository.findBySupplierIdAndBrandNameAndDeletedFlagFalse(
                        request.supplierId(), request.brandName())
                .ifPresent(existing -> {
                    throw new BusinessException(ErrorCode.CONCURRENT_MODIFICATION,
                            "该供应商在此品牌下已有价格表，请直接编辑");
                });

        LocalDate quotedOn = request.quotedOn() == null ? LocalDate.now() : request.quotedOn();
        SupplierPriceList list = new SupplierPriceList();
        list.setId(snowflakeIdGenerator.nextId());
        list.setSupplierId(supplier.getId());
        list.setSupplierName(supplier.getSupplierName());
        list.setBrandName(request.brandName());
        // 兼容列（语义作废，仅满足 NOT NULL）
        list.setReleasedAt(LocalDateTime.now());
        list.setEffectiveFrom(quotedOn);
        list.setStatus("ACTIVE");
        list.setQuotedOn(quotedOn);
        list.setWarehouse(request.warehouse());
        list.setRemark(request.remark());
        list.setVersion(0L);
        repository.save(list);
        return toColumn(list);
    }

    /** 去掉一列：软删除表头，条目随聚合根一起不再可见（不物理删除，保留审计痕迹）。 */
    @Transactional
    public void removeColumn(Long listId) {
        SupplierPriceList list = requireList(listId);
        list.setDeletedFlag(true);
        repository.save(list);
    }

    /**
     * 保存某列的全部格子（整组替换）。
     *
     * <p>为什么整组替换而不是逐格 PATCH：界面一次提交的就是这一列当前的完整内容，
     * 逐格对比会引入「哪一格被删了」的推断；整组替换语义确定，且天然幂等。</p>
     *
     * <p>{@code price} 为 {@code null} 的条目一律丢弃（不报价不落条目），避免留下 price=null
     * 的幽灵行；清空格子因此能真正落库。</p>
     */
    @Transactional
    public SupplierPriceGridResponse.Column saveItems(Long listId,
                                                      SupplierPriceListRequests.SaveItems request) {
        SupplierPriceList list = requireList(listId);
        if (!Objects.equals(list.getVersion(), request.expectedVersion())) {
            throw new BusinessException(ErrorCode.CONCURRENT_MODIFICATION,
                    "该价格表已被他人修改，请刷新后重试");
        }
        List<SupplierPriceItem> next = new ArrayList<>();
        int sortOrder = 0;
        for (SupplierPriceListRequests.Item input : request.items()) {
            if (input.price() == null) {
                continue;
            }
            SupplierPriceItem item = new SupplierPriceItem();
            item.setId(snowflakeIdGenerator.nextId());
            item.setList(list);
            item.setCategory(input.category());
            item.setMaterial(input.material());
            item.setSpec(input.spec());
            item.setLength(input.length() == null ? "" : input.length());
            item.setPrice(input.price());
            item.setPriceStatus(normalizeStatus(input.priceStatus()));
            item.setRemark(input.remark());
            item.setSortOrder(sortOrder++);
            next.add(item);
        }
        list.getItems().clear();
        list.getItems().addAll(next);
        // 版本号由 JPA @Version 递增，此处不手动改：手改会与 JPA 的自增叠加导致跳号
        // 报价日期可由前端随保存一起更新（默认沿用原值）
        repository.save(list);
        return toColumn(list);
    }

    /**
     * 现货价取价：某品牌某商品的**最低报价**（排除无货与未报价）。
     *
     * <p>业务确认：默认取最低价，界面上还可以用下拉改选其它供应商。因此这里返回最低价这一条，
     * 供报单比价带出「价格 + 供应商 + 来源价格表」。</p>
     *
     * <p>同价时按供应商名排序取第一个，保证结果稳定（不随查询顺序漂移）。</p>
     */
    @Transactional(readOnly = true)
    public Optional<LowestQuote> findLowestQuote(String brandName,
                                                 String category,
                                                 String material,
                                                 Integer spec,
                                                 String length) {
        return repository.findQuotedItemsByProduct(brandName, category, material, spec, length).stream()
                .map(item -> new LowestQuote(
                        item.getList().getId(),
                        item.getList().getSupplierId(),
                        item.getList().getSupplierName(),
                        item.getPrice(),
                        item.getList().getQuotedOn()))
                .min(Comparator.comparing(LowestQuote::price)
                        .thenComparing(LowestQuote::supplierName,
                                Comparator.nullsLast(Comparator.naturalOrder())));
    }

    /** 某品牌某商品的最低报价。 */
    public record LowestQuote(
            Long priceListId,
            Long supplierId,
            String supplierName,
            BigDecimal price,
            LocalDate quotedOn) {
    }

    private SupplierPriceList requireList(Long listId) {
        return repository.findByIdAndDeletedFlagFalse(listId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.NOT_FOUND, "价格表不存在或已删除: " + listId));
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return STATUS_NORMAL;
        }
        String upper = status.trim().toUpperCase();
        if (!VALID_STATUSES.contains(upper)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "未知的报价状态: " + status);
        }
        return upper;
    }

    private SupplierPriceGridResponse.Column toColumn(SupplierPriceList list) {
        return new SupplierPriceGridResponse.Column(
                list.getId(),
                list.getSupplierId(),
                list.getSupplierName(),
                list.getQuotedOn(),
                list.getUpdatedAt(),
                list.getWarehouse());
    }

    /**
     * 把各列的条目按商品聚成行。
     *
     * <p>用 {@link LinkedHashMap} 保序：行顺序按首次出现的顺序稳定下来，界面不会因为并发保存
     * 而每次刷新都换行序。</p>
     */
    private List<SupplierPriceGridResponse.Row> toRows(List<SupplierPriceList> lists) {
        Map<String, SupplierPriceGridResponse.Row> rows = new LinkedHashMap<>();
        for (SupplierPriceList list : lists) {
            Long columnId = list.getId();
            for (SupplierPriceItem item : list.getItems()) {
                String key = item.getCategory() + "|" + item.getMaterial() + "|"
                        + item.getSpec() + "|" + item.getLength();
                SupplierPriceGridResponse.Row row = rows.computeIfAbsent(key, ignored ->
                        new SupplierPriceGridResponse.Row(
                                item.getCategory(), item.getMaterial(), item.getSpec(),
                                item.getLength(), new LinkedHashMap<>(), new LinkedHashMap<>(),
                                new LinkedHashMap<>()));
                if (item.getPrice() != null) {
                    row.prices().put(columnId, item.getPrice());
                }
                row.priceStatuses().put(columnId, item.getPriceStatus());
                if (item.getRemark() != null) {
                    row.remarks().put(columnId, item.getRemark());
                }
            }
        }
        return new ArrayList<>(rows.values());
    }
}
