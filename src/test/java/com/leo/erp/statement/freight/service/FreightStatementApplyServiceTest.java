package com.leo.erp.statement.freight.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.statement.freight.domain.entity.FreightStatement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * FreightStatementApplyService 极端情况测试。
 */
@ExtendWith(MockitoExtension.class)
class FreightStatementApplyServiceTest {

    @Mock
    private FreightStatementCarrierResolver carrierResolver;

    @Mock
    private FreightStatementSourceService freightStatementSourceService;

    @InjectMocks
    private FreightStatementApplyService service;

    private FreightStatementCommand command(String status, String attachment, String carrierName,
                                            String carrierCode) {
        return new FreightStatementCommand("FS001", carrierCode, carrierName, null, null,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), new BigDecimal("100"),
                new BigDecimal("5000"), null, null, status, attachment, null, List.of(), null);
    }

    private FreightStatementSourceService.SourceApplyResult result() {
        return new FreightStatementSourceService.SourceApplyResult(
                new BigDecimal("100"), new BigDecimal("5000"),
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));
    }

    @Test
    void apply_shouldSetFieldsAndFreightWhenCreating() {
        FreightStatement entity = new FreightStatement(); // status null → creating
        FreightStatementCommand cmd = command(null, "att.png", "承运商A", null);
        when(freightStatementSourceService.applyItems(any(), any(), any())).thenReturn(result());
        when(carrierResolver.resolveCarrierCode(null, "承运商A")).thenReturn("C001");

        service.apply(entity, cmd, () -> 100L);

        assertThat(entity.getStatus()).isEqualTo(StatusConstants.DRAFT);
        assertThat(entity.getSignStatus()).isEqualTo(StatusConstants.UNSIGNED);
        assertThat(entity.getAttachment()).isEqualTo("att.png"); // creating 且附件非空
        assertThat(entity.getCarrierName()).isEqualTo("承运商A");
        assertThat(entity.getCarrierCode()).isEqualTo("C001");
        assertThat(entity.getTotalFreight()).isEqualByComparingTo("5000");
        assertThat(entity.getPaidAmount()).isEqualByComparingTo("0"); // 实体无已付 → ZERO
        assertThat(entity.getUnpaidAmount()).isEqualByComparingTo("5000");
    }

    @Test
    void apply_shouldNotSetAttachmentWhenUpdating() {
        FreightStatement entity = new FreightStatement();
        entity.setStatus(StatusConstants.DRAFT); // 非 creating
        FreightStatementCommand cmd = command(StatusConstants.DRAFT, "att.png", "承运商A", null);
        when(freightStatementSourceService.applyItems(any(), any(), any())).thenReturn(result());
        when(carrierResolver.resolveCarrierCode(any(), any())).thenReturn("C001");

        service.apply(entity, cmd, () -> 100L);

        assertThat(entity.getAttachment()).isNull(); // 更新时不覆盖附件
    }

    @Test
    void apply_shouldPreferEntityCarrierName() {
        FreightStatement entity = new FreightStatement();
        entity.setCarrierName("已有承运商");
        FreightStatementCommand cmd = command(null, null, "命令承运商", null);
        when(freightStatementSourceService.applyItems(any(), any(), any())).thenReturn(result());
        when(carrierResolver.resolveCarrierCode(null, "已有承运商")).thenReturn("C001");

        service.apply(entity, cmd, () -> 100L);

        assertThat(entity.getCarrierName()).isEqualTo("已有承运商");
    }

    @Test
    void apply_shouldRejectPaidAmountExceedingFreight() {
        FreightStatement entity = new FreightStatement();
        entity.setPaidAmount(new BigDecimal("6000")); // 已付 > 总运费 5000
        FreightStatementCommand cmd = command(StatusConstants.DRAFT, null, "承运商A", null);
        when(freightStatementSourceService.applyItems(any(), any(), any())).thenReturn(result());

        assertThatThrownBy(() -> service.apply(entity, cmd, () -> 100L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void apply_shouldKeepEntityPaidAmountWhenPresent() {
        FreightStatement entity = new FreightStatement();
        entity.setPaidAmount(new BigDecimal("2000"));
        FreightStatementCommand cmd = command(null, null, "承运商A", null);
        when(freightStatementSourceService.applyItems(any(), any(), any())).thenReturn(result());
        when(carrierResolver.resolveCarrierCode(any(), any())).thenReturn("C001");

        service.apply(entity, cmd, () -> 100L);

        assertThat(entity.getPaidAmount()).isEqualByComparingTo("2000");
        assertThat(entity.getUnpaidAmount()).isEqualByComparingTo("3000");
    }

    @Test
    void apply_shouldPreferEntityCarrierCode() {
        FreightStatement entity = new FreightStatement();
        entity.setCarrierCode("已有编码");
        FreightStatementCommand cmd = command(null, null, "承运商A", "命令编码");
        when(freightStatementSourceService.applyItems(any(), any(), any())).thenReturn(result());
        when(carrierResolver.resolveCarrierCode("已有编码", "承运商A")).thenReturn("已有编码");

        service.apply(entity, cmd, () -> 100L);

        assertThat(entity.getCarrierCode()).isEqualTo("已有编码");
    }

    @Test
    void apply_shouldTreatBlankEntityCarrierNameAsMissing() {
        FreightStatement entity = new FreightStatement();
        entity.setCarrierName("   "); // trimToNull → null → 回退到命令承运商名
        FreightStatementCommand cmd = command(null, null, "命令承运商", null);
        when(freightStatementSourceService.applyItems(any(), any(), any())).thenReturn(result());
        when(carrierResolver.resolveCarrierCode(null, "命令承运商")).thenReturn("C001");

        service.apply(entity, cmd, () -> 100L);

        assertThat(entity.getCarrierName()).isEqualTo("命令承运商");
        assertThat(entity.getCarrierCode()).isEqualTo("C001");
    }
}
