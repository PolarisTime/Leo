package com.leo.erp.finance.ledgeradjustment.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.finance.ledgeradjustment.domain.entity.LedgerAdjustment;
import com.leo.erp.finance.ledgeradjustment.web.dto.LedgerAdjustmentRequest;
import com.leo.erp.master.api.CarrierQuery;
import com.leo.erp.master.api.CustomerQuery;
import com.leo.erp.master.api.ProjectQuery;
import com.leo.erp.master.api.SupplierQuery;
import com.leo.erp.system.company.domain.entity.CompanySetting;
import com.leo.erp.system.company.service.CompanySettingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LedgerAdjustmentApplyServiceTest {

    private static final LocalDate ADJUSTMENT_DATE = LocalDate.of(2026, 8, 25);

    @Mock
    private CustomerQuery customerQuery;

    @Mock
    private SupplierQuery supplierQuery;

    @Mock
    private CarrierQuery carrierQuery;

    @Mock
    private ProjectQuery projectQuery;

    @Mock
    private CompanySettingService companySettingService;

    @Test
    void apply_shouldFollowProjectSettlementCompany() {
        LedgerAdjustmentApplyService service = service();
        when(customerQuery.findActiveById(10L)).thenReturn(Optional.of(
                new CustomerQuery.CustomerSnapshot(10L, "CUST001", "客户A", null, 30L, "客户主体")
        ));
        when(projectQuery.findActiveById(20L)).thenReturn(Optional.of(
                new ProjectQuery.ProjectSnapshot(20L, "项目A", "项A", 10L, "CUST001", 40L, "项目主体")
        ));
        when(companySettingService.requireActiveSettlementCompany(40L))
                .thenReturn(company(40L, "项目主体"));

        LedgerAdjustment entity = new LedgerAdjustment();
        service.apply(entity, customerRequest(null, null));

        assertThat(entity.getProjectId()).isEqualTo(20L);
        assertThat(entity.getSettlementCompanyId()).isEqualTo(40L);
        assertThat(entity.getSettlementCompanyName()).isEqualTo("项目主体");
    }

    @Test
    void apply_shouldRejectSettlementCompanyDifferentFromProject() {
        LedgerAdjustmentApplyService service = service();
        when(customerQuery.findActiveById(10L)).thenReturn(Optional.of(
                new CustomerQuery.CustomerSnapshot(10L, "CUST001", "客户A", null, 30L, "客户主体")
        ));
        when(projectQuery.findActiveById(20L)).thenReturn(Optional.of(
                new ProjectQuery.ProjectSnapshot(20L, "项目A", "项A", 10L, "CUST001", 40L, "项目主体")
        ));

        assertThatThrownBy(() -> service.apply(new LedgerAdjustment(), customerRequest(30L, "客户主体")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("结算主体与项目不一致");
    }

    @Test
    void apply_shouldScaleAmountToTwoDecimals() {
        LedgerAdjustmentApplyService service = service();
        when(customerQuery.findActiveById(10L)).thenReturn(Optional.of(
                new CustomerQuery.CustomerSnapshot(10L, "CUST001", "客户A", null, 30L, "客户主体")
        ));
        when(projectQuery.findActiveById(20L)).thenReturn(Optional.of(
                new ProjectQuery.ProjectSnapshot(20L, "项目A", "项A", 10L, "CUST001", 40L, "项目主体")
        ));
        when(companySettingService.requireActiveSettlementCompany(40L))
                .thenReturn(company(40L, "项目主体"));

        LedgerAdjustment entity = new LedgerAdjustment();
        service.apply(entity, customerRequest(null, null, new BigDecimal("10")));

        assertThat(entity.getAmount()).isEqualByComparingTo(new BigDecimal("10.00"));
        assertThat(entity.getAmount().scale()).isEqualTo(2);
    }

    @Test
    void apply_shouldRejectNullAmount() {
        LedgerAdjustmentApplyService service = service();

        assertThatThrownBy(() -> service.apply(new LedgerAdjustment(),
                customerRequest(null, null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("金额必须大于0");
    }

    @Test
    void apply_shouldRejectZeroOrNegativeAmount() {
        LedgerAdjustmentApplyService service = service();

        assertThatThrownBy(() -> service.apply(new LedgerAdjustment(),
                customerRequest(null, null, BigDecimal.ZERO)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("金额必须大于0");
        assertThatThrownBy(() -> service.apply(new LedgerAdjustment(),
                customerRequest(null, null, new BigDecimal("-1"))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("金额必须大于0");
    }

    @Test
    void apply_shouldRejectUnknownDirectionAndCounterpartyType() {
        LedgerAdjustmentApplyService service = service();

        assertThatThrownBy(() -> service.apply(new LedgerAdjustment(),
                request("LA001", "预收", "客户", 10L, "CUST001", "客户A",
                        null, null, 20L, "项目A", new BigDecimal("10.00"), "草稿")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("方向不合法");
        assertThatThrownBy(() -> service.apply(new LedgerAdjustment(),
                request("LA001", "应收", "平台", 10L, "CUST001", "客户A",
                        null, null, 20L, "项目A", new BigDecimal("10.00"), "草稿")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("往来类型不合法");
    }

    @Test
    void apply_shouldRejectDirectionMismatchedWithCounterparty() {
        LedgerAdjustmentApplyService service = service();

        assertThatThrownBy(() -> service.apply(new LedgerAdjustment(),
                request("LA001", "应收", "供应商", null, "SUP001", "供应商A",
                        30L, "客户主体", null, null, new BigDecimal("10.00"), "草稿")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("应收调整只能选择客户");
        assertThatThrownBy(() -> service.apply(new LedgerAdjustment(),
                request("LA001", "应付", "客户", 10L, "CUST001", "客户A",
                        null, null, 20L, "项目A", new BigDecimal("10.00"), "草稿")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("应付调整只能选择供应商或物流商");
    }

    @Test
    void apply_shouldRejectBlankCounterpartyCodeWhenIdMissing() {
        LedgerAdjustmentApplyService service = service();

        assertThatThrownBy(() -> service.apply(new LedgerAdjustment(),
                request("LA001", "应收", "客户", null, "  ", "客户A",
                        null, null, 20L, "项目A", new BigDecimal("10.00"), "草稿")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("往来单位编码不能为空");
    }

    @Test
    void apply_shouldRejectCounterpartySnapshotMismatch() {
        LedgerAdjustmentApplyService service = service();
        when(customerQuery.findActiveById(10L)).thenReturn(Optional.of(
                new CustomerQuery.CustomerSnapshot(10L, "CUST001", "客户A", null, 30L, "客户主体")
        ));

        assertThatThrownBy(() -> service.apply(new LedgerAdjustment(),
                request("LA001", "应收", "客户", 10L, "CUST001", "客户B",
                        null, null, 20L, "项目A", new BigDecimal("10.00"), "草稿")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("客户名称与ID不一致");
    }

    @Test
    void apply_shouldRejectProjectOnNonCustomerAdjustment() {
        LedgerAdjustmentApplyService service = service();
        when(supplierQuery.findActiveByCode("SUP001")).thenReturn(Optional.of(
                new SupplierQuery.SupplierSnapshot(11L, "SUP001", "供应商A")
        ));

        assertThatThrownBy(() -> service.apply(new LedgerAdjustment(),
                request("LA002", "应付", "供应商", null, "SUP001", "供应商A",
                        30L, "客户主体", 20L, "项目A", new BigDecimal("10.00"), "草稿")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("供应商或物流商台账调整不能选择项目");
    }

    @Test
    void apply_shouldResolveSupplierCounterpartyByCodeWithoutProject() {
        LedgerAdjustmentApplyService service = service();
        when(supplierQuery.findActiveByCode("SUP001")).thenReturn(Optional.of(
                new SupplierQuery.SupplierSnapshot(11L, "SUP001", "供应商A")
        ));
        when(companySettingService.requireActiveSettlementCompany(30L))
                .thenReturn(company(30L, "客户主体"));

        LedgerAdjustment entity = new LedgerAdjustment();
        service.apply(entity, request("LA002", "应付", "供应商", null, "SUP001", "供应商A",
                30L, "客户主体", null, null, new BigDecimal("10.00"), "草稿"));

        assertThat(entity.getCounterpartyId()).isEqualTo(11L);
        assertThat(entity.getCounterpartyName()).isEqualTo("供应商A");
        assertThat(entity.getProjectId()).isNull();
        assertThat(entity.getSettlementCompanyId()).isEqualTo(30L);
    }

    @Test
    void apply_shouldRejectUnknownSupplierCode() {
        LedgerAdjustmentApplyService service = service();
        when(supplierQuery.findActiveByCode("SUP001")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.apply(new LedgerAdjustment(),
                request("LA002", "应付", "供应商", null, "SUP001", "供应商A",
                        30L, "客户主体", null, null, new BigDecimal("10.00"), "草稿")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("供应商不存在");
    }

    @Test
    void apply_shouldRejectProjectNameWithoutProjectId() {
        LedgerAdjustmentApplyService service = service();
        when(customerQuery.findActiveById(10L)).thenReturn(Optional.of(
                new CustomerQuery.CustomerSnapshot(10L, "CUST001", "客户A", null, 30L, "客户主体")
        ));

        assertThatThrownBy(() -> service.apply(new LedgerAdjustment(),
                request("LA001", "应收", "客户", 10L, "CUST001", "客户A",
                        null, null, null, "项目A", new BigDecimal("10.00"), "草稿")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("项目名称不能脱离项目ID单独提交");
    }

    @Test
    void apply_shouldRejectProjectBelongingToOtherCustomer() {
        LedgerAdjustmentApplyService service = service();
        when(customerQuery.findActiveById(10L)).thenReturn(Optional.of(
                new CustomerQuery.CustomerSnapshot(10L, "CUST001", "客户A", null, 30L, "客户主体")
        ));
        when(projectQuery.findActiveById(20L)).thenReturn(Optional.of(
                new ProjectQuery.ProjectSnapshot(20L, "项目A", "项A", 99L, "CUST099", 40L, "项目主体")
        ));

        assertThatThrownBy(() -> service.apply(new LedgerAdjustment(),
                customerRequest(20L, "项目A")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("项目不属于所选客户");
    }

    @Test
    void apply_shouldRejectStatusChangedByOrdinarySave() {
        LedgerAdjustmentApplyService service = service();
        LedgerAdjustment entity = new LedgerAdjustment();
        entity.setStatus("已审核");

        assertThatThrownBy(() -> service.apply(entity, customerRequest(null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("普通保存不能修改台账调整单状态");
    }

    @Test
    void apply_shouldRejectNonDraftStatusOnNewEntity() {
        LedgerAdjustmentApplyService service = service();

        assertThatThrownBy(() -> service.apply(new LedgerAdjustment(),
                request("LA001", "应收", "客户", 10L, "CUST001", "客户A",
                        null, null, 20L, "项目A", new BigDecimal("10.00"), "已审核")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("新建台账调整单只能保存为草稿");
    }

    @Test
    void apply_shouldTrimOperatorNameAndRemark() {
        LedgerAdjustmentApplyService service = service();
        when(customerQuery.findActiveById(10L)).thenReturn(Optional.of(
                new CustomerQuery.CustomerSnapshot(10L, "CUST001", "客户A", null, 30L, "客户主体")
        ));
        when(companySettingService.requireActiveSettlementCompany(30L))
                .thenReturn(company(30L, "客户主体"));

        LedgerAdjustment entity = new LedgerAdjustment();
        service.apply(entity, request("LA003", "应收", "客户", 10L, "CUST001", "客户A",
                30L, "客户主体", null, null, new BigDecimal("10.00"), "草稿", "  操作员  ", "  备注  "));

        assertThat(entity.getOperatorName()).isEqualTo("操作员");
        assertThat(entity.getRemark()).isEqualTo("备注");
    }

    @Test
    void apply_shouldRejectBlankOperatorName() {
        LedgerAdjustmentApplyService service = service();
        when(customerQuery.findActiveById(10L)).thenReturn(Optional.of(
                new CustomerQuery.CustomerSnapshot(10L, "CUST001", "客户A", null, 30L, "客户主体")
        ));
        when(projectQuery.findActiveById(20L)).thenReturn(Optional.of(
                new ProjectQuery.ProjectSnapshot(20L, "项目A", "项A", 10L, "CUST001", 40L, "项目主体")
        ));
        when(companySettingService.requireActiveSettlementCompany(40L))
                .thenReturn(company(40L, "项目主体"));

        assertThatThrownBy(() -> service.apply(new LedgerAdjustment(),
                request("LA003", "应收", "客户", 10L, "CUST001", "客户A",
                        null, null, 20L, "项目A", new BigDecimal("10.00"), "草稿", "   ", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("经办人不能为空");
    }

    private LedgerAdjustmentApplyService service() {
        return new LedgerAdjustmentApplyService(
                customerQuery,
                supplierQuery,
                carrierQuery,
                projectQuery,
                companySettingService
        );
    }

    private LedgerAdjustmentRequest customerRequest(Long settlementCompanyId, String settlementCompanyName) {
        return customerRequest(settlementCompanyId, settlementCompanyName, new BigDecimal("10.00"));
    }

    private LedgerAdjustmentRequest customerRequest(Long settlementCompanyId,
                                                    String settlementCompanyName,
                                                    BigDecimal amount) {
        return request("LA001", "应收", "客户", 10L, "CUST001", "客户A",
                settlementCompanyId, settlementCompanyName, 20L, "项目A", amount, "草稿");
    }

    private LedgerAdjustmentRequest request(String adjustmentNo,
                                            String direction,
                                            String counterpartyType,
                                            Long counterpartyId,
                                            String counterpartyCode,
                                            String counterpartyName,
                                            Long settlementCompanyId,
                                            String settlementCompanyName,
                                            Long projectId,
                                            String projectName,
                                            BigDecimal amount,
                                            String status) {
        return request(adjustmentNo, direction, counterpartyType, counterpartyId, counterpartyCode,
                counterpartyName, settlementCompanyId, settlementCompanyName, projectId, projectName,
                amount, status, "操作员", null);
    }

    private LedgerAdjustmentRequest request(String adjustmentNo,
                                            String direction,
                                            String counterpartyType,
                                            Long counterpartyId,
                                            String counterpartyCode,
                                            String counterpartyName,
                                            Long settlementCompanyId,
                                            String settlementCompanyName,
                                            Long projectId,
                                            String projectName,
                                            BigDecimal amount,
                                            String status,
                                            String operatorName,
                                            String remark) {
        return new LedgerAdjustmentRequest(
                adjustmentNo,
                direction,
                counterpartyType,
                counterpartyId,
                counterpartyCode,
                counterpartyName,
                settlementCompanyId,
                settlementCompanyName,
                projectId,
                projectName,
                ADJUSTMENT_DATE,
                amount,
                "其他调整",
                "增加余额",
                status,
                operatorName,
                remark
        );
    }

    private CompanySetting company(Long id, String name) {
        CompanySetting company = new CompanySetting();
        company.setId(id);
        company.setCompanyName(name);
        return company;
    }
}
