package com.leo.erp.master.warehouse.service;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.persistence.Specs;
import com.leo.erp.common.service.AbstractCrudService;
import com.leo.erp.common.support.MasterDataReferenceGuard;
import com.leo.erp.common.support.WarehouseSelectionSupport;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.master.code.service.MasterDataCodeIssuanceService;
import com.leo.erp.master.warehouse.domain.entity.Warehouse;
import com.leo.erp.master.warehouse.repository.WarehouseRepository;
import com.leo.erp.master.warehouse.mapper.WarehouseMapper;
import com.leo.erp.master.warehouse.web.dto.WarehouseOptionResponse;
import com.leo.erp.master.warehouse.web.dto.WarehouseRequest;
import com.leo.erp.master.warehouse.web.dto.WarehouseResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class WarehouseService extends AbstractCrudService<Warehouse, WarehouseRequest, WarehouseResponse> {

    private static final String CODE_MODULE_KEY = "warehouse";

    private final WarehouseRepository warehouseRepository;
    private final WarehouseMapper warehouseMapper;
    private final WarehouseSelectionSupport warehouseSelectionSupport;
    private final WarehouseReferenceGuard warehouseReferenceGuard;
    private final MasterDataCodeIssuanceService codeIssuanceService;

    @Autowired
    public WarehouseService(WarehouseRepository warehouseRepository,
                            SnowflakeIdGenerator snowflakeIdGenerator,
                            WarehouseMapper warehouseMapper,
                            WarehouseSelectionSupport warehouseSelectionSupport,
                            MasterDataReferenceGuard referenceGuard,
                            MasterDataCodeIssuanceService codeIssuanceService) {
        super(snowflakeIdGenerator);
        this.warehouseRepository = warehouseRepository;
        this.warehouseMapper = warehouseMapper;
        this.warehouseSelectionSupport = warehouseSelectionSupport;
        this.warehouseReferenceGuard = referenceGuard == null ? null : new WarehouseReferenceGuard(referenceGuard);
        this.codeIssuanceService = codeIssuanceService;
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public java.util.List<WarehouseOptionResponse> listActiveOptions() {
        return warehouseRepository.findByDeletedFlagFalseAndStatusOrderByWarehouseNameAsc(StatusConstants.NORMAL).stream()
                .map(warehouse -> new WarehouseOptionResponse(
                        warehouse.getId(),
                        warehouse.getWarehouseCode(),
                        warehouse.getWarehouseName()
                ))
                .toList();
    }

    public Page<WarehouseResponse> page(PageQuery query, String keyword, String warehouseType, String status) {
        Specification<Warehouse> spec = Specs.<Warehouse>notDeleted()
                .and(Specs.keywordLike(keyword, "warehouseCode", "warehouseName", "contactName"))
                .and(Specs.equalIfPresent("warehouseType", warehouseType))
                .and(Specs.equalIfPresent("status", StatusConstants.normalizeOptionalActiveStatus(status, "仓库状态")));
        return page(query, spec, warehouseRepository);
    }

    @Override
    protected void beforeDelete(Warehouse entity) {
        if (warehouseReferenceGuard == null) {
            return;
        }
        warehouseReferenceGuard.assertNoReferences(entity);
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
    protected void validateCreate(WarehouseRequest request) {
        codeIssuanceService.validate(CODE_MODULE_KEY, request.warehouseCode());
    }

    @Override
    protected void apply(Warehouse entity, WarehouseRequest request) {
        entity.setWarehouseCode(codeIssuanceService.resolve(
                CODE_MODULE_KEY,
                entity.getWarehouseCode(),
                request.warehouseCode()
        ));
        entity.setWarehouseName(request.warehouseName());
        entity.setWarehouseType(request.warehouseType());
        entity.setContactName(request.contactName());
        entity.setContactPhone(request.contactPhone());
        entity.setAddress(request.address());
        entity.setStatus(StatusConstants.normalizeActiveStatus(request.status(), "仓库状态"));
        entity.setRemark(request.remark());
    }

    @Override
    protected Warehouse saveEntity(Warehouse entity) {
        Warehouse saved = warehouseRepository.save(entity);
        warehouseSelectionSupport.evictCache();
        return saved;
    }

    @Override
    protected Warehouse saveCreatedEntity(Warehouse entity, WarehouseRequest request) {
        Warehouse saved = saveEntity(entity);
        codeIssuanceService.consume(CODE_MODULE_KEY, saved.getWarehouseCode());
        return saved;
    }

    @Override
    protected WarehouseResponse toResponse(Warehouse entity) {
        return warehouseMapper.toResponse(entity);
    }

}
