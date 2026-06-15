package com.leo.erp.purchase.order.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.master.supplier.domain.entity.Supplier;
import com.leo.erp.master.supplier.repository.SupplierRepository;
import org.springframework.stereotype.Service;

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
}
