package com.leo.erp.finance.ledgeradjustment.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.finance.ledgeradjustment.mapper.LedgerAdjustmentMapper;
import com.leo.erp.finance.ledgeradjustment.repository.LedgerAdjustmentRepository;
import com.leo.erp.finance.ledgeradjustment.web.dto.LedgerAdjustmentRequest;
import com.leo.erp.master.api.CarrierQuery;
import com.leo.erp.master.api.CustomerQuery;
import com.leo.erp.master.api.ProjectQuery;
import com.leo.erp.master.api.SupplierQuery;
import com.leo.erp.system.company.service.CompanySettingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class LedgerAdjustmentServiceTest {

    @Mock
    private LedgerAdjustmentRepository repository;

    @Mock
    private LedgerAdjustmentMapper mapper;

    @Mock
    private SnowflakeIdGenerator idGenerator;

    @Mock
    private LedgerAdjustmentApplyService applyService;

    @Test
    void create_shouldBeDisabled() {
        assertThatThrownBy(() -> service().create(request()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("台账调整单已停用");
    }

    @Test
    void update_shouldBeDisabled() {
        assertThatThrownBy(() -> service().update(1L, request()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("台账调整单已停用");
    }

    @Test
    void updateStatus_shouldBeDisabled() {
        assertThatThrownBy(() -> service().updateStatus(1L, "已审核"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("台账调整单已停用");
    }

    @Test
    void delete_shouldBeDisabled() {
        assertThatThrownBy(() -> service().delete(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("台账调整单已停用");
    }

    @Test
    void disabledWrites_shouldNeverTouchRepositoryOrApply() {
        LedgerAdjustmentService service = service();
        assertThatThrownBy(() -> service.create(request())).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.update(1L, request())).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.updateStatus(1L, "已审核")).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.delete(1L)).isInstanceOf(BusinessException.class);
        verify(repository, never()).save(any());
        verify(repository, never()).findByIdAndDeletedFlagFalse(anyLong());
        verify(applyService, never()).apply(any(), any());
        verify(idGenerator, never()).nextId();
    }

    @Test
    void validateCreate_shouldRejectDuplicateAdjustmentNo() {
        LedgerAdjustmentService service = service();
        org.mockito.Mockito.when(repository.existsByAdjustmentNoAndDeletedFlagFalse("LA001"))
                .thenReturn(true);

        assertThatThrownBy(() -> service.validateCreate(request()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("调整单号已存在");
    }

    @Test
    void validateUpdate_shouldRejectDuplicateAdjustmentNoWhenChanged() {
        LedgerAdjustmentService service = service();
        com.leo.erp.finance.ledgeradjustment.domain.entity.LedgerAdjustment entity =
                new com.leo.erp.finance.ledgeradjustment.domain.entity.LedgerAdjustment();
        entity.setAdjustmentNo("LA-OLD");
        org.mockito.Mockito.when(repository.existsByAdjustmentNoAndDeletedFlagFalse("LA001"))
                .thenReturn(true);

        assertThatThrownBy(() -> service.validateUpdate(entity, request()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("调整单号已存在");
    }

    @Test
    void validateUpdate_shouldAllowSameAdjustmentNo() {
        LedgerAdjustmentService service = service();
        com.leo.erp.finance.ledgeradjustment.domain.entity.LedgerAdjustment entity =
                new com.leo.erp.finance.ledgeradjustment.domain.entity.LedgerAdjustment();
        entity.setAdjustmentNo("LA001");

        service.validateUpdate(entity, request());

        verify(repository, never()).existsByAdjustmentNoAndDeletedFlagFalse(anyString());
    }

    // ---------- 停用态下的状态守卫边界：所有写路径统一拒绝且不落库 ----------

    @Test
    void updateStatus_shouldRejectDisabledWriteWithBlankStatus() {
        assertThatThrownBy(() -> service().updateStatus(1L, "   "))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("台账调整单已停用");
        verify(repository, never()).save(any());
    }

    @Test
    void updateStatus_shouldRejectDisabledWriteWithNullStatus() {
        assertThatThrownBy(() -> service().updateStatus(1L, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("台账调整单已停用");
        verify(repository, never()).save(any());
    }

    @Test
    void updateStatus_shouldRejectDisabledWriteWithInvalidTransitionStatus() {
        assertThatThrownBy(() -> service().updateStatus(1L, "已完成"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("台账调整单已停用");
        verify(repository, never()).findByIdAndDeletedFlagFalse(anyLong());
        verify(repository, never()).save(any());
    }

    @Test
    void updateStatus_shouldRejectDisabledWriteWithoutEqualStatusShortCircuit() {
        assertThatThrownBy(() -> service().updateStatus(1L, "草稿"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("台账调整单已停用");
        verify(repository, never()).save(any());
        verify(applyService, never()).apply(any(), any());
    }

    @Test
    void updateStatus_shouldRejectDisabledWriteWithTerminalStatus() {
        assertThatThrownBy(() -> service().updateStatus(1L, "已审核"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("台账调整单已停用");
        verify(repository, never()).save(any());
    }

    @Test
    void detail_shouldRejectMissingEntity() {
        org.mockito.Mockito.when(repository.findById(1L)).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> service().detail(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("台账调整单不存在");
        verify(mapper, never()).toResponse(any());
    }

    private LedgerAdjustmentService service() {
        return new LedgerAdjustmentService(repository, mapper, idGenerator, applyService);
    }

    private LedgerAdjustmentRequest request() {
        return new LedgerAdjustmentRequest(
                "LA001",
                "应收",
                "客户",
                10L,
                "CUST001",
                "客户A",
                null,
                null,
                20L,
                "项目A",
                java.time.LocalDate.of(2026, 8, 25),
                new BigDecimal("10.00"),
                "其他调整",
                "增加余额",
                "草稿",
                "操作员",
                null
        );
    }
}
