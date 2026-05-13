package com.leo.erp.system.company.mapper;

import com.leo.erp.system.company.domain.entity.CompanySetting;
import com.leo.erp.system.company.web.dto.CompanySettingResponse;
import com.leo.erp.system.company.web.dto.CompanySettlementAccountResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.math.BigDecimal;
import java.util.List;

@Mapper(componentModel = "spring")
public interface CompanySettingMapper {

    @Mapping(target = "taxRate", source = "taxRate")
    @Mapping(target = "settlementAccounts", source = "settlementAccounts")
    CompanySettingResponse toResponse(CompanySetting entity,
                                      BigDecimal taxRate,
                                      List<CompanySettlementAccountResponse> settlementAccounts);
}
