package com.leo.erp.market.supplierprice.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.market.supplierprice.domain.entity.SupplierBasePriceRule;
import com.leo.erp.market.supplierprice.repository.SupplierBasePriceRuleRepository;
import com.leo.erp.market.supplierprice.web.dto.SupplierBasePriceRuleRequest;
import com.leo.erp.market.supplierprice.web.dto.SupplierBasePriceRuleResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 供应商品牌基价加价规则的读写(独立事务)。
 *
 * <p><b>整体替换</b>: 保存时把该 {@code (supplier_id, brand_name)} 下的未删除行全部软删, 再按请求插入新行;
 * 两步在同一事务内, 且软删后先 {@code flush()} 再插入 —— Hibernate 的 flush 顺序是「INSERT 先于 UPDATE」,
 * 若不在中间显式 flush, 新行的唯一键会与尚未落库的旧行在未删除部分索引上相撞。</p>
 *
 * <p><b>清空语义</b>: {@code items} 为空表示把这套规则整体替换为空(撤销配错的规则)。此时没有新行可承载
 * 版本号, 因此软删旧行时把它们的 {@code version} 一并写成新版本 —— 版本号取该业务键<b>全部行(含软删留痕行)
 * 的最大值</b>。未删除行为非空时, 这个最大值就等于「未删除行的最大值」(最新一次写入的行一定是未删除的),
 * 所以对外口径不变; 只有清空后才靠留痕行保证「版本仍 +1」, 前端下一次前置条件才不会永久 412。
 * 重复清空空规则是幂等 no-op(没有可承载新版本的行, 版本不变)。</p>
 *
 * <p><b>并发</b>: 同一 {@code (supplierId, brandName)} 的写入由
 * {@link SupplierBasePriceRuleService} 在事务外按业务键串行化, 本类只负责事务内的版本前置条件校验。</p>
 */
@Service
public class SupplierBasePriceRuleStore {

    private final SupplierBasePriceRuleRepository repository;
    private final SnowflakeIdGenerator snowflakeIdGenerator;

    public SupplierBasePriceRuleStore(SupplierBasePriceRuleRepository repository,
                                      SnowflakeIdGenerator snowflakeIdGenerator) {
        this.repository = repository;
        this.snowflakeIdGenerator = snowflakeIdGenerator;
    }

    /** 读取某套规则; 无行时返回版本 0、空 items、base 为 null(而不是 404)。 */
    @Transactional(readOnly = true)
    public SupplierBasePriceRuleResponse find(Long supplierId, String brandName) {
        return toResponse(supplierId, brandName,
                loadCurrent(supplierId, brandName), latestVersion(supplierId, brandName));
    }

    /** 整组替换某套规则; 版本前置条件不满足抛 412。 */
    @Transactional
    public SupplierBasePriceRuleResponse save(SupplierBasePriceRuleRequest request, Long expectedVersion) {
        List<SupplierBasePriceRule> existing = loadCurrent(request.supplierId(), request.brandName());
        long currentVersion = latestVersion(request.supplierId(), request.brandName());
        long nextVersion = nextVersion(currentVersion, expectedVersion);
        if (request.items().isEmpty() && existing.isEmpty()) {
            // 已经是空规则: 没有行可承载新版本, 幂等返回当前版本, 不制造无法持久化的 +1
            return toResponse(request.supplierId(), request.brandName(), List.of(), currentVersion);
        }

        retire(existing, nextVersion);
        insertNew(request, nextVersion);

        // 同事务内回读: PUT 响应与 GET 共用同一排序与基价判定, 不允许两处各算一遍
        return toResponse(request.supplierId(), request.brandName(),
                loadCurrent(request.supplierId(), request.brandName()), nextVersion);
    }

    private List<SupplierBasePriceRule> loadCurrent(Long supplierId, String brandName) {
        return repository
                .findBySupplierIdAndBrandNameAndDeletedFlagFalseOrderByMaterialAscSpecAscLengthAsc(
                        supplierId, brandName);
    }

    /** 该业务键下全部行(含软删留痕行)的最大版本号; 从未写过时为 0。 */
    private long latestVersion(Long supplierId, String brandName) {
        Long latest = repository.findLatestVersion(supplierId, brandName);
        return latest == null ? 0L : latest;
    }

    /**
     * 退役旧行: 软删并写入本次的新版本号。
     *
     * <p>旧行也要写新版本, 是为了让「全部行最大版本」在清空后仍能读出本次 +1 的结果(见类注释)。</p>
     */
    private void retire(List<SupplierBasePriceRule> existing, long version) {
        if (existing.isEmpty()) {
            return;
        }
        existing.forEach(rule -> {
            rule.setDeletedFlag(true);
            rule.setVersion(version);
        });
        repository.saveAll(existing);
        repository.flush();
    }

    private void insertNew(SupplierBasePriceRuleRequest request, long version) {
        List<SupplierBasePriceRule> next = new ArrayList<>(request.items().size());
        SupplierBasePriceRuleRequest.ProductKey base = request.base();
        for (SupplierBasePriceRuleRequest.Item input : request.items()) {
            SupplierBasePriceRule rule = new SupplierBasePriceRule();
            rule.setId(snowflakeIdGenerator.nextId());
            rule.setSupplierId(request.supplierId());
            rule.setBrandName(request.brandName());
            rule.setMaterial(input.material());
            rule.setSpec(input.spec());
            rule.setLength(input.length());
            rule.setDelta(input.delta());
            rule.setBase(base != null
                    && base.material().equals(input.material())
                    && base.spec().equals(input.spec())
                    && base.length().equals(input.length()));
            rule.setVersion(version);
            next.add(rule);
        }
        repository.saveAll(next);
        repository.flush();
    }

    /**
     * 校验资源版本前置条件并返回下一次写入的版本号。
     *
     * <p>不匹配抛 {@link ErrorCode#PRECONDITION_FAILED}(412): 版本前置条件的语义是「这份规则已被他人
     * 替换, 请刷新后重试」, 与 409 的通用并发冲突文案不同, 前端据此提示刷新而不是重试。</p>
     *
     * <p>{@code expectedVersion == null} 只可能出现在配置关闭强制前置条件时, 此时以当前版本继续 +1。</p>
     */
    private long nextVersion(long currentVersion, Long expectedVersion) {
        if (expectedVersion == null) {
            return currentVersion + 1L;
        }
        if (expectedVersion != currentVersion) {
            throw new BusinessException(ErrorCode.PRECONDITION_FAILED,
                    "该供应商品牌基价规则已被他人修改，请刷新后重试");
        }
        return expectedVersion + 1L;
    }

    private SupplierBasePriceRuleResponse toResponse(Long supplierId, String brandName,
                                                     List<SupplierBasePriceRule> rows, long version) {
        SupplierBasePriceRule baseRow = rows.stream()
                .filter(SupplierBasePriceRule::isBase)
                .findFirst()
                .orElse(null);
        SupplierBasePriceRuleResponse.ProductKey base = baseRow == null
                ? null
                : new SupplierBasePriceRuleResponse.ProductKey(
                        baseRow.getMaterial(), baseRow.getSpec(), baseRow.getLength());
        List<SupplierBasePriceRuleResponse.Item> items = rows.stream()
                .map(row -> new SupplierBasePriceRuleResponse.Item(
                        row.getMaterial(), row.getSpec(), row.getLength(), row.getDelta()))
                .toList();
        return new SupplierBasePriceRuleResponse(supplierId, brandName, base, items, version);
    }
}
