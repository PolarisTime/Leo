package com.leo.erp.master.warehouse.service;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.config.CacheConfig;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.persistence.Specs;
import com.leo.erp.common.service.CrudOperationLogger;
import com.leo.erp.common.service.CrudStatusGuard;
import com.leo.erp.common.service.CrudVisibilityPolicy;
import com.leo.erp.common.support.MasterDataReferenceGuard;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.common.support.StatusTransition;
import com.leo.erp.master.code.service.MasterDataCodeIssuanceService;
import com.leo.erp.master.warehouse.domain.entity.Warehouse;
import com.leo.erp.master.warehouse.repository.WarehouseRepository;
import com.leo.erp.master.warehouse.mapper.WarehouseMapper;
import com.leo.erp.master.warehouse.web.dto.WarehouseOptionResponse;
import com.leo.erp.master.warehouse.web.dto.WarehouseRequest;
import com.leo.erp.master.warehouse.web.dto.WarehouseResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
public class WarehouseService {

    private static final String CODE_MODULE_KEY = "warehouse";
    private static final String WAREHOUSE_OPTIONS_CACHE_KEY = "leo:warehouse:all";
    private static final CrudStatusGuard<Warehouse> STATUS_GUARD = CrudStatusGuard.withoutStatus();
    private static final CrudVisibilityPolicy VISIBILITY_POLICY = new CrudVisibilityPolicy();
    private static final Set<StatusTransition> NO_STATUS_TRANSITIONS = Set.of();

    private final CrudOperationLogger operationLogger = CrudOperationLogger.forOwner(WarehouseService.class);
    private final SnowflakeIdGenerator snowflakeIdGenerator;
    private final WarehouseRepository warehouseRepository;
    private final WarehouseMapper warehouseMapper;
    private final WarehouseReferenceGuard warehouseReferenceGuard;
    private final MasterDataCodeIssuanceService codeIssuanceService;
    private final com.leo.erp.master.service.ReferenceSnapshotSyncService referenceSnapshotSyncService;

    @Autowired
    public WarehouseService(WarehouseRepository warehouseRepository,
                            SnowflakeIdGenerator snowflakeIdGenerator,
                            WarehouseMapper warehouseMapper,
                            MasterDataReferenceGuard referenceGuard,
                            MasterDataCodeIssuanceService codeIssuanceService,
                            com.leo.erp.master.service.ReferenceSnapshotSyncService referenceSnapshotSyncService) {
        this.snowflakeIdGenerator = snowflakeIdGenerator;
        this.warehouseRepository = warehouseRepository;
        this.warehouseMapper = warehouseMapper;
        this.warehouseReferenceGuard = referenceGuard == null ? null : new WarehouseReferenceGuard(referenceGuard);
        this.codeIssuanceService = codeIssuanceService;
        this.referenceSnapshotSyncService = referenceSnapshotSyncService;
    }

    @Transactional(readOnly = true)
    public WarehouseResponse detail(Long id) {
        return toResponse(requireActiveWarehouse(id));
    }

    @Transactional
    @CacheEvict(value = CacheConfig.CACHE_OPTIONS, key = "'" + WAREHOUSE_OPTIONS_CACHE_KEY + "'")
    public WarehouseResponse create(WarehouseRequest request) {
        codeIssuanceService.validate(CODE_MODULE_KEY, request.warehouseCode());
        Warehouse entity = new Warehouse();
        long entityId = snowflakeIdGenerator.nextId();
        entity.setId(entityId);
        apply(entity, request);
        Warehouse saved = saveCreatedWarehouse(entity);
        operationLogger.created(entity, entityId);
        return toResponse(saved);
    }

    @Transactional
    @CacheEvict(value = CacheConfig.CACHE_OPTIONS, key = "'" + WAREHOUSE_OPTIONS_CACHE_KEY + "'")
    public WarehouseResponse update(Long id, WarehouseRequest request) {
        Warehouse entity = requireActiveWarehouse(id);
        String currentName = entity.getWarehouseName();
        apply(entity, request);
        Warehouse saved = warehouseRepository.save(entity);
        WarehouseResponse response = toResponse(saved);
        operationLogger.updated(entity, id);
        if (!currentName.equals(request.warehouseName())) {
            referenceSnapshotSyncService.syncWarehouseName(id, request.warehouseName());
        }
        return response;
    }

    @Transactional
    @CacheEvict(value = CacheConfig.CACHE_OPTIONS, key = "'" + WAREHOUSE_OPTIONS_CACHE_KEY + "'")
    public WarehouseResponse updateStatus(Long id, String status) {
        Warehouse entity = requireActiveWarehouse(id);
        String currentStatus = STATUS_GUARD.resolveStatus(entity).orElse("");
        String nextStatus = STATUS_GUARD.normalizeRequiredStatus(status);
        if (currentStatus.equals(nextStatus)) {
            return toResponse(entity);
        }
        STATUS_GUARD.validateStatusTransition(NO_STATUS_TRANSITIONS, currentStatus, nextStatus);
        throw new BusinessException(ErrorCode.BUSINESS_ERROR, "当前模块不支持状态变更");
    }

    @Transactional
    @CacheEvict(value = CacheConfig.CACHE_OPTIONS, key = "'" + WAREHOUSE_OPTIONS_CACHE_KEY + "'")
    public void delete(Long id) {
        Warehouse entity = requireActiveWarehouse(id);
        if (warehouseReferenceGuard != null) {
            warehouseReferenceGuard.assertNoReferences(entity);
        }
        entity.setDeletedFlag(true);
        warehouseRepository.save(entity);
        operationLogger.deleted(entity, id);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = CacheConfig.CACHE_OPTIONS, key = "'" + WAREHOUSE_OPTIONS_CACHE_KEY + "'",
            unless = "#result == null || #result.isEmpty()")
    public List<WarehouseOptionResponse> listActiveOptions() {
        return warehouseRepository.findByDeletedFlagFalseAndStatusOrderByWarehouseNameAsc(StatusConstants.NORMAL).stream()
                .map(warehouse -> new WarehouseOptionResponse(
                        warehouse.getId(),
                        warehouse.getWarehouseCode(),
                        warehouse.getWarehouseName()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<WarehouseResponse> page(PageQuery query, String keyword, String warehouseType, String status) {
        Specification<Warehouse> spec = Specs.<Warehouse>notDeleted()
                .and(Specs.keywordLike(keyword, "warehouseCode", "warehouseName", "contactName"))
                .and(Specs.equalIfPresent("warehouseType", warehouseType))
                .and(Specs.equalIfPresent("status", StatusConstants.normalizeOptionalActiveStatus(status, "仓库状态")));
        return warehouseRepository
                .findAll(VISIBILITY_POLICY.applyDeletedVisibility(spec, false), query.toPageable("id"))
                .map(this::toResponse);
    }

    private Warehouse requireActiveWarehouse(Long id) {
        return warehouseRepository.findByIdAndDeletedFlagFalse(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "仓库不存在"));
    }

    private void apply(Warehouse entity, WarehouseRequest request) {
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

    private Warehouse saveCreatedWarehouse(Warehouse entity) {
        Warehouse saved = warehouseRepository.save(entity);
        codeIssuanceService.consume(CODE_MODULE_KEY, saved.getWarehouseCode());
        return saved;
    }

    private WarehouseResponse toResponse(Warehouse entity) {
        return warehouseMapper.toResponse(entity);
    }
}
