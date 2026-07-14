package com.leo.erp.config;

import com.leo.erp.common.support.BusinessEntityRegistrar;
import com.leo.erp.common.support.BusinessRecordEntityCatalog;
import com.leo.erp.finance.payment.domain.entity.Payment;
import com.leo.erp.finance.receipt.domain.entity.Receipt;
import com.leo.erp.logistics.bill.domain.entity.FreightBill;
import com.leo.erp.master.carrier.domain.entity.Carrier;
import com.leo.erp.master.customer.domain.entity.Customer;
import com.leo.erp.master.material.domain.entity.Material;
import com.leo.erp.master.supplier.domain.entity.Supplier;
import com.leo.erp.master.warehouse.domain.entity.Warehouse;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInbound;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrder;
import com.leo.erp.sales.order.domain.entity.SalesOrder;
import com.leo.erp.sales.outbound.domain.entity.SalesOutbound;
import com.leo.erp.statement.customer.domain.entity.CustomerStatement;
import com.leo.erp.statement.freight.domain.entity.FreightStatement;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BusinessEntityConfig {

    @Bean
    public BusinessEntityRegistrar businessEntityRegistrar() {
        BusinessEntityRegistrar registrar = new BusinessEntityRegistrar();
        registrar.register("material", Material.class);
        registrar.register("supplier", Supplier.class);
        registrar.register("customer", Customer.class);
        registrar.register("carrier", Carrier.class);
        registrar.register("warehouse", Warehouse.class);
        registrar.register("purchase-order", PurchaseOrder.class);
        registrar.register("purchase-inbound", PurchaseInbound.class);
        registrar.register("sales-order", SalesOrder.class);
        registrar.register("sales-outbound", SalesOutbound.class);
        registrar.register("freight-bill", FreightBill.class);
        registrar.register("customer-statement", CustomerStatement.class);
        registrar.register("freight-statement", FreightStatement.class);
        registrar.register("receipt", Receipt.class);
        registrar.register("payment", Payment.class);
        BusinessRecordEntityCatalog.setRegistrar(registrar);
        return registrar;
    }
}
