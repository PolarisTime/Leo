package com.leo.erp.market.supplierprice.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.KeyedLockRegistry;
import com.leo.erp.market.supplierprice.web.dto.SupplierBasePriceRuleRequest;
import com.leo.erp.market.supplierprice.web.dto.SupplierBasePriceRuleResponse;
import com.leo.erp.master.api.SupplierQuery;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;

/**
 * 供应商品牌基价加价规则服务: 供应商有效性校验 + 同一套规则的写入串行化。
 *
 * <p>规则按 {@code (supplierId, brandName)} 归组, 保存是整组替换。两个并发 PUT(或重复提交)若都通过版本
 * 前置条件, 会各自插入不同规格的行, 最终把两套规则合并成一套互不一致的数据; 因此在事务外按业务键串行化
 * (与比价项目配置 {@code QuoteProjectConfigService} 同一做法), 事务内再由
 * {@link SupplierBasePriceRuleStore} 校验版本。</p>
 */
@Service
public class SupplierBasePriceRuleService {

    private static final String KEY_SEPARATOR = "\u0000";

    private final SupplierBasePriceRuleStore store;
    /** 供应商只读端口: 跨模块只依赖 master.api, 不碰 master 的 entity/repository 内部。 */
    private final SupplierQuery supplierQuery;
    private final KeyedLockRegistry locks = new KeyedLockRegistry();

    public SupplierBasePriceRuleService(SupplierBasePriceRuleStore store, SupplierQuery supplierQuery) {
        this.store = store;
        this.supplierQuery = supplierQuery;
    }

    /** 读取某套规则; 无规则时返回版本 0 的空结构。 */
    public SupplierBasePriceRuleResponse find(Long supplierId, String brandName) {
        return store.find(supplierId, brandName);
    }

    /** 整组替换某套规则; {@code expectedVersion} 为空表示不做版本校验(仅在兼容配置下出现)。 */
    public SupplierBasePriceRuleResponse save(SupplierBasePriceRuleRequest request, Long expectedVersion) {
        validateRule(request);
        supplierQuery.findActiveNormalById(request.supplierId())
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.NOT_FOUND,
                        "供应商不存在、已删除或已停用: " + request.supplierId()));
        return locks.execute(
                request.supplierId() + KEY_SEPARATOR + request.brandName(),
                () -> store.save(request, expectedVersion));
    }

    /**
     * 跨字段校验(bean validation 管不到的规则)。
     *
     * <p>① 同一套规则内 {@code (material, spec, length)} 不得重复 —— 否则整组替换后同一规格会有两个
     * delta, 前端无法判断用哪个; ② 基价规格必须出现在 items 中且 delta 必须为 0 —— 否则「基价 0 元 +
     * 全部加减」的口径无从成立。</p>
     */
    private void validateRule(SupplierBasePriceRuleRequest request) {
        Set<String> keys = new HashSet<>();
        for (SupplierBasePriceRuleRequest.Item item : request.items()) {
            String key = keyOf(item.material(), item.spec(), item.length());
            if (!keys.add(key)) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "规则项重复: " + describe(item.material(), item.spec(), item.length()));
            }
        }
        SupplierBasePriceRuleRequest.ProductKey base = request.base();
        if (base == null) {
            return;
        }
        String baseKey = keyOf(base.material(), base.spec(), base.length());
        SupplierBasePriceRuleRequest.Item baseItem = request.items().stream()
                .filter(item -> keyOf(item.material(), item.spec(), item.length()).equals(baseKey))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "基价规格必须出现在规则项中: " + describe(base.material(), base.spec(), base.length())));
        if (baseItem.delta().signum() != 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "基价行的加价必须为 0: " + describe(base.material(), base.spec(), base.length()));
        }
    }

    private String keyOf(String material, Integer spec, String length) {
        return material + KEY_SEPARATOR + spec + KEY_SEPARATOR + length;
    }

    private String describe(String material, Integer spec, String length) {
        return material + " " + spec + " " + length;
    }
}
