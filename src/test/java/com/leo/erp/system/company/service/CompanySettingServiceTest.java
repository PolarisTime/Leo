package com.leo.erp.system.company.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.erp.common.config.CacheConfig;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.system.company.domain.entity.CompanySetting;
import com.leo.erp.system.company.mapper.CompanySettingMapper;
import com.leo.erp.system.company.repository.CompanySettingRepository;
import com.leo.erp.system.company.web.dto.CompanySettingRequest;
import com.leo.erp.system.company.web.dto.CompanySettingResponse;
import com.leo.erp.system.dashboard.service.DashboardSummaryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

import static com.leo.erp.common.support.StatusConstants.NORMAL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompanySettingServiceTest {

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

    private CompanySettingRequest request(String companyName) {
        return new CompanySettingRequest(companyName, "91310000MA1K35X00X", null, "正常", "备注");
    }

    @Test
    void requireActiveSettlementCompanySnapshot_shouldExposeOnlyPublicFields() {
        CompanySetting company = new CompanySetting();
        company.setId(30L);
        company.setCompanyName("结算主体A");
        when(repository.findByIdAndStatusAndDeletedFlagFalse(30L, NORMAL))
                .thenReturn(Optional.of(company));

        assertThat(service().requireActiveSettlementCompanySnapshot(30L))
                .isEqualTo(new com.leo.erp.system.company.api.SettlementCompanySnapshot(30L, "结算主体A"));
    }

    @Test
    void convenienceConstructor_fiveArgs_stillFunctionalForPublicQueries() {
        CompanySetting company = new CompanySetting();
        company.setId(30L);
        company.setCompanyName("结算主体A");
        when(repository.findByIdAndStatusAndDeletedFlagFalse(30L, NORMAL))
                .thenReturn(Optional.of(company));
        CompanySettingService convenience = new CompanySettingService(
                repository,
                mock(SnowflakeIdGenerator.class),
                mock(CompanySettingMapper.class),
                mock(DashboardSummaryService.class),
                mock(ObjectMapper.class)
        );

        assertThat(convenience.requireActiveSettlementCompanySnapshot(30L))
                .isEqualTo(new com.leo.erp.system.company.api.SettlementCompanySnapshot(30L, "结算主体A"));
    }

    @Test
    void requireActiveSettlementCompany_nullId_rejectedWithValidationMessage() {
        assertThatThrownBy(() -> service().requireActiveSettlementCompany(null))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.VALIDATION_ERROR))
                .hasMessage("请选择结算主体");
    }

    @Test
    void create_assignsSnowflakeIdAppliesDefaultsAndEvictsDashboardCache() {
        CompanySetting[] savedHolder = new CompanySetting[1];
        when(snowflakeIdGenerator.nextId()).thenReturn(77L);
        when(repository.existsByCompanyNameAndDeletedFlagFalse("结算主体A")).thenReturn(false);
        when(repository.save(any(CompanySetting.class))).thenAnswer(invocation -> {
            CompanySetting entity = invocation.getArgument(0);
            savedHolder[0] = entity;
            return entity;
        });
        CompanySettingResponse response = new CompanySettingResponse(77L, "结算主体A", "91310000MA1K35X00X",
                "", "", List.of(), "正常", "备注");
        when(mapper.toResponse(any(CompanySetting.class), eq(List.of()))).thenReturn(response);

        service().create(request("结算主体A"));

        CompanySetting entity = savedHolder[0];
        assertThat(entity.getId()).isEqualTo(77L);
        assertThat(entity.getCompanyName()).isEqualTo("结算主体A");
        assertThat(entity.getBankName()).isEmpty();
        assertThat(entity.getBankAccount()).isEmpty();
        assertThat(entity.getSettlementAccountsJson()).isEqualTo("[]");
        assertThat(entity.getStatus()).isEqualTo("正常");
        verify(dashboardSummaryService).evictAllCache();
    }

    @Test
    void create_duplicateCompanyName_rejectedBeforeSave() {
        when(repository.existsByCompanyNameAndDeletedFlagFalse("结算主体A")).thenReturn(true);

        assertThatThrownBy(() -> service().create(request("结算主体A")))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.BUSINESS_ERROR))
                .hasMessage("结算主体名称已存在");
        verify(repository, never()).save(any());
        verify(dashboardSummaryService, never()).evictAllCache();
    }

    @Test
    void detail_notFound_throwsWithModuleMessage() {
        when(repository.findByIdAndDeletedFlagFalse(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().detail(9L))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.NOT_FOUND))
                .hasMessage("结算主体不存在");
    }

    @Test
    void update_nameChanged_syncsSettlementCompanyNameAndEvictsDashboardCache() {
        CompanySetting entity = new CompanySetting();
        entity.setId(5L);
        entity.setCompanyName("旧名称");
        when(repository.findByIdAndDeletedFlagFalse(5L)).thenReturn(Optional.of(entity));
        when(repository.existsByCompanyNameAndDeletedFlagFalse("新名称")).thenReturn(false);
        when(repository.save(entity)).thenReturn(entity);
        when(mapper.toResponse(eq(entity), any())).thenReturn(mock(CompanySettingResponse.class));

        service().update(5L, request("新名称"));

        verify(nameSyncService).syncSettlementCompanyName(5L, "新名称");
        verify(dashboardSummaryService).evictAllCache();
    }

    @Test
    void update_nameUnchanged_skipsUniquenessCheckAndNameSync() {
        CompanySetting entity = new CompanySetting();
        entity.setId(5L);
        entity.setCompanyName("结算主体A");
        when(repository.findByIdAndDeletedFlagFalse(5L)).thenReturn(Optional.of(entity));
        when(repository.save(entity)).thenReturn(entity);
        when(mapper.toResponse(eq(entity), any())).thenReturn(mock(CompanySettingResponse.class));

        service().update(5L, request("结算主体A"));

        verify(repository, never()).existsByCompanyNameAndDeletedFlagFalse(any());
        verify(nameSyncService, never()).syncSettlementCompanyName(any(), any());
        verify(dashboardSummaryService).evictAllCache();
    }

    @Test
    void updateStatus_blank_rejectedWithValidationMessage() {
        when(repository.findByIdAndDeletedFlagFalse(5L)).thenReturn(Optional.of(new CompanySetting()));

        assertThatThrownBy(() -> service().updateStatus(5L, null))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.VALIDATION_ERROR))
                .hasMessage("状态不能为空");
    }

    @Test
    void updateStatus_anyStatus_rejectedAsUnsupported() {
        when(repository.findByIdAndDeletedFlagFalse(5L)).thenReturn(Optional.of(new CompanySetting()));

        assertThatThrownBy(() -> service().updateStatus(5L, "正常"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.BUSINESS_ERROR))
                .hasMessage("当前模块不支持状态变更");
    }

    @Test
    void delete_guardRefusal_blocksBeforeSoftDelete() {
        CompanySetting entity = new CompanySetting();
        entity.setId(5L);
        when(repository.findByIdAndDeletedFlagFalse(5L)).thenReturn(Optional.of(entity));
        doThrow(new BusinessException(ErrorCode.BUSINESS_ERROR, "该结算主体被引用，无法删除"))
                .when(mutationGuardService).assertDeletable(entity);

        assertThatThrownBy(() -> service().delete(5L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("该结算主体被引用，无法删除");
        assertThat(entity.isDeletedFlag()).isFalse();
        verify(repository, never()).save(any());
    }

    @Test
    void delete_softDeletesAndEvictsDashboardCache() {
        CompanySetting entity = new CompanySetting();
        entity.setId(5L);
        when(repository.findByIdAndDeletedFlagFalse(5L)).thenReturn(Optional.of(entity));
        when(repository.save(entity)).thenReturn(entity);

        service().delete(5L);

        assertThat(entity.isDeletedFlag()).isTrue();
        verify(repository).save(entity);
        verify(dashboardSummaryService).evictAllCache();
    }

    @Test
    void cacheAnnotations_preservedOnPublicWriteAndReadMethods() throws Exception {
        Method create = CompanySettingService.class.getMethod("create", CompanySettingRequest.class);
        Method update = CompanySettingService.class.getMethod("update", Long.class, CompanySettingRequest.class);
        Method updateStatus = CompanySettingService.class.getMethod("updateStatus", Long.class, String.class);
        Method delete = CompanySettingService.class.getMethod("delete", Long.class);
        Method saveCurrent = CompanySettingService.class.getMethod("saveCurrent", CompanySettingRequest.class);
        Method current = CompanySettingService.class.getMethod("current");

        for (Method method : List.of(create, update, updateStatus, delete, saveCurrent)) {
            CacheEvict cacheEvict = method.getAnnotation(CacheEvict.class);
            assertThat(cacheEvict).as(method.getName()).isNotNull();
            assertThat(cacheEvict.value()).containsExactly(CacheConfig.CACHE_STATIC);
            assertThat(cacheEvict.key()).isEqualTo("'leo:company:current:v2'");
        }
        Cacheable cacheable = current.getAnnotation(Cacheable.class);
        assertThat(cacheable).isNotNull();
        assertThat(cacheable.value()).containsExactly(CacheConfig.CACHE_STATIC);
        assertThat(cacheable.key()).isEqualTo("'leo:company:current:v2'");
        assertThat(cacheable.unless()).isEqualTo("#result == null");
    }
}
