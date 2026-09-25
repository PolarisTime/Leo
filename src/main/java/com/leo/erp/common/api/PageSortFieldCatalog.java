package com.leo.erp.common.api;

import com.leo.erp.common.support.ModuleKeys;
import java.util.Map;
import java.util.Set;

public final class PageSortFieldCatalog {

    private static final Map<String, Set<String>> FIELDS_BY_KEY = Map.ofEntries(
            Map.entry(ModuleKeys.MATERIAL, Set.of("id", "materialCode", "brand", "material", "category", "spec", "length", "unit", "quantityUnit", "pieceWeightTon", "piecesPerBundle", "unitPrice", "lengthSort", "specSort")),
            Map.entry(ModuleKeys.MATERIAL_CATEGORY, Set.of("id", "categoryCode", "categoryName", "sortOrder", "purchaseWeighRequired", "status", "remark")),
            Map.entry(ModuleKeys.SUPPLIER, Set.of("id", "supplierCode", "supplierName", "contactName", "contactPhone", "city", "status")),
            Map.entry(ModuleKeys.CUSTOMER, Set.of("id", "customerCode", "customerName", "contactName", "contactPhone", "city", "settlementMode", "projectName", "status")),
            Map.entry(ModuleKeys.CARRIER, Set.of("id", "carrierCode", "carrierName", "contactName", "contactPhone", "vehicleType", "priceMode", "status")),
            Map.entry(ModuleKeys.WAREHOUSE, Set.of("id", "warehouseCode", "warehouseName", "warehouseType", "contactName", "contactPhone", "address", "status")),
            Map.entry(ModuleKeys.PURCHASE_ORDER, Set.of("id", "orderNo", "supplierCode", "supplierName", "orderDate", "buyerName", "totalWeight", "totalAmount", "status")),
            Map.entry(ModuleKeys.PURCHASE_INBOUND, Set.of("id", "inboundNo", "purchaseOrderNo", "supplierCode", "supplierName", "warehouseName", "inboundDate", "settlementMode", "totalWeight", "totalAmount", "status")),
            Map.entry(ModuleKeys.SALES_ORDER, Set.of("id", "orderNo", "purchaseInboundNo", "purchaseOrderNo", "customerName", "projectName", "deliveryDate", "salesName", "totalWeight", "totalAmount", "status")),
            Map.entry(ModuleKeys.SALES_CONTRACT, Set.of("id", "contractNo", "name", "customerName", "projectName", "signDate", "startDate", "endDate", "totalAmount", "totalTonnage", "status")),
            Map.entry(ModuleKeys.SALES_OUTBOUND, Set.of("id", "outboundNo", "salesOrderNo", "customerName", "projectName", "warehouseName", "outboundDate", "totalWeight", "totalAmount", "status")),
            Map.entry(ModuleKeys.SALES_RETURN, Set.of("id", "returnNo", "salesOrderNo", "customerName", "projectName", "warehouseName", "returnDate", "totalWeight", "totalAmount", "status")),
            Map.entry(ModuleKeys.FREIGHT_BILL, Set.of("id", "billNo", "carrierCode", "carrierName", "vehiclePlate", "customerName", "projectName", "billTime", "unitPrice", "totalWeight", "totalFreight", "status")),
            Map.entry(ModuleKeys.CUSTOMER_STATEMENT, Set.of("id", "statementNo", "customerName", "projectName", "startDate", "endDate", "salesAmount", "receiptAmount", "closingAmount", "status")),
            Map.entry(ModuleKeys.FREIGHT_STATEMENT, Set.of("id", "statementNo", "carrierCode", "carrierName", "startDate", "endDate", "totalWeight", "totalFreight", "paidAmount", "unpaidAmount", "status", "signStatus")),
            Map.entry(ModuleKeys.RECEIPT, Set.of("id", "receiptNo", "customerName", "projectName", "receiptDate", "payType", "amount", "status", "operatorName")),
            Map.entry(ModuleKeys.PAYMENT, Set.of("id", "paymentNo", "businessType", "counterpartyCode", "counterpartyName", "paymentDate", "payType", "amount", "status", "operatorName")),
            Map.entry(ModuleKeys.LEDGER_ADJUSTMENT, Set.of("id", "adjustmentNo", "direction", "counterpartyType", "counterpartyCode", "counterpartyName", "projectName", "adjustmentDate", "amount", "adjustmentType", "effect", "status", "operatorName")),
            Map.entry("company-setting", Set.of("id", "companyName", "taxNo", "bankName", "bankAccount", "status")),
            Map.entry("operation-log", Set.of("id", "logNo", "operatorName", "loginName", "moduleName", "actionType", "businessNo", "requestMethod", "requestPath", "clientIp", "resultStatus", "operationTime")),
            Map.entry(ModuleKeys.CASH_LEDGER, Set.of("businessDate")),
            Map.entry(ModuleKeys.FINANCE_OVERVIEW, Set.of("counterpartyName", "recognizedAmount", "settledAmount", "outstandingAmount", "advanceAmount")),
            Map.entry(ModuleKeys.PROJECT, Set.of("id", "projectCode", "projectName", "projectNameAbbr", "customerCode", "projectManager", "status")),
            Map.entry("inventory-balance", Set.of("materialId", "materialCode", "warehouseId", "warehouseName", "batchNo", "quantity", "amount", "avgUnitCost")),
            Map.entry("inventory-transaction", Set.of("id", "transactionNo", "transactionType", "materialCode", "warehouseName", "batchNo", "quantity", "unitCost", "amount", "occurredAt")),
            Map.entry("steel-quote", Set.of("id", "quoteDate", "period", "breed", "spec", "material", "factory", "price", "scrapedAt")),
            Map.entry("quote-sheet", Set.of("id", "sheetNo", "name", "orderDate", "refDate", "refPeriod", "status")),
            Map.entry("role", Set.of("id", "code", "name", "status", "builtin", "createdAt", "updatedAt")),
            Map.entry("user", Set.of("id", "loginName", "userName", "mobile", "status", "lastLoginDate", "createdAt", "updatedAt"))
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
