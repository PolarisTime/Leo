package com.leo.erp.system.generalsetting.mapper;

import com.leo.erp.system.generalsetting.domain.entity.GeneralSetting;
import com.leo.erp.system.generalsetting.web.dto.GeneralSettingResponse;
import com.leo.erp.common.mapper.StrictMapperConfig;
import org.mapstruct.Mapper;

@Mapper(config = StrictMapperConfig.class)
public interface GeneralSettingMapper {

    GeneralSettingResponse toResponse(GeneralSetting setting);
}
