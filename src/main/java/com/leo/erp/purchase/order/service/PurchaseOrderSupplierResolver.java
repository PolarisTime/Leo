package com.leo.erp.purchase.order.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.master.supplier.domain.entity.Supplier;
import com.leo.erp.master.supplier.repository.SupplierRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PurchaseOrderSupplierResolver {

    private final SupplierRepository supplierRepository;

    public PurchaseOrderSupplierResolver(SupplierRepository supplierRepository) {
        this.supplierRepository = supplierRepository;
    }

    String requireMasterSupplierName(String supplierName) {
        String normalizedName = supplierName == null ? "" : supplierName.trim();
        return supplierRepository.findFirstBySupplierNameAndDeletedFlagFalseOrderBySupplierCodeAsc(normalizedName)
                .map(Supplier::getSupplierName)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.BUSINESS_ERROR,
                        "供应商不存在，请先在主数据供应商资料中维护"
                ));
    }

    SupplierIdentity requireMasterSupplier(String supplierCode, String supplierName) {
        String normalizedCode = normalize(supplierCode);
        if (normalizedCode != null) {
            Supplier supplier = supplierRepository.findBySupplierCodeAndDeletedFlagFalse(normalizedCode)
                    .orElseThrow(() -> missingSupplier());
            return identityOf(supplier);
        }

        String normalizedName = normalize(supplierName);
        List<Supplier> suppliers = supplierRepository
                .findBySupplierNameAndDeletedFlagFalseOrderBySupplierCodeAsc(
                        normalizedName == null ? "" : normalizedName
                );
        if (suppliers.isEmpty()) {
            suppliers = supplierRepository
                    .findFirstBySupplierNameAndDeletedFlagFalseOrderBySupplierCodeAsc(
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
        return identityOf(suppliers.get(0));
    }

    private SupplierIdentity identityOf(Supplier supplier) {
        return new SupplierIdentity(supplier.getSupplierCode(), supplier.getSupplierName());
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

    record SupplierIdentity(String supplierCode, String supplierName) {
    }
}
