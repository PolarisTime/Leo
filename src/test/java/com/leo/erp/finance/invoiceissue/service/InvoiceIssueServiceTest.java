package com.leo.erp.finance.invoiceissue.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.finance.invoiceissue.domain.entity.InvoiceIssue;
import com.leo.erp.finance.invoiceissue.repository.InvoiceIssueRepository;
import com.leo.erp.finance.invoiceissue.mapper.InvoiceIssueMapper;
import com.leo.erp.finance.invoiceissue.web.dto.InvoiceIssueItemRequest;
import com.leo.erp.finance.invoiceissue.web.dto.InvoiceIssueRequest;
import com.leo.erp.finance.invoiceissue.web.dto.InvoiceIssueResponse;
import com.leo.erp.sales.order.domain.entity.SalesOrderItem;
import com.leo.erp.sales.order.service.SalesOrderItemQueryService;
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
class InvoiceIssueServiceTest {

    @Mock
    private InvoiceIssueRepository repository;

    @Mock
    private SalesOrderItemQueryService salesOrderItemQueryService;

    @Mock
    private SnowflakeIdGenerator idGenerator;

    @Mock
    private InvoiceIssueMapper mapper;

    @Mock
    private CompanySettingService companySettingService;

    @InjectMocks
    private InvoiceIssueService service;

    @Test
    void createRejectsOverAllocatedSalesOrderItem() {
        SalesOrderItem sourceItem = buildSalesOrderItem(101L, "M-1", new BigDecimal("2.000"), new BigDecimal("6000.00"));

        when(repository.existsByIssueNoAndDeletedFlagFalse("KP-NEW")).thenReturn(false);
        when(salesOrderItemQueryService.findActiveByIdIn(anyCollection())).thenReturn(List.of(sourceItem));
        when(repository.summarizeAllocatedBySourceSalesOrderItemIds(anyCollection(), nullable(Long.class)))
                .thenReturn(List.of(summary(101L, "1.500", "4500.00")));

        BusinessException exception = assertThrows(BusinessException.class, () -> service.create(buildRequest(
                "KP-NEW",
                101L,
                new BigDecimal("0.600"),
                new BigDecimal("3000.00"),
                new BigDecimal("1800.00")
        )));

        assertEquals("第1行来源销售订单明细可开票吨位不足", exception.getMessage());
    }

    @Test
    void createRecalculatesAmountFromRoundedWeight() {
        SalesOrderItem sourceItem = buildSalesOrderItem(101L, "M-1", new BigDecimal("2.000"), new BigDecimal("6666.66"));

        when(repository.existsByIssueNoAndDeletedFlagFalse("KP-ROUND")).thenReturn(false);
        when(salesOrderItemQueryService.findActiveByIdIn(anyCollection())).thenReturn(List.of(sourceItem));
        when(repository.summarizeAllocatedBySourceSalesOrderItemIds(anyCollection(), anyLong()))
                .thenReturn(List.of());
        when(companySettingService.resolveCurrentTaxRate()).thenReturn(new BigDecimal("0.13"));
        when(idGenerator.nextId()).thenReturn(1L, 2L);
        when(mapper.toResponse(any(InvoiceIssue.class))).thenAnswer(invocation -> {
            InvoiceIssue entity = invocation.getArgument(0);
            return new InvoiceIssueResponse(
                    entity.getId(),
                    entity.getIssueNo(),
                    entity.getInvoiceNo(),
                    entity.getSourceSalesOrderNos(),
                    entity.getCustomerName(),
                    entity.getProjectName(),
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

        ArgumentCaptor<InvoiceIssue> captor = ArgumentCaptor.forClass(InvoiceIssue.class);
        when(repository.save(captor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        service.create(buildRequest(
                "KP-ROUND",
                101L,
                new BigDecimal("0.300"),
                new BigDecimal("3333.33"),
                new BigDecimal("1000.00")
        ));

        assertEquals(new BigDecimal("1000.00"), captor.getValue().getAmount());
        assertEquals(new BigDecimal("130.00"), captor.getValue().getTaxAmount());
    }

    @Test
    void createRejectsMissingSourceSalesOrderItemId() {
        when(repository.existsByIssueNoAndDeletedFlagFalse("KP-MISSING")).thenReturn(false);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.create(buildRequest(
                "KP-MISSING",
                null,
                new BigDecimal("0.300"),
                new BigDecimal("3333.33"),
                new BigDecimal("1000.01")
        )));

        assertEquals("第1行来源销售订单明细不能为空", exception.getMessage());
    }

    @Test
    void createRejectsDeclaredAmountMismatch() {
        SalesOrderItem sourceItem = buildSalesOrderItem(101L, "M-1", new BigDecimal("2.000"), new BigDecimal("6666.66"));

        when(repository.existsByIssueNoAndDeletedFlagFalse("KP-AMOUNT")).thenReturn(false);
        when(salesOrderItemQueryService.findActiveByIdIn(anyCollection())).thenReturn(List.of(sourceItem));
        when(repository.summarizeAllocatedBySourceSalesOrderItemIds(anyCollection(), nullable(Long.class)))
                .thenReturn(List.of());

        BusinessException exception = assertThrows(BusinessException.class, () -> service.create(buildRequest(
                "KP-AMOUNT",
                101L,
                new BigDecimal("0.300"),
                new BigDecimal("3333.33"),
                new BigDecimal("999.99")
        )));

        assertEquals("开票与明细计算结果不一致", exception.getMessage());
    }

    private InvoiceIssueRepository.SourceAllocationSummary summary(Long itemId, String weightTon, String amount) {
        return new InvoiceIssueRepository.SourceAllocationSummary() {
            @Override
            public Long getSourceSalesOrderItemId() {
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

    private SalesOrderItem buildSalesOrderItem(Long id, String materialCode, BigDecimal weightTon, BigDecimal amount) {
        SalesOrderItem item = new SalesOrderItem();
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

    private InvoiceIssueRequest buildRequest(String issueNo,
                                             Long sourceSalesOrderItemId,
                                             BigDecimal weightTon,
                                             BigDecimal unitPrice,
                                             BigDecimal amount) {
        return new InvoiceIssueRequest(
                issueNo,
                "INV-001",
                "SO-001",
                "客户A",
                "项目A",
                LocalDate.of(2026, 4, 26),
                "增值税专票",
                amount,
                BigDecimal.ZERO,
                "草稿",
                "财务A",
                null,
                List.of(new InvoiceIssueItemRequest(
                        "SO-001",
                        sourceSalesOrderItemId,
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
