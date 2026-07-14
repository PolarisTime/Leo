package com.leo.erp.system.operationlog.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OperationLogCommandTest {

    @Test
    void shouldCreateWithShortConstructor() {
        var command = new OperationLogCommand(
                "测试模块", "新增", "BIZ001", "POST", "/api/test",
                "127.0.0.1", "成功", "操作成功"
        );

        assertThat(command.moduleName()).isEqualTo("测试模块");
        assertThat(command.actionType()).isEqualTo("新增");
        assertThat(command.businessNo()).isEqualTo("BIZ001");
        assertThat(command.requestMethod()).isEqualTo("POST");
        assertThat(command.requestPath()).isEqualTo("/api/test");
        assertThat(command.clientIp()).isEqualTo("127.0.0.1");
        assertThat(command.resultStatus()).isEqualTo("成功");
        assertThat(command.remark()).isEqualTo("操作成功");
        assertThat(command.recordId()).isNull();
        assertThat(command.moduleKey()).isNull();
        assertThat(command.operatorId()).isNull();
        assertThat(command.operatorName()).isNull();
        assertThat(command.loginName()).isNull();
    }

    @Test
    void shouldCreateWithFullConstructor() {
        var command = new OperationLogCommand(
                "测试模块", "编辑", "BIZ002", "PUT", "/api/test/1",
                "192.168.1.1", "成功", "编辑成功", 1L, "test",
                100L, "管理员", "admin"
        );

        assertThat(command.moduleName()).isEqualTo("测试模块");
        assertThat(command.actionType()).isEqualTo("编辑");
        assertThat(command.businessNo()).isEqualTo("BIZ002");
        assertThat(command.requestMethod()).isEqualTo("PUT");
        assertThat(command.requestPath()).isEqualTo("/api/test/1");
        assertThat(command.clientIp()).isEqualTo("192.168.1.1");
        assertThat(command.resultStatus()).isEqualTo("成功");
        assertThat(command.remark()).isEqualTo("编辑成功");
        assertThat(command.recordId()).isEqualTo(1L);
        assertThat(command.moduleKey()).isEqualTo("test");
        assertThat(command.operatorId()).isEqualTo(100L);
        assertThat(command.operatorName()).isEqualTo("管理员");
        assertThat(command.loginName()).isEqualTo("admin");
    }
}
