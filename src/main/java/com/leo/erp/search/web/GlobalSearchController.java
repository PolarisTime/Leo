package com.leo.erp.search.web;

import org.springframework.validation.annotation.Validated;
import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.search.web.GlobalSearchResponse;
import com.leo.erp.search.service.GlobalSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "全局搜索")
@RestController
@Validated
@RequestMapping("/global-search")
public class GlobalSearchController {

    private final GlobalSearchService globalSearchService;

    public GlobalSearchController(GlobalSearchService globalSearchService) {
        this.globalSearchService = globalSearchService;
    }

    @Operation(summary = "聚合搜索业务单据")
    @GetMapping
    public ApiResponse<List<GlobalSearchResponse>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) List<String> moduleKeys
    ) {
        return ApiResponse.success(globalSearchService.search(keyword != null ? keyword : "", limit, moduleKeys));
    }
}
