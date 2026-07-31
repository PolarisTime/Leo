package com.leo.erp.search.web;

import org.springframework.validation.annotation.Validated;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.leo.erp.search.service.GlobalSearchService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.leo.erp.common.api.ApiVersion;

@Tag(name = "全局搜索")
@RestController
@Validated
@RequestMapping(ApiVersion.V2_PREFIX + "/global-search")
public class V2GlobalSearchController {

    private final GlobalSearchService globalSearchService;

    public V2GlobalSearchController(GlobalSearchService globalSearchService) {
        this.globalSearchService = globalSearchService;
    }

    @Operation(summary = "聚合搜索业务单据")
    @GetMapping
    public List<GlobalSearchResponse> search(@RequestParam(required = false) String keyword, @RequestParam(defaultValue = "20") int limit, @RequestParam(required = false) List<String> moduleKeys) {
        return globalSearchService.search(keyword != null ? keyword : "", limit, moduleKeys);
    }
}
