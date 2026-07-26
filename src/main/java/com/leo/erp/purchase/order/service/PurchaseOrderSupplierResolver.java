package com.leo.erp.purchase.order.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.master.api.SupplierQuery;
import com.leo.erp.master.api.SupplierQuery.SupplierSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
public class PurchaseOrderSupplierResolver {

    private static final Logger log = LoggerFactory.getLogger(PurchaseOrderSupplierResolver.class);

    private final SupplierQuery supplierQuery;

    public PurchaseOrderSupplierResolver(SupplierQuery supplierQuery) {
        this.supplierQuery = supplierQuery;
    }

    String requireMasterSupplierName(String supplierName) {
        String normalizedName = supplierName == null ? "" : supplierName.trim();
        return supplierQuery.findFirstActiveByNameOrderByCode(normalizedName)
                .map(SupplierSnapshot::name)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.BUSINESS_ERROR,
                        "供应商不存在，请先在主数据供应商资料中维护"
                ));
    }

    SupplierIdentity requireMasterSupplier(String supplierCode, String supplierName) {
        return requireMasterSupplier(null, supplierCode, supplierName);
    }

    SupplierIdentity requireMasterSupplier(Long supplierId, String supplierCode, String supplierName) {
        String normalizedCode = normalize(supplierCode);
        String normalizedName = normalize(supplierName);
        if (supplierId != null) {
            SupplierSnapshot supplier = supplierQuery.findActiveById(supplierId)
                    .orElseThrow(() -> missingSupplier());
            if (normalizedCode != null && !Objects.equals(normalizedCode, normalize(supplier.code()))) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "供应商ID与供应商编码不一致");
            }
            if (normalizedName != null && !Objects.equals(normalizedName, normalize(supplier.name()))) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "供应商ID与供应商名称不一致");
            }
            return identityOf(supplier);
        }

        if (normalizedCode != null) {
            SupplierSnapshot supplier = supplierQuery.findActiveByCode(normalizedCode)
                    .orElseThrow(() -> missingSupplier());
            log.warn("identity_fallback module=purchase-order field=supplierId reason=supplier-code resolvedId={}",
                    supplier.id());
            return identityOf(supplier);
        }

        List<SupplierSnapshot> suppliers = supplierQuery
                .findActiveByNameOrderByCode(
                        normalizedName == null ? "" : normalizedName
                );
        if (suppliers.isEmpty()) {
            suppliers = supplierQuery
                    .findFirstActiveByNameOrderByCode(
                            normalizedName == null ? "" : normalizedName
                    )
                    .map(List::of)
                    .orElseGet(List::of);
        }
        if (suppliers.isEmpty()) {
            throw missingSupplier();
        }
        if (suppliers.size() > 1) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "供应商名称对应多个编码，请按供应商编码重新选择"
            );
        }
        log.warn("identity_fallback module=purchase-order field=supplierId reason=supplier-name resolvedId={}",
                suppliers.get(0).id());
        return identityOf(suppliers.get(0));
    }

    private SupplierIdentity identityOf(SupplierSnapshot supplier) {
        return new SupplierIdentity(supplier.id(), supplier.code(), supplier.name());
    }

    private BusinessException missingSupplier() {
        return new BusinessException(
                ErrorCode.BUSINESS_ERROR,
                "供应商不存在，请先在主数据供应商资料中维护"
        );
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    record SupplierIdentity(Long supplierId, String supplierCode, String supplierName) {

        SupplierIdentity(String supplierCode, String supplierName) {
            this(null, supplierCode, supplierName);
        }
    }
}
