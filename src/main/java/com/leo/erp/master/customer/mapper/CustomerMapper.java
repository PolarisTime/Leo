package com.leo.erp.master.customer.mapper;

import com.leo.erp.master.customer.domain.entity.Customer;
import com.leo.erp.master.customer.web.dto.CustomerResponse;
import com.leo.erp.common.mapper.StrictMapperConfig;
import org.mapstruct.Mapper;

@Mapper(config = StrictMapperConfig.class)
public interface CustomerMapper {

    CustomerResponse toResponse(Customer customer);
}
