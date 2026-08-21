package com.leo.erp.master.statistics.service;

import com.leo.erp.master.api.MasterDataStatisticsQuery.MasterDataStatistics;
import com.leo.erp.master.customer.repository.CustomerRepository;
import com.leo.erp.master.material.repository.MaterialRepository;
import com.leo.erp.master.supplier.repository.SupplierRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * MasterDataStatisticsQueryService 极端情况测试：三仓聚合、零计数、边界大数透传。
 */
@ExtendWith(MockitoExtension.class)
class MasterDataStatisticsQueryServiceTest {

    @Mock
    private MaterialRepository materialRepository;

    @Mock
    private SupplierRepository supplierRepository;

    @Mock
    private CustomerRepository customerRepository;

    @InjectMocks
    private MasterDataStatisticsQueryService service;

    @Test
    void countActiveRecords_shouldAggregateCounts() {
        when(materialRepository.countByDeletedFlagFalse()).thenReturn(10L);
        when(supplierRepository.countByDeletedFlagFalse()).thenReturn(20L);
        when(customerRepository.countByDeletedFlagFalse()).thenReturn(30L);

        MasterDataStatistics statistics = service.countActiveRecords();

        assertThat(statistics).isEqualTo(new MasterDataStatistics(10L, 20L, 30L));
    }

    @Test
    void countActiveRecords_shouldHandleZeroCounts() {
        when(materialRepository.countByDeletedFlagFalse()).thenReturn(0L);
        when(supplierRepository.countByDeletedFlagFalse()).thenReturn(0L);
        when(customerRepository.countByDeletedFlagFalse()).thenReturn(0L);

        assertThat(service.countActiveRecords()).isEqualTo(new MasterDataStatistics(0L, 0L, 0L));
    }

    @Test
    void countActiveRecords_shouldHandleLargeCounts() {
        when(materialRepository.countByDeletedFlagFalse()).thenReturn(Long.MAX_VALUE);
        when(supplierRepository.countByDeletedFlagFalse()).thenReturn(9007199254740991L);
        when(customerRepository.countByDeletedFlagFalse()).thenReturn(123L);

        assertThat(service.countActiveRecords())
                .isEqualTo(new MasterDataStatistics(Long.MAX_VALUE, 9007199254740991L, 123L));
    }
}
