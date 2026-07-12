package com.leo.erp.purchase.order.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.master.supplier.domain.entity.Supplier;
import com.leo.erp.master.supplier.repository.SupplierRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
public class PurchaseOrderSupplierResolver {

    private static final Logger log = LoggerFactory.getLogger(PurchaseOrderSupplierResolver.class);

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
        return requireMasterSupplier(null, supplierCode, supplierName);
    }

    SupplierIdentity requireMasterSupplier(Long supplierId, String supplierCode, String supplierName) {
        String normalizedCode = normalize(supplierCode);
        String normalizedName = normalize(supplierName);
        if (supplierId != null) {
            Supplier supplier = supplierRepository.findByIdAndDeletedFlagFalse(supplierId)
                    .orElseThrow(() -> missingSupplier());
            if (normalizedCode != null && !Objects.equals(normalizedCode, normalize(supplier.getSupplierCode()))) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "供应商ID与供应商编码不一致");
            }
            if (normalizedName != null && !Objects.equals(normalizedName, normalize(supplier.getSupplierName()))) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "供应商ID与供应商名称不一致");
            }
            return identityOf(supplier);
        }

        if (normalizedCode != null) {
            Supplier supplier = supplierRepository.findBySupplierCodeAndDeletedFlagFalse(normalizedCode)
                    .orElseThrow(() -> missingSupplier());
            log.warn("identity_fallback module=purchase-order field=supplierId reason=supplier-code resolvedId={}",
                    supplier.getId());
            return identityOf(supplier);
        }

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
        log.warn("identity_fallback module=purchase-order field=supplierId reason=supplier-name resolvedId={}",
                suppliers.get(0).getId());
        return identityOf(suppliers.get(0));
    }

    private SupplierIdentity identityOf(Supplier supplier) {
        return new SupplierIdentity(supplier.getId(), supplier.getSupplierCode(), supplier.getSupplierName());
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
