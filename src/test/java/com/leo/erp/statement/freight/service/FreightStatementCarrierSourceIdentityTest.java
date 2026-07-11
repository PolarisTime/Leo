package com.leo.erp.statement.freight.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.logistics.bill.domain.entity.FreightBill;
import com.leo.erp.logistics.bill.domain.entity.FreightBillItem;
import com.leo.erp.logistics.bill.repository.FreightBillRepository;
import com.leo.erp.statement.freight.domain.entity.FreightStatement;
import com.leo.erp.statement.freight.repository.FreightStatementRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FreightStatementCarrierSourceIdentityTest {

    @Test
    void shouldInheritCarrierCodeAndNameFromSourceFreightBill() {
        FreightBill sourceBill = sourceBill(1L, "FB-001", "CR-001", "物流甲", 11L);
        FreightStatement entity = new FreightStatement();
        FreightStatementSourceService service = service(List.of(sourceBill), new LinkedHashSet<>(List.of("FB-001")));

        service.applyItems(entity, command(null, "物流甲", item("FB-001", 11L)), () -> 1000L);

        assertThat(entity.getCarrierCode()).isEqualTo("CR-001");
        assertThat(entity.getCarrierName()).isEqualTo("物流甲");
    }

    @Test
    void shouldRejectSameCarrierNameWithDifferentStableCodes() {
        FreightBill first = sourceBill(1L, "FB-001", "CR-001", "同名物流", 11L);
        FreightBill second = sourceBill(2L, "FB-002", "CR-002", "同名物流", 22L);
        LinkedHashSet<String> billNos = new LinkedHashSet<>(List.of("FB-001", "FB-002"));
        FreightStatementSourceService service = service(List.of(first, second), billNos);

        assertThatThrownBy(() -> service.applyItems(
                new FreightStatement(),
                command(null, "同名物流", item("FB-001", 11L), item("FB-002", 22L)),
                () -> 1000L
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不同物流商编码");
    }

    @Test
    void shouldRejectRequestedCarrierCodeThatDiffersFromSource() {
        FreightBill sourceBill = sourceBill(1L, "FB-001", "CR-001", "物流甲", 11L);
        FreightStatementSourceService service = service(List.of(sourceBill), new LinkedHashSet<>(List.of("FB-001")));

        assertThatThrownBy(() -> service.applyItems(
                new FreightStatement(),
                command("CR-002", "物流甲", item("FB-001", 11L)),
                () -> 1000L
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("物流商编码与来源物流单不一致");
    }

    private FreightStatementSourceService service(List<FreightBill> bills, LinkedHashSet<String> billNos) {
        FreightStatementRepository statementRepository = mock(FreightStatementRepository.class);
        FreightBillRepository billRepository = mock(FreightBillRepository.class);
        when(billRepository.findByBillNoInAndDeletedFlagFalse(billNos)).thenReturn(bills);
        when(statementRepository.findAllBySourceNosExcludingCurrentStatement(billNos, null)).thenReturn(List.of());
        return new FreightStatementSourceService(statementRepository, billRepository);
    }

    private FreightStatementCommand command(String carrierCode,
                                            String carrierName,
                                            FreightStatementItemCommand... items) {
        return new FreightStatementCommand(
                "FS-001",
                carrierCode,
                carrierName,
                null,
                null,
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 31),
                null,
                null,
                BigDecimal.ZERO,
                null,
                StatusConstants.PENDING_AUDIT,
                StatusConstants.UNSIGNED,
                null,
                null,
                List.of(items)
        );
    }

    private FreightStatementItemCommand item(String billNo, Long sourceItemId) {
        return new FreightStatementItemCommand(
                null,
                billNo,
                sourceItemId,
                null,
                null,
                "客户甲",
                "项目甲",
                "M-001",
                "螺纹钢",
                "品牌甲",
                "钢材",
                "钢",
                "10",
                "9m",
                2,
                "件",
                BigDecimal.ONE,
                1,
                "B-001",
                null,
                "仓库甲"
        );
    }

    private FreightBill sourceBill(Long id,
                                   String billNo,
                                   String carrierCode,
                                   String carrierName,
                                   Long sourceItemId) {
        FreightBill bill = new FreightBill();
        bill.setId(id);
        bill.setBillNo(billNo);
        bill.setCarrierCode(carrierCode);
        bill.setCarrierName(carrierName);
        bill.setStatus(StatusConstants.AUDITED);
        bill.setTotalFreight(new BigDecimal("20.00"));
        FreightBillItem item = new FreightBillItem();
        item.setId(sourceItemId);
        item.setFreightBill(bill);
        item.setSourceSalesOutboundItemId(sourceItemId);
        item.setQuantity(2);
        item.setPieceWeightTon(BigDecimal.ONE);
        item.setQuantityUnit("件");
        bill.getItems().add(item);
        return bill;
    }
}
