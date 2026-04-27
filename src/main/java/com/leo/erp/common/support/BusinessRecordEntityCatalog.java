package com.leo.erp.common.support;

import com.leo.erp.common.persistence.AuditableEntity;
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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class BusinessRecordEntityCatalog {

    private static final Map<String, Class<? extends AuditableEntity>> ENTITY_TYPES_BY_MODULE_KEY = buildEntityTypes();

    private BusinessRecordEntityCatalog() {
    }

    public static Optional<Class<? extends AuditableEntity>> findEntityType(String moduleKey) {
        return Optional.ofNullable(ENTITY_TYPES_BY_MODULE_KEY.get(normalizeModuleKey(moduleKey)));
    }

    public static boolean hasEntity(String moduleKey) {
        return findEntityType(moduleKey).isPresent();
    }

    public static Set<String> moduleKeys() {
        return ENTITY_TYPES_BY_MODULE_KEY.keySet();
    }

    public static String normalizeModuleKey(String moduleKey) {
        return String.valueOf(moduleKey == null ? "" : moduleKey)
                .trim()
                .replaceFirst("^/+", "")
                .toLowerCase(Locale.ROOT);
    }

    private static Map<String, Class<? extends AuditableEntity>> buildEntityTypes() {
        Map<String, Class<? extends AuditableEntity>> entityTypes = new LinkedHashMap<>();
        entityTypes.put("materials", Material.class);
        entityTypes.put("suppliers", Supplier.class);
        entityTypes.put("customers", Customer.class);
        entityTypes.put("carriers", Carrier.class);
        entityTypes.put("warehouses", Warehouse.class);
        entityTypes.put("purchase-orders", PurchaseOrder.class);
        entityTypes.put("purchase-inbounds", PurchaseInbound.class);
        entityTypes.put("sales-orders", SalesOrder.class);
        entityTypes.put("sales-outbounds", SalesOutbound.class);
        entityTypes.put("freight-bills", FreightBill.class);
        entityTypes.put("purchase-contracts", PurchaseContract.class);
        entityTypes.put("sales-contracts", SalesContract.class);
        entityTypes.put("supplier-statements", SupplierStatement.class);
        entityTypes.put("customer-statements", CustomerStatement.class);
        entityTypes.put("freight-statements", FreightStatement.class);
        entityTypes.put("receipts", Receipt.class);
        entityTypes.put("payments", Payment.class);
        entityTypes.put("invoice-receipts", InvoiceReceipt.class);
        entityTypes.put("invoice-issues", InvoiceIssue.class);
        return Collections.unmodifiableMap(entityTypes);
    }
}
