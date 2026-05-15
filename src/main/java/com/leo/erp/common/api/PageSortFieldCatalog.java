package com.leo.erp.common.api;

import java.util.Map;
import java.util.Set;

public final class PageSortFieldCatalog {

    private static final Map<String, Set<String>> FIELDS_BY_KEY = Map.ofEntries(
            Map.entry("material", Set.of("id", "materialCode", "brand", "material", "category", "spec", "length", "unit", "quantityUnit", "pieceWeightTon", "piecesPerBundle", "unitPrice", "lengthSort", "specSort")),
            Map.entry("supplier", Set.of("id", "supplierCode", "supplierName", "contactName", "contactPhone", "city", "status")),
            Map.entry("customer", Set.of("id", "customerCode", "customerName", "contactName", "contactPhone", "city", "settlementMode", "projectName", "status")),
            Map.entry("carrier", Set.of("id", "carrierCode", "carrierName", "contactName", "contactPhone", "vehicleType", "priceMode", "status")),
            Map.entry("warehouse", Set.of("id", "warehouseCode", "warehouseName", "warehouseType", "contactName", "contactPhone", "address", "status")),
            Map.entry("purchase-order", Set.of("id", "orderNo", "supplierName", "orderDate", "buyerName", "totalWeight", "totalAmount", "status")),
            Map.entry("purchase-inbound", Set.of("id", "inboundNo", "purchaseOrderNo", "supplierName", "warehouseName", "inboundDate", "settlementMode", "totalWeight", "totalAmount", "status")),
            Map.entry("sales-order", Set.of("id", "orderNo", "purchaseInboundNo", "purchaseOrderNo", "customerName", "projectName", "deliveryDate", "salesName", "totalWeight", "totalAmount", "status")),
            Map.entry("sales-outbound", Set.of("id", "outboundNo", "salesOrderNo", "customerName", "projectName", "warehouseName", "outboundDate", "totalWeight", "totalAmount", "status")),
            Map.entry("freight-bill", Set.of("id", "billNo", "outboundNo", "carrierName", "vehiclePlate", "customerName", "projectName", "billTime", "unitPrice", "totalWeight", "totalFreight", "status", "deliveryStatus")),
            Map.entry("purchase-contract", Set.of("id", "contractNo", "supplierName", "signDate", "effectiveDate", "expireDate", "buyerName", "totalWeight", "totalAmount", "status")),
            Map.entry("sales-contract", Set.of("id", "contractNo", "customerName", "projectName", "signDate", "effectiveDate", "expireDate", "salesName", "totalWeight", "totalAmount", "status")),
            Map.entry("supplier-statement", Set.of("id", "statementNo", "sourceInboundNos", "supplierName", "startDate", "endDate", "purchaseAmount", "paymentAmount", "closingAmount", "status")),
            Map.entry("customer-statement", Set.of("id", "statementNo", "sourceOrderNos", "customerName", "projectName", "startDate", "endDate", "salesAmount", "receiptAmount", "closingAmount", "status")),
            Map.entry("freight-statement", Set.of("id", "statementNo", "sourceBillNos", "carrierName", "startDate", "endDate", "totalWeight", "totalFreight", "paidAmount", "unpaidAmount", "status", "signStatus")),
            Map.entry("receipt", Set.of("id", "receiptNo", "customerName", "projectName", "receiptDate", "payType", "amount", "status", "operatorName")),
            Map.entry("payment", Set.of("id", "paymentNo", "businessType", "counterpartyName", "paymentDate", "payType", "amount", "status", "operatorName")),
            Map.entry("invoice-receipt", Set.of("id", "receiveNo", "invoiceNo", "sourcePurchaseOrderNos", "supplierName", "invoiceDate", "invoiceType", "amount", "taxAmount", "status", "operatorName")),
            Map.entry("invoice-issue", Set.of("id", "issueNo", "invoiceNo", "sourceSalesOrderNos", "customerName", "projectName", "invoiceDate", "invoiceType", "amount", "taxAmount", "status", "operatorName")),
            Map.entry("pending-invoice-receipt-report", Set.of("orderNo", "supplierName", "orderDate", "materialCode", "pendingInvoiceWeightTon", "pendingInvoiceAmount")),
            Map.entry("department", Set.of("id", "departmentCode", "departmentName", "parentId", "managerName", "contactPhone", "sortOrder", "status")),
            Map.entry("user-account", Set.of("id", "loginName", "userName", "mobile", "roleName", "dataScope", "lastLoginDate", "status")),
            Map.entry("role-setting", Set.of("id", "roleCode", "roleName", "roleType", "dataScope", "status")),
            Map.entry("general-setting", Set.of("id", "settingCode", "settingName", "billName", "prefix", "dateRule", "serialLength", "resetRule", "sampleNo", "status")),
            Map.entry("company-setting", Set.of("id", "companyName", "taxNo", "bankName", "bankAccount", "status")),
            Map.entry("permission", Set.of("id", "permissionCode", "permissionName", "moduleName", "permissionType", "actionName", "status")),
            Map.entry("operation-log", Set.of("id", "logNo", "operatorName", "loginName", "moduleName", "actionType", "businessNo", "requestMethod", "requestPath", "clientIp", "resultStatus", "operationTime")),
            Map.entry("inventory-report", Set.of("brand", "category", "warehouseName", "quantity", "weightTon")),
            Map.entry("io-report", Set.of("businessDate", "businessType", "sourceNo", "materialCode", "warehouseName")),
            Map.entry("receivable-payable", Set.of("counterpartyName", "direction", "counterpartyType", "currentAmount", "balanceAmount", "status")),
            Map.entry("project", Set.of("id", "projectCode", "projectName", "projectNameAbbr", "customerCode", "projectManager", "status")),
            Map.entry("project-ar", Set.of("customerCode", "customerName", "projectName", "projectNameAbbr", "projectManager", "completedSalesAmount", "receivedAmount", "unreceivedAmount", "prepaymentBalance", "netUnreceivedAmount", "unreconciledDocumentCount", "reconciledDocumentCount", "latestBusinessDate")),
            Map.entry("project-ar-detail", Set.of("sourceDocumentNo", "businessDate", "customerCode", "customerName", "amount", "writtenOffAmount", "unwrittenOffAmount", "reconciliationStatus", "receiptStatus", "operatorName"))
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
