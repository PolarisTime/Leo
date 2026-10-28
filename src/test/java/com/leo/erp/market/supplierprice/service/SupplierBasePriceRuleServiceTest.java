package com.leo.erp.market.supplierprice.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.market.supplierprice.domain.entity.SupplierBasePriceRule;
import com.leo.erp.market.supplierprice.repository.SupplierBasePriceRuleRepository;
import com.leo.erp.market.supplierprice.web.dto.SupplierBasePriceRuleRequest;
import com.leo.erp.market.supplierprice.web.dto.SupplierBasePriceRuleResponse;
import com.leo.erp.master.api.SupplierQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 供应商品牌基价加价规则的核心口径。
 *
 * <p>覆盖最容易悄悄坏掉的几件事: ① 未配置时 version=0 而不是 404/异常; ② 整组替换只保留新规则,
 * 旧行被软删而不是与新行并存; ③ 同一套规则内规格不得重复, 基价必须出现在规则项里且 delta=0;
 * ④ 版本前置条件不满足时返回 412 且不写库; ⑤ 写入后的行版本 = 前置条件版本 + 1, 整套共享;
 * ⑥ 清空规则(items 为空)后版本仍 +1 并能被读回, 下一次前置条件不会永久 412。</p>
 *
 * <p>仓储用内存替身: 只模拟「按 supplier+brand 取未删除行」「saveAll」「flush」三件事,
 * 让 service/store 的真实逻辑(排序、软删、版本推导)跑在测试里。</p>
 */
class SupplierBasePriceRuleServiceTest {

    private static final long SUPPLIER_ID = 1001L;
    private static final String BRAND = "中天";

    private final SupplierBasePriceRuleRepository repository = mock(SupplierBasePriceRuleRepository.class);
    private final SupplierQuery supplierQuery = mock(SupplierQuery.class);
    private final SnowflakeIdGenerator idGenerator = mock(SnowflakeIdGenerator.class);

    /** 模拟持久化状态: 软删只是把标记置 true, 行仍留在列表里(与真实表一致)。 */
    private final List<SupplierBasePriceRule> rows = new ArrayList<>();
    private final AtomicLong idSequence = new AtomicLong(9000L);
    private final SupplierBasePriceRuleService service = new SupplierBasePriceRuleService(
            new SupplierBasePriceRuleStore(repository, idGenerator), supplierQuery);

    @BeforeEach
    void setUp() {
        when(idGenerator.nextId()).thenAnswer(invocation -> idSequence.getAndIncrement());
        when(repository
                .findBySupplierIdAndBrandNameAndDeletedFlagFalseOrderByMaterialAscSpecAscLengthAsc(
                        anyLong(), anyString()))
                .thenAnswer(invocation -> rows.stream()
                        .filter(rule -> !rule.isDeletedFlag())
                        .filter(rule -> Objects.equals(rule.getSupplierId(), invocation.getArgument(0)))
                        .filter(rule -> Objects.equals(rule.getBrandName(), invocation.getArgument(1)))
                        .sorted(Comparator.comparing(SupplierBasePriceRule::getMaterial)
                                .thenComparing(SupplierBasePriceRule::getSpec)
                                .thenComparing(SupplierBasePriceRule::getLength))
                        .toList());
        when(repository.saveAll(anyList())).thenAnswer(invocation -> {
            List<SupplierBasePriceRule> saved = invocation.getArgument(0);
            for (SupplierBasePriceRule rule : saved) {
                if (!rows.contains(rule)) {
                    rows.add(rule);
                }
            }
            return saved;
        });
        // 版本号查询覆盖全部行(含软删留痕行): 清空后靠留痕行承载新版本
        when(repository.findLatestVersion(anyLong(), anyString())).thenAnswer(invocation -> rows.stream()
                .filter(rule -> Objects.equals(rule.getSupplierId(), invocation.getArgument(0)))
                .filter(rule -> Objects.equals(rule.getBrandName(), invocation.getArgument(1)))
                .map(SupplierBasePriceRule::getVersion)
                .filter(Objects::nonNull)
                .max(Long::compareTo)
                .orElse(null));
        when(supplierQuery.findActiveNormalById(SUPPLIER_ID)).thenReturn(Optional.of(
                new SupplierQuery.SupplierSnapshot(SUPPLIER_ID, "SUP-001", "物产中大")));
    }

    @Test
    /** 从没配置过时返回空结构 + version=0, 前端据此可直接新建, 不需要先判断 404。 */
    void find_emptyRuleSet_returnsVersionZero() {
        SupplierBasePriceRuleResponse response = service.find(SUPPLIER_ID, BRAND);

        assertThat(response.supplierId()).isEqualTo(SUPPLIER_ID);
        assertThat(response.brandName()).isEqualTo(BRAND);
        assertThat(response.base()).isNull();
        assertThat(response.items()).isEmpty();
        assertThat(response.version()).isZero();
    }

    @Test
    /** 写入后读回一致: 基价定位正确, delta 保留负数与两位小数, 版本为前置条件 + 1。 */
    void save_thenFind_roundTripsRuleSet() {
        SupplierBasePriceRuleRequest request = request(
                new SupplierBasePriceRuleRequest.ProductKey("HRB400E", 12, "9米"),
                item("HRB400E", 14, "9米", "-30.50"),
                item("HRB400E", 12, "9米", "0.00"),
                item("HRB400E", 16, "9米", "12.25"));

        SupplierBasePriceRuleResponse saved = service.save(request, 0L);

        assertThat(saved.version()).isEqualTo(1L);
        assertThat(saved.base())
                .isEqualTo(new SupplierBasePriceRuleResponse.ProductKey("HRB400E", 12, "9米"));
        // 响应顺序固定为 material/spec/length 升序, 与提交顺序无关
        assertThat(saved.items()).extracting(SupplierBasePriceRuleResponse.Item::spec)
                .containsExactly(12, 14, 16);
        assertThat(saved.items()).extracting(SupplierBasePriceRuleResponse.Item::delta)
                .containsExactly(new BigDecimal("0.00"), new BigDecimal("-30.50"), new BigDecimal("12.25"));

        assertThat(service.find(SUPPLIER_ID, BRAND)).isEqualTo(saved);
    }

    @Test
    /** 没有基价也允许: base 为 null, items 原样保存(前端可先存加减、稍后再指定基价)。 */
    void save_withoutBase_keepsItemsWithoutBase() {
        SupplierBasePriceRuleResponse saved = service.save(request(
                null,
                item("HRB400E", 12, "9米", "0.00"),
                item("HRB400E", 14, "9米", "5.00")), 0L);

        assertThat(saved.base()).isNull();
        assertThat(saved.items()).hasSize(2);
    }

    @Test
    /** 同一套规则内 (material,spec,length) 重复直接 422, 且不落库(否则同一规格会有两个 delta)。 */
    void save_rejectsDuplicateSpecKeys() {
        SupplierBasePriceRuleRequest request = request(
                new SupplierBasePriceRuleRequest.ProductKey("HRB400E", 12, "9米"),
                item("HRB400E", 12, "9米", "0.00"),
                item("HRB400E", 12, "9米", "8.00"));

        assertThatThrownBy(() -> service.save(request, 0L))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.VALIDATION_ERROR))
                .hasMessageContaining("规则项重复");
        assertThat(rows).isEmpty();
    }

    @Test
    /** 基价规格必须出现在 items 里, 否则「基价 + 加减」没有锚点。 */
    void save_rejectsBaseMissingFromItems() {
        SupplierBasePriceRuleRequest request = request(
                new SupplierBasePriceRuleRequest.ProductKey("HRB400E", 12, "9米"),
                item("HRB400E", 14, "9米", "5.00"));

        assertThatThrownBy(() -> service.save(request, 0L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("基价规格必须出现在规则项中");
        assertThat(rows).isEmpty();
    }

    @Test
    /** 基价行的加价必须为 0: 基价本身就是 0 元基准, 带加减会使整套推算偏移。 */
    void save_rejectsBaseWithNonZeroDelta() {
        SupplierBasePriceRuleRequest request = request(
                new SupplierBasePriceRuleRequest.ProductKey("HRB400E", 12, "9米"),
                item("HRB400E", 12, "9米", "1.00"));

        assertThatThrownBy(() -> service.save(request, 0L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("基价行的加价必须为 0");
    }

    @Test
    /** 旧版本提交返回 412, 且旧规则原样保留, 不允许静默覆盖他人刚保存的内容。 */
    void save_rejectsStaleVersionWithoutWriting() {
        service.save(request(
                new SupplierBasePriceRuleRequest.ProductKey("HRB400E", 12, "9米"),
                item("HRB400E", 12, "9米", "0.00")), 0L);
        List<SupplierBasePriceRule> before = List.copyOf(rows);

        assertThatThrownBy(() -> service.save(request(
                new SupplierBasePriceRuleRequest.ProductKey("HRB400E", 14, "9米"),
                item("HRB400E", 14, "9米", "0.00")), 0L))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.PRECONDITION_FAILED));

        assertThat(rows).containsExactlyElementsOf(before);
        assertThat(service.find(SUPPLIER_ID, BRAND).items()).hasSize(1);
    }

    @Test
    /** 整组替换: 旧行只软删(保留审计痕迹), 新行版本 = 前置条件 + 1, 读回只剩新规则。 */
    void save_replacesOldRowsInsteadOfMerging() {
        service.save(request(
                new SupplierBasePriceRuleRequest.ProductKey("HRB400E", 12, "9米"),
                item("HRB400E", 12, "9米", "0.00"),
                item("HRB400E", 14, "9米", "-10.00")), 0L);

        SupplierBasePriceRuleResponse replaced = service.save(request(
                new SupplierBasePriceRuleRequest.ProductKey("HRB400E", 16, "9米"),
                item("HRB400E", 16, "9米", "0.00")), 1L);

        assertThat(replaced.version()).isEqualTo(2L);
        assertThat(replaced.items()).hasSize(1);
        assertThat(replaced.items().get(0).spec()).isEqualTo(16);
        assertThat(rows).hasSize(3);
        assertThat(rows.stream().filter(SupplierBasePriceRule::isDeletedFlag)).hasSize(2);
        assertThat(rows.stream()
                .filter(rule -> !rule.isDeletedFlag())
                .map(SupplierBasePriceRule::getVersion))
                .containsExactly(2L);
    }

    @Test
    /** 清空规则: items 为空把整套替换为空, 版本仍 +1 并由软删留痕行承载, 下一次前置条件可继续使用。 */
    void save_withEmptyItems_clearsRuleSetAndKeepsVersionIncrement() {
        service.save(request(
                new SupplierBasePriceRuleRequest.ProductKey("HRB400E", 12, "9米"),
                item("HRB400E", 12, "9米", "0.00"),
                item("HRB400E", 14, "9米", "-10.00")), 0L);

        SupplierBasePriceRuleResponse cleared = service.save(
                new SupplierBasePriceRuleRequest(SUPPLIER_ID, BRAND, null, List.of()), 1L);

        assertThat(cleared.base()).isNull();
        assertThat(cleared.items()).isEmpty();
        assertThat(cleared.version()).isEqualTo(2L);
        assertThat(rows).hasSize(2);
        assertThat(rows).allSatisfy(rule -> {
            assertThat(rule.isDeletedFlag()).isTrue();
            assertThat(rule.getVersion()).isEqualTo(2L);
        });
        assertThat(service.find(SUPPLIER_ID, BRAND).version()).isEqualTo(2L);
        assertThat(service.find(SUPPLIER_ID, BRAND).items()).isEmpty();
    }

    @Test
    /** 重复清空已是空的规则是幂等 no-op: 没有行可承载新版本, 不制造读不回来的 +1。 */
    void save_withEmptyItemsOnEmptyRuleSet_isIdempotentNoOp() {
        service.save(request(
                new SupplierBasePriceRuleRequest.ProductKey("HRB400E", 12, "9米"),
                item("HRB400E", 12, "9米", "0.00")), 0L);
        service.save(new SupplierBasePriceRuleRequest(SUPPLIER_ID, BRAND, null, List.of()), 1L);
        int rowsBefore = rows.size();

        SupplierBasePriceRuleResponse clearedAgain = service.save(
                new SupplierBasePriceRuleRequest(SUPPLIER_ID, BRAND, null, List.of()), 2L);

        assertThat(clearedAgain.version()).isEqualTo(2L);
        assertThat(rows).hasSize(rowsBefore);
    }

    @Test
    /** 清空后重新录入: 版本从清空时持久化的值继续 +1, 不会回到 1 也不会撞前置条件。 */
    void save_afterClear_continuesVersionChain() {
        service.save(request(
                new SupplierBasePriceRuleRequest.ProductKey("HRB400E", 12, "9米"),
                item("HRB400E", 12, "9米", "0.00")), 0L);
        service.save(new SupplierBasePriceRuleRequest(SUPPLIER_ID, BRAND, null, List.of()), 1L);

        SupplierBasePriceRuleResponse rebuilt = service.save(request(
                new SupplierBasePriceRuleRequest.ProductKey("HRB400E", 16, "9米"),
                item("HRB400E", 16, "9米", "0.00")), 2L);

        assertThat(rebuilt.version()).isEqualTo(3L);
        assertThat(rebuilt.items()).hasSize(1);
    }

    @Test
    /** 前置条件关闭(缺头)时以当前版本 + 1 继续, 不会把版本号重置成 1。 */
    void save_withoutPrecondition_incrementsCurrentVersion() {
        service.save(request(
                new SupplierBasePriceRuleRequest.ProductKey("HRB400E", 12, "9米"),
                item("HRB400E", 12, "9米", "0.00")), 0L);

        SupplierBasePriceRuleResponse saved = service.save(request(
                new SupplierBasePriceRuleRequest.ProductKey("HRB400E", 12, "9米"),
                item("HRB400E", 12, "9米", "0.00")), null);

        assertThat(saved.version()).isEqualTo(2L);
    }

    @Test
    /** 供应商不存在/已停用返回 404 语义, 且不写库。 */
    void save_rejectsInactiveSupplier() {
        when(supplierQuery.findActiveNormalById(SUPPLIER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.save(request(
                new SupplierBasePriceRuleRequest.ProductKey("HRB400E", 12, "9米"),
                item("HRB400E", 12, "9米", "0.00")), 0L))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.NOT_FOUND));
        assertThat(rows).isEmpty();
    }

    private static SupplierBasePriceRuleRequest request(SupplierBasePriceRuleRequest.ProductKey base,
                                                        SupplierBasePriceRuleRequest.Item... items) {
        return new SupplierBasePriceRuleRequest(SUPPLIER_ID, BRAND, base, List.of(items));
    }

    private static SupplierBasePriceRuleRequest.Item item(String material, int spec, String length,
                                                          String delta) {
        return new SupplierBasePriceRuleRequest.Item(material, spec, length, new BigDecimal(delta));
    }
}
