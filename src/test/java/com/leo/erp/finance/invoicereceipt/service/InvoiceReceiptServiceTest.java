package com.leo.erp.finance.invoicereceipt.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.finance.invoicereceipt.domain.entity.InvoiceReceipt;
import com.leo.erp.finance.invoicereceipt.domain.entity.InvoiceReceiptItem;
import com.leo.erp.finance.invoicereceipt.repository.InvoiceReceiptRepository;
import com.leo.erp.finance.invoicereceipt.mapper.InvoiceReceiptMapper;
import com.leo.erp.finance.invoicereceipt.web.dto.InvoiceReceiptItemRequest;
import com.leo.erp.finance.invoicereceipt.web.dto.InvoiceReceiptRequest;
import com.leo.erp.finance.invoicereceipt.web.dto.InvoiceReceiptResponse;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrder;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.verify;
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
        PurchaseOrderItem sourceItem = buildPurchaseOrderItem(201L, "M-1", new BigDecimal("0.300"), new BigDecimal("1000.00"));

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

    @Test
    void createRejectsDuplicateReceiveNo() {
        when(repository.existsByReceiveNoAndDeletedFlagFalse("SP-DUP")).thenReturn(true);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.create(buildRequest(
                "SP-DUP", 201L, new BigDecimal("0.300"), new BigDecimal("3333.33"), new BigDecimal("1000.00")
        )));

        assertEquals("收票单号已存在", exception.getMessage());
    }

    @Test
    void createRejectsMissingSourcePurchaseOrderItemId() {
        when(repository.existsByReceiveNoAndDeletedFlagFalse("SP-MISSING")).thenReturn(false);

        InvoiceReceiptRequest request = new InvoiceReceiptRequest(
                "SP-MISSING", "INV-001", "供应商A", null,
                LocalDate.of(2026, 4, 26), "增值税专票",
                new BigDecimal("1000.00"), BigDecimal.ZERO,
                "草稿", "财务A", null,
                List.of(new InvoiceReceiptItemRequest(
                        "PO-001", null, "M-1", "品牌A", "品类A", "材质A", "规格A",
                        null, "吨", "仓库A", null, 1, "件",
                        new BigDecimal("0.300"), 1, new BigDecimal("0.300"),
                        new BigDecimal("3333.33"), new BigDecimal("1000.00")
                ))
        );

        BusinessException exception = assertThrows(BusinessException.class, () -> service.create(request));

        assertEquals("第1行来源采购订单明细不能为空", exception.getMessage());
    }

    @Test
    void createRejectsSourcePurchaseOrderItemNotFound() {
        when(repository.existsByReceiveNoAndDeletedFlagFalse("SP-NOTFOUND")).thenReturn(false);
        when(purchaseOrderItemQueryService.findActiveByIdIn(anyCollection())).thenReturn(List.of());

        BusinessException exception = assertThrows(BusinessException.class, () -> service.create(buildRequest(
                "SP-NOTFOUND", 999L, new BigDecimal("0.300"), new BigDecimal("3333.33"), new BigDecimal("1000.00")
        )));

        assertEquals("第1行来源采购订单明细不存在", exception.getMessage());
    }

    @Test
    void createRejectsUnauditedSourcePurchaseOrder() {
        PurchaseOrderItem sourceItem = buildPurchaseOrderItem(202L, "M-1", new BigDecimal("0.300"), new BigDecimal("1000.00"));
        sourceItem.getPurchaseOrder().setStatus(StatusConstants.DRAFT);

        when(repository.existsByReceiveNoAndDeletedFlagFalse("SP-DRAFT-PO")).thenReturn(false);
        when(purchaseOrderItemQueryService.findActiveByIdIn(anyCollection())).thenReturn(List.of(sourceItem));

        BusinessException exception = assertThrows(BusinessException.class, () -> service.create(buildRequest(
                "SP-DRAFT-PO", 202L, new BigDecimal("0.300"), new BigDecimal("3333.33"), new BigDecimal("1000.00")
        )));

        assertEquals("第1行来源采购订单未审核，不能收票", exception.getMessage());
    }

    @Test
    void createRejectsSourcePurchaseOrderSupplierMismatch() {
        PurchaseOrderItem sourceItem = buildPurchaseOrderItem(203L, "M-1", new BigDecimal("0.300"), new BigDecimal("1000.00"));
        sourceItem.getPurchaseOrder().setSupplierName("供应商B");

        when(repository.existsByReceiveNoAndDeletedFlagFalse("SP-SUPPLIER")).thenReturn(false);
        when(purchaseOrderItemQueryService.findActiveByIdIn(anyCollection())).thenReturn(List.of(sourceItem));

        BusinessException exception = assertThrows(BusinessException.class, () -> service.create(buildRequest(
                "SP-SUPPLIER", 203L, new BigDecimal("0.300"), new BigDecimal("3333.33"), new BigDecimal("1000.00")
        )));

        assertEquals("第1行来源采购订单供应商与收票单不一致", exception.getMessage());
    }

    @Test
    void createRejectsSourcePurchaseOrderWithBlankOrderNo() {
        PurchaseOrderItem sourceItem = new PurchaseOrderItem();
        sourceItem.setId(202L);
        PurchaseOrder order = new PurchaseOrder();
        order.setId(2202L);
        order.setOrderNo("  ");
        sourceItem.setPurchaseOrder(order);
        sourceItem.setMaterialCode("M-1");
        sourceItem.setBrand("品牌A");
        sourceItem.setCategory("品类A");
        sourceItem.setMaterial("材质A");
        sourceItem.setSpec("规格A");
        sourceItem.setUnit("吨");
        sourceItem.setQuantity(1);
        sourceItem.setQuantityUnit("件");
        sourceItem.setPieceWeightTon(new BigDecimal("0.300"));
        sourceItem.setPiecesPerBundle(1);
        sourceItem.setWeightTon(new BigDecimal("0.300"));
        sourceItem.setUnitPrice(new BigDecimal("3333.33"));
        sourceItem.setAmount(new BigDecimal("1000.00"));

        when(repository.existsByReceiveNoAndDeletedFlagFalse("SP-BLANK")).thenReturn(false);
        when(purchaseOrderItemQueryService.findActiveByIdIn(anyCollection())).thenReturn(List.of(sourceItem));

        BusinessException exception = assertThrows(BusinessException.class, () -> service.create(buildRequest(
                "SP-BLANK", 202L, new BigDecimal("0.300"), new BigDecimal("3333.33"), new BigDecimal("1000.00")
        )));

        assertEquals("第1行来源采购订单不存在", exception.getMessage());
    }

    @Test
    void createRejectsSourcePurchaseOrderItemAmountExceeded() {
        PurchaseOrderItem sourceItem = buildPurchaseOrderItem(204L, "M-1", new BigDecimal("1.100"), new BigDecimal("6000.00"));

        when(repository.existsByReceiveNoAndDeletedFlagFalse("SP-AMT-EXCEED")).thenReturn(false);
        when(purchaseOrderItemQueryService.findActiveByIdIn(anyCollection())).thenReturn(List.of(sourceItem));
        when(repository.summarizeAllocatedBySourcePurchaseOrderItemIds(anyCollection(), nullable(Long.class)))
                .thenReturn(List.of(summary(204L, "0.000", "5500.00")));

        BusinessException exception = assertThrows(BusinessException.class, () -> service.create(buildRequest(
                "SP-AMT-EXCEED", 204L, new BigDecimal("0.000"), new BigDecimal("0.00"), new BigDecimal("0.00")
        )));

        assertEquals("第1行来源采购订单明细可收票金额不足", exception.getMessage());
    }

    @Test
    void updateRejectsDuplicateReceiveNoWhenChanged() {
        InvoiceReceipt existing = new InvoiceReceipt();
        existing.setId(1L);
        existing.setReceiveNo("SP-OLD");
        existing.setDeletedFlag(false);
        existing.setItems(new ArrayList<>());

        PurchaseOrderItem sourceItem = buildPurchaseOrderItem(201L, "M-1", new BigDecimal("0.300"), new BigDecimal("1000.00"));

        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(existing));
        when(purchaseOrderItemQueryService.findActiveByIdIn(anyCollection())).thenReturn(List.of(sourceItem));
        when(repository.summarizeAllocatedBySourcePurchaseOrderItemIds(anyCollection(), anyLong()))
                .thenReturn(List.of());
        when(companySettingService.resolveCurrentTaxRate()).thenReturn(new BigDecimal("0.13"));
        when(idGenerator.nextId()).thenReturn(1L, 2L);
        when(repository.save(any(InvoiceReceipt.class))).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toResponse(any(InvoiceReceipt.class))).thenAnswer(invocation -> {
            InvoiceReceipt entity = invocation.getArgument(0);
            return new InvoiceReceiptResponse(
                    entity.getId(), entity.getReceiveNo(), entity.getInvoiceNo(),
                    entity.getSupplierName(), entity.getInvoiceTitle(), entity.getInvoiceDate(),
                    entity.getInvoiceType(), entity.getAmount(), entity.getTaxAmount(),
                    entity.getStatus(), entity.getOperatorName(), entity.getRemark(), List.of()
            );
        });

        InvoiceReceiptRequest request = buildRequest(
                "SP-DUP", 201L, new BigDecimal("0.300"), new BigDecimal("3333.33"), new BigDecimal("1000.00")
        );

        service.update(1L, request);

        assertThat(existing.getReceiveNo()).isEqualTo("SP-OLD");
    }

    @Test
    void updateAllowsSameReceiveNo() {
        InvoiceReceipt existing = new InvoiceReceipt();
        existing.setId(1L);
        existing.setReceiveNo("SP-SAME");
        existing.setInvoiceNo("INV-001");
        existing.setSupplierName("供应商A");
        existing.setInvoiceDate(LocalDate.of(2026, 4, 26));
        existing.setInvoiceType("增值税专票");
        existing.setAmount(BigDecimal.ZERO);
        existing.setTaxAmount(BigDecimal.ZERO);
        existing.setStatus("草稿");
        existing.setOperatorName("财务A");
        existing.setDeletedFlag(false);
        existing.setItems(new ArrayList<>());

        PurchaseOrderItem sourceItem = buildPurchaseOrderItem(201L, "M-1", new BigDecimal("0.300"), new BigDecimal("1000.00"));

        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(existing));
        when(purchaseOrderItemQueryService.findActiveByIdIn(anyCollection())).thenReturn(List.of(sourceItem));
        when(repository.summarizeAllocatedBySourcePurchaseOrderItemIds(anyCollection(), anyLong()))
                .thenReturn(List.of());
        when(companySettingService.resolveCurrentTaxRate()).thenReturn(new BigDecimal("0.13"));
        when(idGenerator.nextId()).thenReturn(1L, 2L);
        when(repository.save(any(InvoiceReceipt.class))).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toResponse(any(InvoiceReceipt.class))).thenAnswer(invocation -> {
            InvoiceReceipt entity = invocation.getArgument(0);
            return new InvoiceReceiptResponse(
                    entity.getId(), entity.getReceiveNo(), entity.getInvoiceNo(),
                    entity.getSupplierName(), entity.getInvoiceTitle(), entity.getInvoiceDate(),
                    entity.getInvoiceType(), entity.getAmount(), entity.getTaxAmount(),
                    entity.getStatus(), entity.getOperatorName(), entity.getRemark(), List.of()
            );
        });

        InvoiceReceiptRequest request = buildRequest(
                "SP-SAME", 201L, new BigDecimal("0.300"), new BigDecimal("3333.33"), new BigDecimal("1000.00")
        );

        InvoiceReceiptResponse result = service.update(1L, request);

        assertThat(result).isNotNull();
        assertThat(result.receiveNo()).isEqualTo("SP-SAME");
    }

    @Test
    void shouldSetInvoiceTitleToSupplierNameWhenNull() {
        PurchaseOrderItem sourceItem = buildPurchaseOrderItem(201L, "M-1", new BigDecimal("0.300"), new BigDecimal("1000.00"));

        when(repository.existsByReceiveNoAndDeletedFlagFalse("SP-TITLE")).thenReturn(false);
        when(purchaseOrderItemQueryService.findActiveByIdIn(anyCollection())).thenReturn(List.of(sourceItem));
        when(repository.summarizeAllocatedBySourcePurchaseOrderItemIds(anyCollection(), nullable(Long.class)))
                .thenReturn(List.of());
        when(companySettingService.resolveCurrentTaxRate()).thenReturn(new BigDecimal("0.13"));
        when(idGenerator.nextId()).thenReturn(1L, 2L);

        ArgumentCaptor<InvoiceReceipt> captor = ArgumentCaptor.forClass(InvoiceReceipt.class);
        when(repository.save(captor.capture())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mapper.toResponse(any(InvoiceReceipt.class))).thenAnswer(invocation -> {
            InvoiceReceipt entity = invocation.getArgument(0);
            return new InvoiceReceiptResponse(
                    entity.getId(), entity.getReceiveNo(), entity.getInvoiceNo(),
                    entity.getSupplierName(), entity.getInvoiceTitle(), entity.getInvoiceDate(),
                    entity.getInvoiceType(), entity.getAmount(), entity.getTaxAmount(),
                    entity.getStatus(), entity.getOperatorName(), entity.getRemark(), List.of()
            );
        });

        InvoiceReceiptRequest request = new InvoiceReceiptRequest(
                "SP-TITLE", "INV-001", "供应商A", null,
                LocalDate.of(2026, 4, 26), "增值税专票",
                new BigDecimal("1000.00"), BigDecimal.ZERO,
                "草稿", "财务A", null,
                List.of(new InvoiceReceiptItemRequest(
                        "PO-001", 201L, "M-1", "品牌A", "品类A", "材质A", "规格A",
                        null, "吨", "仓库A", null, 1, "件",
                        new BigDecimal("0.300"), 1, new BigDecimal("0.300"),
                        new BigDecimal("3333.33"), new BigDecimal("1000.00")
                ))
        );

        service.create(request);

        assertEquals("供应商A", captor.getValue().getInvoiceTitle());
    }

    @Test
    void shouldSetInvoiceTitleToSupplierNameWhenBlank() {
        PurchaseOrderItem sourceItem = buildPurchaseOrderItem(201L, "M-1", new BigDecimal("0.300"), new BigDecimal("1000.00"));

        when(repository.existsByReceiveNoAndDeletedFlagFalse("SP-BLANK-TITLE")).thenReturn(false);
        when(purchaseOrderItemQueryService.findActiveByIdIn(anyCollection())).thenReturn(List.of(sourceItem));
        when(repository.summarizeAllocatedBySourcePurchaseOrderItemIds(anyCollection(), nullable(Long.class)))
                .thenReturn(List.of());
        when(companySettingService.resolveCurrentTaxRate()).thenReturn(new BigDecimal("0.13"));
        when(idGenerator.nextId()).thenReturn(1L, 2L);

        ArgumentCaptor<InvoiceReceipt> captor = ArgumentCaptor.forClass(InvoiceReceipt.class);
        when(repository.save(captor.capture())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mapper.toResponse(any(InvoiceReceipt.class))).thenAnswer(invocation -> {
            InvoiceReceipt entity = invocation.getArgument(0);
            return new InvoiceReceiptResponse(
                    entity.getId(), entity.getReceiveNo(), entity.getInvoiceNo(),
                    entity.getSupplierName(), entity.getInvoiceTitle(), entity.getInvoiceDate(),
                    entity.getInvoiceType(), entity.getAmount(), entity.getTaxAmount(),
                    entity.getStatus(), entity.getOperatorName(), entity.getRemark(), List.of()
            );
        });

        InvoiceReceiptRequest request = new InvoiceReceiptRequest(
                "SP-BLANK-TITLE", "INV-001", "供应商A", "  ",
                LocalDate.of(2026, 4, 26), "增值税专票",
                new BigDecimal("1000.00"), BigDecimal.ZERO,
                "草稿", "财务A", null,
                List.of(new InvoiceReceiptItemRequest(
                        "PO-001", 201L, "M-1", "品牌A", "品类A", "材质A", "规格A",
                        null, "吨", "仓库A", null, 1, "件",
                        new BigDecimal("0.300"), 1, new BigDecimal("0.300"),
                        new BigDecimal("3333.33"), new BigDecimal("1000.00")
                ))
        );

        service.create(request);

        assertEquals("供应商A", captor.getValue().getInvoiceTitle());
    }

    @Test
    void shouldReturnDetailForExistingInvoiceReceipt() {
        InvoiceReceipt existing = new InvoiceReceipt();
        existing.setId(1L);
        existing.setReceiveNo("SP-001");
        existing.setInvoiceNo("INV-001");
        existing.setSupplierName("供应商A");
        existing.setInvoiceTitle("发票抬头");
        existing.setInvoiceDate(LocalDate.of(2026, 4, 26));
        existing.setInvoiceType("增值税专票");
        existing.setAmount(new BigDecimal("1000.00"));
        existing.setTaxAmount(new BigDecimal("130.00"));
        existing.setStatus("草稿");
        existing.setOperatorName("财务A");
        existing.setDeletedFlag(false);
        existing.setItems(new ArrayList<>());

        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(existing));
        when(mapper.toResponse(existing)).thenReturn(
                new InvoiceReceiptResponse(1L, "SP-001", "INV-001", "供应商A", "发票抬头",
                        LocalDate.of(2026, 4, 26), "增值税专票", new BigDecimal("1000.00"),
                        new BigDecimal("130.00"), "草稿", "财务A", null, null)
        );

        InvoiceReceiptResponse result = service.detail(1L);

        assertThat(result).isNotNull();
        assertThat(result.receiveNo()).isEqualTo("SP-001");
    }

    @Test
    void shouldReturnPageResults() {
        InvoiceReceipt receipt = new InvoiceReceipt();
        receipt.setId(1L);
        receipt.setReceiveNo("SP-001");
        Page<InvoiceReceipt> page = new PageImpl<>(List.of(receipt));
        when(repository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(Pageable.class)))
                .thenReturn(page);
        when(mapper.toResponse(receipt)).thenReturn(
                new InvoiceReceiptResponse(1L, "SP-001", "INV-001", "供应商A", null,
                        LocalDate.of(2026, 4, 26), "增值税专票", BigDecimal.ZERO,
                        BigDecimal.ZERO, "草稿", "财务A", null, null)
        );

        Page<InvoiceReceiptResponse> result = service.page(
                new com.leo.erp.common.api.PageQuery(0, 10, "id", "desc"),
                com.leo.erp.common.api.PageFilter.of(null, null, null, null)
        );

        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    void shouldReturnSearchResults() {
        InvoiceReceipt receipt = new InvoiceReceipt();
        receipt.setId(1L);
        receipt.setReceiveNo("SP-001");
        Page<InvoiceReceipt> page = new PageImpl<>(List.of(receipt));
        when(repository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(Pageable.class)))
                .thenReturn(page);
        when(mapper.toResponse(receipt)).thenReturn(
                new InvoiceReceiptResponse(1L, "SP-001", "INV-001", "供应商A", null,
                        LocalDate.of(2026, 4, 26), "增值税专票", BigDecimal.ZERO,
                        BigDecimal.ZERO, "草稿", "财务A", null, null)
        );

        List<InvoiceReceiptResponse> results = service.search("SP", 10);

        assertThat(results).isNotNull();
    }

    @Test
    void shouldAllowValidStatusTransitionDraftToInvoiceReceived() {
        PurchaseOrderItem sourceItem = buildPurchaseOrderItem(201L, "M-1", new BigDecimal("2.000"), new BigDecimal("6000.00"));

        InvoiceReceipt existing = new InvoiceReceipt();
        existing.setId(1L);
        existing.setReceiveNo("SP-001");
        existing.setInvoiceNo("INV-001");
        existing.setSupplierName("供应商A");
        existing.setInvoiceDate(LocalDate.of(2026, 4, 26));
        existing.setInvoiceType("增值税专票");
        existing.setAmount(new BigDecimal("1000.00"));
        existing.setTaxAmount(new BigDecimal("130.00"));
        existing.setStatus("草稿");
        existing.setOperatorName("财务A");
        existing.setDeletedFlag(false);

        InvoiceReceiptItem item = new InvoiceReceiptItem();
        item.setId(200L);
        item.setLineNo(1);
        item.setSourcePurchaseOrderItemId(201L);
        item.setMaterialCode("M-1");
        item.setBrand("品牌A");
        item.setCategory("品类A");
        item.setMaterial("材质A");
        item.setSpec("规格A");
        item.setUnit("吨");
        item.setQuantity(1);
        item.setQuantityUnit("件");
        item.setPieceWeightTon(new BigDecimal("0.300"));
        item.setPiecesPerBundle(1);
        item.setWeightTon(new BigDecimal("0.300"));
        item.setUnitPrice(new BigDecimal("3333.33"));
        item.setAmount(new BigDecimal("1000.00"));
        existing.setItems(new ArrayList<>(List.of(item)));

        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(existing));
        when(purchaseOrderItemQueryService.findActiveByIdIn(anyCollection())).thenReturn(List.of(sourceItem));
        when(repository.summarizeAllocatedBySourcePurchaseOrderItemIds(anyCollection(), anyLong()))
                .thenReturn(List.of());
        when(repository.save(any(InvoiceReceipt.class))).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toResponse(any(InvoiceReceipt.class))).thenReturn(
                new InvoiceReceiptResponse(1L, "SP-001", "INV-001", "供应商A", null,
                        LocalDate.of(2026, 4, 26), "增值税专票", new BigDecimal("1000.00"),
                        new BigDecimal("130.00"), "已收票", "财务A", null, null)
        );

        InvoiceReceiptResponse result = service.updateStatus(1L, "已收票");

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo("已收票");
    }

    @Test
    void shouldRejectInvalidStatusTransition() {
        InvoiceReceipt existing = new InvoiceReceipt();
        existing.setId(1L);
        existing.setStatus("草稿");
        existing.setDeletedFlag(false);
        existing.setItems(new ArrayList<>());
        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(existing));

        assertThrows(BusinessException.class, () -> service.updateStatus(1L, "已审核"));
    }

    @Test
    void shouldHandleBeforeStatusUpdateForNonInvoiceReceivedStatus() {
        InvoiceReceipt existing = new InvoiceReceipt();
        existing.setId(1L);
        existing.setReceiveNo("SP-001");
        existing.setInvoiceNo("INV-001");
        existing.setSupplierName("供应商A");
        existing.setInvoiceDate(LocalDate.of(2026, 4, 26));
        existing.setInvoiceType("增值税专票");
        existing.setAmount(new BigDecimal("1000.00"));
        existing.setTaxAmount(new BigDecimal("130.00"));
        existing.setStatus("已收票");
        existing.setOperatorName("财务A");
        existing.setDeletedFlag(false);
        existing.setItems(new ArrayList<>());

        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(existing));
        when(repository.save(any(InvoiceReceipt.class))).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toResponse(any(InvoiceReceipt.class))).thenReturn(
                new InvoiceReceiptResponse(1L, "SP-001", "INV-001", "供应商A", null,
                        LocalDate.of(2026, 4, 26), "增值税专票", new BigDecimal("1000.00"),
                        new BigDecimal("130.00"), "草稿", "财务A", null, null)
        );

        InvoiceReceiptResponse result = service.updateStatus(1L, "草稿");

        assertThat(result).isNotNull();
    }

    @Test
    void shouldDeleteSuccessfully() {
        InvoiceReceipt existing = new InvoiceReceipt();
        existing.setId(1L);
        existing.setStatus("草稿");
        existing.setDeletedFlag(false);
        existing.setItems(new ArrayList<>());
        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(existing));
        when(repository.save(any(InvoiceReceipt.class))).thenAnswer(inv -> inv.getArgument(0));

        service.delete(1L);

        verify(repository).save(any(InvoiceReceipt.class));
    }

    @Test
    void shouldRejectDeleteWhenStatusIsProtected() {
        InvoiceReceipt existing = new InvoiceReceipt();
        existing.setId(1L);
        existing.setStatus(StatusConstants.AUDITED);
        existing.setDeletedFlag(false);
        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(existing));

        assertThrows(BusinessException.class, () -> service.delete(1L));
    }

    @Test
    void shouldRejectWhenEntityNotFound() {
        when(repository.findByIdAndDeletedFlagFalse(999L)).thenReturn(Optional.empty());

        assertThrows(BusinessException.class, () -> service.detail(999L));
    }

    private PurchaseOrderItem buildPurchaseOrderItem(Long id, String materialCode, BigDecimal weightTon, BigDecimal amount) {
        PurchaseOrderItem item = new PurchaseOrderItem();
        item.setId(id);
        PurchaseOrder order = new PurchaseOrder();
        order.setId(2000L + id);
        order.setOrderNo("PO-001");
        order.setStatus(StatusConstants.AUDITED);
        order.setSupplierName("供应商A");
        item.setPurchaseOrder(order);
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
