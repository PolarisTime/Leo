package com.leo.erp.master.carrier.web;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.web.BindPageQuery;
import com.leo.erp.master.carrier.service.CarrierService;
import com.leo.erp.master.carrier.web.dto.CarrierOptionResponse;
import com.leo.erp.master.carrier.web.dto.CarrierRequest;
import com.leo.erp.master.carrier.web.dto.CarrierResponse;
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
@RequestMapping(ApiVersion.V2_PREFIX + "/carriers")
public class V2CarrierController {

    private final CarrierService carrierService;

    public V2CarrierController(CarrierService carrierService) {
        this.carrierService = carrierService;
    }

    @GetMapping("/options")
    public List<CarrierOptionResponse> options() {
        return carrierService.listActiveOptions();
    }

    @GetMapping
    public PageResponse<CarrierResponse> page(@BindPageQuery(sortFieldKey = "carrier") PageQuery query, @RequestParam(required = false) String keyword, @RequestParam(required = false) String status) {
        return PageResponse.from(carrierService.page(query, keyword, status));
    }

    @GetMapping("/{id}")
    public CarrierResponse detail(@PathVariable Long id) {
        return carrierService.detail(id);
    }

    @PostMapping
    @V2Created
    public ResponseEntity<CarrierResponse> create(@Valid @RequestBody CarrierRequest request) {
        return V2ResponseSupport.created("/carriers", carrierService.create(request));
    }

    @PutMapping("/{id}")
    public CarrierResponse update(@PathVariable Long id, @Valid @RequestBody CarrierRequest request) {
        return carrierService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @V2NoContent
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        carrierService.delete(id);
        return V2ResponseSupport.noContent();
    }
}
