package com.leo.erp.market.quotation.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.market.quotation.web.dto.QuoteSheetRequest;
import com.leo.erp.market.quotation.web.dto.QuoteSheetResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuoteSheetServiceTest {

    @Mock
    private QuoteSheetStore store;

    @Mock
    private QuoteSheetEditLockService editLockService;

    @Mock
    private PurchaseOrderTonnageService purchaseOrderTonnageService;

    @InjectMocks
    private QuoteSheetService service;

    @Test
    void update_checksEditLockThenDelegates() {
        when(store.update(eq(9L), any(), eq(3L))).thenReturn(response());
        when(store.currentVersion(9L)).thenReturn(3L);

        QuoteSheetResponse result = service.update(9L, request(), 3L, 7L);

        assertThat(result).isNotNull();
        verify(editLockService).ensureWritable(9L, 7L);
        verify(store).update(9L, request(), 3L);
    }

    /**
     * P0-1 回归: FORCE_INCREMENT 在提交后才应用, 存储层返回的版本会落后 1;
     * 服务层必须以写事务提交后的回读版本覆盖响应版本。
     */
    @Test
    void update_overridesResponseVersionWithCommittedReadBackVersion() {
        when(store.update(eq(9L), any(), eq(3L))).thenReturn(response());
        when(store.currentVersion(9L)).thenReturn(9L);

        QuoteSheetResponse result = service.update(9L, request(), 3L, 7L);

        assertThat(result.version()).isEqualTo(9L);
    }

    @Test
    void update_propagatesVersionConflictWithoutRetry() {
        when(store.update(eq(9L), any(), eq(2L)))
                .thenThrow(new BusinessException(ErrorCode.PRECONDITION_FAILED, "版本不匹配"));

        assertThatThrownBy(() -> service.update(9L, request(), 2L, 7L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("版本不匹配");
        verify(store, times(1)).update(eq(9L), any(), eq(2L));
    }

    /** 乐观锁失败属版本已变更, 原样重试注定失败: 只执行一次并直接冒泡, 由全局异常映射 412。 */
    @Test
    void update_doesNotRetryOptimisticLockFailure() {
        when(store.update(eq(9L), any(), eq(3L)))
                .thenThrow(new ObjectOptimisticLockingFailureException("QuoteSheet", 9L));

        assertThatThrownBy(() -> service.update(9L, request(), 3L, 7L))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
        verify(store, times(1)).update(eq(9L), any(), eq(3L));
    }

    /** 唯一键/取锁类瞬时冲突仍按上限重试。 */
    @Test
    void update_retriesOnDataIntegrityViolationThenSucceeds() {
        when(store.update(eq(9L), any(), eq(3L)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"))
                .thenReturn(response());

        QuoteSheetResponse result = service.update(9L, request(), 3L, 7L);

        assertThat(result).isNotNull();
        verify(store, times(2)).update(eq(9L), any(), eq(3L));
    }

    @Test
    void update_blocksWhenOthersHoldEditLock() {
        doThrow(new BusinessException(ErrorCode.CONCURRENT_MODIFICATION, "单据已被 张三 签出编辑"))
                .when(editLockService).ensureWritable(9L, 7L);

        assertThatThrownBy(() -> service.update(9L, request(), 3L, 7L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("签出编辑");
        verify(store, times(0)).update(any(), any(), any());
    }

    @Test
    void addItem_checksEditLockThenDelegates() {
        QuoteSheetItemWrite write = new QuoteSheetItemWrite(itemResponse(), 4L);
        when(store.addItem(eq(9L), any(), eq(3L))).thenReturn(write);
        when(store.currentVersion(9L)).thenReturn(4L);

        QuoteSheetItemWrite result = service.addItem(9L, itemRequest(), 3L, 7L);

        assertThat(result).isEqualTo(write);
        assertThat(result.version()).isEqualTo(4L);
        verify(editLockService).ensureWritable(9L, 7L);
    }

    /** 行级写同样必须以提交后回读版本覆盖写入结果版本。 */
    @Test
    void addItem_overridesVersionWithCommittedReadBackVersion() {
        when(store.addItem(eq(9L), any(), eq(3L))).thenReturn(new QuoteSheetItemWrite(itemResponse(), 4L));
        when(store.currentVersion(9L)).thenReturn(5L);

        QuoteSheetItemWrite result = service.addItem(9L, itemRequest(), 3L, 7L);

        assertThat(result.version()).isEqualTo(5L);
        assertThat(result.item()).isEqualTo(itemResponse());
    }

    @Test
    void deleteItem_delegatesAfterEditLockCheck() {
        when(store.deleteItem(9L, 301L, 3L)).thenReturn(4L);
        when(store.currentVersion(9L)).thenReturn(4L);

        Long version = service.deleteItem(9L, 301L, 3L, 7L);

        assertThat(version).isEqualTo(4L);
        verify(editLockService).ensureWritable(9L, 7L);
        verify(store).deleteItem(9L, 301L, 3L);
    }

    private QuoteSheetRequest.ItemRequest itemRequest() {
        return new QuoteSheetRequest.ItemRequest("螺纹钢", "HRB400E", 12, "9米", BigDecimal.TEN, List.of());
    }

    private QuoteSheetResponse.ItemResponse itemResponse() {
        return new QuoteSheetResponse.ItemResponse(301L, 1, "螺纹钢", "HRB400E", 12, "9米",
                BigDecimal.TEN, List.of());
    }

    private QuoteSheetRequest request() {
        return new QuoteSheetRequest("9月9日报单", null, "云潮筝鸣府", LocalDate.of(2026, 9, 9),
                LocalDate.of(2026, 9, 10), "9:30 上午", new BigDecimal("30"), false, false, "报价", null,
                List.of(new QuoteSheetRequest.BrandRequest("中天", new BigDecimal("30"), 0)),
                List.of(new QuoteSheetRequest.ItemRequest("螺纹钢", "HRB400E", 12, "9米", BigDecimal.TEN,
                        List.of(new QuoteSheetRequest.ItemPriceRequest("中天", new BigDecimal("3280"), null)))));
    }

    private QuoteSheetResponse response() {
        return new QuoteSheetResponse(9L, "9", "9月9日报单", null, "云潮筝鸣府",
                LocalDate.of(2026, 9, 9), LocalDate.of(2026, 9, 10), "9:30 上午", new BigDecimal("30"),
                false, false, "报价", null, List.of(), List.of(), null, null, 3L);
    }
}
