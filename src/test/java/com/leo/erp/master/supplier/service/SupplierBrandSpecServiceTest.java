package com.leo.erp.master.supplier.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.master.supplier.domain.entity.Supplier;
import com.leo.erp.master.supplier.domain.entity.SupplierBrand;
import com.leo.erp.master.supplier.domain.entity.SupplierBrandSpec;
import com.leo.erp.master.supplier.repository.SupplierBrandRepository;
import com.leo.erp.master.supplier.repository.SupplierBrandSpecRepository;
import com.leo.erp.master.supplier.repository.SupplierRepository;
import com.leo.erp.master.supplier.web.dto.SupplierBrandSpecRequest;
import com.leo.erp.master.supplier.web.dto.SupplierBrandSpecResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 供应商品牌规格配置的核心口径。
 *
 * <p>覆盖最容易悄悄坏掉的几件事: ① 未配置时返回空数组而不是 404/异常; ② 首次写入把每条组合落到正确的
 * 品牌行上; ③ 整集替换只保留新集合并把旧行<b>软删</b>(不物理删); ④ 空数组清空全部配置(回落默认);
 * ⑤ 品牌不在该供应商经营品牌内 / 规格非正 / 字段为空或超长 / 组合重复 一律 422 且一条都不落库;
 * ⑥ 内容完全相同的重复 PUT 是 no-op(不软删不重插, 行 id 稳定); ⑦ 供应商不存在 → 404;
 * ⑧ 只动本供应商品牌的行, 不影响别人的配置。</p>
 *
 * <p>仓储用内存替身: 只模拟「按品牌 id 取未删除行」「saveAll」「flush」三件事, 让 service 的真实逻辑
 * (校验、集合比较、软删、插入)跑在测试里。</p>
 */
class SupplierBrandSpecServiceTest {

    private static final long SUPPLIER_ID = 960000000000000001L;
    private static final long YONGGANG_BRAND_ID = 960000000000000011L;
    private static final long SHAGANG_BRAND_ID = 960000000000000012L;
    /** 另一家供应商的品牌行: 本供应商的整集替换不得碰到它。 */
    private static final long OTHER_SUPPLIER_BRAND_ID = 960000000000000021L;

    private final SupplierRepository supplierRepository = mock(SupplierRepository.class);
    private final SupplierBrandRepository supplierBrandRepository = mock(SupplierBrandRepository.class);
    private final SupplierBrandSpecRepository specRepository = mock(SupplierBrandSpecRepository.class);
    private final SnowflakeIdGenerator idGenerator = mock(SnowflakeIdGenerator.class);

    /** 模拟持久化状态: 软删只把标记置 true, 行仍留在列表里(与真实表一致)。 */
    private final List<SupplierBrandSpec> rows = new ArrayList<>();
    private final AtomicLong idSequence = new AtomicLong(960000000000000101L);
    private final SupplierBrandSpecService service = new SupplierBrandSpecService(
            supplierRepository, supplierBrandRepository, specRepository, idGenerator);

    @BeforeEach
    void setUp() {
        Supplier supplier = new Supplier();
        supplier.setId(SUPPLIER_ID);
        when(supplierRepository.findByIdAndDeletedFlagFalse(SUPPLIER_ID)).thenReturn(Optional.of(supplier));
        when(supplierBrandRepository.findBySupplierIdAndDeletedFlagFalseOrderByBrandNameAsc(SUPPLIER_ID))
                .thenReturn(List.of(brand(YONGGANG_BRAND_ID, "永钢"), brand(SHAGANG_BRAND_ID, "沙钢")));
        when(specRepository.findBySupplierBrandIdInAndDeletedFlagFalse(anyCollection()))
                .thenAnswer(invocation -> {
                    Collection<Long> brandIds = invocation.getArgument(0);
                    return rows.stream()
                            .filter(row -> !row.isDeletedFlag())
                            .filter(row -> brandIds.contains(row.getSupplierBrandId()))
                            .toList();
                });
        when(specRepository.saveAll(anyList())).thenAnswer(invocation -> {
            List<SupplierBrandSpec> saved = invocation.getArgument(0);
            for (SupplierBrandSpec row : saved) {
                if (rows.stream().noneMatch(existing -> existing == row)) {
                    rows.add(row);
                }
            }
            return saved;
        });
        when(idGenerator.nextId()).thenAnswer(invocation -> idSequence.getAndIncrement());
    }

    @Test
    /** 从未配置过时返回空数组(不是 404): 前端据此知道"回落到按商品资料全量"。 */
    void list_whenNeverConfigured_returnsEmptyArray() {
        assertThat(service.list(SUPPLIER_ID)).isEmpty();
    }

    @Test
    /** 首次写入: 每条组合都落到对应品牌行, 规格/定尺/类别/材质原样保存。 */
    void replace_firstWrite_persistsEveryCombination() {
        List<SupplierBrandSpecResponse> saved = service.replace(SUPPLIER_ID, List.of(
                spec("永钢", "HRB400E", "螺纹钢", 12, "9米"),
                spec("沙钢", "HRB400E", "螺纹钢", 14, "12米")));

        assertThat(saved).extracting(this::describe)
                .containsExactly("永钢/HRB400E/螺纹钢/12/9米", "沙钢/HRB400E/螺纹钢/14/12米");
        assertThat(rows).hasSize(2);
        assertThat(rows).allSatisfy(row -> {
            assertThat(row.isDeletedFlag()).isFalse();
            assertThat(row.getId()).isNotNull();
            assertThat(row.getSupplierBrandId()).isIn(YONGGANG_BRAND_ID, SHAGANG_BRAND_ID);
        });
        assertThat(rows).extracting(SupplierBrandSpec::getSupplierBrandId)
                .containsExactlyInAnyOrder(YONGGANG_BRAND_ID, SHAGANG_BRAND_ID);
    }

    @Test
    /** 读取顺序固定为 品牌/类别/材质/规格/定尺 升序, 与写入顺序无关: 前端可直达渲染 Transfer 右侧。 */
    void replace_thenList_returnsStableAscendingOrder() {
        service.replace(SUPPLIER_ID, List.of(
                spec("沙钢", "盘螺", "盘螺", 8, "9米"),
                spec("永钢", "盘螺", "盘螺", 10, "9米"),
                spec("永钢", "HRB400E", "螺纹钢", 12, "9米")));

        assertThat(service.list(SUPPLIER_ID)).extracting(this::describe)
                .containsExactly(
                        "永钢/HRB400E/螺纹钢/12/9米",
                        "永钢/盘螺/盘螺/10/9米",
                        "沙钢/盘螺/盘螺/8/9米");
    }

    @Test
    /** 整集替换(增删): 旧集合里不再出现的组合被软删, 新出现的被插入, 最终只剩请求里的集合。 */
    void replace_removesCombinationsMissingFromRequest() {
        service.replace(SUPPLIER_ID, List.of(
                spec("永钢", "HRB400E", "螺纹钢", 12, "9米"),
                spec("永钢", "HRB400E", "螺纹钢", 14, "9米"),
                spec("沙钢", "HRB400E", "螺纹钢", 16, "9米")));

        List<SupplierBrandSpecResponse> replaced = service.replace(SUPPLIER_ID, List.of(
                spec("永钢", "HRB400E", "螺纹钢", 14, "9米"),
                spec("沙钢", "HRB400E", "螺纹钢", 18, "9米")));

        assertThat(replaced).extracting(this::describe)
                .containsExactly("永钢/HRB400E/螺纹钢/14/9米", "沙钢/HRB400E/螺纹钢/18/9米");
        // 旧行只软删不物理删: 5 行都在, 其中 3 行已软删(12、16 以及被替换掉的 14 旧行)
        assertThat(rows).hasSize(5);
        assertThat(rows).filteredOn(SupplierBrandSpec::isDeletedFlag).hasSize(3);
        assertThat(rows).filteredOn(row -> !row.isDeletedFlag()).hasSize(2);
    }

    @Test
    /** 空数组 = 清空全部配置(回落到按商品资料全量): 所有行软删, GET 读回空数组。 */
    void replace_withEmptyArray_clearsAllConfiguration() {
        service.replace(SUPPLIER_ID, List.of(
                spec("永钢", "HRB400E", "螺纹钢", 12, "9米"),
                spec("沙钢", "HRB400E", "螺纹钢", 14, "9米")));

        List<SupplierBrandSpecResponse> cleared = service.replace(SUPPLIER_ID, List.of());

        assertThat(cleared).isEmpty();
        assertThat(service.list(SUPPLIER_ID)).isEmpty();
        assertThat(rows).hasSize(2).allSatisfy(row -> assertThat(row.isDeletedFlag()).isTrue());
    }

    @Test
    /** 内容完全相同的重复 PUT 是 no-op: 不软删、不重插, 行 id 保持稳定(幂等)。 */
    void replace_repeatedWithSameCombinations_isIdempotentNoOp() {
        List<SupplierBrandSpecRequest> items = List.of(
                spec("永钢", "HRB400E", "螺纹钢", 12, "9米"),
                spec("沙钢", "HRB400E", "螺纹钢", 14, "9米"));
        service.replace(SUPPLIER_ID, items);
        List<Long> idsBefore = rows.stream().map(SupplierBrandSpec::getId).toList();

        List<SupplierBrandSpecResponse> repeated = service.replace(SUPPLIER_ID, items);

        assertThat(rows).hasSize(2);
        assertThat(rows).extracting(SupplierBrandSpec::getId).containsExactlyElementsOf(idsBefore);
        assertThat(rows).allSatisfy(row -> assertThat(row.isDeletedFlag()).isFalse());
        assertThat(repeated).extracting(this::describe)
                .containsExactly("永钢/HRB400E/螺纹钢/12/9米", "沙钢/HRB400E/螺纹钢/14/9米");
    }

    @Test
    /** 顺序不同但集合相同也算 no-op: 配置是集合语义, 不是列表语义。 */
    void replace_sameCombinationsInDifferentOrder_isIdempotentNoOp() {
        service.replace(SUPPLIER_ID, List.of(
                spec("永钢", "HRB400E", "螺纹钢", 12, "9米"),
                spec("沙钢", "HRB400E", "螺纹钢", 14, "9米")));
        List<Long> idsBefore = rows.stream().map(SupplierBrandSpec::getId).toList();

        service.replace(SUPPLIER_ID, List.of(
                spec("沙钢", "HRB400E", "螺纹钢", 14, "9米"),
                spec("永钢", "HRB400E", "螺纹钢", 12, "9米")));

        assertThat(rows).extracting(SupplierBrandSpec::getId).containsExactlyElementsOf(idsBefore);
    }

    @Test
    /** 品牌不在该供应商经营品牌内 → 422, 且不落库(避免给"别人家的品牌"写配置)。 */
    void replace_rejectsBrandOutsideBusinessBrands() {
        assertThatThrownBy(() -> service.replace(SUPPLIER_ID, List.of(
                spec("中天", "HRB400E", "螺纹钢", 12, "9米"))))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.VALIDATION_ERROR))
                .hasMessageContaining("不在该供应商的经营品牌内")
                .hasMessageContaining("中天");
        assertThat(rows).isEmpty();
    }

    @Test
    /** 同一请求内组合重复 → 422: 否则整集替换后同一组合会有两行, 前端无法判断用哪个。 */
    void replace_rejectsDuplicateCombinations() {
        assertThatThrownBy(() -> service.replace(SUPPLIER_ID, List.of(
                spec("永钢", "HRB400E", "螺纹钢", 12, "9米"),
                spec("永钢", "HRB400E", "螺纹钢", 12, "9米"))))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.VALIDATION_ERROR))
                .hasMessageContaining("重复");
        assertThat(rows).isEmpty();
    }

    @Test
    /** 规格必须是正整数: 0 与负数都是 422(库侧 CHECK spec > 0 兜底, 但不能靠它抛 5xx)。 */
    void replace_rejectsNonPositiveSpec() {
        assertThatThrownBy(() -> service.replace(SUPPLIER_ID, List.of(
                spec("永钢", "HRB400E", "螺纹钢", 0, "9米"))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("规格必须是正整数");
        assertThatThrownBy(() -> service.replace(SUPPLIER_ID, List.of(
                spec("永钢", "HRB400E", "螺纹钢", -12, "9米"))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("规格必须是正整数");
        assertThat(rows).isEmpty();
    }

    @Test
    /** 类别/材质/定尺为空(含纯空白) → 422; 品牌为空 → 422。 */
    void replace_rejectsBlankFields() {
        assertThatThrownBy(() -> service.replace(SUPPLIER_ID, List.of(
                spec("永钢", "HRB400E", "螺纹钢", 12, "  "))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("定尺不能为空");
        assertThatThrownBy(() -> service.replace(SUPPLIER_ID, List.of(
                spec("永钢", "HRB400E", "  ", 12, "9米"))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("材质不能为空");
        assertThatThrownBy(() -> service.replace(SUPPLIER_ID, List.of(
                spec("  ", "HRB400E", "螺纹钢", 12, "9米"))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("品牌不能为空");
        assertThat(rows).isEmpty();
    }

    @Test
    /** 超长字段在接口层拦成 422, 不落库触发字段长度/唯一键的 409、500。 */
    void replace_rejectsOverlongFields() {
        assertThatThrownBy(() -> service.replace(SUPPLIER_ID, List.of(
                spec("永钢", "A".repeat(33), "螺纹钢", 12, "9米"))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("类别长度不能超过32个字符");
        assertThatThrownBy(() -> service.replace(SUPPLIER_ID, List.of(
                spec("永钢", "HRB400E", "螺纹钢", 12, "9米米米米米米米米米米米米米米米米米"))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("定尺长度不能超过16个字符");
        assertThat(rows).isEmpty();
    }

    @Test
    /** 请求体为 null(整体缺失) → 422 并提示空数组语义, 不当作"未携带, 保持原样"。 */
    void replace_rejectsNullBody() {
        assertThatThrownBy(() -> service.replace(SUPPLIER_ID, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("空数组");
        assertThat(rows).isEmpty();
    }

    @Test
    /** 一次提交条数超上限 → 422(与仓库其它整集替换接口同一量级上限)。 */
    void replace_rejectsTooManyItems() {
        List<SupplierBrandSpecRequest> items = new ArrayList<>();
        for (int index = 0; index < 2001; index++) {
            items.add(spec("永钢", "HRB400E", "螺纹钢", index + 1, "9米"));
        }

        assertThatThrownBy(() -> service.replace(SUPPLIER_ID, items))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("一次最多提交");
        assertThat(rows).isEmpty();
    }

    @Test
    /** 字段两端空白被 trim 后保存: 与 SupplierService 品牌名归一化口径一致, 避免"看起来一样却写了两行"。 */
    void replace_trimsSurroundingWhitespace() {
        List<SupplierBrandSpecResponse> saved = service.replace(SUPPLIER_ID, List.of(
                spec(" 永钢 ", " HRB400E ", " 螺纹钢 ", 12, " 9米 ")));

        assertThat(saved).extracting(this::describe).containsExactly("永钢/HRB400E/螺纹钢/12/9米");
        assertThat(rows.get(0).getCategory()).isEqualTo("HRB400E");
        assertThat(rows.get(0).getLength()).isEqualTo("9米");
    }

    @Test
    /** 供应商不存在/已删除 → 404(而不是把"查不到"当成"没有配置")。 */
    void replace_rejectsUnknownSupplier() {
        when(supplierRepository.findByIdAndDeletedFlagFalse(SUPPLIER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.replace(SUPPLIER_ID, List.of(
                spec("永钢", "HRB400E", "螺纹钢", 12, "9米"))))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.NOT_FOUND));
        assertThatThrownBy(() -> service.list(SUPPLIER_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.NOT_FOUND));
        assertThat(rows).isEmpty();
    }

    @Test
    /** 只整集替换本供应商品牌的配置: 另一家供应商品牌下的行不得被软删。 */
    void replace_doesNotTouchOtherSuppliersRows() {
        SupplierBrandSpec other = row(OTHER_SUPPLIER_BRAND_ID, "HRB400E", "螺纹钢", 12, "9米");
        rows.add(other);
        service.replace(SUPPLIER_ID, List.of(spec("永钢", "HRB400E", "螺纹钢", 12, "9米")));

        assertThat(other.isDeletedFlag()).isFalse();
        assertThat(rows).filteredOn(SupplierBrandSpec::isDeletedFlag).isEmpty();
    }

    private SupplierBrandSpec row(long brandId, String category, String material, int spec, String length) {
        SupplierBrandSpec row = new SupplierBrandSpec();
        row.setId(idSequence.getAndIncrement());
        row.setSupplierBrandId(brandId);
        row.setCategory(category);
        row.setMaterial(material);
        row.setSpec(spec);
        row.setLength(length);
        return row;
    }

    private static SupplierBrand brand(long id, String name) {
        SupplierBrand brand = new SupplierBrand();
        brand.setId(id);
        brand.setSupplierId(SUPPLIER_ID);
        brand.setBrandName(name);
        return brand;
    }

    private static SupplierBrandSpecRequest spec(String brandName, String category, String material,
                                                 int spec, String length) {
        return new SupplierBrandSpecRequest(brandName, category, material, spec, length);
    }

    private String describe(SupplierBrandSpecResponse response) {
        return response.brandName() + "/" + response.category() + "/" + response.material()
                + "/" + response.spec() + "/" + response.length();
    }
}
