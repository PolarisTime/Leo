package com.leo.erp.common.charge.service;

import com.leo.erp.common.charge.api.DocumentChargeItemRequest;
import com.leo.erp.common.charge.domain.entity.DocumentChargeItem;
import com.leo.erp.common.charge.repository.DocumentChargeItemRepository;
import com.leo.erp.common.support.ModuleKeys;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 费用行同步的排序语义: 请求数组顺序即最终 {@code line_no}(拖拽重排后按新顺序落库),
 * 并且换位时先做负值占位再写最终值 —— 未删除行上 {@code (module_key, document_id, line_no)}
 * 有唯一偏索引, 直接逐行改号会在同一 flush 内瞬时撞唯一键(线上表现为保存 500)。
 */
@ExtendWith(MockitoExtension.class)
class DocumentChargeItemServiceTest {

    private static final long DOCUMENT_ID = 9L;

    @Mock
    private DocumentChargeItemRepository repository;

    @Mock
    private SnowflakeIdGenerator idGenerator;

    private DocumentChargeItemService service() {
        return new DocumentChargeItemService(repository, idGenerator);
    }

    private static DocumentChargeItem existing(long id, int lineNo) {
        DocumentChargeItem item = new DocumentChargeItem();
        item.setId(id);
        item.setModuleKey(ModuleKeys.SALES_ORDER);
        item.setDocumentId(DOCUMENT_ID);
        item.setLineNo(lineNo);
        item.setChargeName("费用" + id);
        item.setAmount(new BigDecimal("10.00"));
        return item;
    }

    private static DocumentChargeItemRequest request(Long id, String name) {
        return new DocumentChargeItemRequest(id, name, null, new BigDecimal("10.00"), null, null);
    }

    private void stubExisting(DocumentChargeItem... items) {
        when(repository.findByModuleKeyAndDocumentIdAndDeletedFlagFalseOrderByLineNoAscIdAsc(
                ModuleKeys.SALES_ORDER, DOCUMENT_ID)).thenReturn(List.of(items));
    }

    /** 换位(1↔2): 最终行号按请求顺序, 且写最终值之前所有既有行已被挪成互不冲突的负值占位。 */
    @Test
    void sync_reorderedRows_assignsRequestOrderAfterVacating() {
        DocumentChargeItem first = existing(1L, 1);
        DocumentChargeItem second = existing(2L, 2);
        stubExisting(first, second);
        List<List<Integer>> lineNosAtFlush = new ArrayList<>();
        doAnswer(invocation -> {
            lineNosAtFlush.add(List.of(first.getLineNo(), second.getLineNo()));
            return null;
        }).when(repository).flush();

        // 提交顺序 [2, 1]: 两行互换位置
        service().sync(ModuleKeys.SALES_ORDER, DOCUMENT_ID,
                List.of(request(2L, "费用2"), request(1L, "费用1")));

        assertThat(lineNosAtFlush).hasSize(1);
        assertThat(lineNosAtFlush.get(0))
                .as("占位阶段必须全部为负, 否则 UPDATE 期间会与对方仍占用的行号相撞")
                .allSatisfy(lineNo -> assertThat(lineNo).isNegative());
        assertThat(second.getLineNo()).isEqualTo(1);
        assertThat(first.getLineNo()).isEqualTo(2);
    }

    /** 顺序未变: 不腾挪、不产生多余 flush 与多余写入。 */
    @Test
    void sync_sameOrder_doesNotVacate() {
        DocumentChargeItem first = existing(1L, 1);
        DocumentChargeItem second = existing(2L, 2);
        stubExisting(first, second);

        service().sync(ModuleKeys.SALES_ORDER, DOCUMENT_ID,
                List.of(request(1L, "费用1"), request(2L, "费用2")));

        verify(repository, never()).flush();
        assertThat(first.getLineNo()).isEqualTo(1);
        assertThat(second.getLineNo()).isEqualTo(2);
    }

    /** 新行顶替"将被软删行"占用的行号: 同样必须先腾挪, 否则 INSERT 会撞旧行尚未释放的行号。 */
    @Test
    void sync_newRowTakesStaleRowLineNo_vacates() {
        DocumentChargeItem stale = existing(1L, 1);
        stubExisting(stale);
        when(idGenerator.nextId()).thenReturn(100L);
        List<Integer> staleLineNoAtFlush = new ArrayList<>();
        doAnswer(invocation -> {
            staleLineNoAtFlush.add(stale.getLineNo());
            return null;
        }).when(repository).flush();

        service().sync(ModuleKeys.SALES_ORDER, DOCUMENT_ID, List.of(request(null, "新费用")));

        assertThat(staleLineNoAtFlush).hasSize(1);
        assertThat(staleLineNoAtFlush.get(0)).isNegative();
        assertThat(stale.isDeletedFlag()).isTrue();
        assertThat(stale.getLineNo()).isNegative();
        verify(repository, times(1)).flush();
    }

    /** 行数增加但无需换位(顺序为 1,2,+新行): 不腾挪。 */
    @Test
    void sync_appendNewRowAtEnd_doesNotVacate() {
        DocumentChargeItem first = existing(1L, 1);
        stubExisting(first);
        when(idGenerator.nextId()).thenReturn(100L);

        service().sync(ModuleKeys.SALES_ORDER, DOCUMENT_ID,
                List.of(request(1L, "费用1"), request(null, "新费用")));

        verify(repository, never()).flush();
        assertThat(first.getLineNo()).isEqualTo(1);
    }
}
