package com.leo.erp.master.code.web;

import com.leo.erp.common.api.ApiProblemFactory;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.exception.GlobalExceptionHandler;
import com.leo.erp.master.code.service.MasterDataCodeIssuanceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * V2MasterDataCodeIssuanceController RESTful 契约测试：201 Location 必须可解引用。
 */
@ExtendWith(MockitoExtension.class)
class V2MasterDataCodeIssuanceControllerTest {

    private static final String MODULE_KEY = "material";
    private static final String CODE = "1234567890123456789";

    @Mock
    private MasterDataCodeIssuanceService codeIssuanceService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new V2MasterDataCodeIssuanceController(codeIssuanceService))
                .setControllerAdvice(new GlobalExceptionHandler(new ApiProblemFactory("Asia/Shanghai")))
                .build();
    }

    @Test
    void issue_shouldReturnCreatedWithDereferenceableLocation() throws Exception {
        when(codeIssuanceService.issue(MODULE_KEY)).thenReturn(CODE);
        when(codeIssuanceService.get(MODULE_KEY, CODE)).thenReturn(CODE);

        String location = mockMvc.perform(post("/v2.0/master-data/code-issuances/{moduleKey}", MODULE_KEY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location",
                        "http://localhost/v2.0/master-data/code-issuances/material/" + CODE))
                .andExpect(jsonPath("$.code").value(CODE))
                .andReturn()
                .getResponse()
                .getHeader("Location");

        assertThat(location).isNotBlank();
        mockMvc.perform(get(URI.create(location).getPath()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(CODE));
    }

    @Test
    void detail_shouldReturnNotFoundWhenIssuanceExpired() throws Exception {
        when(codeIssuanceService.get(MODULE_KEY, CODE))
                .thenThrow(new BusinessException(ErrorCode.NOT_FOUND, "编码签发记录不存在或已失效"));

        mockMvc.perform(get("/v2.0/master-data/code-issuances/{moduleKey}/{code}", MODULE_KEY, CODE))
                .andExpect(status().isNotFound());
    }

    @Test
    void detail_shouldValidateModuleKeyAndCode() throws Exception {
        when(codeIssuanceService.get(anyString(), anyString()))
                .thenThrow(new BusinessException(ErrorCode.VALIDATION_ERROR, "编码必须使用系统生成的雪花ID"));

        mockMvc.perform(get("/v2.0/master-data/code-issuances/{moduleKey}/{code}", MODULE_KEY, "not-a-code"))
                .andExpect(status().isBadRequest());
    }
}
