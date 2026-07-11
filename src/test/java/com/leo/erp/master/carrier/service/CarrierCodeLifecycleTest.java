package com.leo.erp.master.carrier.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.MasterDataReferenceGuard;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.master.carrier.domain.entity.Carrier;
import com.leo.erp.master.carrier.mapper.CarrierMapper;
import com.leo.erp.master.carrier.repository.CarrierRepository;
import com.leo.erp.master.carrier.repository.VehicleRepository;
import com.leo.erp.master.carrier.web.dto.CarrierRequest;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CarrierCodeLifecycleTest {

    @Test
    void shouldRejectCarrierCodeChangeWhenStableBusinessSnapshotsReferenceOldCode() {
        CarrierRepository repository = mock(CarrierRepository.class);
        MasterDataReferenceGuard referenceGuard = mock(MasterDataReferenceGuard.class);
        Carrier existing = new Carrier();
        existing.setId(1L);
        existing.setCarrierCode("CR-001");
        existing.setCarrierName("物流甲");
        existing.setStatus(StatusConstants.NORMAL);
        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(existing));
        doThrow(new BusinessException(ErrorCode.BUSINESS_ERROR, "该物流商编码已被引用，不能修改"))
                .when(referenceGuard)
                .assertNoReferences(eq("该物流商编码"), eq("修改"), anyList());
        CarrierService service = new CarrierService(
                repository,
                mock(VehicleRepository.class),
                mock(SnowflakeIdGenerator.class),
                mock(CarrierMapper.class),
                null,
                referenceGuard
        );
        CarrierRequest request = new CarrierRequest(
                "CR-002",
                "物流甲",
                null,
                null,
                null,
                null,
                null,
                null,
                StatusConstants.NORMAL,
                null
        );

        assertThatThrownBy(() -> service.update(1L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("物流商编码")
                .hasMessageContaining("不能修改");
        verify(repository, never()).save(existing);
    }
}
