package com.leo.erp.system.norule.mapper;

import com.leo.erp.system.norule.domain.entity.NoRule;
import com.leo.erp.system.norule.web.dto.NoRuleResponse;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import static org.assertj.core.api.Assertions.assertThat;

class NoRuleMapperTest {

    private final NoRuleMapper mapper = Mappers.getMapper(NoRuleMapper.class);

    @Test
    void shouldMapEntityToResponse() {
        NoRule entity = new NoRule();
        entity.setId(1L);
        entity.setSettingCode("TAX_RATE");
        entity.setSettingName("默认税率");
        entity.setBillName("发票税率");
        entity.setPrefix("SYS");
        entity.setDateRule("yyyy");
        entity.setSerialLength(1);
        entity.setResetRule("YEARLY");
        entity.setSampleNo("0.13");
        entity.setStatus("正常");
        entity.setRemark("用于发票默认税率");

        NoRuleResponse response = mapper.toResponse(entity);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.settingCode()).isEqualTo("TAX_RATE");
        assertThat(response.settingName()).isEqualTo("默认税率");
        assertThat(response.billName()).isEqualTo("发票税率");
        assertThat(response.prefix()).isEqualTo("SYS");
        assertThat(response.status()).isEqualTo("正常");
    }

    @Test
    void shouldMapWithNullOptionalFields() {
        NoRule entity = new NoRule();
        entity.setId(2L);
        entity.setSettingCode("OOBE");
        entity.setSettingName("OOBE状态");
        entity.setBillName("系统初始化");
        entity.setPrefix("SYS");
        entity.setDateRule("yyyy");
        entity.setSerialLength(1);
        entity.setResetRule("ONCE");
        entity.setSampleNo("COMPLETED");
        entity.setStatus("正常");

        NoRuleResponse response = mapper.toResponse(entity);

        assertThat(response).isNotNull();
        assertThat(response.remark()).isNull();
    }

    @Test
    void shouldReturnNullWhenEntityIsNull() {
        assertThat(mapper.toResponse(null)).isNull();
    }
}
