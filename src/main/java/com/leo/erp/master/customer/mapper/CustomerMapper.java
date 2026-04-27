package com.leo.erp.master.customer.mapper;

import com.leo.erp.master.customer.domain.entity.Customer;
import com.leo.erp.master.customer.web.dto.CustomerResponse;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface CustomerMapper {

    CustomerResponse toResponse(Customer customer);
}
