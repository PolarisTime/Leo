package com.leo.erp.logistics.bill.mapper;

import com.leo.erp.logistics.bill.domain.entity.FreightBill;
import com.leo.erp.logistics.bill.web.dto.FreightBillResponse;
import org.springframework.stereotype.Component;

@Component
public class FreightBillMapper {

    public FreightBillResponse toResponse(FreightBill bill) {
        if (bill == null) {
            return null;
        }
        return new FreightBillResponse(
                bill.getId(),
                bill.getBillNo(),
                bill.getOutboundNo(),
                bill.getCarrierName(),
                bill.getVehiclePlate(),
                bill.getCustomerName(),
                bill.getProjectName(),
                bill.getBillTime(),
                bill.getUnitPrice(),
                bill.getTotalWeight(),
                bill.getTotalFreight(),
                bill.getStatus(),
                bill.getDeliveryStatus(),
                bill.getRemark(),
                null
        );
    }
}
