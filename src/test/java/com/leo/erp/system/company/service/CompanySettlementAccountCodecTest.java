package com.leo.erp.system.company.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.system.company.domain.entity.CompanySetting;
import com.leo.erp.system.company.web.dto.CompanySettlementAccountRequest;
import com.leo.erp.system.company.web.dto.CompanySettlementAccountResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompanySettlementAccountCodecTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Mock
    private SnowflakeIdGenerator idGenerator;

    @Test
    void normalize_shouldReturnEmptyListForNullAndEmptyInput() {
        CompanySettlementAccountCodec codec = codec();

        assertThat(codec.normalize(null)).isEmpty();
        assertThat(codec.normalize(List.of())).isEmpty();
        verifyNoInteractions(idGenerator);
    }

    @Test
    void normalize_shouldSkipBlankEntriesAndAssignGeneratedIds() {
        CompanySettlementAccountCodec codec = codec();
        when(idGenerator.nextId()).thenReturn(101L, 102L);

        List<CompanySettlementAccountResponse> result = codec.normalize(Arrays.asList(
                null,
                new CompanySettlementAccountRequest(null, null, null, null, null, null, null),
                new CompanySettlementAccountRequest(null, "户名A", "银行A", "622200001", "通用", "正常", "备注"),
                new CompanySettlementAccountRequest(null, "户名B", "银行B", null, null, null, null)
        ));

        assertThat(result).hasSize(2);
        assertThat(result.get(0).id()).isEqualTo(101L);
        assertThat(result.get(0).bankAccount()).isEqualTo("622200001");
        assertThat(result.get(1).id()).isEqualTo(102L);
        assertThat(result.get(1).usageType()).isEqualTo("通用");
        assertThat(result.get(1).status()).isEqualTo("正常");
    }

    @Test
    void normalize_shouldKeepExplicitIdsWithoutIdGenerator() {
        CompanySettlementAccountCodec codec = codec();

        List<CompanySettlementAccountResponse> result = codec.normalize(List.of(
                new CompanySettlementAccountRequest(88L, "户名A", "银行A", "622200001", "通用", "正常", null)
        ));

        assertThat(result.get(0).id()).isEqualTo(88L);
        verifyNoInteractions(idGenerator);
    }

    @Test
    void normalize_shouldRejectDuplicateBankAccounts() {
        CompanySettlementAccountCodec codec = codec();

        assertThatThrownBy(() -> codec.normalize(List.of(
                new CompanySettlementAccountRequest(null, "户名A", "银行A", "622200001", "通用", "正常", null),
                new CompanySettlementAccountRequest(null, "户名B", "银行B", "622200001", "通用", "正常", null)
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("银行账号不能重复")
                .hasMessageContaining("622200001");
    }

    @Test
    void normalize_shouldTrimOptionalFields() {
        CompanySettlementAccountCodec codec = codec();

        List<CompanySettlementAccountResponse> result = codec.normalize(List.of(
                new CompanySettlementAccountRequest(null, " 户名A ", " 银行A ", " 622200001 ", " 通用 ", " 正常 ", " 备注 ")
        ));

        assertThat(result.get(0).accountName()).isEqualTo("户名A");
        assertThat(result.get(0).bankName()).isEqualTo("银行A");
        assertThat(result.get(0).bankAccount()).isEqualTo("622200001");
        assertThat(result.get(0).remark()).isEqualTo("备注");
    }

    @Test
    void read_shouldParseJsonAccounts() throws Exception {
        CompanySettlementAccountCodec codec = codec();
        CompanySetting entity = new CompanySetting();
        entity.setId(1L);
        entity.setSettlementAccountsJson(OBJECT_MAPPER.writeValueAsString(List.of(
                new CompanySettlementAccountResponse(9L, "户名A", "银行A", "622200001", "通用", "正常", null)
        )));

        List<CompanySettlementAccountResponse> result = codec.read(entity);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(9L);
    }

    @Test
    void read_shouldFallbackToLegacyBankColumns() {
        CompanySettlementAccountCodec codec = codec();
        CompanySetting entity = new CompanySetting();
        entity.setId(1L);
        entity.setCompanyName("公司A");
        entity.setBankName("银行A");
        entity.setBankAccount("622200001");
        entity.setStatus("正常");
        entity.setRemark("备注");

        List<CompanySettlementAccountResponse> result = codec.read(entity);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(1L);
        assertThat(result.get(0).accountName()).isEqualTo("公司A");
        assertThat(result.get(0).usageType()).isEqualTo("通用");
    }

    @Test
    void read_shouldReturnEmptyWhenNoJsonAndLegacyColumnsBlank() {
        CompanySettlementAccountCodec codec = codec();
        CompanySetting entity = new CompanySetting();
        entity.setId(1L);

        assertThat(codec.read(entity)).isEmpty();
    }

    @Test
    void read_shouldThrowIllegalStateOnCorruptedJson() {
        CompanySettlementAccountCodec codec = codec();
        CompanySetting entity = new CompanySetting();
        entity.setId(1L);
        entity.setSettlementAccountsJson("not-json");

        assertThatThrownBy(() -> codec.read(entity))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("公司结算信息解析失败");
    }

    @Test
    void read_shouldFallbackWhenJsonIsEmptyArray() throws Exception {
        CompanySettlementAccountCodec codec = codec();
        CompanySetting entity = new CompanySetting();
        entity.setId(1L);
        entity.setCompanyName("公司A");
        entity.setBankName("银行A");
        entity.setBankAccount("622200001");
        entity.setSettlementAccountsJson("[]");

        assertThat(codec.read(entity)).hasSize(1);
    }

    @Test
    void write_shouldSerializeAccountsToJson() {
        CompanySettlementAccountCodec codec = codec();
        CompanySettlementAccountResponse account = new CompanySettlementAccountResponse(
                9L, "户名A", "银行A", "622200001", "通用", "正常", null);

        String json = codec.write(List.of(account));

        assertThat(json).contains("\"id\":9").contains("622200001");
    }

    private CompanySettlementAccountCodec codec() {
        return new CompanySettlementAccountCodec(OBJECT_MAPPER, idGenerator);
    }
}
