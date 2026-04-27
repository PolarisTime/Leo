package com.leo.erp.master.warehouse.service;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.persistence.Specs;
import com.leo.erp.common.service.AbstractCrudService;
import com.leo.erp.common.support.WarehouseSelectionSupport;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.master.warehouse.domain.entity.Warehouse;
import com.leo.erp.master.warehouse.repository.WarehouseRepository;
import com.leo.erp.master.warehouse.mapper.WarehouseMapper;
import com.leo.erp.master.warehouse.web.dto.WarehouseRequest;
import com.leo.erp.master.warehouse.web.dto.WarehouseResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class WarehouseService extends AbstractCrudService<Warehouse, WarehouseRequest, WarehouseResponse> {

    private final WarehouseRepository warehouseRepository;
    private final WarehouseMapper warehouseMapper;
    private final WarehouseSelectionSupport warehouseSelectionSupport;

    public WarehouseService(WarehouseRepository warehouseRepository,
                            SnowflakeIdGenerator snowflakeIdGenerator,
                            WarehouseMapper warehouseMapper,
                            WarehouseSelectionSupport warehouseSelectionSupport) {
        super(snowflakeIdGenerator);
        this.warehouseRepository = warehouseRepository;
        this.warehouseMapper = warehouseMapper;
        this.warehouseSelectionSupport = warehouseSelectionSupport;
    }

    public Page<WarehouseResponse> page(PageQuery query, String keyword, String warehouseType, String status) {
        Specification<Warehouse> spec = Specs.<Warehouse>notDeleted()
                .and(Specs.keywordLike(keyword, "warehouseCode", "warehouseName", "contactName"))
                .and(Specs.equalIfPresent("warehouseType", warehouseType))
                .and(Specs.equalIfPresent("status", status));
        return page(query, spec, warehouseRepository);
    }

    @Override
    protected void validateCreate(WarehouseRequest request) {
        ensureWarehouseCodeUnique(request.warehouseCode());
    }

    @Override
    protected void validateUpdate(Warehouse entity, WarehouseRequest request) {
        if (!entity.getWarehouseCode().equals(request.warehouseCode())) {
            ensureWarehouseCodeUnique(request.warehouseCode());
        }
    }

    @Override
    protected Warehouse newEntity() {
        return new Warehouse();
    }

    @Override
    protected void assignId(Warehouse entity, Long id) {
        entity.setId(id);
    }

    @Override
    protected Optional<Warehouse> findActiveEntity(Long id) {
        return warehouseRepository.findByIdAndDeletedFlagFalse(id);
    }

    @Override
    protected String notFoundMessage() {
        return "仓库不存在";
    }

    @Override
    protected void apply(Warehouse entity, WarehouseRequest request) {
        entity.setWarehouseCode(request.warehouseCode());
        entity.setWarehouseName(request.warehouseName());
        entity.setWarehouseType(request.warehouseType());
        entity.setContactName(request.contactName());
        entity.setContactPhone(request.contactPhone());
        entity.setAddress(request.address());
        entity.setStatus(request.status());
        entity.setRemark(request.remark());
    }

    @Override
    protected Warehouse saveEntity(Warehouse entity) {
        Warehouse saved = warehouseRepository.save(entity);
        warehouseSelectionSupport.evictCache();
        return saved;
    }

    @Override
    protected WarehouseResponse toResponse(Warehouse entity) {
        return warehouseMapper.toResponse(entity);
    }

    private void ensureWarehouseCodeUnique(String warehouseCode) {
        if (warehouseRepository.existsByWarehouseCodeAndDeletedFlagFalse(warehouseCode)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "仓库编码已存在");
        }
    }
}
