package com.leo.erp.master.material.web;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.web.BindPageQuery;
import com.leo.erp.master.material.service.MaterialCategoryService;
import com.leo.erp.master.material.web.dto.MaterialCategoryOptionResponse;
import com.leo.erp.master.material.web.dto.MaterialCategoryRequest;
import com.leo.erp.master.material.web.dto.MaterialCategoryResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import com.leo.erp.common.api.ApiVersion;
import com.leo.erp.common.api.V2ResponseSupport;
import com.leo.erp.common.api.V2Created;
import com.leo.erp.common.api.V2NoContent;
import org.springframework.http.ResponseEntity;

@RestController
@Validated
@RequestMapping(ApiVersion.V2_PREFIX + "/material-categories")
public class V2MaterialCategoryController {

    private final MaterialCategoryService service;

    public V2MaterialCategoryController(MaterialCategoryService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<MaterialCategoryResponse> page(@BindPageQuery(sortFieldKey = "material-category") PageQuery query, @RequestParam(required = false) String keyword, @RequestParam(required = false) String status) {
        return PageResponse.from(service.page(query, keyword, status));
    }

    @GetMapping("/{id}")
    public MaterialCategoryResponse detail(@PathVariable @Positive Long id) {
        return service.detail(id);
    }

    @PostMapping
    @V2Created
    public ResponseEntity<MaterialCategoryResponse> create(@Valid @RequestBody MaterialCategoryRequest request) {
        return V2ResponseSupport.created("/material-categories", service.create(request));
    }

    @PutMapping("/{id}")
    public MaterialCategoryResponse update(@PathVariable @Positive Long id, @Valid @RequestBody MaterialCategoryRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @V2NoContent
    public ResponseEntity<Void> delete(@PathVariable @Positive Long id) {
        service.delete(id);
        return V2ResponseSupport.noContent();
    }

    @GetMapping("/options")
    public List<MaterialCategoryOptionResponse> options() {
        return service.options();
    }
}
