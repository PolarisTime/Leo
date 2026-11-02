package com.leo.erp.finance.receipt.service;

import com.leo.erp.common.support.ValidationMessages;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.BusinessDocumentValidator;
import com.leo.erp.finance.receipt.web.dto.ReceiptRequest;
import com.leo.erp.master.api.CustomerQuery;
import com.leo.erp.master.api.ProjectQuery;
import com.leo.erp.master.api.SupplierQuery;
import com.leo.erp.system.company.api.CompanySettingQuery;
import com.leo.erp.system.company.api.SettlementCompanySnapshot;
import org.springframework.stereotype.Service;

@Service
public class ReceiptPartyIdentityResolver {

    private final CustomerQuery customerQuery;
    private final ProjectQuery projectQuery;
    private final SupplierQuery supplierQuery;
    private final CompanySettingQuery companySettingQuery;

    public ReceiptPartyIdentityResolver(CustomerQuery customerQuery,
                                        ProjectQuery projectQuery,
                                        SupplierQuery supplierQuery,
                                        CompanySettingQuery companySettingQuery) {
        this.customerQuery = customerQuery;
        this.projectQuery = projectQuery;
        this.supplierQuery = supplierQuery;
        this.companySettingQuery = companySettingQuery;
    }

    PartySnapshot resolve(ReceiptRequest request) {
        if (request.customerId() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "客户ID不能为空");
        }
        CustomerQuery.CustomerSnapshot customer = customerQuery.findActiveById(request.customerId())
                .orElseThrow(() -> new BusinessException(ErrorCode.BUSINESS_ERROR, ValidationMessages.CUSTOMER_NOT_FOUND));
        BusinessDocumentValidator.requireSameText(
                request.customerName(),
                customer.name(),
                "客户名称与ID不一致"
        );
        BusinessDocumentValidator.requireSameOptionalCode(
                request.customerCode(),
                customer.code(),
                "客户编码与ID不一致"
        );
        ProjectQuery.ProjectSnapshot project = resolveOptionalProject(request);
        SettlementCompanySnapshot company = resolveCompany(
                request.settlementCompanyId(),
                request.settlementCompanyName(),
                project
        );
        return new PartySnapshot(
                customer.id(),
                BusinessDocumentValidator.trimToNull(customer.code()),
                BusinessDocumentValidator.trimToNull(customer.name()),
                project == null ? null : project.id(),
                project == null ? null : BusinessDocumentValidator.trimToNull(project.name()),
                company.id(),
                BusinessDocumentValidator.trimToNull(company.name())
        );
    }

    SupplierPartySnapshot resolveSupplier(ReceiptRequest request) {
        if (request.counterpartyId() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "供应商ID不能为空");
        }
        SupplierQuery.SupplierSnapshot supplier = supplierQuery.findActiveById(request.counterpartyId())
                .orElseThrow(() -> new BusinessException(ErrorCode.BUSINESS_ERROR, ValidationMessages.SUPPLIER_NOT_FOUND));
        BusinessDocumentValidator.requireSameText(
                request.counterpartyName(),
                supplier.name(),
                "供应商名称与ID不一致"
        );
        BusinessDocumentValidator.requireSameOptionalCode(
                request.counterpartyCode(),
                supplier.code(),
                "供应商编码与ID不一致"
        );
        SettlementCompanySnapshot company = resolveCompany(
                request.settlementCompanyId(),
                request.settlementCompanyName(),
                null
        );
        return new SupplierPartySnapshot(
                supplier.id(),
                BusinessDocumentValidator.trimToNull(supplier.code()),
                BusinessDocumentValidator.trimToNull(supplier.name()),
                company.id(),
                BusinessDocumentValidator.trimToNull(company.name())
        );
    }

    private ProjectQuery.ProjectSnapshot resolveOptionalProject(ReceiptRequest request) {
        if (request.projectId() == null) {
            if (BusinessDocumentValidator.trimToNull(request.projectName()) != null) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "未选择项目时不能填写项目名称");
            }
            return null;
        }
        ProjectQuery.ProjectSnapshot project = projectQuery.findActiveById(request.projectId())
                .orElseThrow(() -> new BusinessException(ErrorCode.BUSINESS_ERROR, ValidationMessages.PROJECT_NOT_FOUND));
        if (!java.util.Objects.equals(project.customerId(), request.customerId())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "项目不属于所选客户");
        }
        BusinessDocumentValidator.requireSameText(
                request.projectName(),
                project.name(),
                "项目名称与ID不一致"
        );
        return project;
    }

    /**
     * 解析结算主体快照。
     *
     * <p>走 {@code CompanySettingQuery} 端口而非直接读 system 模块的实体/仓储：
     * 本模块只需要「id + 名称」，直接依赖仓储会让 finance→system 的跨模块边界形同虚设。
     * 端口方法只排除软删除、不校验启用——收款单沿用单据上已有的结算主体，主体停用不应阻断保存。</p>
     */
    private SettlementCompanySnapshot resolveCompany(Long companyId,
                                                     String companyName,
                                                     ProjectQuery.ProjectSnapshot project) {
        if (project != null && project.settlementCompanyId() != null) {
            if (companyId != null && !project.settlementCompanyId().equals(companyId)) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "结算主体与项目不一致");
            }
            SettlementCompanySnapshot projectCompany = companySettingQuery
                    .findSettlementCompanySnapshot(project.settlementCompanyId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.BUSINESS_ERROR, "项目结算主体不存在"));
            if (BusinessDocumentValidator.trimToNull(companyName) != null) {
                BusinessDocumentValidator.requireSameText(
                        companyName,
                        projectCompany.name(),
                        "结算主体名称与项目不一致"
                );
            }
            return projectCompany;
        }
        if (companyId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "结算主体不能为空");
        }
        SettlementCompanySnapshot company = companySettingQuery
                .findSettlementCompanySnapshot(companyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BUSINESS_ERROR, ValidationMessages.SETTLEMENT_COMPANY_NOT_FOUND));
        BusinessDocumentValidator.requireSameText(
                companyName,
                company.name(),
                "结算主体名称与ID不一致"
        );
        return company;
    }

    record PartySnapshot(
            Long customerId,
            String customerCode,
            String customerName,
            Long projectId,
            String projectName,
            Long settlementCompanyId,
            String settlementCompanyName
    ) {
    }

    record SupplierPartySnapshot(
            Long supplierId,
            String supplierCode,
            String supplierName,
            Long settlementCompanyId,
            String settlementCompanyName
    ) {
    }
}
