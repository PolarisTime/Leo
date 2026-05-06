package com.leo.erp.finance.payment.mapper;

import com.leo.erp.finance.payment.domain.entity.Payment;
import com.leo.erp.finance.payment.web.dto.PaymentResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PaymentMapper {

    @Mapping(target = "items", ignore = true)
    PaymentResponse toResponse(Payment payment);
}
