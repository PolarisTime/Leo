package com.leo.erp.attachment.service;

import com.leo.erp.attachment.domain.entity.AttachmentBinding;
import com.leo.erp.attachment.repository.AttachmentBindingRepository;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.persistence.AuditableEntity;
import com.leo.erp.contract.purchase.domain.entity.PurchaseContract;
import com.leo.erp.contract.sales.domain.entity.SalesContract;
import com.leo.erp.finance.invoiceissue.domain.entity.InvoiceIssue;
import com.leo.erp.finance.invoicereceipt.domain.entity.InvoiceReceipt;
import com.leo.erp.finance.payment.domain.entity.Payment;
import com.leo.erp.finance.receipt.domain.entity.Receipt;
import com.leo.erp.logistics.bill.domain.entity.FreightBill;
import com.leo.erp.master.carrier.domain.entity.Carrier;
import com.leo.erp.master.customer.domain.entity.Customer;
import com.leo.erp.master.material.domain.entity.Material;
import com.leo.erp.master.supplier.domain.entity.Supplier;
import com.leo.erp.master.warehouse.domain.entity.Warehouse;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInbound;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrder;
import com.leo.erp.sales.order.domain.entity.SalesOrder;
import com.leo.erp.sales.outbound.domain.entity.SalesOutbound;
import com.leo.erp.security.permission.DataScopeContext;
import com.leo.erp.security.permission.PermissionService;
import com.leo.erp.security.permission.ResourcePermissionCatalog;
import com.leo.erp.security.support.SecurityPrincipal;
import com.leo.erp.statement.customer.domain.entity.CustomerStatement;
import com.leo.erp.statement.freight.domain.entity.FreightStatement;
import com.leo.erp.statement.supplier.domain.entity.SupplierStatement;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class AttachmentRecordAccessService {

    private static final Map<String, Class<? extends AuditableEntity>> BUSINESS_ENTITY_TYPES = Map.ofEntries(
            Map.entry("materials", Material.class),
            Map.entry("suppliers", Supplier.class),
            Map.entry("customers", Customer.class),
            Map.entry("carriers", Carrier.class),
            Map.entry("warehouses", Warehouse.class),
            Map.entry("purchase-orders", PurchaseOrder.class),
            Map.entry("purchase-inbounds", PurchaseInbound.class),
            Map.entry("sales-orders", SalesOrder.class),
            Map.entry("sales-outbounds", SalesOutbound.class),
            Map.entry("freight-bills", FreightBill.class),
            Map.entry("purchase-contracts", PurchaseContract.class),
            Map.entry("sales-contracts", SalesContract.class),
            Map.entry("supplier-statements", SupplierStatement.class),
            Map.entry("customer-statements", CustomerStatement.class),
            Map.entry("freight-statements", FreightStatement.class),
            Map.entry("receipts", Receipt.class),
            Map.entry("payments", Payment.class),
            Map.entry("invoice-receipts", InvoiceReceipt.class),
            Map.entry("invoice-issues", InvoiceIssue.class)
    );

    private final EntityManager entityManager;
    private final PermissionService permissionService;
    private final AttachmentBindingRepository attachmentBindingRepository;

    public AttachmentRecordAccessService(EntityManager entityManager,
                                         PermissionService permissionService,
                                         AttachmentBindingRepository attachmentBindingRepository) {
        this.entityManager = entityManager;
        this.permissionService = permissionService;
        this.attachmentBindingRepository = attachmentBindingRepository;
    }

    @Transactional(readOnly = true)
    public void assertRecordAccessible(SecurityPrincipal principal, String moduleKey, String actionCode, Long recordId) {
        String normalizedModuleKey = normalizeModuleKey(moduleKey);
        long normalizedRecordId = normalizeRecordId(recordId);
        if (!isBusinessModule(normalizedModuleKey)) {
            return;
        }
        AuditableEntity entity = loadBusinessEntity(normalizedModuleKey, normalizedRecordId);
        if (entity == null || Boolean.TRUE.equals(entity.getDeletedFlag())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "业务记录不存在");
        }
        assertCanAccess(principal, normalizedModuleKey, actionCode, entity);
    }

    @Transactional(readOnly = true)
    public void assertAttachmentAccessible(SecurityPrincipal principal, String moduleKey, String actionCode, Long attachmentId) {
        String normalizedModuleKey = normalizeModuleKey(moduleKey);
        long normalizedAttachmentId = normalizeRecordId(attachmentId);
        if (!isBusinessModule(normalizedModuleKey)) {
            return;
        }
        List<AttachmentBinding> bindings = attachmentBindingRepository
                .findByModuleKeyAndAttachmentIdAndDeletedFlagFalseOrderByRecordIdAscSortOrderAscIdAsc(
                        normalizedModuleKey,
                        normalizedAttachmentId
                );
        if (bindings.isEmpty()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "附件未绑定到当前业务记录");
        }
        boolean accessible = bindings.stream()
                .map(AttachmentBinding::getRecordId)
                .map(recordId -> loadBusinessEntity(normalizedModuleKey, recordId))
                .filter(entity -> entity != null && !Boolean.TRUE.equals(entity.getDeletedFlag()))
                .anyMatch(entity -> canAccess(principal, normalizedModuleKey, actionCode, entity));
        if (!accessible) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无数据权限");
        }
    }

    private void assertCanAccess(SecurityPrincipal principal, String moduleKey, String actionCode, AuditableEntity entity) {
        if (!canAccess(principal, moduleKey, actionCode, entity)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无数据权限");
        }
    }

    private boolean canAccess(SecurityPrincipal principal, String moduleKey, String actionCode, AuditableEntity entity) {
        DataScopeContext.Context previous = DataScopeContext.current();
        String resource = resolveResource(moduleKey);
        String action = ResourcePermissionCatalog.normalizeAction(actionCode);
        String dataScope = permissionService.getUserDataScope(principal.id(), resource, action);
        try {
            DataScopeContext.set(
                    principal.id(),
                    resource,
                    dataScope,
                    permissionService.getDataScopeOwnerUserIds(principal.id(), dataScope)
            );
            return DataScopeContext.canAccess(entity);
        } finally {
            restore(previous);
        }
    }

    private AuditableEntity loadBusinessEntity(String moduleKey, Long recordId) {
        Class<? extends AuditableEntity> entityType = BUSINESS_ENTITY_TYPES.get(moduleKey);
        return entityType == null ? null : entityManager.find(entityType, recordId);
    }

    private boolean isBusinessModule(String moduleKey) {
        return BUSINESS_ENTITY_TYPES.containsKey(moduleKey);
    }

    private String resolveResource(String moduleKey) {
        return ResourcePermissionCatalog.resolveResourceByMenuCode(moduleKey)
                .orElseGet(() -> ResourcePermissionCatalog.normalizeResource(moduleKey));
    }

    private String normalizeModuleKey(String moduleKey) {
        if (moduleKey == null || moduleKey.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "缺少模块标识");
        }
        return moduleKey.trim();
    }

    private long normalizeRecordId(Long recordId) {
        if (recordId == null || recordId <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "缺少业务记录标识");
        }
        return recordId;
    }

    private void restore(DataScopeContext.Context previous) {
        if (previous == null) {
            DataScopeContext.clear();
            return;
        }
        DataScopeContext.set(previous.userId(), previous.resource(), previous.scope(), previous.ownerUserIds());
    }
}
