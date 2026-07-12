package com.leo.erp.logistics.bill.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.finance.payment.repository.PaymentAllocationRepository;
import com.leo.erp.logistics.bill.domain.entity.FreightBill;
import com.leo.erp.statement.freight.domain.entity.FreightStatement;
import com.leo.erp.statement.freight.repository.FreightStatementRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class FreightBillDownstreamMutationGuardTest {

    @Test
    void shouldRejectReverseAuditWhenActiveFreightStatementExists() {
        Fixture fixture = new Fixture();
        fixture.stubStatement(0L);

        assertThatThrownBy(() -> fixture.guard.assertReverseAuditAllowed(fixture.bill))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("物流对账单")
                .hasMessageContaining("不能反审核");
    }

    @Test
    void shouldProtectByFreightBillIdWithoutUsingBillNumberAsIdentity() {
        Fixture fixture = new Fixture();
        fixture.bill.setBillNo(null);
        fixture.stubStatement(0L);

        assertThatThrownBy(() -> fixture.guard.assertReverseAuditAllowed(fixture.bill))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("物流对账单")
                .hasMessageContaining("不能反审核");
        verify(fixture.freightStatementRepository)
                .findAllBySourceFreightBillIdsExcludingCurrentStatement(List.of(1L), null);
        verify(fixture.freightStatementRepository, never())
                .findAllBySourceNosExcludingCurrentStatement(List.of("FB-001"), null);
    }

    @Test
    void shouldRejectDeleteWhenActiveFreightStatementExists() {
        Fixture fixture = new Fixture();
        fixture.stubStatement(0L);

        assertThatThrownBy(() -> fixture.guard.assertDeleteAllowed(fixture.bill))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("物流对账单")
                .hasMessageContaining("不能删除");
    }

    @Test
    void shouldRejectReverseAuditWhenPaidFreightAllocationExists() {
        Fixture fixture = new Fixture();
        fixture.stubStatement(1L);

        assertThatThrownBy(() -> fixture.guard.assertReverseAuditAllowed(fixture.bill))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已付款")
                .hasMessageContaining("不能反审核");
    }

    @Test
    void shouldRejectDeleteWhenPaidFreightAllocationExists() {
        Fixture fixture = new Fixture();
        fixture.stubStatement(1L);

        assertThatThrownBy(() -> fixture.guard.assertDeleteAllowed(fixture.bill))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已付款")
                .hasMessageContaining("不能删除");
    }

    @Test
    void shouldAllowReverseAuditAndDeleteWithoutDownstreamReferences() {
        Fixture fixture = new Fixture();

        assertThatCode(() -> fixture.guard.assertReverseAuditAllowed(fixture.bill))
                .doesNotThrowAnyException();
        assertThatCode(() -> fixture.guard.assertDeleteAllowed(fixture.bill))
                .doesNotThrowAnyException();
        verifyNoInteractions(fixture.paymentAllocationRepository);
    }

    private static final class Fixture {
        private final FreightStatementRepository freightStatementRepository =
                mock(FreightStatementRepository.class);
        private final PaymentAllocationRepository paymentAllocationRepository =
                mock(PaymentAllocationRepository.class);
        private final FreightBillDownstreamMutationGuard guard =
                new FreightBillDownstreamMutationGuard(
                        freightStatementRepository,
                        paymentAllocationRepository
                );
        private final FreightBill bill = bill();
        private final FreightStatement statement = statement();

        private Fixture() {
            when(freightStatementRepository.findAllBySourceFreightBillIdsExcludingCurrentStatement(
                    List.of(1L),
                    null
            )).thenReturn(List.of());
        }

        private void stubStatement(long settledAllocationCount) {
            when(freightStatementRepository.findAllBySourceFreightBillIdsExcludingCurrentStatement(
                    List.of(1L),
                    null
            )).thenReturn(List.of(statement));
            when(paymentAllocationRepository.countSettledAllocationsByStatementIdAndBusinessTypeAndStatus(
                    11L,
                    "物流商",
                    StatusConstants.PAID
            )).thenReturn(settledAllocationCount);
        }

        private static FreightBill bill() {
            FreightBill bill = new FreightBill();
            bill.setId(1L);
            bill.setBillNo("FB-001");
            return bill;
        }

        private static FreightStatement statement() {
            FreightStatement statement = new FreightStatement();
            statement.setId(11L);
            statement.setStatementNo("WL-DZ-001");
            return statement;
        }
    }
}
