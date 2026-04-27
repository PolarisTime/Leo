package com.leo.erp.common.api;

import java.util.Map;
import java.util.Set;

public final class PageSortFieldCatalog {

    private static final Map<String, Set<String>> FIELDS_BY_KEY = Map.ofEntries(
            Map.entry("materials", Set.of("id", "materialCode", "brand", "material", "category", "spec", "length", "unit", "quantityUnit", "pieceWeightTon", "piecesPerBundle", "unitPrice")),
            Map.entry("suppliers", Set.of("id", "supplierCode", "supplierName", "contactName", "contactPhone", "city", "status")),
            Map.entry("customers", Set.of("id", "customerCode", "customerName", "contactName", "contactPhone", "city", "settlementMode", "status")),
            Map.entry("carriers", Set.of("id", "carrierCode", "carrierName", "contactName", "contactPhone", "vehicleType", "priceMode", "status")),
            Map.entry("warehouses", Set.of("id", "warehouseCode", "warehouseName", "warehouseType", "contactName", "contactPhone", "address", "status")),
            Map.entry("purchase-orders", Set.of("id", "orderNo", "supplierName", "orderDate", "buyerName", "totalWeight", "totalAmount", "status")),
            Map.entry("purchase-inbounds", Set.of("id", "inboundNo", "purchaseOrderNo", "supplierName", "warehouseName", "inboundDate", "settlementMode", "totalWeight", "totalAmount", "status")),
            Map.entry("sales-orders", Set.of("id", "orderNo", "purchaseInboundNo", "customerName", "projectName", "deliveryDate", "salesName", "totalWeight", "totalAmount", "status")),
            Map.entry("sales-outbounds", Set.of("id", "outboundNo", "salesOrderNo", "customerName", "projectName", "warehouseName", "outboundDate", "totalWeight", "totalAmount", "status")),
            Map.entry("freight-bills", Set.of("id", "billNo", "outboundNo", "carrierName", "customerName", "projectName", "billTime", "unitPrice", "totalWeight", "totalFreight", "status", "deliveryStatus")),
            Map.entry("purchase-contracts", Set.of("id", "contractNo", "supplierName", "signDate", "effectiveDate", "expireDate", "buyerName", "totalWeight", "totalAmount", "status")),
            Map.entry("sales-contracts", Set.of("id", "contractNo", "customerName", "projectName", "signDate", "effectiveDate", "expireDate", "salesName", "totalWeight", "totalAmount", "status")),
            Map.entry("supplier-statements", Set.of("id", "statementNo", "sourceInboundNos", "supplierName", "startDate", "endDate", "purchaseAmount", "paymentAmount", "closingAmount", "status")),
            Map.entry("customer-statements", Set.of("id", "statementNo", "sourceOrderNos", "customerName", "projectName", "startDate", "endDate", "salesAmount", "receiptAmount", "closingAmount", "status")),
            Map.entry("freight-statements", Set.of("id", "statementNo", "sourceBillNos", "carrierName", "startDate", "endDate", "totalWeight", "totalFreight", "paidAmount", "unpaidAmount", "status", "signStatus")),
            Map.entry("receipts", Set.of("id", "receiptNo", "customerName", "projectName", "receiptDate", "payType", "amount", "status", "operatorName")),
            Map.entry("payments", Set.of("id", "paymentNo", "businessType", "counterpartyName", "paymentDate", "payType", "amount", "status", "operatorName")),
            Map.entry("invoice-receipts", Set.of("id", "receiveNo", "invoiceNo", "sourcePurchaseOrderNos", "supplierName", "invoiceDate", "invoiceType", "amount", "taxAmount", "status", "operatorName")),
            Map.entry("invoice-issues", Set.of("id", "issueNo", "invoiceNo", "sourceSalesOrderNos", "customerName", "projectName", "invoiceDate", "invoiceType", "amount", "taxAmount", "status", "operatorName")),
            Map.entry("pending-invoice-receipt-report", Set.of("orderNo", "supplierName", "orderDate", "materialCode", "pendingInvoiceWeightTon", "pendingInvoiceAmount")),
            Map.entry("departments", Set.of("id", "departmentCode", "departmentName", "parentId", "managerName", "contactPhone", "sortOrder", "status")),
            Map.entry("user-accounts", Set.of("id", "loginName", "userName", "mobile", "roleName", "dataScope", "lastLoginDate", "status")),
            Map.entry("role-settings", Set.of("id", "roleCode", "roleName", "roleType", "dataScope", "status")),
            Map.entry("general-settings", Set.of("id", "settingCode", "settingName", "billName", "prefix", "dateRule", "serialLength", "resetRule", "sampleNo", "status")),
            Map.entry("company-settings", Set.of("id", "companyName", "taxNo", "bankName", "bankAccount", "status")),
            Map.entry("permission-management", Set.of("id", "permissionCode", "permissionName", "moduleName", "permissionType", "actionName", "status")),
            Map.entry("operation-logs", Set.of("id", "logNo", "operatorName", "loginName", "moduleName", "actionType", "businessNo", "requestMethod", "requestPath", "clientIp", "resultStatus", "operationTime")),
            Map.entry("inventory-report", Set.of("brand", "category", "warehouseName", "quantity", "weightTon")),
            Map.entry("io-report", Set.of("businessDate", "businessType", "sourceNo", "materialCode", "warehouseName")),
            Map.entry("receivables-payables", Set.of("counterpartyName", "direction", "counterpartyType", "currentAmount", "balanceAmount", "status"))
    );

    private PageSortFieldCatalog() {
    }

    public static Set<String> fields(String key) {
        Set<String> fields = FIELDS_BY_KEY.get(key);
        if (fields == null) {
            throw new IllegalArgumentException("Unknown page sort field key: " + key);
        }
        return fields;
    }
}
