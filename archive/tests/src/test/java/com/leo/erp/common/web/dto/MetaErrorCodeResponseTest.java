package com.leo.erp.common.web.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MetaErrorCodeResponseTest {

    @Test
    void recordAccessors() {
        MetaErrorCodeResponse response = new MetaErrorCodeResponse("NOT_FOUND", 4040, "资源不存在");
        assertThat(response.name()).isEqualTo("NOT_FOUND");
        assertThat(response.code()).isEqualTo(4040);
        assertThat(response.message()).isEqualTo("资源不存在");
    }

    @Test
    void recordEquality() {
        MetaErrorCodeResponse a = new MetaErrorCodeResponse("ERROR", 5000, "系统内部错误");
        MetaErrorCodeResponse b = new MetaErrorCodeResponse("ERROR", 5000, "系统内部错误");
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void recordToString() {
        MetaErrorCodeResponse response = new MetaErrorCodeResponse("ERR", 400, "bad");
        assertThat(response.toString()).contains("ERR", "400", "bad");
    }
}
