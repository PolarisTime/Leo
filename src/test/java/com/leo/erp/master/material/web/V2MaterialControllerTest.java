package com.leo.erp.master.material.web;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.master.material.service.MaterialDocumentService;
import com.leo.erp.master.material.service.MaterialHistoryQueryService;
import com.leo.erp.master.material.service.MaterialService;
import com.leo.erp.master.material.web.dto.MaterialResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 商品资料接口契约：状态过滤参数透传 + {@code PATCH /materials/{id}/status} 启停端点。
 */
@ExtendWith(MockitoExtension.class)
class V2MaterialControllerTest {

    private static final String PATH = "/v2.0/materials";

    @Mock
    private MaterialService materialService;
    @Mock
    private MaterialDocumentService materialDocumentService;
    @Mock
    private MaterialHistoryQueryService materialHistoryQueryService;

    private V2MaterialController controller;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        controller = new V2MaterialController(
                materialService, materialDocumentService, materialHistoryQueryService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void page_forwardsStatusFilterForEnabledAndDisabled() {
        PageQuery query = new PageQuery(0, 20, null, null);
        when(materialService.page(eq(query), eq(null), eq(null), eq(null), eq(null),
                eq(StatusConstants.DISABLED)))
                .thenReturn(new PageImpl<>(List.of(response(5L, StatusConstants.DISABLED))));
        when(materialService.page(eq(query), eq("棉"), eq("面料"), eq("棉布"), eq("实体商品"),
                eq(StatusConstants.NORMAL)))
                .thenReturn(new PageImpl<>(List.of(response(6L, StatusConstants.NORMAL))));

        PageResponse<MaterialResponse> disabledPage =
                controller.page(query, null, null, null, null, StatusConstants.DISABLED);
        PageResponse<MaterialResponse> normalPage =
                controller.page(query, "棉", "面料", "棉布", "实体商品", StatusConstants.NORMAL);

        // 两个状态都能筛，且 status 原样透传给服务层（服务层负责归一化校验）
        assertThat(disabledPage.content()).extracting(MaterialResponse::status)
                .containsExactly(StatusConstants.DISABLED);
        assertThat(normalPage.content()).extracting(MaterialResponse::status)
                .containsExactly(StatusConstants.NORMAL);
        verify(materialService).page(query, null, null, null, null, StatusConstants.DISABLED);
        verify(materialService).page(query, "棉", "面料", "棉布", "实体商品", StatusConstants.NORMAL);
    }

    @Test
    void updateStatus_patch_returnsUpdatedResource() throws Exception {
        when(materialService.updateStatus(5L, StatusConstants.DISABLED))
                .thenReturn(response(5L, StatusConstants.DISABLED));

        mockMvc.perform(patch(PATH + "/5/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"禁用\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.status").value(StatusConstants.DISABLED));
        verify(materialService).updateStatus(5L, StatusConstants.DISABLED);
    }

    @Test
    void updateStatus_enableAgain_returnsNormal() throws Exception {
        when(materialService.updateStatus(5L, StatusConstants.NORMAL))
                .thenReturn(response(5L, StatusConstants.NORMAL));

        mockMvc.perform(patch(PATH + "/5/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"正常\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(StatusConstants.NORMAL));
    }

    @Test
    void updateStatus_blankStatus_returns400WithoutCallingService() throws Exception {
        mockMvc.perform(patch(PATH + "/5/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"   \"}"))
                .andExpect(status().isBadRequest());
        verify(materialService, never()).updateStatus(any(), any());
    }

    private MaterialResponse response(Long id, String status) {
        return new MaterialResponse(id, "M001", "中天", "HRB400E", "直条", "16", "9米", "吨", "件",
                new BigDecimal("1.998"), 100, new BigDecimal("4000.00"), null, "实体商品", status);
    }
}
