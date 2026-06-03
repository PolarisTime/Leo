package com.leo.erp.system.operationlog.mapper;

import com.leo.erp.system.operationlog.domain.entity.OperationLog;
import com.leo.erp.system.operationlog.web.dto.OperationLogResponse;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class OperationLogMapperTest {

    private final OperationLogMapper mapper = Mappers.getMapper(OperationLogMapper.class);

    @Test
    void shouldMapEntityToResponse() {
        OperationLog entity = new OperationLog();
        entity.setId(1L);
        entity.setLogNo("OP1");
        entity.setOperatorId(10L);
        entity.setOperatorName("管理员");
        entity.setLoginName("admin");
        entity.setModuleName("用户管理");
        entity.setActionType("创建");
        entity.setBusinessNo("BIZ001");
        entity.setRecordId(100L);
        entity.setModuleKey("user");
        entity.setRequestMethod("POST");
        entity.setRequestPath("/api/users");
        entity.setClientIp("127.0.0.1");
        entity.setResultStatus("成功");
        entity.setOperationTime(LocalDateTime.of(2026, 1, 15, 10, 30, 0));
        entity.setAuthType("WEB");
        entity.setRemark("创建用户");

        OperationLogResponse response = mapper.toResponse(entity);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.logNo()).isEqualTo("OP1");
        assertThat(response.operatorName()).isEqualTo("管理员");
        assertThat(response.loginName()).isEqualTo("admin");
        assertThat(response.moduleName()).isEqualTo("用户管理");
        assertThat(response.actionType()).isEqualTo("创建");
        assertThat(response.resultStatus()).isEqualTo("成功");
    }

    @Test
    void shouldMapNullFieldsToNull() {
        OperationLog entity = new OperationLog();
        entity.setId(2L);
        entity.setLogNo("OP2");
        entity.setOperatorName("系统");
        entity.setLoginName("system");
        entity.setModuleName("系统");
        entity.setActionType("自动");
        entity.setRequestMethod("INTERNAL");
        entity.setRequestPath("internal");
        entity.setResultStatus("成功");
        entity.setOperationTime(LocalDateTime.now());

        OperationLogResponse response = mapper.toResponse(entity);

        assertThat(response).isNotNull();
        assertThat(response.businessNo()).isNull();
        assertThat(response.moduleKey()).isNull();
        assertThat(response.clientIp()).isNull();
        assertThat(response.authType()).isNull();
        assertThat(response.remark()).isNull();
    }
}
