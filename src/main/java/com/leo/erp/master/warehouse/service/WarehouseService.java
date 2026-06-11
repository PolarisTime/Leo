package com.leo.erp.master.warehouse.service;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.persistence.Specs;
import com.leo.erp.common.service.AbstractCrudService;
import com.leo.erp.common.support.MasterDataReferenceGuard;
import com.leo.erp.common.support.MasterDataReferenceGuard.ReferenceCheck;
import com.leo.erp.common.support.WarehouseSelectionSupport;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.master.warehouse.domain.entity.Warehouse;
import com.leo.erp.master.warehouse.repository.WarehouseRepository;
import com.leo.erp.master.warehouse.mapper.WarehouseMapper;
import com.leo.erp.master.warehouse.web.dto.WarehouseRequest;
import com.leo.erp.master.warehouse.web.dto.WarehouseResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class WarehouseService extends AbstractCrudService<Warehouse, WarehouseRequest, WarehouseResponse> {

    private final WarehouseRepository warehouseRepository;
    private final WarehouseMapper warehouseMapper;
    private final WarehouseSelectionSupport warehouseSelectionSupport;
    private final MasterDataReferenceGuard referenceGuard;

    @Autowired
    public WarehouseService(WarehouseRepository warehouseRepository,
                            SnowflakeIdGenerator snowflakeIdGenerator,
                            WarehouseMapper warehouseMapper,
                            WarehouseSelectionSupport warehouseSelectionSupport,
                            MasterDataReferenceGuard referenceGuard) {
        super(snowflakeIdGenerator);
        this.warehouseRepository = warehouseRepository;
        this.warehouseMapper = warehouseMapper;
        this.warehouseSelectionSupport = warehouseSelectionSupport;
        this.referenceGuard = referenceGuard;
    }

    public WarehouseService(WarehouseRepository warehouseRepository,
                            SnowflakeIdGenerator snowflakeIdGenerator,
                            WarehouseMapper warehouseMapper,
                            WarehouseSelectionSupport warehouseSelectionSupport) {
        this(warehouseRepository, snowflakeIdGenerator, warehouseMapper, warehouseSelectionSupport, null);
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public java.util.List<com.leo.erp.common.web.OptionResponse> listActiveOptions() {
        return warehouseSelectionSupport.listActiveOptions();
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
    protected void beforeDelete(Warehouse entity) {
        if (referenceGuard == null) {
            return;
        }
        referenceGuard.assertNoReferences("该仓库", warehouseReferences(entity));
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

    private List<ReferenceCheck> warehouseReferences(Warehouse entity) {
        String warehouseName = entity.getWarehouseName();
        return List.of(
                ReferenceCheck.active("po_purchase_inbound", "warehouse_name", warehouseName),
                ReferenceCheck.when(
                        "po_purchase_inbound_item",
                        "warehouse_name",
                        warehouseName,
                        "EXISTS (SELECT 1 FROM po_purchase_inbound parent "
                                + "WHERE parent.id = po_purchase_inbound_item.inbound_id "
                                + "AND parent.deleted_flag = false)"
                ),
                ReferenceCheck.when(
                        "po_purchase_order_item",
                        "warehouse_name",
                        warehouseName,
                        "EXISTS (SELECT 1 FROM po_purchase_order parent "
                                + "WHERE parent.id = po_purchase_order_item.order_id "
                                + "AND parent.deleted_flag = false)"
                ),
                ReferenceCheck.when(
                        "so_sales_order_item",
                        "warehouse_name",
                        warehouseName,
                        "EXISTS (SELECT 1 FROM so_sales_order parent "
                                + "WHERE parent.id = so_sales_order_item.order_id "
                                + "AND parent.deleted_flag = false)"
                ),
                ReferenceCheck.active("so_sales_outbound", "warehouse_name", warehouseName),
                ReferenceCheck.when(
                        "so_sales_outbound_item",
                        "warehouse_name",
                        warehouseName,
                        "EXISTS (SELECT 1 FROM so_sales_outbound parent "
                                + "WHERE parent.id = so_sales_outbound_item.outbound_id "
                                + "AND parent.deleted_flag = false)"
                ),
                ReferenceCheck.when(
                        "lg_freight_bill_item",
                        "warehouse_name",
                        warehouseName,
                        "EXISTS (SELECT 1 FROM lg_freight_bill parent "
                                + "WHERE parent.id = lg_freight_bill_item.bill_id "
                                + "AND parent.deleted_flag = false)"
                ),
                ReferenceCheck.when(
                        "st_freight_statement_item",
                        "warehouse_name",
                        warehouseName,
                        "EXISTS (SELECT 1 FROM st_freight_statement parent "
                                + "WHERE parent.id = st_freight_statement_item.statement_id "
                                + "AND parent.deleted_flag = false)"
                ),
                ReferenceCheck.when(
                        "fm_invoice_receipt_item",
                        "warehouse_name",
                        warehouseName,
                        "EXISTS (SELECT 1 FROM fm_invoice_receipt parent "
                                + "WHERE parent.id = fm_invoice_receipt_item.receipt_id "
                                + "AND parent.deleted_flag = false)"
                ),
                ReferenceCheck.when(
                        "fm_invoice_issue_item",
                        "warehouse_name",
                        warehouseName,
                        "EXISTS (SELECT 1 FROM fm_invoice_issue parent "
                                + "WHERE parent.id = fm_invoice_issue_item.issue_id "
                                + "AND parent.deleted_flag = false)"
                )
        );
    }
}
