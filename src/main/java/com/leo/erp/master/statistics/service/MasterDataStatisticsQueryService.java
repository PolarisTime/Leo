package com.leo.erp.master.statistics.service;

import com.leo.erp.master.api.MasterDataStatisticsQuery;
import com.leo.erp.master.customer.repository.CustomerRepository;
import com.leo.erp.master.material.repository.MaterialRepository;
import com.leo.erp.master.supplier.repository.SupplierRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class MasterDataStatisticsQueryService implements MasterDataStatisticsQuery {

    private final MaterialRepository materialRepository;
    private final SupplierRepository supplierRepository;
    private final CustomerRepository customerRepository;

    public MasterDataStatisticsQueryService(MaterialRepository materialRepository,
                                            SupplierRepository supplierRepository,
                                            CustomerRepository customerRepository) {
        this.materialRepository = materialRepository;
        this.supplierRepository = supplierRepository;
        this.customerRepository = customerRepository;
    }

    @Override
    public MasterDataStatistics countActiveRecords() {
        return new MasterDataStatistics(
                materialRepository.countByDeletedFlagFalse(),
                supplierRepository.countByDeletedFlagFalse(),
                customerRepository.countByDeletedFlagFalse()
        );
    }
}
