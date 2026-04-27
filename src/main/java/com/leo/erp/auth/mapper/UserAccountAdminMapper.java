package com.leo.erp.auth.mapper;

import com.leo.erp.auth.domain.entity.UserAccount;
import com.leo.erp.auth.domain.enums.UserStatus;
import com.leo.erp.auth.web.dto.UserAccountAdminResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UserAccountAdminMapper {

    @Mapping(target = "status", expression = "java(fromStatus(entity.getStatus()))")
    UserAccountAdminResponse toResponse(UserAccount entity);

    default String fromStatus(UserStatus status) {
        if (status == null) {
            return "正常";
        }
        return status == UserStatus.DISABLED ? "禁用" : "正常";
    }
}
