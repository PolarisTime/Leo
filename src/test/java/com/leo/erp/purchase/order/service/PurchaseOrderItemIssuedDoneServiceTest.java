package com.leo.erp.purchase.order.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrderItem;
import com.leo.erp.purchase.order.repository.PurchaseOrderItemRepository;
import com.leo.erp.purchase.order.web.dto.PurchaseOrderItemIssuedDoneResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 采购订单明细行「已开完」手动标记的服务层测试: 幂等、留痕、取消清空、404。
 *
 * <p>标记是人工裁定"不用再开了", 与吨位无关(吨位列保留自动事实): 因此这里只断言标记与留痕本身。</p>
 */
@ExtendWith(MockitoExtension.class)
class PurchaseOrderItemIssuedDoneServiceTest {

    private static final long ITEM_ID = 9007199254740993L;

    @Mock
    private PurchaseOrderItemRepository itemRepository;

    private PurchaseOrderItemIssuedDoneService service() {
        return new PurchaseOrderItemIssuedDoneService(itemRepository);
    }

    private static PurchaseOrderItem item(boolean issuedDone) {
        PurchaseOrderItem item = new PurchaseOrderItem();
        item.setId(ITEM_ID);
        item.setIssuedDone(issuedDone);
        return item;
    }

    private void stubItem(PurchaseOrderItem item) {
        when(itemRepository.findActiveByIdIn(List.of(ITEM_ID))).thenReturn(List.of(item));
        when(itemRepository.saveAndFlush(any(PurchaseOrderItem.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    /** 标记已开完: 写入状态与留痕(谁/何时), 返回标记后的状态。 */
    @Test
    void mark_trueWritesFlagAndTrace() {
        PurchaseOrderItem item = item(false);
        stubItem(item);

        PurchaseOrderItemIssuedDoneResponse response =
                service().mark(ITEM_ID, true, 42L, "张三");

        assertThat(response.purchaseOrderItemId()).isEqualTo(ITEM_ID);
        assertThat(response.issuedDone()).isTrue();
        assertThat(response.issuedDoneBy()).isEqualTo(42L);
        assertThat(response.issuedDoneName()).isEqualTo("张三");
        assertThat(response.issuedDoneAt()).isNotNull();
        assertThat(item.isIssuedDone()).isTrue();
    }

    /** 重复标记幂等, 且不覆盖首次留痕(谁先标就是谁)。 */
    @Test
    void mark_trueTwiceIsIdempotentAndKeepsFirstTrace() {
        PurchaseOrderItem item = item(false);
        stubItem(item);

        service().mark(ITEM_ID, true, 42L, "张三");
        java.time.LocalDateTime firstAt = item.getIssuedDoneAt();
        PurchaseOrderItemIssuedDoneResponse second = service().mark(ITEM_ID, true, 99L, "李四");

        assertThat(second.issuedDone()).isTrue();
        assertThat(second.issuedDoneBy()).isEqualTo(42L);
        assertThat(second.issuedDoneName()).isEqualTo("张三");
        assertThat(second.issuedDoneAt()).isEqualTo(firstAt);
    }

    /** 取消标记: 状态与留痕一并清空。 */
    @Test
    void mark_falseClearsFlagAndTrace() {
        PurchaseOrderItem item = item(false);
        stubItem(item);
        service().mark(ITEM_ID, true, 42L, "张三");

        PurchaseOrderItemIssuedDoneResponse response = service().mark(ITEM_ID, false, 42L, "张三");

        assertThat(response.issuedDone()).isFalse();
        assertThat(response.issuedDoneBy()).isNull();
        assertThat(response.issuedDoneName()).isNull();
        assertThat(response.issuedDoneAt()).isNull();
    }

    /** 未标记时取消是 no-op, 仍返回未标记状态(幂等, 不报错)。 */
    @Test
    void mark_falseOnUnmarkedItemIsNoOp() {
        PurchaseOrderItem item = item(false);
        stubItem(item);

        PurchaseOrderItemIssuedDoneResponse response = service().mark(ITEM_ID, false, 42L, "张三");

        assertThat(response.issuedDone()).isFalse();
        assertThat(response.issuedDoneAt()).isNull();
        verify(itemRepository).saveAndFlush(item);
    }

    /** 明细行不存在(或所属订单已删除) → 404, 不写库。 */
    @Test
    void mark_missingItemThrowsNotFound() {
        when(itemRepository.findActiveByIdIn(List.of(ITEM_ID))).thenReturn(List.of());

        assertThatThrownBy(() -> service().mark(ITEM_ID, true, 42L, "张三"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("明细行不存在")
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
        verify(itemRepository, never()).saveAndFlush(any());
    }

    /** 操作人缺失时留痕回落 system/0, 不写 null。 */
    @Test
    void mark_trueWithoutOperatorFallsBackToSystem() {
        PurchaseOrderItem item = item(false);
        stubItem(item);

        PurchaseOrderItemIssuedDoneResponse response = service().mark(ITEM_ID, true, null, "  ");

        assertThat(response.issuedDoneBy()).isEqualTo(0L);
        assertThat(response.issuedDoneName()).isEqualTo("system");
    }

    /** 明细行 id 为空 → 422(而不是 404 或 500)。 */
    @Test
    void mark_nullItemIdThrowsValidationError() {
        assertThatThrownBy(() -> service().mark(null, true, 1L, "张三"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    /** 仓储返回多条(理论上不可能)时取第一条, 不抛异常。 */
    @Test
    void mark_usesFirstItemWhenRepositoryReturnsMultiple() {
        PurchaseOrderItem item = item(false);
        when(itemRepository.findActiveByIdIn(List.of(ITEM_ID))).thenReturn(List.of(item, item));
        when(itemRepository.saveAndFlush(any(PurchaseOrderItem.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(service().mark(ITEM_ID, true, 1L, "张三").issuedDone()).isTrue();
        assertThat(Optional.of(item).orElseThrow().isIssuedDone()).isTrue();
    }
}
