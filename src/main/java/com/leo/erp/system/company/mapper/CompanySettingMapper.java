package com.leo.erp.system.company.mapper;

import com.leo.erp.system.company.domain.entity.CompanySetting;
import com.leo.erp.system.company.web.dto.CompanySettingResponse;
import com.leo.erp.system.company.web.dto.CompanySettlementAccountResponse;
import com.leo.erp.common.mapper.StrictMapperConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(config = StrictMapperConfig.class)
public interface CompanySettingMapper {

    @Mapping(target = "settlementAccounts", source = "settlementAccounts")
    CompanySettingResponse toResponse(CompanySetting entity,
                                      List<CompanySettlementAccountResponse> settlementAccounts);
}
