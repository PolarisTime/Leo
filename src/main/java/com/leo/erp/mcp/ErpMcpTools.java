package com.leo.erp.mcp;

import com.leo.erp.common.api.PageResponse;
import com.leo.erp.search.web.GlobalSearchResponse;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(prefix = "spring.ai.mcp.server", name = "enabled", havingValue = "true")
public class ErpMcpTools {

    private final ErpMcpQueryFacade queryFacade;

    public ErpMcpTools(ErpMcpQueryFacade queryFacade) {
        this.queryFacade = queryFacade;
    }

    @Tool(
            name = "erp_global_search",
            description = "ERP 全局搜索，只返回当前用户有查看权限和数据权限的业务记录。"
    )
    public List<GlobalSearchResponse> globalSearch(
            @ToolParam(description = "搜索关键词，可传单号、客户、供应商、项目等文本。") String keyword,
            @ToolParam(required = false, description = "模块白名单，如 purchase-order、sales-order。为空时使用默认业务模块。")
            List<String> moduleKeys,
            @ToolParam(required = false, description = "最大返回数量，默认20，最大50。") Integer limit
    ) {
        return queryFacade.globalSearch(keyword, moduleKeys, limit);
    }

    @Tool(
            name = "erp_query_records",
            description = "分页查询 ERP 只读模块，支持基础资料、采购/销售单据和库存报表白名单。"
    )
    public PageResponse<?> queryRecords(
            @ToolParam(description = "模块标识，如 material、purchase-order、sales-order、inventory-report。") String moduleKey,
            @ToolParam(required = false, description = "关键词过滤。") String keyword,
            @ToolParam(required = false, description = "状态过滤。") String status,
            @ToolParam(required = false, description = "页码，从0开始，默认0。") Integer page,
            @ToolParam(required = false, description = "每页数量，默认20，最大50。") Integer size
    ) {
        return queryFacade.queryRecords(moduleKey, keyword, status, page, size);
    }

    @Tool(
            name = "erp_get_record",
            description = "读取单条 ERP 记录详情，只支持显式白名单模块。"
    )
    public Object getRecord(
            @ToolParam(description = "模块标识，如 material、purchase-order、sales-order。") String moduleKey,
            @ToolParam(description = "记录雪花 ID。") Long recordId
    ) {
        return queryFacade.getRecord(moduleKey, recordId);
    }

    @Tool(
            name = "erp_list_options",
            description = "读取开发调试常用下拉选项，只支持显式白名单选项。"
    )
    public Object listOptions(
            @ToolParam(description = "选项类型：material-category、material-grade、supplier、customer、warehouse、carrier。")
            String optionType,
            @ToolParam(required = false, description = "关键词过滤；高基数选项建议必传。") String keyword,
            @ToolParam(required = false, description = "最大返回数量，默认20，最大50。") Integer limit
    ) {
        return queryFacade.listOptions(optionType, keyword, limit);
    }

    @Tool(
            name = "erp_print_payload_preview",
            description = "预览打印模板渲染前后的 JSON 数据，不生成 PDF、不下载文件、不执行真实打印。"
    )
    public Map<String, Object> printPayloadPreview(
            @ToolParam(description = "业务模块标识，如 sales-order。") String moduleKey,
            @ToolParam(description = "业务记录雪花 ID。") Long recordId,
            @ToolParam(description = "打印模板 ID。") String templateId
    ) {
        return queryFacade.printPayloadPreview(moduleKey, recordId, templateId);
    }
}
