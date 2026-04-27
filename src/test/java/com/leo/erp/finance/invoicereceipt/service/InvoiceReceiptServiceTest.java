package com.leo.erp.finance.invoicereceipt.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.finance.invoicereceipt.domain.entity.InvoiceReceipt;
import com.leo.erp.finance.invoicereceipt.repository.InvoiceReceiptRepository;
import com.leo.erp.finance.invoicereceipt.mapper.InvoiceReceiptMapper;
import com.leo.erp.finance.invoicereceipt.web.dto.InvoiceReceiptItemRequest;
import com.leo.erp.finance.invoicereceipt.web.dto.InvoiceReceiptRequest;
import com.leo.erp.finance.invoicereceipt.web.dto.InvoiceReceiptResponse;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrderItem;
import com.leo.erp.purchase.order.service.PurchaseOrderItemQueryService;
import com.leo.erp.security.permission.WorkflowTransitionGuard;
import com.leo.erp.system.company.service.CompanySettingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvoiceReceiptServiceTest {

    @Mock
    private InvoiceReceiptRepository repository;

    @Mock
    private PurchaseOrderItemQueryService purchaseOrderItemQueryService;

    @Mock
    private SnowflakeIdGenerator idGenerator;

    @Mock
    private InvoiceReceiptMapper mapper;

    @Mock
    private CompanySettingService companySettingService;

    @Mock
    private WorkflowTransitionGuard workflowTransitionGuard;

    @InjectMocks
    private InvoiceReceiptService service;

    @Test
    void createRejectsOverAllocatedPurchaseOrderItem() {
        PurchaseOrderItem sourceItem = buildPurchaseOrderItem(201L, "M-1", new BigDecimal("2.000"), new BigDecimal("6000.00"));

        when(repository.existsByReceiveNoAndDeletedFlagFalse("SP-NEW")).thenReturn(false);
        when(purchaseOrderItemQueryService.findActiveByIdIn(anyCollection())).thenReturn(List.of(sourceItem));
        when(repository.summarizeAllocatedBySourcePurchaseOrderItemIds(anyCollection(), nullable(Long.class)))
                .thenReturn(List.of(summary(201L, "1.600", "4800.00")));

        BusinessException exception = assertThrows(BusinessException.class, () -> service.create(buildRequest(
                "SP-NEW",
                201L,
                new BigDecimal("0.500"),
                new BigDecimal("3000.00"),
                new BigDecimal("1500.00")
        )));

        assertEquals("第1行来源采购订单明细可收票吨位不足", exception.getMessage());
    }

    @Test
    void createRecalculatesAmountFromRoundedWeight() {
        PurchaseOrderItem sourceItem = buildPurchaseOrderItem(201L, "M-1", new BigDecimal("2.000"), new BigDecimal("6666.66"));

        when(repository.existsByReceiveNoAndDeletedFlagFalse("SP-ROUND")).thenReturn(false);
        when(purchaseOrderItemQueryService.findActiveByIdIn(anyCollection())).thenReturn(List.of(sourceItem));
        when(repository.summarizeAllocatedBySourcePurchaseOrderItemIds(anyCollection(), anyLong()))
                .thenReturn(List.of());
        when(companySettingService.resolveCurrentTaxRate()).thenReturn(new BigDecimal("0.13"));
        when(idGenerator.nextId()).thenReturn(1L, 2L);
        when(mapper.toResponse(any(InvoiceReceipt.class))).thenAnswer(invocation -> {
            InvoiceReceipt entity = invocation.getArgument(0);
            return new InvoiceReceiptResponse(
                    entity.getId(),
                    entity.getReceiveNo(),
                    entity.getInvoiceNo(),
                    entity.getSourcePurchaseOrderNos(),
                    entity.getSupplierName(),
                    entity.getInvoiceTitle(),
                    entity.getInvoiceDate(),
                    entity.getInvoiceType(),
                    entity.getAmount(),
                    entity.getTaxAmount(),
                    entity.getStatus(),
                    entity.getOperatorName(),
                    entity.getRemark(),
                    List.of()
            );
        });

        ArgumentCaptor<InvoiceReceipt> captor = ArgumentCaptor.forClass(InvoiceReceipt.class);
        when(repository.save(captor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        service.create(buildRequest(
                "SP-ROUND",
                201L,
                new BigDecimal("0.300"),
                new BigDecimal("3333.33"),
                new BigDecimal("1000.00")
        ));

        assertEquals(new BigDecimal("1000.00"), captor.getValue().getAmount());
        assertEquals(new BigDecimal("130.00"), captor.getValue().getTaxAmount());
    }

    @Test
    void createRejectsDeclaredAmountMismatch() {
        PurchaseOrderItem sourceItem = buildPurchaseOrderItem(201L, "M-1", new BigDecimal("2.000"), new BigDecimal("6666.66"));

        when(repository.existsByReceiveNoAndDeletedFlagFalse("SP-AMOUNT")).thenReturn(false);
        when(purchaseOrderItemQueryService.findActiveByIdIn(anyCollection())).thenReturn(List.of(sourceItem));
        when(repository.summarizeAllocatedBySourcePurchaseOrderItemIds(anyCollection(), nullable(Long.class)))
                .thenReturn(List.of());

        BusinessException exception = assertThrows(BusinessException.class, () -> service.create(buildRequest(
                "SP-AMOUNT",
                201L,
                new BigDecimal("0.300"),
                new BigDecimal("3333.33"),
                new BigDecimal("999.99")
        )));

        assertEquals("收票与明细计算结果不一致", exception.getMessage());
    }

    private PurchaseOrderItem buildPurchaseOrderItem(Long id, String materialCode, BigDecimal weightTon, BigDecimal amount) {
        PurchaseOrderItem item = new PurchaseOrderItem();
        item.setId(id);
        item.setMaterialCode(materialCode);
        item.setBrand("品牌A");
        item.setCategory("品类A");
        item.setMaterial("材质A");
        item.setSpec("规格A");
        item.setUnit("吨");
        item.setQuantity(1);
        item.setQuantityUnit("件");
        item.setPieceWeightTon(weightTon);
        item.setPiecesPerBundle(1);
        item.setWeightTon(weightTon);
        item.setUnitPrice(amount.divide(weightTon, 2, RoundingMode.HALF_UP));
        item.setAmount(amount);
        return item;
    }

    private InvoiceReceiptRepository.SourceAllocationSummary summary(Long itemId, String weightTon, String amount) {
        return new InvoiceReceiptRepository.SourceAllocationSummary() {
            @Override
            public Long getSourcePurchaseOrderItemId() {
                return itemId;
            }

            @Override
            public BigDecimal getTotalWeightTon() {
                return new BigDecimal(weightTon);
            }

            @Override
            public BigDecimal getTotalAmount() {
                return new BigDecimal(amount);
            }
        };
    }

    private InvoiceReceiptRequest buildRequest(String receiveNo,
                                               Long sourcePurchaseOrderItemId,
                                               BigDecimal weightTon,
                                               BigDecimal unitPrice,
                                               BigDecimal amount) {
        return new InvoiceReceiptRequest(
                receiveNo,
                "INV-001",
                "PO-001",
                "供应商A",
                null,
                LocalDate.of(2026, 4, 26),
                "增值税专票",
                amount,
                BigDecimal.ZERO,
                "草稿",
                "财务A",
                null,
                List.of(new InvoiceReceiptItemRequest(
                        "PO-001",
                        sourcePurchaseOrderItemId,
                        "M-1",
                        "品牌A",
                        "品类A",
                        "材质A",
                        "规格A",
                        null,
                        "吨",
                        "仓库A",
                        null,
                        1,
                        "件",
                        weightTon,
                        1,
                        weightTon,
                        unitPrice,
                        amount
                ))
        );
    }
}
