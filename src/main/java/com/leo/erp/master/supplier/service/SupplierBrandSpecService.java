package com.leo.erp.master.supplier.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.ValidationMessages;
import com.leo.erp.master.supplier.domain.entity.SupplierBrand;
import com.leo.erp.master.supplier.domain.entity.SupplierBrandSpec;
import com.leo.erp.master.supplier.repository.SupplierBrandRepository;
import com.leo.erp.master.supplier.repository.SupplierBrandSpecRepository;
import com.leo.erp.master.supplier.repository.SupplierRepository;
import com.leo.erp.master.supplier.web.dto.SupplierBrandSpecRequest;
import com.leo.erp.master.supplier.web.dto.SupplierBrandSpecResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 供应商品牌规格配置服务: 维护「某供应商某品牌允许的 类别 + 材质 + 规格 + 定尺 组合」。
 *
 * <p><b>空集语义</b>: 某品牌没有任何未删除配置行 = 未配置 = 回落到「按商品资料筛出的该品牌全量组合」。
 * 后端不展开默认值, 也不写「已配置」标记行 —— 空数组就是清空, 由调用方(报价表)解释空集。</p>
 *
 * <p><b>整集替换</b>: 保存时把该供应商<b>全部品牌</b>的旧行软删, 再按请求写入新行, 同一事务内完成。
 * 旧行只置 {@code deleted_flag}, 保留审计痕迹; 部分唯一索引只约束未删除行, 所以之后可以再次写入同样
 * 的组合。<b>软删后必须先 {@code flush()} 再插入</b>: Hibernate 的 flush 顺序是 INSERT 先于 UPDATE,
 * 中间不 flush 会让新行与尚未落库的旧行在部分唯一索引上相撞(与
 * {@code SupplierBasePriceRuleStore} 同一处坑)。</p>
 *
 * <p><b>幂等</b>: 重复提交完全相同的组合时不软删也不重插, 直接返回当前配置 —— 既避免行 id 抖动, 也让
 * 「重复 PUT 无副作用」可被测试锁定。</p>
 *
 * <p><b>校验</b>: 全部在 service 内做并抛 {@link ErrorCode#VALIDATION_ERROR}(映射 422), 不依赖 bean
 * validation —— 请求体是顶层数组, Spring MVC 不会级联校验数组元素, 放 service 才能保证
 * 「不合法的元素一律 422 且一条都不落库」。</p>
 */
@Service
public class SupplierBrandSpecService {

    /** 单次提交的配置条数上限: 与仓库其它整集替换接口同一量级, 防止一次请求把子表写爆。 */
    private static final int MAX_ITEMS = 2000;
    private static final int BRAND_NAME_MAX_LENGTH = 64;
    private static final int CATEGORY_MAX_LENGTH = 32;
    private static final int MATERIAL_MAX_LENGTH = 32;
    private static final int LENGTH_MAX_LENGTH = 16;

    /** 组合键分隔符: 用 NUL 保证不会与任何字段内容冲突。 */
    private static final String KEY_SEPARATOR = "\u0000";

    /**
     * 响应唯一排序口径: 品牌 → 类别 → 材质 → 规格 → 定尺 升序。
     *
     * <p>GET 与 PUT 回读都走这一处, 不允许两处各排一遍(前端按数组顺序直接渲染 Transfer 右侧列表)。</p>
     */
    private static final Comparator<SupplierBrandSpecResponse> RESPONSE_ORDER =
            Comparator.comparing(SupplierBrandSpecResponse::brandName)
                    .thenComparing(SupplierBrandSpecResponse::category)
                    .thenComparing(SupplierBrandSpecResponse::material)
                    .thenComparing(SupplierBrandSpecResponse::spec)
                    .thenComparing(SupplierBrandSpecResponse::length);

    private final SupplierRepository supplierRepository;
    private final SupplierBrandRepository supplierBrandRepository;
    private final SupplierBrandSpecRepository supplierBrandSpecRepository;
    private final SnowflakeIdGenerator snowflakeIdGenerator;

    public SupplierBrandSpecService(SupplierRepository supplierRepository,
                                    SupplierBrandRepository supplierBrandRepository,
                                    SupplierBrandSpecRepository supplierBrandSpecRepository,
                                    SnowflakeIdGenerator snowflakeIdGenerator) {
        this.supplierRepository = supplierRepository;
        this.supplierBrandRepository = supplierBrandRepository;
        this.supplierBrandSpecRepository = supplierBrandSpecRepository;
        this.snowflakeIdGenerator = snowflakeIdGenerator;
    }

    /**
     * 读取该供应商全部(未删除)品牌的已配置组合; 未配置时返回空数组。
     *
     * <p>供应商不存在/已删除 → 404: 与 {@code GET /suppliers/{id}} 同一口径, 避免前端把"供应商不存在"
     * 误读成"这个供应商没有任何配置"。</p>
     */
    @Transactional(readOnly = true)
    public List<SupplierBrandSpecResponse> list(Long supplierId) {
        requireActiveSupplier(supplierId);
        Map<Long, String> brandNamesById = activeBrandNamesById(supplierId);
        return toResponses(loadActive(brandNamesById.keySet()), brandNamesById);
    }

    /**
     * 整集替换该供应商全部品牌的配置; 空数组表示清空(回落到按商品资料全量)。
     *
     * @return 保存后的完整配置(与 GET 同一结构与排序)
     */
    @Transactional
    public List<SupplierBrandSpecResponse> replace(Long supplierId, List<SupplierBrandSpecRequest> items) {
        requireActiveSupplier(supplierId);
        Map<Long, String> brandNamesById = activeBrandNamesById(supplierId);
        List<DesiredSpec> desired = normalize(items, brandNamesById);
        List<SupplierBrandSpec> current = loadActive(brandNamesById.keySet());
        if (isSameConfiguration(current, brandNamesById, desired)) {
            return toResponses(current, brandNamesById);
        }
        retire(current);
        insert(desired);
        // 同事务内回读: PUT 响应与 GET 共用同一排序与同一映射, 不允许两处各算一遍
        return toResponses(loadActive(brandNamesById.keySet()), brandNamesById);
    }

    private void requireActiveSupplier(Long supplierId) {
        supplierRepository.findByIdAndDeletedFlagFalse(supplierId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, ValidationMessages.SUPPLIER_NOT_FOUND));
    }

    /** 该供应商当前经营品牌的 id → 名称(名称升序, 仅未删除品牌)。 */
    private Map<Long, String> activeBrandNamesById(Long supplierId) {
        List<SupplierBrand> brands =
                supplierBrandRepository.findBySupplierIdAndDeletedFlagFalseOrderByBrandNameAsc(supplierId);
        Map<Long, String> brandNamesById = new LinkedHashMap<>(brands.size());
        for (SupplierBrand brand : brands) {
            brandNamesById.put(brand.getId(), brand.getBrandName());
        }
        return brandNamesById;
    }

    private List<SupplierBrandSpec> loadActive(Collection<Long> supplierBrandIds) {
        if (supplierBrandIds.isEmpty()) {
            return List.of();
        }
        return supplierBrandSpecRepository.findBySupplierBrandIdInAndDeletedFlagFalse(supplierBrandIds);
    }

    /**
     * 归一化并校验请求: trim、非空、长度、规格正整数、品牌必须在该供应商经营品牌内、组合不得重复。
     *
     * <p>任何一条不合法都抛 422 并且不落库(校验在写操作之前完成, 不做"边写边校验")。</p>
     */
    private List<DesiredSpec> normalize(List<SupplierBrandSpecRequest> items, Map<Long, String> brandNamesById) {
        if (items == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "规格配置不能为空; 清空配置请提交空数组 []");
        }
        if (items.size() > MAX_ITEMS) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "一次最多提交" + MAX_ITEMS + "条规格配置");
        }
        Map<String, Long> brandIdsByName = new HashMap<>(brandNamesById.size() * 2);
        brandNamesById.forEach((brandId, brandName) -> brandIdsByName.put(brandName, brandId));

        List<DesiredSpec> desired = new ArrayList<>(items.size());
        Set<String> keys = new LinkedHashSet<>();
        for (int index = 0; index < items.size(); index++) {
            String position = "第" + (index + 1) + "条规格配置";
            SupplierBrandSpecRequest item = items.get(index);
            if (item == null) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, position + "不能为空");
            }
            String brandName = requireText(item.brandName(), position + "的品牌不能为空",
                    BRAND_NAME_MAX_LENGTH, "品牌名称");
            Long brandId = brandIdsByName.get(brandName);
            if (brandId == null) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                        position + "的品牌不在该供应商的经营品牌内: " + brandName);
            }
            String category = requireText(item.category(), position + "的类别不能为空",
                    CATEGORY_MAX_LENGTH, "类别");
            String material = requireText(item.material(), position + "的材质不能为空",
                    MATERIAL_MAX_LENGTH, "材质");
            String length = requireText(item.length(), position + "的定尺不能为空",
                    LENGTH_MAX_LENGTH, "定尺");
            Integer spec = item.spec();
            if (spec == null || spec <= 0) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                        position + "的规格必须是正整数: " + spec);
            }
            DesiredSpec candidate = new DesiredSpec(brandId, brandName, category, material, spec, length);
            if (!keys.add(candidate.key())) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                        position + "与前面的配置重复: " + candidate.describe());
            }
            desired.add(candidate);
        }
        return desired;
    }

    /** trim 后必须非空且不超过列长度; 超长在此拦成 422, 不落库触发字段长度/唯一键的错误。 */
    private String requireText(String value, String blankMessage, int maxLength, String fieldLabel) {
        String normalized = value == null ? null : value.trim();
        if (normalized == null || normalized.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, blankMessage);
        }
        if (normalized.length() > maxLength) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    fieldLabel + "长度不能超过" + maxLength + "个字符");
        }
        return normalized;
    }

    /** 当前配置与请求是否完全一致(按组合键做集合比较, 顺序无关)。 */
    private boolean isSameConfiguration(List<SupplierBrandSpec> current,
                                        Map<Long, String> brandNamesById,
                                        List<DesiredSpec> desired) {
        Set<String> desiredKeys = new HashSet<>(desired.size() * 2);
        desired.forEach(spec -> desiredKeys.add(spec.key()));
        Set<String> currentKeys = new HashSet<>(current.size() * 2);
        for (SupplierBrandSpec row : current) {
            currentKeys.add(keyOf(brandNamesById.get(row.getSupplierBrandId()),
                    row.getCategory(), row.getMaterial(), row.getSpec(), row.getLength()));
        }
        return currentKeys.equals(desiredKeys);
    }

    /**
     * 退役旧行: 只置软删标记, 保留审计痕迹。
     *
     * <p>必须在插入新行之前 {@code flush()}: Hibernate flush 顺序为 INSERT 先于 UPDATE, 否则新行的
     * 组合会与仍是未删除状态的旧行在部分唯一索引上相撞。</p>
     */
    private void retire(List<SupplierBrandSpec> current) {
        if (current.isEmpty()) {
            return;
        }
        for (SupplierBrandSpec row : current) {
            row.setDeletedFlag(true);
        }
        supplierBrandSpecRepository.saveAll(current);
        supplierBrandSpecRepository.flush();
    }

    private void insert(List<DesiredSpec> desired) {
        if (desired.isEmpty()) {
            return;
        }
        List<SupplierBrandSpec> rows = new ArrayList<>(desired.size());
        for (DesiredSpec spec : desired) {
            SupplierBrandSpec row = new SupplierBrandSpec();
            row.setId(snowflakeIdGenerator.nextId());
            row.setSupplierBrandId(spec.brandId());
            row.setCategory(spec.category());
            row.setMaterial(spec.material());
            row.setSpec(spec.spec());
            row.setLength(spec.length());
            rows.add(row);
        }
        supplierBrandSpecRepository.saveAll(rows);
    }

    private List<SupplierBrandSpecResponse> toResponses(List<SupplierBrandSpec> rows,
                                                        Map<Long, String> brandNamesById) {
        return rows.stream()
                .filter(row -> brandNamesById.containsKey(row.getSupplierBrandId()))
                .map(row -> new SupplierBrandSpecResponse(
                        brandNamesById.get(row.getSupplierBrandId()),
                        row.getCategory(),
                        row.getMaterial(),
                        row.getSpec(),
                        row.getLength()))
                .sorted(RESPONSE_ORDER)
                .toList();
    }

    private String keyOf(String brandName, String category, String material, Integer spec, String length) {
        return brandName + KEY_SEPARATOR + category + KEY_SEPARATOR + material
                + KEY_SEPARATOR + spec + KEY_SEPARATOR + length;
    }

    /** 归一化后的一条请求: 品牌已解析成品牌行 id, 五个字段都已 trim 并校验。 */
    private record DesiredSpec(Long brandId,
                               String brandName,
                               String category,
                               String material,
                               Integer spec,
                               String length) {

        String key() {
            return brandName + KEY_SEPARATOR + category + KEY_SEPARATOR + material
                    + KEY_SEPARATOR + spec + KEY_SEPARATOR + length;
        }

        String describe() {
            return brandName + " " + category + " " + material + " " + spec + " " + length;
        }
    }
}
