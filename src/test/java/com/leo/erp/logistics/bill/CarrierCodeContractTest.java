package com.leo.erp.logistics.bill;

import com.leo.erp.logistics.bill.domain.entity.FreightBill;
import com.leo.erp.logistics.bill.mapper.FreightBillMapper;
import com.leo.erp.logistics.bill.web.dto.FreightBillRequest;
import com.leo.erp.logistics.bill.web.dto.FreightBillResponse;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CarrierCodeContractTest {

    @Test
    void shouldExposeStableCarrierCodeAcrossEntityRequestResponseAndMapper() {
        FreightBill entity = new FreightBill();
        entity.setId(1L);
        entity.setBillNo("FB-001");
        entity.setCarrierCode("CR-001");
        entity.setCarrierName("物流甲");
        entity.setCustomerName("客户甲");
        entity.setProjectName("项目甲");
        entity.setBillTime(LocalDate.of(2026, 7, 11));
        entity.setUnitPrice(new BigDecimal("10.00"));
        entity.setTotalWeight(new BigDecimal("2.00000000"));
        entity.setTotalFreight(new BigDecimal("20.00"));
        entity.setStatus("未审核");

        FreightBillRequest request = new FreightBillRequest(
                "FB-001",
                "CR-001",
                "物流甲",
                null,
                null,
                null,
                "客户甲",
                "项目甲",
                LocalDate.of(2026, 7, 11),
                new BigDecimal("10.00"),
                "未审核",
                null,
                List.of()
        );
        FreightBillResponse response = Mappers.getMapper(FreightBillMapper.class).toResponse(entity);

        assertThat(entity.getCarrierCode()).isEqualTo("CR-001");
        assertThat(request.carrierCode()).isEqualTo("CR-001");
        assertThat(response.carrierCode()).isEqualTo("CR-001");
    }
}
