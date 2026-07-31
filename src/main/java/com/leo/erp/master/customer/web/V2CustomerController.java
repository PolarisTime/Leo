package com.leo.erp.master.customer.web;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.web.BindPageQuery;
import com.leo.erp.master.customer.service.CustomerService;
import com.leo.erp.master.customer.web.dto.CustomerOptionResponse;
import com.leo.erp.master.customer.web.dto.CustomerRequest;
import com.leo.erp.master.customer.web.dto.CustomerResponse;
import jakarta.validation.Valid;
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
@RequestMapping(ApiVersion.V2_PREFIX + "/customers")
public class V2CustomerController {

    private final CustomerService customerService;

    public V2CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @GetMapping("/options")
    public List<CustomerOptionResponse> options() {
        return customerService.listActiveOptions();
    }

    @GetMapping
    public PageResponse<CustomerResponse> page(@BindPageQuery(sortFieldKey = "customer") PageQuery query, @RequestParam(required = false) String keyword, @RequestParam(required = false) String status) {
        return PageResponse.from(customerService.page(query, keyword, status));
    }

    @GetMapping("/{id}")
    public CustomerResponse detail(@PathVariable Long id) {
        return customerService.detail(id);
    }

    @PostMapping
    @V2Created
    public ResponseEntity<CustomerResponse> create(@Valid @RequestBody CustomerRequest request) {
        return V2ResponseSupport.created("/customers", customerService.create(request));
    }

    @PutMapping("/{id}")
    public CustomerResponse update(@PathVariable Long id, @Valid @RequestBody CustomerRequest request) {
        return customerService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @V2NoContent
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        customerService.delete(id);
        return V2ResponseSupport.noContent();
    }
}
