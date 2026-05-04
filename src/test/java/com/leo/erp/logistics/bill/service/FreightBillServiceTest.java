package com.leo.erp.logistics.bill.service;

import com.leo.erp.logistics.bill.domain.entity.FreightBill;
import com.leo.erp.logistics.bill.mapper.FreightBillMapper;
import com.leo.erp.logistics.bill.repository.FreightBillRepository;
import com.leo.erp.logistics.bill.web.dto.FreightBillItemRequest;
import com.leo.erp.logistics.bill.web.dto.FreightBillRequest;
import com.leo.erp.logistics.bill.web.dto.FreightBillResponse;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.security.permission.WorkflowTransitionGuard;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FreightBillServiceTest {

    @Test
    void shouldSaveVehiclePlateAndExposeItInResponse() {
        FreightBillRepository repository = mock(FreightBillRepository.class);
        when(repository.existsByBillNoAndDeletedFlagFalse("FB-001")).thenReturn(false);
        when(repository.save(any(FreightBill.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FreightBillService service = new FreightBillService(
                repository,
                new SnowflakeIdGenerator(),
                new FreightBillMapper(),
                mock(WorkflowTransitionGuard.class)
        );

        FreightBillResponse response = service.create(new FreightBillRequest(
                "FB-001",
                "OB-001",
                "物流甲",
                "苏A12345",
                "客户甲",
                "项目甲",
                LocalDate.of(2026, 5, 4),
                new BigDecimal("20.00"),
                null,
                null,
                null,
                List.of(new FreightBillItemRequest(
                        "OB-001",
                        "客户甲",
                        "项目甲",
                        "M001",
                        null,
                        "宝钢",
                        "钢材",
                        "HRB400",
                        "18",
                        "12m",
                        2,
                        "件",
                        new BigDecimal("1.250"),
                        0,
                        "B001",
                        null,
                        "一号库"
                ))
        ));

        assertThat(response.vehiclePlate()).isEqualTo("苏A12345");
        assertThat(response.totalWeight()).isEqualByComparingTo("2.500");
        assertThat(response.totalFreight()).isEqualByComparingTo("50.00");

        ArgumentCaptor<FreightBill> billCaptor = ArgumentCaptor.forClass(FreightBill.class);
        verify(repository).save(billCaptor.capture());
        assertThat(billCaptor.getValue().getItems())
                .singleElement()
                .satisfies(item -> assertThat(item.getMaterialName()).isEqualTo("宝钢"));
    }
}
