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
            Map.entry("purchase-order", Set.of("id", "orderNo", "supplierCode", "supplierName", "orderDate", "buyerName", "totalWeight", "totalAmount", "status")),
            Map.entry("purchase-inbound", Set.of("id", "inboundNo", "purchaseOrderNo", "supplierCode", "supplierName", "warehouseName", "inboundDate", "settlementMode", "totalWeight", "totalAmount", "status")),
            Map.entry("sales-order", Set.of("id", "orderNo", "purchaseInboundNo", "purchaseOrderNo", "customerName", "projectName", "deliveryDate", "salesName", "totalWeight", "totalAmount", "status")),
            Map.entry("sales-outbound", Set.of("id", "outboundNo", "salesOrderNo", "customerName", "projectName", "warehouseName", "outboundDate", "totalWeight", "totalAmount", "status")),
            Map.entry("freight-bill", Set.of("id", "billNo", "carrierCode", "carrierName", "vehiclePlate", "customerName", "projectName", "billTime", "unitPrice", "totalWeight", "totalFreight", "status")),
            Map.entry("customer-statement", Set.of("id", "statementNo", "customerName", "projectName", "startDate", "endDate", "salesAmount", "receiptAmount", "closingAmount", "status")),
            Map.entry("freight-statement", Set.of("id", "statementNo", "carrierCode", "carrierName", "startDate", "endDate", "totalWeight", "totalFreight", "paidAmount", "unpaidAmount", "status", "signStatus")),
            Map.entry("receipt", Set.of("id", "receiptNo", "customerName", "projectName", "receiptDate", "payType", "amount", "status", "operatorName")),
            Map.entry("payment", Set.of("id", "paymentNo", "businessType", "counterpartyCode", "counterpartyName", "paymentDate", "payType", "amount", "status", "operatorName")),
            Map.entry("ledger-adjustment", Set.of("id", "adjustmentNo", "direction", "counterpartyType", "counterpartyCode", "counterpartyName", "projectName", "adjustmentDate", "amount", "adjustmentType", "effect", "status", "operatorName")),
            Map.entry("department", Set.of("id", "departmentCode", "departmentName", "parentId", "managerName", "contactPhone", "sortOrder", "status")),
            Map.entry("user-account", Set.of("id", "loginName", "userName", "mobile", "lastLoginDate", "status")),
            Map.entry("general-setting", Set.of(
                    "id", "settingCode", "settingName", "settingGroup", "settingValue", "status"
            )),
            Map.entry("company-setting", Set.of("id", "companyName", "taxNo", "bankName", "bankAccount", "status")),
            Map.entry("operation-log", Set.of("id", "logNo", "operatorName", "loginName", "moduleName", "actionType", "businessNo", "requestMethod", "requestPath", "clientIp", "resultStatus", "operationTime")),
            Map.entry("cash-ledger", Set.of("businessDate")),
            Map.entry("project", Set.of("id", "projectCode", "projectName", "projectNameAbbr", "customerCode", "projectManager", "status"))
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
