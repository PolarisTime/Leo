package com.leo.erp.system.company.mapper;

import com.leo.erp.system.company.domain.entity.CompanySetting;
import com.leo.erp.system.company.web.dto.CompanySettingResponse;
import com.leo.erp.system.company.web.dto.CompanySettlementAccountResponse;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class CompanySettingMapper {

    public CompanySettingResponse toResponse(CompanySetting entity,
                                             BigDecimal taxRate,
                                             List<CompanySettlementAccountResponse> settlementAccounts) {
        return new CompanySettingResponse(
                entity.getId(),
                entity.getCompanyName(),
                entity.getTaxNo(),
                entity.getBankName(),
                entity.getBankAccount(),
                taxRate,
                settlementAccounts,
                entity.getStatus(),
                entity.getRemark()
        );
    }
}
