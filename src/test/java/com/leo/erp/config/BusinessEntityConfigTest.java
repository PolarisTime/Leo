package com.leo.erp.config;

import com.leo.erp.common.support.BusinessEntityRegistrar;
import com.leo.erp.common.support.BusinessRecordEntityCatalog;
import com.leo.erp.contract.purchase.domain.entity.PurchaseContract;
import com.leo.erp.contract.sales.domain.entity.SalesContract;
import com.leo.erp.finance.invoiceissue.domain.entity.InvoiceIssue;
import com.leo.erp.finance.invoicereceipt.domain.entity.InvoiceReceipt;
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
import com.leo.erp.statement.supplier.domain.entity.SupplierStatement;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessEntityConfigTest {

    @Test
    void shouldRegisterAllBusinessEntities() {
        // given
        BusinessEntityConfig config = new BusinessEntityConfig();

        // when
        BusinessEntityRegistrar registrar = config.businessEntityRegistrar();

        // then
        assertThat(registrar).isNotNull();
        
        // 验证所有实体都已注册
        Map<String, ?> snapshot = registrar.snapshot();
        assertThat(snapshot).hasSize(19);
        
        // 验证每个实体的注册键和类型
        assertThat(snapshot).containsKey("material");
        assertThat(snapshot).containsKey("supplier");
        assertThat(snapshot).containsKey("customer");
        assertThat(snapshot).containsKey("carrier");
        assertThat(snapshot).containsKey("warehouse");
        assertThat(snapshot).containsKey("purchase-order");
        assertThat(snapshot).containsKey("purchase-inbound");
        assertThat(snapshot).containsKey("sales-order");
        assertThat(snapshot).containsKey("sales-outbound");
        assertThat(snapshot).containsKey("freight-bill");
        assertThat(snapshot).containsKey("purchase-contract");
        assertThat(snapshot).containsKey("sales-contract");
        assertThat(snapshot).containsKey("supplier-statement");
        assertThat(snapshot).containsKey("customer-statement");
        assertThat(snapshot).containsKey("freight-statement");
        assertThat(snapshot).containsKey("receipt");
        assertThat(snapshot).containsKey("payment");
        assertThat(snapshot).containsKey("invoice-receipt");
        assertThat(snapshot).containsKey("invoice-issue");
    }

    @Test
    void shouldSetRegistrarInBusinessRecordEntityCatalog() {
        // given
        BusinessEntityConfig config = new BusinessEntityConfig();

        // when
        BusinessEntityRegistrar registrar = config.businessEntityRegistrar();

        // then
        assertThat(BusinessRecordEntityCatalog.moduleKeys()).containsExactlyInAnyOrder(
                "material", "supplier", "customer", "carrier", "warehouse",
                "purchase-order", "purchase-inbound", "sales-order", "sales-outbound",
                "freight-bill", "purchase-contract", "sales-contract",
                "supplier-statement", "customer-statement", "freight-statement",
                "receipt", "payment", "invoice-receipt", "invoice-issue"
        );
    }

    @Test
    void shouldReturnCorrectEntityTypeForModuleKey() {
        // given
        BusinessEntityConfig config = new BusinessEntityConfig();
        BusinessEntityRegistrar registrar = config.businessEntityRegistrar();

        // when & then
        assertThat(registrar.findEntityType("material")).contains(Material.class);
        assertThat(registrar.findEntityType("supplier")).contains(Supplier.class);
        assertThat(registrar.findEntityType("customer")).contains(Customer.class);
        assertThat(registrar.findEntityType("carrier")).contains(Carrier.class);
        assertThat(registrar.findEntityType("warehouse")).contains(Warehouse.class);
        assertThat(registrar.findEntityType("purchase-order")).contains(PurchaseOrder.class);
        assertThat(registrar.findEntityType("purchase-inbound")).contains(PurchaseInbound.class);
        assertThat(registrar.findEntityType("sales-order")).contains(SalesOrder.class);
        assertThat(registrar.findEntityType("sales-outbound")).contains(SalesOutbound.class);
        assertThat(registrar.findEntityType("freight-bill")).contains(FreightBill.class);
        assertThat(registrar.findEntityType("purchase-contract")).contains(PurchaseContract.class);
        assertThat(registrar.findEntityType("sales-contract")).contains(SalesContract.class);
        assertThat(registrar.findEntityType("supplier-statement")).contains(SupplierStatement.class);
        assertThat(registrar.findEntityType("customer-statement")).contains(CustomerStatement.class);
        assertThat(registrar.findEntityType("freight-statement")).contains(FreightStatement.class);
        assertThat(registrar.findEntityType("receipt")).contains(Receipt.class);
        assertThat(registrar.findEntityType("payment")).contains(Payment.class);
        assertThat(registrar.findEntityType("invoice-receipt")).contains(InvoiceReceipt.class);
        assertThat(registrar.findEntityType("invoice-issue")).contains(InvoiceIssue.class);
    }

    @Test
    void shouldReturnEmptyOptionalForUnknownModuleKey() {
        // given
        BusinessEntityConfig config = new BusinessEntityConfig();
        BusinessEntityRegistrar registrar = config.businessEntityRegistrar();

        // when & then
        assertThat(registrar.findEntityType("unknown")).isEmpty();
        assertThat(registrar.findEntityType("")).isEmpty();
        assertThat(registrar.findEntityType(null)).isEmpty();
    }

    @Test
    void shouldNormalizeModuleKey() {
        // given
        BusinessEntityConfig config = new BusinessEntityConfig();
        BusinessEntityRegistrar registrar = config.businessEntityRegistrar();

        // when & then
        assertThat(registrar.hasEntity("Material")).isTrue();
        assertThat(registrar.hasEntity("MATERIAL")).isTrue();
        assertThat(registrar.hasEntity(" material ")).isTrue();
        assertThat(registrar.hasEntity("/material")).isTrue();
        assertThat(registrar.hasEntity("material")).isTrue();
    }
}