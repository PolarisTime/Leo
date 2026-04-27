package com.leo.erp.statement.freight.web;

import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.web.BindPageQuery;
import com.leo.erp.security.permission.RequiresPermission;
import com.leo.erp.statement.freight.mapper.FreightStatementWebMapper;
import com.leo.erp.statement.freight.service.FreightStatementService;
import com.leo.erp.statement.freight.service.FreightStatementView;
import com.leo.erp.statement.freight.web.dto.FreightStatementRequest;
import com.leo.erp.statement.freight.web.dto.FreightStatementResponse;
import com.leo.erp.system.operationlog.support.OperationLoggable;
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/freight-statements")
public class FreightStatementController {

    private final FreightStatementService freightStatementService;
    private final FreightStatementWebMapper freightStatementWebMapper;

    public FreightStatementController(FreightStatementService freightStatementService,
                                     FreightStatementWebMapper freightStatementWebMapper) {
        this.freightStatementService = freightStatementService;
        this.freightStatementWebMapper = freightStatementWebMapper;
    }

    @GetMapping
    @RequiresPermission(resource = "freight-statement", action = "read")
    public ApiResponse<PageResponse<FreightStatementResponse>> page(
            @BindPageQuery(sortFieldKey = "freight-statements") PageQuery query,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String carrierName,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String signStatus,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodStart,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodEnd
    ) {
        Page<FreightStatementView> result = freightStatementService.page(
                query,
                keyword,
                carrierName,
                status,
                signStatus,
                periodStart,
                periodEnd
        );
        return ApiResponse.success(PageResponse.from(toResponsePage(result)));
    }

    @GetMapping("/{id}")
    @RequiresPermission(resource = "freight-statement", action = "read")
    public ApiResponse<FreightStatementResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(freightStatementWebMapper.toResponse(freightStatementService.detail(id)));
    }

    @PostMapping
    @RequiresPermission(resource = "freight-statement", action = "create")
    @OperationLoggable(moduleName = "物流对账单", actionType = "新增", businessNoFields = {"statementNo"})
    public ApiResponse<FreightStatementResponse> create(@Valid @RequestBody FreightStatementRequest request) {
        return ApiResponse.success(
                "创建成功",
                freightStatementWebMapper.toResponse(freightStatementService.create(freightStatementWebMapper.toCommand(request)))
        );
    }

    @PutMapping("/{id}")
    @RequiresPermission(resource = "freight-statement", action = "update")
    @OperationLoggable(moduleName = "物流对账单", actionType = "编辑", businessNoFields = {"statementNo"})
    public ApiResponse<FreightStatementResponse> update(@PathVariable Long id, @Valid @RequestBody FreightStatementRequest request) {
        return ApiResponse.success(
                "更新成功",
                freightStatementWebMapper.toResponse(freightStatementService.update(id, freightStatementWebMapper.toCommand(request)))
        );
    }

    @DeleteMapping("/{id}")
    @RequiresPermission(resource = "freight-statement", action = "delete")
    @OperationLoggable(moduleName = "物流对账单", actionType = "删除")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        freightStatementService.delete(id);
        return ApiResponse.success("删除成功", null);
    }

    private Page<FreightStatementResponse> toResponsePage(Page<FreightStatementView> page) {
        return new PageImpl<>(
                page.getContent().stream().map(freightStatementWebMapper::toResponse).toList(),
                page.getPageable(),
                page.getTotalElements()
        );
    }
}
