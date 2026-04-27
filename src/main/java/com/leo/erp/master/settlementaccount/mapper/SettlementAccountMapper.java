package com.leo.erp.master.settlementaccount.mapper;

import com.leo.erp.master.settlementaccount.domain.entity.SettlementAccount;
import com.leo.erp.master.settlementaccount.web.dto.SettlementAccountResponse;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface SettlementAccountMapper {

    SettlementAccountResponse toResponse(SettlementAccount entity);
}
