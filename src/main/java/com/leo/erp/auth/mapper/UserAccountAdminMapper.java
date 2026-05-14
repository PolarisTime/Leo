package com.leo.erp.auth.mapper;

import com.leo.erp.auth.domain.entity.UserAccount;
import com.leo.erp.auth.domain.enums.UserStatus;
import com.leo.erp.auth.web.dto.UserAccountAdminResponse;
import com.leo.erp.common.mapper.StrictMapperConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(config = StrictMapperConfig.class)
public interface UserAccountAdminMapper {

    @Mapping(target = "status", expression = "java(fromStatus(entity.getStatus()))")
    @Mapping(target = "roleNames", expression = "java(toRoleNames(entity.getRoleName()))")
    UserAccountAdminResponse toResponse(UserAccount entity);

    default String fromStatus(UserStatus status) {
        if (status == null) {
            return "正常";
        }
        return status == UserStatus.DISABLED ? "禁用" : "正常";
    }

    default List<String> toRoleNames(String roleName) {
        if (roleName == null || roleName.isBlank()) {
            return List.of();
        }
        return List.of(roleName);
    }
}
