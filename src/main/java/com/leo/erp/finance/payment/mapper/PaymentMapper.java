package com.leo.erp.finance.payment.mapper;

import com.leo.erp.finance.payment.domain.entity.Payment;
import com.leo.erp.finance.payment.web.dto.PaymentResponse;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface PaymentMapper {

    PaymentResponse toResponse(Payment payment);
}
