package com.leo.erp.common.charge.service;

import com.leo.erp.common.charge.domain.entity.DocumentChargeItem;
import com.leo.erp.common.charge.repository.DocumentChargeItemRepository;
import com.leo.erp.common.charge.web.dto.DocumentChargeItemRequest;
import com.leo.erp.common.charge.web.dto.DocumentChargeItemResponse;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentChargeItemServiceTest {

    @Test
    void syncShouldCreateUpdateReorderAndSoftDeleteMissingActiveItems() {
        DocumentChargeItemRepository repository = mock(DocumentChargeItemRepository.class);
        SnowflakeIdGenerator idGenerator = mock(SnowflakeIdGenerator.class);
        DocumentChargeItemService service = new DocumentChargeItemService(repository, idGenerator);
        DocumentChargeItem removed = chargeItem(10L, 2, "purchase-order", 1L, "运费", "PAYABLE", "20.00", true);
        DocumentChargeItem updated = chargeItem(11L, 1, "purchase-order", 1L, "卸货费", "PAYABLE", "30.00", true);

        when(repository.findByModuleKeyAndDocumentIdAndDeletedFlagFalseOrderByLineNoAscIdAsc("purchase-order", 1L))
                .thenReturn(List.of(updated, removed));
        when(idGenerator.nextId()).thenReturn(99L);
        when(repository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        List<DocumentChargeItemResponse> responses = service.sync(
                "purchase-order",
                1L,
                List.of(
                        new DocumentChargeItemRequest(
                                11L,
                                "  叉车费  ",
                                "PAYABLE",
                                "SUPPLIER",
                                7L,
                                "供应商A",
                                new BigDecimal("12.345"),
                                null,
                                "现场"
                        ),
                        new DocumentChargeItemRequest(
                                null,
                                "内部转运",
                                "INTERNAL",
                                null,
                                null,
                                null,
                                new BigDecimal("5"),
                                false,
                                null
                        )
                )
        );

        verify(repository).saveAll(anyList());
        assertThat(removed.isDeletedFlag()).isTrue();
        assertThat(updated.getLineNo()).isEqualTo(1);
        assertThat(updated.getChargeName()).isEqualTo("叉车费");
        assertThat(updated.getAmount()).isEqualByComparingTo("12.35");
        assertThat(updated.isBillable()).isTrue();
        assertThat(responses).extracting(DocumentChargeItemResponse::id).containsExactly(11L, 99L);
        assertThat(responses.get(1).lineNo()).isEqualTo(2);
        assertThat(responses.get(1).billable()).isFalse();
    }

    @Test
    void totalChargeAmountShouldCountOnlyBillableItemsForModuleDirection() {
        DocumentChargeItemService service = new DocumentChargeItemService(
                mock(DocumentChargeItemRepository.class),
                mock(SnowflakeIdGenerator.class)
        );
        DocumentChargeItem payable = chargeItem(1L, 1, "purchase-order", 1L, "运费", "PAYABLE", "10.235", true);
        DocumentChargeItem receivable = chargeItem(2L, 2, "purchase-order", 1L, "代收", "RECEIVABLE", "20.00", true);
        DocumentChargeItem notBillable = chargeItem(3L, 3, "purchase-order", 1L, "展示", "PAYABLE", "30.00", false);
        DocumentChargeItem deleted = chargeItem(4L, 4, "purchase-order", 1L, "删除", "PAYABLE", "40.00", true);
        deleted.setDeletedFlag(true);

        BigDecimal total = service.totalChargeAmount("purchase-order", List.of(payable, receivable, notBillable, deleted));

        assertThat(total).isEqualByComparingTo("10.24");
    }

    @Test
    void syncShouldRejectInvalidModuleNameDirectionAndAmount() {
        DocumentChargeItemService service = new DocumentChargeItemService(
                mock(DocumentChargeItemRepository.class),
                mock(SnowflakeIdGenerator.class)
        );

        assertThatThrownBy(() -> service.sync("unknown", 1L, List.of()))
                .hasMessageContaining("不支持费用明细");
        assertThatThrownBy(() -> service.sync("purchase-order", 1L, List.of(request("", "PAYABLE", "1.00"))))
                .hasMessageContaining("费用名称不能为空");
        assertThatThrownBy(() -> service.sync("purchase-order", 1L, List.of(request("运费", "INVALID", "1.00"))))
                .hasMessageContaining("费用方向不合法");
        assertThatThrownBy(() -> service.sync("purchase-order", 1L, List.of(request("运费", "PAYABLE", "-0.01"))))
                .hasMessageContaining("费用金额不能小于 0");
    }

    @Test
    void copyFromSourceShouldCreateMissingSourceItemsAndKeepExistingAdjustments() {
        DocumentChargeItemRepository repository = mock(DocumentChargeItemRepository.class);
        SnowflakeIdGenerator idGenerator = mock(SnowflakeIdGenerator.class);
        DocumentChargeItemService service = new DocumentChargeItemService(repository, idGenerator);
        DocumentChargeItem sourceFreight = chargeItem(11L, 1, "purchase-order", 1L, "运费", "PAYABLE", "100.00", true);
        sourceFreight.setSettlementPartyType("SUPPLIER");
        sourceFreight.setSettlementPartyId(7L);
        sourceFreight.setSettlementPartyName("供应商A");
        sourceFreight.setRemark("来源备注");
        DocumentChargeItem sourceUnload = chargeItem(12L, 2, "purchase-order", 1L, "卸货费", "PAYABLE", "80.00", true);
        DocumentChargeItem existingAdjusted = chargeItem(20L, 1, "purchase-inbound", 2L, "运费", "PAYABLE", "120.00", true);
        existingAdjusted.setSourceModuleKey("purchase-order");
        existingAdjusted.setSourceDocumentId(1L);
        existingAdjusted.setSourceChargeItemId(11L);

        when(repository.findByModuleKeyAndDocumentIdAndDeletedFlagFalseOrderByLineNoAscIdAsc("purchase-order", 1L))
                .thenReturn(List.of(sourceFreight, sourceUnload));
        when(repository.findByModuleKeyAndDocumentIdAndDeletedFlagFalseOrderByLineNoAscIdAsc("purchase-inbound", 2L))
                .thenReturn(List.of(existingAdjusted));
        when(idGenerator.nextId()).thenReturn(99L);
        when(repository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        List<DocumentChargeItemResponse> responses = service.copyFromSource(
                "purchase-order",
                1L,
                "purchase-inbound",
                2L
        );

        verify(repository).saveAll(anyList());
        assertThat(responses).extracting(DocumentChargeItemResponse::id).containsExactly(20L, 99L);
        assertThat(responses.get(0).amount()).isEqualByComparingTo("120.00");
        assertThat(responses.get(1)).satisfies(item -> {
            assertThat(item.chargeName()).isEqualTo("卸货费");
            assertThat(item.sourceModuleKey()).isEqualTo("purchase-order");
            assertThat(item.sourceDocumentId()).isEqualTo(1L);
            assertThat(item.sourceChargeItemId()).isEqualTo(12L);
        });
    }

    private DocumentChargeItemRequest request(String chargeName, String chargeDirection, String amount) {
        return new DocumentChargeItemRequest(
                null,
                chargeName,
                chargeDirection,
                null,
                null,
                null,
                new BigDecimal(amount),
                true,
                null
        );
    }

    private DocumentChargeItem chargeItem(Long id,
                                          Integer lineNo,
                                          String moduleKey,
                                          Long documentId,
                                          String chargeName,
                                          String chargeDirection,
                                          String amount,
                                          boolean billable) {
        DocumentChargeItem item = new DocumentChargeItem();
        item.setId(id);
        item.setLineNo(lineNo);
        item.setModuleKey(moduleKey);
        item.setDocumentId(documentId);
        item.setChargeName(chargeName);
        item.setChargeDirection(chargeDirection);
        item.setAmount(new BigDecimal(amount));
        item.setBillable(billable);
        return item;
    }
}
