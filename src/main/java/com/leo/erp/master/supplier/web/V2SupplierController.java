package com.leo.erp.master.supplier.web;

import org.springframework.validation.annotation.Validated;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.web.BindPageQuery;
import com.leo.erp.master.supplier.service.SupplierService;
import com.leo.erp.master.supplier.web.dto.SupplierRequest;
import com.leo.erp.master.supplier.web.dto.SupplierOptionResponse;
import com.leo.erp.master.supplier.web.dto.SupplierResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.leo.erp.common.api.ApiVersion;
import com.leo.erp.common.api.V2ResponseSupport;
import com.leo.erp.common.api.V2Created;
import com.leo.erp.common.api.V2NoContent;
import org.springframework.http.ResponseEntity;

@RestController
@Validated
@RequestMapping(ApiVersion.V2_PREFIX + "/suppliers")
public class V2SupplierController {

    private final SupplierService supplierService;

    public V2SupplierController(SupplierService supplierService) {
        this.supplierService = supplierService;
    }

    @GetMapping("/options")
    public java.util.List<SupplierOptionResponse> options() {
        return supplierService.listActiveOptions();
    }

    @GetMapping
    public PageResponse<SupplierResponse> page(@BindPageQuery(sortFieldKey = "supplier") PageQuery query, @RequestParam(required = false) String keyword, @RequestParam(required = false) String status) {
        return PageResponse.from(supplierService.page(query, keyword, status));
    }

    @GetMapping("/{id}")
    public SupplierResponse detail(@PathVariable Long id) {
        return supplierService.detail(id);
    }

    @PostMapping
    @V2Created
    public ResponseEntity<SupplierResponse> create(@Valid @RequestBody SupplierRequest request) {
        return V2ResponseSupport.created("/suppliers", supplierService.create(request));
    }

    @PutMapping("/{id}")
    public SupplierResponse update(@PathVariable Long id, @Valid @RequestBody SupplierRequest request) {
        return supplierService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @V2NoContent
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        supplierService.delete(id);
        return V2ResponseSupport.noContent();
    }
}
