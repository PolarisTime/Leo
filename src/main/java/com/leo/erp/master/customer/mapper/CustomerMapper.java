package com.leo.erp.master.customer.mapper;

import com.leo.erp.master.customer.domain.entity.Customer;
import com.leo.erp.master.customer.web.dto.CustomerResponse;
import com.leo.erp.common.mapper.StrictMapperConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = StrictMapperConfig.class)
public interface CustomerMapper {

    @Mapping(target = "projectNames", ignore = true)
    CustomerResponse toResponse(Customer customer);
}
