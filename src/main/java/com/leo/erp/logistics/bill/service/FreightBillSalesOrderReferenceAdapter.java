package com.leo.erp.logistics.bill.service;

import com.leo.erp.logistics.bill.repository.FreightBillSourceOrderRepository;
import com.leo.erp.sales.api.SalesOrderDownstreamReference;
import com.leo.erp.sales.api.SalesOrderReferenceGuard;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class FreightBillSalesOrderReferenceAdapter implements SalesOrderReferenceGuard {

    private final FreightBillSourceOrderRepository freightBillSourceOrderRepository;

    public FreightBillSalesOrderReferenceAdapter(
            FreightBillSourceOrderRepository freightBillSourceOrderRepository
    ) {
        this.freightBillSourceOrderRepository = freightBillSourceOrderRepository;
    }

    @Override
    public Optional<SalesOrderDownstreamReference> findActiveReference(Long salesOrderId) {
        return freightBillSourceOrderRepository.findActiveBySourceOrderId(salesOrderId).stream()
                .findFirst()
                .map(relation -> new SalesOrderDownstreamReference(
                        relation.getFreightBill().getBillNo()
                ));
    }
}
