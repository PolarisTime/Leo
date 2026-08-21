package com.leo.erp.logistics.bill.service;

import com.leo.erp.logistics.api.FreightBillStatementSourceQuery.BillSnapshot;
import com.leo.erp.logistics.api.FreightBillStatementSourceQuery.CandidateCriteria;
import com.leo.erp.logistics.api.FreightBillStatementSourceQuery.CandidatePage;
import com.leo.erp.logistics.api.FreightBillStatementSourceQuery.CandidateSnapshot;
import com.leo.erp.logistics.api.FreightBillStatementSourceQuery.ItemSnapshot;
import com.leo.erp.logistics.bill.domain.entity.FreightBill;
import com.leo.erp.logistics.bill.domain.entity.FreightBillItem;
import com.leo.erp.logistics.bill.repository.FreightBillRepository;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyChar;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * FreightBillStatementSourceQueryService 极端情况测试。
 * <p>
 * 聚焦边界与异常输入：null/空集合、含 null/重复 ID、雪花 ID 精度边界、
 * 非法分页参数、非法排序方向、逆序日期范围、null 查询条件（已知残余风险）。
 * 通过 mock 仓储层验证 service 的委托与映射行为，不依赖真实数据库。
 */
@ExtendWith(MockitoExtension.class)
class FreightBillStatementSourceQueryServiceTest {

    @Mock
    private FreightBillRepository freightBillRepository;

    @InjectMocks
    private FreightBillStatementSourceQueryService service;

    // ---------- 测试数据 ----------

    private FreightBill bill(Long id, String billNo) {
        FreightBill bill = new FreightBill();
        bill.setId(id);
        bill.setBillNo(billNo);
        bill.setCarrierId(100L);
        bill.setCarrierCode("C001");
        bill.setCarrierName("承运商A");
        bill.setSettlementCompanyId(200L);
        bill.setSettlementCompanyName("结算公司A");
        bill.setCustomerName("客户A");
        bill.setProjectName("项目A");
        bill.setBillTime(LocalDate.of(2026, 8, 1));
        bill.setTotalWeight(new BigDecimal("1000.50"));
        bill.setUnitPrice(new BigDecimal("50.00"));
        bill.setTotalFreight(new BigDecimal("50500.00"));
        bill.setStatus("AUDITED");
        return bill;
    }

    private FreightBillItem item(Long id) {
        FreightBillItem item = new FreightBillItem();
        item.setId(id);
        item.setSettlementCompanyId(200L);
        item.setSettlementCompanyName("结算公司A");
        item.setCustomerId(300L);
        item.setCustomerName("客户A");
        item.setProjectId(400L);
        item.setProjectName("项目A");
        item.setMaterialId(500L);
        item.setMaterialCode("M001");
        item.setMaterialName("螺纹钢");
        item.setQuantity(10);
        item.setQuantityUnit("件");
        item.setWeightTon(new BigDecimal("100.25"));
        return item;
    }

    private CandidateCriteria criteria(int page, int size) {
        return new CandidateCriteria(page, size, null, null, null, null, null, null, null, null, null, null);
    }

    // ---------- findByBillIds 极端情况 ----------

    @Test
    void findByBillIds_shouldReturnEmptyForNullInput() {
        assertThat(service.findByBillIds(null)).isEmpty();
        verifyNoInteractions(freightBillRepository);
    }

    @Test
    void findByBillIds_shouldReturnEmptyForEmptyCollection() {
        assertThat(service.findByBillIds(List.of())).isEmpty();
        verifyNoInteractions(freightBillRepository);
    }

    @Test
    void findByBillIds_shouldFilterNullIds() {
        when(freightBillRepository.findByIdInAndDeletedFlagFalse(any())).thenReturn(List.of());

        // Arrays.asList 允许 null 元素，验证 service 过滤掉 null ID
        service.findByBillIds(Arrays.asList(1L, null, 2L));

        ArgumentCaptor<Collection<Long>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(freightBillRepository).findByIdInAndDeletedFlagFalse(captor.capture());
        assertThat(captor.getValue()).containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    void findByBillIds_shouldDeduplicateRepeatedIds() {
        when(freightBillRepository.findByIdInAndDeletedFlagFalse(any())).thenReturn(List.of());

        service.findByBillIds(List.of(1L, 1L, 2L, 2L, 2L));

        ArgumentCaptor<Collection<Long>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(freightBillRepository).findByIdInAndDeletedFlagFalse(captor.capture());
        assertThat(captor.getValue()).containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    void findByBillIds_shouldReturnEmptyWhenRepositoryReturnsNone() {
        when(freightBillRepository.findByIdInAndDeletedFlagFalse(any())).thenReturn(List.of());
        assertThat(service.findByBillIds(List.of(999L, 888L))).isEmpty();
    }

    @Test
    void findByBillIds_shouldMapBillAndItemSnapshots() {
        FreightBill bill = bill(1L, "FB001");
        bill.setItems(List.of(item(11L), item(12L)));
        when(freightBillRepository.findByIdInAndDeletedFlagFalse(any())).thenReturn(List.of(bill));

        List<BillSnapshot> result = service.findByBillIds(List.of(1L));

        assertThat(result).hasSize(1);
        BillSnapshot snapshot = result.get(0);
        assertThat(snapshot.id()).isEqualTo(1L);
        assertThat(snapshot.billNo()).isEqualTo("FB001");
        assertThat(snapshot.totalFreight()).isEqualByComparingTo("50500.00");
        assertThat(snapshot.items()).hasSize(2);
        assertThat(snapshot.items().get(0).materialCode()).isEqualTo("M001");
    }

    @Test
    void findByBillIds_shouldHandleMaxSnowflakeId() {
        Long max = Long.MAX_VALUE; // 9223372036854775807，19 位雪花 ID 上界
        FreightBill bill = bill(max, "FB-MAX");
        when(freightBillRepository.findByIdInAndDeletedFlagFalse(any())).thenReturn(List.of(bill));

        List<BillSnapshot> result = service.findByBillIds(List.of(max));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(max);
    }

    // ---------- findCandidates 极端情况 ----------

    @Test
    void findCandidates_shouldReturnEmptyPageWhenRepositoryEmpty() {
        when(freightBillRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0L));

        CandidatePage page = service.findCandidates(criteria(0, 20));

        assertThat(page.content()).isEmpty();
        assertThat(page.totalElements()).isZero();
        assertThat(page.totalPages()).isZero();
        assertThat(page.page()).isZero();
        assertThat(page.size()).isEqualTo(20);
    }

    @Test
    void findCandidates_shouldMapCandidateSnapshot() {
        when(freightBillRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(bill(1L, "FB001"))));

        CandidatePage page = service.findCandidates(criteria(0, 20));

        assertThat(page.content()).hasSize(1);
        CandidateSnapshot snapshot = page.content().get(0);
        assertThat(snapshot.id()).isEqualTo(1L);
        assertThat(snapshot.billNo()).isEqualTo("FB001");
        assertThat(snapshot.status()).isEqualTo("AUDITED");
        assertThat(snapshot.carrierId()).isEqualTo(100L);
        assertThat(snapshot.totalFreight()).isEqualByComparingTo("50500.00");
    }

    @Test
    void findCandidates_shouldRejectZeroSize() {
        assertThatThrownBy(() -> service.findCandidates(criteria(0, 0)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void findCandidates_shouldRejectNegativeSize() {
        assertThatThrownBy(() -> service.findCandidates(criteria(0, -5)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void findCandidates_shouldRejectNegativePage() {
        assertThatThrownBy(() -> service.findCandidates(criteria(-1, 20)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void findCandidates_shouldDefaultSortToIdDescendingWhenSortBlankAndDirectionInvalid() {
        when(freightBillRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service.findCandidates(new CandidateCriteria(
                0, 20, null, "weird-direction", null, null, null, null, null, null, null, null));

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(freightBillRepository).findAll(any(Specification.class), captor.capture());
        Sort.Order order = captor.getValue().getSort().getOrderFor("id");
        assertThat(order).isNotNull();
        assertThat(order.getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void findCandidates_shouldDefaultSortToIdWhenSortByBlankString() {
        when(freightBillRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0L));

        // 空字符串 sortBy：isBlank() 为 true，回退到默认 "id" 排序（覆盖 #144 的空串分支）
        service.findCandidates(new CandidateCriteria(
                0, 20, "", null, null, null, null, null, null, null, null, null));

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(freightBillRepository).findAll(any(Specification.class), captor.capture());
        Sort.Order order = captor.getValue().getSort().getOrderFor("id");
        assertThat(order).isNotNull();
        assertThat(order.getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void findCandidates_shouldHonorAscDirectionIgnoringCase() {
        when(freightBillRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service.findCandidates(new CandidateCriteria(
                1, 10, "billTime", "ASC", null, null, null, null, null, null, null, null));

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(freightBillRepository).findAll(any(Specification.class), captor.capture());
        Pageable pageable = captor.getValue();
        assertThat(pageable.getPageNumber()).isEqualTo(1);
        assertThat(pageable.getPageSize()).isEqualTo(10);
        assertThat(pageable.getSort().getOrderFor("billTime").getDirection()).isEqualTo(Sort.Direction.ASC);
    }

    @Test
    void findCandidates_shouldAcceptNullExcludedBillIds() {
        when(freightBillRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        CandidatePage page = service.findCandidates(criteria(0, 20));

        assertThat(page.content()).isEmpty();
    }

    @Test
    void findCandidates_shouldAcceptExcludedBillIds() {
        when(freightBillRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        CandidatePage page = service.findCandidates(new CandidateCriteria(
                0, 20, null, null, null, null, null, null, null, null, null, List.of(1L, 2L)));

        assertThat(page.content()).isEmpty();
        verify(freightBillRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void findCandidates_shouldAcceptReversedDateRangeWithoutCrash() {
        // 起始日期晚于结束日期：Specs.betweenIfPresent 不校验先后，不应崩溃，返回空结果
        when(freightBillRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        CandidatePage page = service.findCandidates(new CandidateCriteria(
                0, 20, null, null, null, null, null, null, null,
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 8, 1),
                null));

        assertThat(page.content()).isEmpty();
    }

    @Test
    void findCandidates_shouldRejectNullCriteriaAsKnownResidualRisk() {
        // 已知残余风险：findCandidates 未对 null criteria 做防御，会直接 NPE。
        // 若需对外提供稳定契约，应改为抛出带语义的 IllegalArgumentException 或返回空页。
        assertThatThrownBy(() -> service.findCandidates(null))
                .isInstanceOf(NullPointerException.class);
    }

    // ---------- Specification 执行级（真实执行 spec，覆盖谓词 lambda 分支） ----------

    /**
     * 非空排除集：真实执行 spec 的 toPredicate，覆盖 excludeIds 的
     * {@code not(id in (...))} 分支（源码 #152-154）以及 Specs 各过滤 lambda。
     */
    @Test
    void findCandidates_shouldExecuteSpecificationWithExcludedIdsBranch() {
        ArgumentCaptor<Specification<FreightBill>> specCaptor = ArgumentCaptor.forClass(Specification.class);
        when(freightBillRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0L));

        service.findCandidates(new CandidateCriteria(
                0, 20, null, null, "钢材", 100L, "C001", "承运商", 200L,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), List.of(1L, 2L)));

        verify(freightBillRepository).findAll(specCaptor.capture(), any(Pageable.class));

        assertThatCode(() -> executeSpec(specCaptor.getValue())).doesNotThrowAnyException();
        verify(criteriaBuilder).not(any(Expression.class));   // 非空排除集 → not(id in (...))
        verify(criteriaBuilder, atLeastOnce()).like(any(), anyString(), anyChar()); // keywordLike 对 6 个字段触发 like
    }

    /**
     * 空排除集：执行 spec 覆盖 excludeIds 的 {@code conjunction()} 分支。
     */
    @Test
    void findCandidates_shouldExecuteSpecificationWithEmptyExclusion() {
        ArgumentCaptor<Specification<FreightBill>> specCaptor = ArgumentCaptor.forClass(Specification.class);
        when(freightBillRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0L));

        service.findCandidates(new CandidateCriteria(
                0, 20, null, null, null, null, null, null, null, null, null, null));

        verify(freightBillRepository).findAll(specCaptor.capture(), any(Pageable.class));

        assertThatCode(() -> executeSpec(specCaptor.getValue())).doesNotThrowAnyException();
        verify(criteriaBuilder, never()).not(any(Expression.class)); // 空排除集不走 not 分支
        verify(criteriaBuilder, atLeastOnce()).conjunction();        // keyword 与排除集等空过滤 → conjunction
    }

    @Mock(lenient = true)
    private CriteriaBuilder criteriaBuilder;

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void executeSpec(Specification<FreightBill> spec) {
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        Root<FreightBill> root = mock(Root.class);
        Path<Object> idPath = mock(Path.class);
        when(criteriaBuilder.conjunction()).thenReturn(mock(Predicate.class));
        when(criteriaBuilder.isFalse(any(Expression.class))).thenReturn(mock(Predicate.class));
        when(criteriaBuilder.like(any(), anyString(), anyChar())).thenReturn(mock(Predicate.class));
        when(criteriaBuilder.or(any(Predicate[].class))).thenReturn(mock(Predicate.class));
        when(criteriaBuilder.and(any(Predicate[].class))).thenReturn(mock(Predicate.class));
        when(criteriaBuilder.equal(any(Expression.class), any())).thenReturn(mock(Predicate.class));
        when(criteriaBuilder.<LocalDate>greaterThanOrEqualTo(any(Expression.class), any(LocalDate.class)))
                .thenReturn(mock(Predicate.class));
        when(criteriaBuilder.<LocalDate>lessThanOrEqualTo(any(Expression.class), any(LocalDate.class)))
                .thenReturn(mock(Predicate.class));
        when(criteriaBuilder.not(any(Expression.class))).thenReturn(mock(Predicate.class));
        when(root.get(anyString())).thenReturn(mock(Path.class));
        lenient().when(root.get("id")).thenReturn(idPath);
        lenient().when(idPath.in(any(Collection.class))).thenReturn(mock(Predicate.class));
        spec.toPredicate(root, query, criteriaBuilder);
    }
}
