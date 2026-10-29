package com.leo.erp.system.company.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.system.company.api.SettlementAccountSnapshot;
import com.leo.erp.system.company.domain.entity.CompanySetting;
import com.leo.erp.system.company.mapper.CompanySettingMapper;
import com.leo.erp.system.company.repository.CompanySettingRepository;
import com.leo.erp.system.company.web.dto.CompanySettlementAccountResponse;
import com.leo.erp.system.dashboard.service.DashboardSummaryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 资金账户快照解析边界：账户存在时返回名称/账号，账户不存在、已禁用或结算主体已删除/缺失时
 * 返回 null（或空索引）且不抛异常；空入参不查库。
 */
@ExtendWith(MockitoExtension.class)
class SettlementAccountSnapshotQueryTest {

    private static final Long COMPANY_ID = 1L;
    private static final Long ACCOUNT_ID = 2L;

    @Mock
    private CompanySettingRepository repository;
    @Mock
    private SnowflakeIdGenerator snowflakeIdGenerator;
    @Mock
    private CompanySettingMapper mapper;
    @Mock
    private DashboardSummaryService dashboardSummaryService;
    @Mock
    private CompanySettingMutationGuardService mutationGuardService;
    @Mock
    private CompanySettlementNameSyncService nameSyncService;

    private CompanySettingService service() {
        return new CompanySettingService(repository, snowflakeIdGenerator, mapper, dashboardSummaryService,
                new CompanySettlementAccountCodec(new ObjectMapper(), snowflakeIdGenerator),
                mutationGuardService, nameSyncService);
    }

    private CompanySetting company(Long id, boolean deleted, String accountsJson) {
        CompanySetting setting = new CompanySetting();
        setting.setId(id);
        setting.setCompanyName("结算主体" + id);
        setting.setDeletedFlag(deleted);
        setting.setSettlementAccountsJson(accountsJson);
        return setting;
    }

    private String accountsJson(CompanySettlementAccountResponse... accounts) throws Exception {
        return new ObjectMapper().writeValueAsString(List.of(accounts));
    }

    @Test
    void snapshot_shouldReturnNameAndAccountNoWhenAccountExists() throws Exception {
        when(repository.findAllById(any())).thenReturn(List.of(company(COMPANY_ID, false, accountsJson(
                new CompanySettlementAccountResponse(
                        ACCOUNT_ID, "基本户", "工商银行", "6222000011223344", "通用", StatusConstants.NORMAL, "备注")
        ))));

        SettlementAccountSnapshot snapshot = service().settlementAccountSnapshot(COMPANY_ID, ACCOUNT_ID);

        assertThat(snapshot).isNotNull();
        assertThat(snapshot.id()).isEqualTo(ACCOUNT_ID);
        assertThat(snapshot.accountName()).isEqualTo("基本户");
        assertThat(snapshot.accountNo()).isEqualTo("6222000011223344");
    }

    @Test
    void snapshots_shouldIndexWholeCompanyInOneQuery() throws Exception {
        when(repository.findAllById(any())).thenReturn(List.of(company(COMPANY_ID, false, accountsJson(
                new CompanySettlementAccountResponse(
                        ACCOUNT_ID, "基本户", "工商银行", "6222000011223344", "通用", StatusConstants.NORMAL, null),
                new CompanySettlementAccountResponse(
                        3L, "一般户", "建设银行", "6222000055667788", "通用", StatusConstants.NORMAL, null)
        ))));

        Map<Long, SettlementAccountSnapshot> snapshots = service().settlementAccountSnapshots(List.of(COMPANY_ID));

        assertThat(snapshots).containsOnlyKeys(ACCOUNT_ID, 3L);
        assertThat(snapshots.get(3L).accountName()).isEqualTo("一般户");
        assertThat(snapshots.get(3L).accountNo()).isEqualTo("6222000055667788");
    }

    @Test
    void snapshot_shouldFallBackToBankNameWhenAccountNameBlank() throws Exception {
        when(repository.findAllById(any())).thenReturn(List.of(company(COMPANY_ID, false, accountsJson(
                new CompanySettlementAccountResponse(
                        ACCOUNT_ID, "   ", "农业银行", "6222000099001122", "通用", StatusConstants.NORMAL, null)
        ))));

        SettlementAccountSnapshot snapshot = service().settlementAccountSnapshot(COMPANY_ID, ACCOUNT_ID);

        assertThat(snapshot).isNotNull();
        assertThat(snapshot.accountName()).isEqualTo("农业银行");
        assertThat(snapshot.accountNo()).isEqualTo("6222000099001122");
    }

    @Test
    void snapshot_shouldReturnNullWhenAccountMissing() throws Exception {
        when(repository.findAllById(any())).thenReturn(List.of(company(COMPANY_ID, false, accountsJson(
                new CompanySettlementAccountResponse(
                        ACCOUNT_ID, "基本户", "工商银行", "6222000011223344", "通用", StatusConstants.NORMAL, null)
        ))));

        assertThat(service().settlementAccountSnapshot(COMPANY_ID, 999L)).isNull();
    }

    @Test
    void snapshot_shouldReturnNullWhenSettlementCompanyMissing() {
        when(repository.findAllById(any())).thenReturn(List.of());

        assertThat(service().settlementAccountSnapshot(999L, ACCOUNT_ID)).isNull();
    }

    @Test
    void snapshot_shouldReturnNullWhenAccountDisabled() throws Exception {
        when(repository.findAllById(any())).thenReturn(List.of(company(COMPANY_ID, false, accountsJson(
                new CompanySettlementAccountResponse(
                        ACCOUNT_ID, "基本户", "工商银行", "6222000011223344", "通用", StatusConstants.DISABLED, null),
                new CompanySettlementAccountResponse(
                        4L, "旧账户", "工商银行", "6222000077889900", "通用", "停用", null)
        ))));

        CompanySettingService service = service();

        assertThat(service.settlementAccountSnapshot(COMPANY_ID, ACCOUNT_ID)).isNull();
        assertThat(service.settlementAccountSnapshot(COMPANY_ID, 4L)).isNull();
        assertThat(service.settlementAccountSnapshots(List.of(COMPANY_ID))).isEmpty();
    }

    @Test
    void snapshots_shouldIgnoreDeletedCompany() throws Exception {
        when(repository.findAllById(any())).thenReturn(List.of(company(COMPANY_ID, true, accountsJson(
                new CompanySettlementAccountResponse(
                        ACCOUNT_ID, "基本户", "工商银行", "6222000011223344", "通用", StatusConstants.NORMAL, null)
        ))));

        assertThat(service().settlementAccountSnapshots(List.of(COMPANY_ID))).isEmpty();
        assertThat(service().settlementAccountSnapshot(COMPANY_ID, ACCOUNT_ID)).isNull();
    }

    @Test
    void snapshots_shouldReturnEmptyWithoutQueryForBlankInput() {
        CompanySettingService service = service();

        assertThat(service.settlementAccountSnapshots(null)).isEmpty();
        assertThat(service.settlementAccountSnapshots(List.of())).isEmpty();
        assertThat(service.settlementAccountSnapshot(null, ACCOUNT_ID)).isNull();
        assertThat(service.settlementAccountSnapshot(COMPANY_ID, null)).isNull();

        verify(repository, never()).findAllById(any());
    }
}
