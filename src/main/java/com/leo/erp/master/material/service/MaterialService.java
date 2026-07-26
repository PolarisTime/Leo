package com.leo.erp.master.material.service;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.service.AbstractCrudService;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.TradeItemCalculator;
import com.leo.erp.common.support.TradeItemMaterialSupport;
import com.leo.erp.master.code.service.MasterDataCodeIssuanceService;
import com.leo.erp.master.material.domain.entity.Material;
import com.leo.erp.master.material.mapper.MaterialMapper;
import com.leo.erp.master.material.repository.MaterialRepository;
import com.leo.erp.master.material.web.dto.MaterialRequest;
import com.leo.erp.master.material.web.dto.MaterialResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
public class MaterialService extends AbstractCrudService<Material, MaterialRequest, MaterialResponse> {

    private static final String CODE_MODULE_KEY = "material";

    private final MaterialRepository materialRepository;
    private final MaterialMapper materialMapper;
    private final TradeItemMaterialSupport tradeItemMaterialSupport;
    private final MaterialReferenceGuard materialReferenceGuard;
    private final MasterDataCodeIssuanceService codeIssuanceService;
    private final MaterialIdentityService identityService;

    public MaterialService(MaterialRepository materialRepository,
                           SnowflakeIdGenerator snowflakeIdGenerator,
                           MaterialMapper materialMapper,
                           TradeItemMaterialSupport tradeItemMaterialSupport,
                           MaterialReferenceGuard materialReferenceGuard,
                           MasterDataCodeIssuanceService codeIssuanceService,
                           MaterialIdentityService identityService) {
        super(snowflakeIdGenerator);
        this.materialRepository = materialRepository;
        this.materialMapper = materialMapper;
        this.tradeItemMaterialSupport = tradeItemMaterialSupport;
        this.materialReferenceGuard = materialReferenceGuard;
        this.codeIssuanceService = codeIssuanceService;
        this.identityService = identityService;
    }

    public Page<MaterialResponse> page(PageQuery query, String keyword, String category, String material) {
        Pageable pageable = query.sortBy() != null
                ? query.toPageable("id")
                : PageRequest.of(query.page(), query.size(), MaterialSearchPolicy.DEFAULT_SORT);
        return materialRepository.findAll(MaterialSearchPolicy.page(keyword, category, material), pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public List<MaterialResponse> search(String keyword, int maxSize) {
        return materialRepository.findAll(
                        MaterialSearchPolicy.search(keyword),
                        PageRequest.of(0, maxSize, MaterialSearchPolicy.DEFAULT_SORT)
                )
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<String> materialGrades() {
        return materialRepository.findDistinctMaterials();
    }

    @Override
    protected void validateCreate(MaterialRequest request) {
        codeIssuanceService.validate(CODE_MODULE_KEY, request.materialCode());
        identityService.ensureUnique(null, identity(request));
    }

    @Override
    protected void validateUpdate(Material entity, MaterialRequest request) {
        identityService.ensureUnique(entity.getId(), identity(request));
    }

    @Override
    protected void beforeDelete(Material entity) {
        materialReferenceGuard.assertNoReferences(entity);
    }

    @Override
    protected Material newEntity() {
        return new Material();
    }

    @Override
    protected void assignId(Material entity, Long id) {
        entity.setId(id);
    }

    @Override
    protected Optional<Material> findActiveEntity(Long id) {
        return materialRepository.findByIdAndDeletedFlagFalse(id);
    }

    @Override
    protected String notFoundMessage() {
        return "商品不存在";
    }

    @Override
    protected void apply(Material entity, MaterialRequest request) {
        entity.setMaterialCode(codeIssuanceService.resolve(
                CODE_MODULE_KEY,
                entity.getMaterialCode(),
                request.materialCode()
        ));
        entity.setBrand(request.brand());
        entity.setMaterial(request.material());
        entity.setCategory(request.category());
        entity.setSpec(request.spec());
        entity.setLength(request.length());
        entity.setUnit(request.unit());
        entity.setQuantityUnit(TradeItemCalculator.normalizeQuantityUnit(request.quantityUnit()));
        entity.setPieceWeightTon(request.pieceWeightTon());
        entity.setPiecesPerBundle(request.piecesPerBundle() == null ? 0 : request.piecesPerBundle());
        entity.setUnitPrice(request.unitPrice() == null ? BigDecimal.ZERO : request.unitPrice());
        entity.setRemark(request.remark());
    }

    @Override
    protected Material saveEntity(Material entity) {
        try {
            Material saved = materialRepository.save(entity);
            tradeItemMaterialSupport.evictCache();
            return saved;
        } catch (DataIntegrityViolationException exception) {
            throw identityService.mapViolation(exception, ErrorCode.BUSINESS_ERROR, null);
        }
    }

    @Override
    protected Material saveCreatedEntity(Material entity, MaterialRequest request) {
        Material saved = saveEntity(entity);
        codeIssuanceService.consume(CODE_MODULE_KEY, saved.getMaterialCode());
        return saved;
    }

    @Override
    protected MaterialResponse toResponse(Material entity) {
        return materialMapper.toResponse(entity);
    }

    private MaterialIdentityService.Identity identity(MaterialRequest request) {
        return identityService.identity(request.brand(), request.material(), request.spec(), request.length());
    }
}
