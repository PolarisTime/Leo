package com.leo.erp.master.material.web;

import com.leo.erp.master.material.service.MaterialBatchRollbackService;
import com.leo.erp.master.material.web.dto.MaterialBatchRollbackResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * V2ImportBatchController 测试：回滚是对导入批次执行的同步命令（200 + 回滚统计），不返回 201 也不发 Location。
 */
@ExtendWith(MockitoExtension.class)
class V2ImportBatchControllerTest {

    @Mock
    private MaterialBatchRollbackService materialBatchRollbackService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new V2ImportBatchController(materialBatchRollbackService))
                .build();
    }

    @Test
    void rollback_shouldReturnOk() throws Exception {
        when(materialBatchRollbackService.rollback("B1"))
                .thenReturn(new MaterialBatchRollbackResponse("B1", 3, 2, 1, 0));

        mockMvc.perform(post("/v2.0/import-batches/B1/rollbacks"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Location"))
                .andExpect(jsonPath("$.importBatchNo").value("B1"))
                .andExpect(jsonPath("$.totalRows").value(3))
                .andExpect(jsonPath("$.createdRolledBack").value(2))
                .andExpect(jsonPath("$.updatedRestored").value(1));
    }
}
