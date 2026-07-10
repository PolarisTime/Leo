package com.leo.erp.system.company.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.config.CacheConfig;
import com.leo.erp.common.support.MasterDataReferenceGuard;
import com.leo.erp.common.support.RedisJsonCacheSupport;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.system.norule.domain.entity.NoRule;
import com.leo.erp.system.company.domain.entity.CompanySetting;
import com.leo.erp.system.company.mapper.CompanySettingMapper;
import com.leo.erp.system.company.repository.CompanySettingRepository;
import com.leo.erp.system.company.web.dto.CompanySettingRequest;
import com.leo.erp.system.company.web.dto.CompanySettingResponse;
import com.leo.erp.system.company.web.dto.CompanySettlementAccountRequest;
import com.leo.erp.system.company.web.dto.CompanySettlementAccountResponse;
import com.leo.erp.system.dashboard.service.DashboardSummaryService;
import com.leo.erp.system.norule.repository.NoRuleRepository;
import com.leo.erp.system.runtimeconfig.service.RuntimeConfigService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

class CompanySettingServiceTest {

    @Test
    void shouldReturnCurrent_whenEntityExists() {
        var repository = (CompanySettingRepository) Proxy.newProxyInstance(
                CompanySettingRepository.class.getClassLoader(),
                new Class[]{CompanySettingRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findFirstByStatusAndDeletedFlagFalseOrderByIdAsc" -> Optional.of(createCompanySetting(1L));
                    case "findFirstByDeletedFlagFalseOrderByIdAsc" -> Optional.of(createCompanySetting(1L));
                    case "toString" -> "CompanySettingRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var mapper = (CompanySettingMapper) Proxy.newProxyInstance(
                CompanySettingMapper.class.getClassLoader(),
                new Class[]{CompanySettingMapper.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "toResponse" -> new com.leo.erp.system.company.web.dto.CompanySettingResponse(
                            1L, "公司A", "税号", "银行", "账号", new BigDecimal("0.13"), List.of(), "正常", null);
                    case "toString" -> "CompanySettingMapperStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var noRuleRepository = mock(NoRuleRepository.class);
        var service = new CompanySettingService(repository, new SnowflakeIdGenerator(1), mapper, null, noRuleRepository, new ObjectMapper());

        var result = service.current();

        assertThat(result).isNotNull();
        assertThat(result.companyName()).isEqualTo("公司A");
    }

    @Test
    void shouldCreateSettlementCompanyDirectly() {
        var repository = (CompanySettingRepository) Proxy.newProxyInstance(
                CompanySettingRepository.class.getClassLoader(),
                new Class[]{CompanySettingRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "existsByCompanyNameAndDeletedFlagFalse" -> false;
                    case "findFirstByStatusAndDeletedFlagFalseOrderByIdAsc" -> Optional.empty();
                    case "findFirstByDeletedFlagFalseOrderByIdAsc" -> Optional.empty();
                    case "save" -> args[0];
                    case "toString" -> "CompanySettingRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var noRuleRepository = (NoRuleRepository) Proxy.newProxyInstance(
                NoRuleRepository.class.getClassLoader(),
                new Class[]{NoRuleRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findBySettingCodeAndDeletedFlagFalse" -> Optional.empty();
                    case "toString" -> "NoRuleRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var mapper = (CompanySettingMapper) Proxy.newProxyInstance(
                CompanySettingMapper.class.getClassLoader(),
                new Class[]{CompanySettingMapper.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "toResponse" -> {
                        CompanySetting setting = (CompanySetting) args[0];
                        yield new com.leo.erp.system.company.web.dto.CompanySettingResponse(
                                setting.getId(),
                                setting.getCompanyName(),
                                setting.getTaxNo(),
                                setting.getBankName(),
                                setting.getBankAccount(),
                                new BigDecimal("0.13"),
                                List.of(),
                                setting.getStatus(),
                                setting.getRemark()
                        );
                    }
                    case "toString" -> "CompanySettingMapperStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var dashboardSummaryService = mock(DashboardSummaryService.class);
        var service = new CompanySettingService(repository, new SnowflakeIdGenerator(1), mapper,
                dashboardSummaryService, noRuleRepository, new ObjectMapper());

        var result = service.create(new CompanySettingRequest(
                "公司B",
                "税号B",
                List.of(new CompanySettlementAccountRequest(null, "账户B", "银行B", "账号B", "通用", "正常", "")),
                "正常",
                "备注"
        ));

        assertThat(result.companyName()).isEqualTo("公司B");
        verify(dashboardSummaryService).evictAllCache();
    }

    @Test
    void shouldSetTaxRateWhenCreatingSettlementCompany() {
        var repository = mock(CompanySettingRepository.class);
        var mapper = mock(CompanySettingMapper.class);
        var dashboardSummaryService = mock(DashboardSummaryService.class);
        var noRuleRepository = mock(NoRuleRepository.class);
        var service = new CompanySettingService(repository, new SnowflakeIdGenerator(1), mapper,
                dashboardSummaryService, noRuleRepository, new ObjectMapper());
        var entityCaptor = org.mockito.ArgumentCaptor.forClass(CompanySetting.class);
        var taxRateCaptor = org.mockito.ArgumentCaptor.forClass(BigDecimal.class);

        org.mockito.Mockito.when(repository.existsByCompanyNameAndDeletedFlagFalse("公司C")).thenReturn(false);
        org.mockito.Mockito.when(noRuleRepository.findBySettingCodeAndDeletedFlagFalse(CompanySettingService.DEFAULT_TAX_RATE_SETTING_CODE))
                .thenReturn(Optional.empty());
        org.mockito.Mockito.when(repository.findFirstByStatusAndDeletedFlagFalseOrderByIdAsc(StatusConstants.NORMAL))
                .thenReturn(Optional.empty());
        org.mockito.Mockito.when(repository.findFirstByDeletedFlagFalseOrderByIdAsc())
                .thenReturn(Optional.empty());
        org.mockito.Mockito.when(repository.save(any(CompanySetting.class))).thenAnswer(invocation -> invocation.getArgument(0));
        org.mockito.Mockito.when(mapper.toResponse(entityCaptor.capture(), taxRateCaptor.capture(), any()))
                .thenAnswer(invocation -> {
                    CompanySetting setting = invocation.getArgument(0);
                    BigDecimal taxRate = invocation.getArgument(1);
                    return new com.leo.erp.system.company.web.dto.CompanySettingResponse(
                            setting.getId(),
                            setting.getCompanyName(),
                            setting.getTaxNo(),
                            setting.getBankName(),
                            setting.getBankAccount(),
                            taxRate,
                            List.of(),
                            setting.getStatus(),
                            setting.getRemark()
                    );
                });

        service.create(new CompanySettingRequest(
                "公司C",
                "税号C",
                List.of(new CompanySettlementAccountRequest(null, "账户C", "银行C", "账号C", "通用", "正常", "")),
                "正常",
                ""
        ));

        verify(repository).save(entityCaptor.getValue());
        assertThat(entityCaptor.getValue().getTaxRate()).isEqualByComparingTo("0.1300");
        assertThat(taxRateCaptor.getValue()).isEqualByComparingTo("0.1300");
        verify(dashboardSummaryService).evictAllCache();
    }

    @Test
    void shouldThrowException_whenSaveCurrentWithoutEntity() {
        var repository = (CompanySettingRepository) Proxy.newProxyInstance(
                CompanySettingRepository.class.getClassLoader(),
                new Class[]{CompanySettingRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findFirstByStatusAndDeletedFlagFalseOrderByIdAsc" -> Optional.empty();
                    case "findFirstByDeletedFlagFalseOrderByIdAsc" -> Optional.empty();
                    case "toString" -> "CompanySettingRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var service = new CompanySettingService(repository, new SnowflakeIdGenerator(1), null, null, null, null);

        assertThatThrownBy(() -> service.saveCurrent(new CompanySettingRequest(
                "公司A", null, List.of(), null, null
        ))).isInstanceOf(BusinessException.class)
                .hasMessageContaining("请先通过首次初始化页面创建默认结算主体");
    }

    @Test
    void shouldThrowException_whenCompanyNameChanged() {
        var repository = (CompanySettingRepository) Proxy.newProxyInstance(
                CompanySettingRepository.class.getClassLoader(),
                new Class[]{CompanySettingRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findFirstByStatusAndDeletedFlagFalseOrderByIdAsc" -> Optional.of(createCompanySetting(1L));
                    case "findFirstByDeletedFlagFalseOrderByIdAsc" -> Optional.of(createCompanySetting(1L));
                    case "findByIdAndDeletedFlagFalse" -> Optional.of(createCompanySetting(1L));
                    case "save" -> args[0];
                    case "toString" -> "CompanySettingRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var service = new CompanySettingService(repository, new SnowflakeIdGenerator(1), null, null, null, new ObjectMapper());

        assertThatThrownBy(() -> service.saveCurrent(new CompanySettingRequest(
                "新公司名", null, List.of(
                new CompanySettlementAccountRequest(null, "账户A", "银行", "账号1", "通用", "正常", "")
        ), null, null))).isInstanceOf(BusinessException.class)
                .hasMessageContaining("公司名称由首次初始化写入");
    }

    @Test
    void shouldReturnActiveSettlementCompanyOptions() {
        var active = createCompanySetting(1L);
        var repository = (CompanySettingRepository) Proxy.newProxyInstance(
                CompanySettingRepository.class.getClassLoader(),
                new Class[]{CompanySettingRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByStatusAndDeletedFlagFalseOrderByIdAsc" -> {
                        assertThat(args[0]).isEqualTo(StatusConstants.NORMAL);
                        yield List.of(active);
                    }
                    case "toString" -> "CompanySettingRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var service = new CompanySettingService(repository, new SnowflakeIdGenerator(1), null, null, null, null);

        var options = service.listActiveOptions();

        assertThat(options).singleElement().satisfies(option -> {
            assertThat(option.id()).isEqualTo(1L);
            assertThat(option.companyName()).isEqualTo("公司A");
        });
    }

    @Test
    void shouldRequireActiveSettlementCompany() {
        var active = createCompanySetting(1L);
        var repository = (CompanySettingRepository) Proxy.newProxyInstance(
                CompanySettingRepository.class.getClassLoader(),
                new Class[]{CompanySettingRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByIdAndStatusAndDeletedFlagFalse" -> Optional.of(active);
                    case "toString" -> "CompanySettingRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var service = new CompanySettingService(repository, new SnowflakeIdGenerator(1), null, null, null, null);

        CompanySetting result = service.requireActiveSettlementCompany(1L);

        assertThat(result.getCompanyName()).isEqualTo("公司A");
    }

    @Test
    void shouldRejectInactiveSettlementCompany() {
        var repository = (CompanySettingRepository) Proxy.newProxyInstance(
                CompanySettingRepository.class.getClassLoader(),
                new Class[]{CompanySettingRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByIdAndStatusAndDeletedFlagFalse" -> Optional.empty();
                    case "toString" -> "CompanySettingRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var service = new CompanySettingService(repository, new SnowflakeIdGenerator(1), null, null, null, null);

        assertThatThrownBy(() -> service.requireActiveSettlementCompany(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("结算主体不存在或已禁用");
    }

    @Test
    void shouldReturnPage_whenCallingPage() {
        var repository = (CompanySettingRepository) Proxy.newProxyInstance(
                CompanySettingRepository.class.getClassLoader(),
                new Class[]{CompanySettingRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findAll" -> org.springframework.data.domain.Page.empty();
                    case "toString" -> "CompanySettingRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var service = new CompanySettingService(repository, new SnowflakeIdGenerator(1), null, null, null, null);

        var result = service.page(new com.leo.erp.common.api.PageQuery(0, 10, "id", "desc"), null, null);

        assertThat(result).isNotNull();
    }

    @Test
    void shouldEvictCacheWhenRedisAvailable() {
        var repository = (CompanySettingRepository) Proxy.newProxyInstance(
                CompanySettingRepository.class.getClassLoader(),
                new Class[]{CompanySettingRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByIdAndDeletedFlagFalse" -> Optional.of(createCompanySetting(1L));
                    case "findFirstByStatusAndDeletedFlagFalseOrderByIdAsc" -> Optional.of(createCompanySetting(1L));
                    case "findFirstByDeletedFlagFalseOrderByIdAsc" -> Optional.of(createCompanySetting(1L));
                    case "save" -> args[0];
                    case "toString" -> "CompanySettingRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var cache = mock(RedisJsonCacheSupport.class);
        var dashboardSummaryService = mock(DashboardSummaryService.class);
        var noRuleRepository = (NoRuleRepository) Proxy.newProxyInstance(
                NoRuleRepository.class.getClassLoader(),
                new Class[]{NoRuleRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findBySettingCodeAndDeletedFlagFalse" -> Optional.empty();
                    case "toString" -> "NoRuleRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var mapper = (CompanySettingMapper) Proxy.newProxyInstance(
                CompanySettingMapper.class.getClassLoader(),
                new Class[]{CompanySettingMapper.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "toResponse" -> new com.leo.erp.system.company.web.dto.CompanySettingResponse(
                            1L, "公司A", "税号", "银行", "账号", new BigDecimal("0.13"), List.of(), "正常", null);
                    case "toString" -> "CompanySettingMapperStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var service = new CompanySettingService(repository, new SnowflakeIdGenerator(1), mapper,
                dashboardSummaryService, noRuleRepository, new ObjectMapper(), cache);

        var result = service.saveCurrent(new CompanySettingRequest("公司A", "税号", List.of(
                new CompanySettlementAccountRequest(null, "账户A", "银行", "账号", "通用", "正常", "")
        ), null, null));

        assertThat(result).isNotNull();
        verify(cache).delete(List.of(
                CompanySettingService.CURRENT_COMPANY_CACHE_KEY,
                CompanySettingService.CURRENT_TAX_RATE_CACHE_KEY,
                RuntimeConfigService.RUNTIME_CONFIG_CACHE_KEY
        ));
    }

    @Test
    void shouldLoadCurrentFromRepositoryOnSpringCachePath() {
        var repository = mock(CompanySettingRepository.class);
        var cache = mock(RedisJsonCacheSupport.class);
        var expected = new CompanySettingResponse(1L, "公司A", "税号", "银行", "账号",
                new BigDecimal("0.1300"), List.of(), "正常", null);
        var mapper = mock(CompanySettingMapper.class);
        var entity = createCompanySetting(1L);
        when(repository.findFirstByStatusAndDeletedFlagFalseOrderByIdAsc(StatusConstants.NORMAL))
                .thenReturn(Optional.of(entity));
        when(mapper.toResponse(eq(entity), any(BigDecimal.class), any())).thenReturn(expected);
        var service = new CompanySettingService(repository, new SnowflakeIdGenerator(1), mapper,
                mock(DashboardSummaryService.class), mock(NoRuleRepository.class), new ObjectMapper(), cache);

        var result = service.current();

        assertThat(result).isSameAs(expected);
    }

    @Test
    void shouldDeclareSpringCacheAnnotationsForCurrentSettings() throws Exception {
        Method current = CompanySettingService.class.getDeclaredMethod("current");
        Cacheable currentCacheable = current.getAnnotation(Cacheable.class);
        assertThat(currentCacheable.value()).containsExactly(CacheConfig.CACHE_STATIC);
        assertThat(currentCacheable.key()).isEqualTo("'" + CompanySettingService.CURRENT_COMPANY_CACHE_KEY + "'");

        Method taxRate = CompanySettingService.class.getDeclaredMethod("resolveCurrentTaxRate");
        Cacheable taxRateCacheable = taxRate.getAnnotation(Cacheable.class);
        assertThat(taxRateCacheable.value()).containsExactly(CacheConfig.CACHE_STATIC);
        assertThat(taxRateCacheable.key()).isEqualTo("'" + CompanySettingService.CURRENT_TAX_RATE_CACHE_KEY + "'");

        Method saveCurrent = CompanySettingService.class.getDeclaredMethod("saveCurrent", CompanySettingRequest.class);
        Caching caching = saveCurrent.getAnnotation(Caching.class);
        assertThat(caching.evict()).hasSize(2);
        assertThat(caching.evict())
                .extracting(evict -> evict.key())
                .containsExactlyInAnyOrder(
                        "'" + CompanySettingService.CURRENT_COMPANY_CACHE_KEY + "'",
                        "'" + CompanySettingService.CURRENT_TAX_RATE_CACHE_KEY + "'"
                );
    }

    @Test
    void shouldRefreshCurrentCompanyAndTaxRateSpringCaches() {
        var repository = mock(CompanySettingRepository.class);
        var mapper = mock(CompanySettingMapper.class);
        var noRuleRepository = mock(NoRuleRepository.class);
        var entity = createCompanySetting(1L);
        var expectedCompany = new CompanySettingResponse(
                1L,
                entity.getCompanyName(),
                entity.getTaxNo(),
                entity.getBankName(),
                entity.getBankAccount(),
                new BigDecimal("0.1300"),
                List.of(),
                StatusConstants.NORMAL,
                entity.getRemark()
        );
        when(repository.findFirstByStatusAndDeletedFlagFalseOrderByIdAsc(StatusConstants.NORMAL))
                .thenReturn(Optional.of(entity));
        when(mapper.toResponse(eq(entity), any(BigDecimal.class), any())).thenReturn(expectedCompany);
        when(noRuleRepository.findBySettingCodeAndDeletedFlagFalse(CompanySettingService.DEFAULT_TAX_RATE_SETTING_CODE))
                .thenReturn(Optional.empty());
        var service = new CompanySettingService(
                repository,
                new SnowflakeIdGenerator(1),
                mapper,
                mock(DashboardSummaryService.class),
                noRuleRepository,
                new ObjectMapper(),
                mock(RedisJsonCacheSupport.class)
        );
        var cacheManager = new ConcurrentMapCacheManager(CacheConfig.CACHE_STATIC);
        var cache = cacheManager.getCache(CacheConfig.CACHE_STATIC);
        cache.put(CompanySettingService.CURRENT_COMPANY_CACHE_KEY, "stale");
        cache.put(CompanySettingService.CURRENT_TAX_RATE_CACHE_KEY, new BigDecimal("0.0500"));
        service.setCacheManager(cacheManager);

        var result = service.verifyAndRefreshCache();

        assertThat(cache.get(CompanySettingService.CURRENT_COMPANY_CACHE_KEY, CompanySettingResponse.class))
                .isEqualTo(expectedCompany);
        assertThat(cache.get(CompanySettingService.CURRENT_TAX_RATE_CACHE_KEY, BigDecimal.class))
                .isEqualByComparingTo("0.1300");
        assertThat(result.currentSize()).isEqualTo(2);
        assertThat(result.refreshedSize()).isEqualTo(2);
        assertThat(result.refreshed()).isTrue();
    }

    @Test
    void shouldResolveCurrentTaxRateFromRepositoryOnSpringCachePath() {
        var cache = mock(RedisJsonCacheSupport.class);
        var repository = mock(CompanySettingRepository.class);
        CompanySetting current = createCompanySetting(1L);
        current.setTaxRate(new BigDecimal("0.0600"));
        when(repository.findFirstByStatusAndDeletedFlagFalseOrderByIdAsc(StatusConstants.NORMAL))
                .thenReturn(Optional.of(current));
        var service = new CompanySettingService(repository, new SnowflakeIdGenerator(1),
                mock(CompanySettingMapper.class), mock(DashboardSummaryService.class), mock(NoRuleRepository.class),
                new ObjectMapper(), cache);

        assertThat(service.resolveCurrentTaxRate()).isEqualByComparingTo("0.0600");
    }

    @Test
    void shouldResolveCurrentTaxRateFromConfiguredNoRule() {
        var noRule = new NoRule();
        noRule.setSampleNo("0.0900");
        var noRuleRepository = mock(NoRuleRepository.class);
        when(noRuleRepository.findBySettingCodeAndDeletedFlagFalse(CompanySettingService.DEFAULT_TAX_RATE_SETTING_CODE))
                .thenReturn(Optional.of(noRule));
        var service = new CompanySettingService(mock(CompanySettingRepository.class), new SnowflakeIdGenerator(1),
                mock(CompanySettingMapper.class), mock(DashboardSummaryService.class), noRuleRepository,
                new ObjectMapper());

        assertThat(service.resolveCurrentTaxRate()).isEqualByComparingTo("0.0900");
    }

    @Test
    void shouldFallbackToZeroTaxRateWhenConfiguredValueInvalidAndNoCurrentCompany() {
        var noRule = new NoRule();
        noRule.setSampleNo("not-a-number");
        var noRuleRepository = mock(NoRuleRepository.class);
        when(noRuleRepository.findBySettingCodeAndDeletedFlagFalse(CompanySettingService.DEFAULT_TAX_RATE_SETTING_CODE))
                .thenReturn(Optional.of(noRule));
        var repository = mock(CompanySettingRepository.class);
        when(repository.findFirstByStatusAndDeletedFlagFalseOrderByIdAsc(StatusConstants.NORMAL)).thenReturn(Optional.empty());
        when(repository.findFirstByDeletedFlagFalseOrderByIdAsc()).thenReturn(Optional.empty());
        var service = new CompanySettingService(repository, new SnowflakeIdGenerator(1),
                mock(CompanySettingMapper.class), mock(DashboardSummaryService.class), noRuleRepository,
                new ObjectMapper());

        assertThat(service.resolveCurrentTaxRate()).isEqualByComparingTo("0.0000");
    }

    @Test
    void shouldRejectNullSettlementCompanyId() {
        var service = new CompanySettingService(mock(CompanySettingRepository.class), new SnowflakeIdGenerator(1), null, null, null, null);

        assertThatThrownBy(() -> service.requireActiveSettlementCompany(null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("请选择结算主体");
    }

    @Test
    void shouldRejectDuplicateCompanyNameOnCreate() {
        var repository = mock(CompanySettingRepository.class);
        when(repository.existsByCompanyNameAndDeletedFlagFalse("公司A")).thenReturn(true);
        var service = new CompanySettingService(repository, new SnowflakeIdGenerator(1), mock(CompanySettingMapper.class),
                mock(DashboardSummaryService.class), mock(NoRuleRepository.class), new ObjectMapper());

        assertThatThrownBy(() -> service.create(new CompanySettingRequest("公司A", "税号", List.of(), "正常", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("结算主体名称已存在");
    }

    @Test
    void shouldRejectDuplicateCompanyNameOnUpdateWhenNameChanges() {
        var repository = mock(CompanySettingRepository.class);
        var entity = createCompanySetting(1L);
        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(entity));
        when(repository.existsByCompanyNameAndDeletedFlagFalse("公司B")).thenReturn(true);
        var service = new CompanySettingService(repository, new SnowflakeIdGenerator(1), mock(CompanySettingMapper.class),
                mock(DashboardSummaryService.class), mock(NoRuleRepository.class), new ObjectMapper());

        assertThatThrownBy(() -> service.update(1L, new CompanySettingRequest("公司B", "税号", List.of(), "正常", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("结算主体名称已存在");
    }

    @Test
    void shouldCheckDuplicateCompanyNameOnUpdateWhenNameChangesAndIsUnique() {
        var repository = mock(CompanySettingRepository.class);
        var mapper = mock(CompanySettingMapper.class);
        var savedCaptor = org.mockito.ArgumentCaptor.forClass(CompanySetting.class);
        var entity = createCompanySetting(1L);
        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(entity));
        when(repository.existsByCompanyNameAndDeletedFlagFalse("公司B")).thenReturn(false);
        when(repository.save(savedCaptor.capture())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mapper.toResponse(any(CompanySetting.class), any(BigDecimal.class), any()))
                .thenAnswer(invocation -> {
                    CompanySetting setting = invocation.getArgument(0);
                    return new CompanySettingResponse(setting.getId(), setting.getCompanyName(), setting.getTaxNo(),
                            setting.getBankName(), setting.getBankAccount(), invocation.getArgument(1),
                            invocation.getArgument(2), setting.getStatus(), setting.getRemark());
                });
        var service = new CompanySettingService(repository, new SnowflakeIdGenerator(1), mapper,
                mock(DashboardSummaryService.class), mock(NoRuleRepository.class), new ObjectMapper());

        service.update(1L, new CompanySettingRequest("公司B", "税号",
                List.of(new CompanySettlementAccountRequest(null, "账户", "银行", "账号", "通用", "正常", null)),
                "正常",
                null));

        verify(repository).existsByCompanyNameAndDeletedFlagFalse("公司B");
        assertThat(savedCaptor.getValue().getCompanyName()).isEqualTo("公司B");
    }

    @Test
    void shouldUpdateWhenCompanyNameUnchangedWithoutDuplicateCheck() {
        var repository = mock(CompanySettingRepository.class);
        var mapper = mock(CompanySettingMapper.class);
        var entity = createCompanySetting(1L);
        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(entity));
        when(repository.save(any(CompanySetting.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(mapper.toResponse(any(CompanySetting.class), any(BigDecimal.class), any()))
                .thenReturn(new CompanySettingResponse(1L, "公司A", "税号", "", "", new BigDecimal("0.1300"),
                        List.of(), "正常", null));
        var service = new CompanySettingService(repository, new SnowflakeIdGenerator(1), mapper,
                mock(DashboardSummaryService.class), mock(NoRuleRepository.class), new ObjectMapper());

        service.update(1L, new CompanySettingRequest("公司A", "税号",
                List.of(new CompanySettlementAccountRequest(null, "账户", "银行", "账号", "通用", "正常", null)),
                "正常",
                null));

        verify(repository, never()).existsByCompanyNameAndDeletedFlagFalse("公司A");
    }

    @Test
    void shouldRejectTaxNoChangeWhenSavingCurrent() {
        var repository = mock(CompanySettingRepository.class);
        when(repository.findFirstByStatusAndDeletedFlagFalseOrderByIdAsc(StatusConstants.NORMAL))
                .thenReturn(Optional.of(createCompanySetting(1L)));
        var service = new CompanySettingService(repository, new SnowflakeIdGenerator(1), mock(CompanySettingMapper.class),
                mock(DashboardSummaryService.class), mock(NoRuleRepository.class), new ObjectMapper());

        assertThatThrownBy(() -> service.saveCurrent(new CompanySettingRequest("公司A", "新税号", List.of(), null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("税号由首次初始化写入");
    }

    @Test
    void shouldRejectDuplicateSettlementBankAccount() {
        var repository = mock(CompanySettingRepository.class);
        when(repository.existsByCompanyNameAndDeletedFlagFalse("公司A")).thenReturn(false);
        var service = new CompanySettingService(repository, new SnowflakeIdGenerator(1), mock(CompanySettingMapper.class),
                mock(DashboardSummaryService.class), mock(NoRuleRepository.class), new ObjectMapper());
        var account = "62220000";

        assertThatThrownBy(() -> service.create(new CompanySettingRequest("公司A", "税号",
                List.of(
                        new CompanySettlementAccountRequest(null, "账户1", "银行", account, "通用", "正常", null),
                        new CompanySettlementAccountRequest(null, "账户2", "银行", account, "通用", "正常", null)
                ), "正常", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("银行账号不能重复");
    }

    @Test
    void shouldSaveBlankSettlementAccountsAsEmptyBankInfo() {
        var repository = mock(CompanySettingRepository.class);
        var mapper = mock(CompanySettingMapper.class);
        var savedCaptor = org.mockito.ArgumentCaptor.forClass(CompanySetting.class);
        when(repository.existsByCompanyNameAndDeletedFlagFalse("公司A")).thenReturn(false);
        when(repository.findFirstByStatusAndDeletedFlagFalseOrderByIdAsc(StatusConstants.NORMAL)).thenReturn(Optional.empty());
        when(repository.findFirstByDeletedFlagFalseOrderByIdAsc()).thenReturn(Optional.empty());
        when(repository.save(savedCaptor.capture())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mapper.toResponse(any(CompanySetting.class), any(BigDecimal.class), any()))
                .thenReturn(new CompanySettingResponse(1L, "公司A", "税号", "", "", new BigDecimal("0.1300"),
                        List.of(), "正常", null));
        var service = new CompanySettingService(repository, new SnowflakeIdGenerator(1), mapper,
                mock(DashboardSummaryService.class), mock(NoRuleRepository.class), new ObjectMapper());
        var settlementAccounts = new ArrayList<CompanySettlementAccountRequest>();
        settlementAccounts.add(null);
        settlementAccounts.add(new CompanySettlementAccountRequest(null, " ", " ", " ", " ", " ", " "));

        service.create(new CompanySettingRequest("公司A", "税号", settlementAccounts, "正常", null));

        assertThat(savedCaptor.getValue().getBankName()).isEmpty();
        assertThat(savedCaptor.getValue().getBankAccount()).isEmpty();
        assertThat(savedCaptor.getValue().getSettlementAccountsJson()).isEqualTo("[]");
    }

    @Test
    void shouldTreatNullSettlementAccountFieldsAsBlank() {
        var repository = mock(CompanySettingRepository.class);
        var mapper = mock(CompanySettingMapper.class);
        var savedCaptor = org.mockito.ArgumentCaptor.forClass(CompanySetting.class);
        when(repository.existsByCompanyNameAndDeletedFlagFalse("公司A")).thenReturn(false);
        when(repository.findFirstByStatusAndDeletedFlagFalseOrderByIdAsc(StatusConstants.NORMAL)).thenReturn(Optional.empty());
        when(repository.findFirstByDeletedFlagFalseOrderByIdAsc()).thenReturn(Optional.empty());
        when(repository.save(savedCaptor.capture())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mapper.toResponse(any(CompanySetting.class), any(BigDecimal.class), any()))
                .thenReturn(new CompanySettingResponse(1L, "公司A", "税号", "", "", new BigDecimal("0.1300"),
                        List.of(), "正常", null));
        var service = new CompanySettingService(repository, new SnowflakeIdGenerator(1), mapper,
                mock(DashboardSummaryService.class), mock(NoRuleRepository.class), new ObjectMapper());

        service.create(new CompanySettingRequest("公司A", "税号",
                List.of(new CompanySettlementAccountRequest(null, null, null, null, null, null, null)),
                "正常",
                null));

        assertThat(savedCaptor.getValue().getBankName()).isEmpty();
        assertThat(savedCaptor.getValue().getBankAccount()).isEmpty();
        assertThat(savedCaptor.getValue().getSettlementAccountsJson()).isEqualTo("[]");
    }

    @Test
    void shouldHandleNullSettlementAccountsAndExistingAccountDefaults() {
        var repository = mock(CompanySettingRepository.class);
        var mapper = mock(CompanySettingMapper.class);
        var savedCaptor = org.mockito.ArgumentCaptor.forClass(CompanySetting.class);
        var accountsCaptor = org.mockito.ArgumentCaptor.forClass(List.class);
        when(repository.existsByCompanyNameAndDeletedFlagFalse("公司A")).thenReturn(false);
        when(repository.findFirstByStatusAndDeletedFlagFalseOrderByIdAsc(StatusConstants.NORMAL)).thenReturn(Optional.empty());
        when(repository.findFirstByDeletedFlagFalseOrderByIdAsc()).thenReturn(Optional.empty());
        when(repository.save(savedCaptor.capture())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mapper.toResponse(any(CompanySetting.class), any(BigDecimal.class), accountsCaptor.capture()))
                .thenReturn(new CompanySettingResponse(1L, "公司A", "税号", "", "", new BigDecimal("0.1300"),
                        List.of(), "正常", null));
        var service = new CompanySettingService(repository, new SnowflakeIdGenerator(1), mapper,
                mock(DashboardSummaryService.class), null, new ObjectMapper());

        service.create(new CompanySettingRequest("公司A", "税号", null, "正常", null));

        assertThat(savedCaptor.getValue().getBankName()).isEmpty();
        assertThat(savedCaptor.getValue().getSettlementAccountsJson()).isEqualTo("[]");

        service.create(new CompanySettingRequest("公司A", "税号",
                List.of(new CompanySettlementAccountRequest(9L, "账户", "银行", "账号", null, null, " 备注 ")),
                "正常",
                null));

        assertThat(savedCaptor.getValue().getBankName()).isEqualTo("银行");
        assertThat(accountsCaptor.getValue()).last().satisfies(account -> {
            CompanySettlementAccountResponse response = (CompanySettlementAccountResponse) account;
            assertThat(response.id()).isEqualTo(9L);
            assertThat(response.usageType()).isEqualTo("通用");
            assertThat(response.status()).isEqualTo(StatusConstants.NORMAL);
            assertThat(response.remark()).isEqualTo("备注");
        });
    }

    @Test
    void shouldUseDefaultTaxRateWhenCurrentTaxRateIsZeroDuringCreate() {
        var repository = mock(CompanySettingRepository.class);
        var mapper = mock(CompanySettingMapper.class);
        var savedCaptor = org.mockito.ArgumentCaptor.forClass(CompanySetting.class);
        CompanySetting current = createCompanySetting(1L);
        current.setTaxRate(BigDecimal.ZERO);
        when(repository.existsByCompanyNameAndDeletedFlagFalse("公司D")).thenReturn(false);
        when(repository.findFirstByStatusAndDeletedFlagFalseOrderByIdAsc(StatusConstants.NORMAL))
                .thenReturn(Optional.of(current));
        when(repository.save(savedCaptor.capture())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mapper.toResponse(any(CompanySetting.class), any(BigDecimal.class), any()))
                .thenReturn(new CompanySettingResponse(2L, "公司D", "税号", "", "", new BigDecimal("0.1300"),
                        List.of(), "正常", null));
        var service = new CompanySettingService(repository, new SnowflakeIdGenerator(1), mapper,
                mock(DashboardSummaryService.class), mock(NoRuleRepository.class), new ObjectMapper());

        service.create(new CompanySettingRequest("公司D", "税号", List.of(), "正常", null));

        assertThat(savedCaptor.getValue().getTaxRate()).isEqualByComparingTo("0.1300");
    }

    @Test
    void shouldIgnoreBlankConfiguredTaxRateAndFallbackToCurrent() {
        var noRule = new NoRule();
        noRule.setSampleNo(" ");
        var noRuleRepository = mock(NoRuleRepository.class);
        var repository = mock(CompanySettingRepository.class);
        CompanySetting current = createCompanySetting(1L);
        current.setTaxRate(new BigDecimal("0.0500"));
        when(noRuleRepository.findBySettingCodeAndDeletedFlagFalse(CompanySettingService.DEFAULT_TAX_RATE_SETTING_CODE))
                .thenReturn(Optional.of(noRule));
        when(repository.findFirstByStatusAndDeletedFlagFalseOrderByIdAsc(StatusConstants.NORMAL))
                .thenReturn(Optional.of(current));
        var service = new CompanySettingService(repository, new SnowflakeIdGenerator(1),
                mock(CompanySettingMapper.class), mock(DashboardSummaryService.class), noRuleRepository,
                new ObjectMapper());

        assertThat(service.resolveCurrentTaxRate()).isEqualByComparingTo("0.0500");
    }

    @Test
    void shouldFallbackToLegacyAccountWhenAccountsJsonReadsEmptyArray() {
        var entity = createCompanySetting(1L);
        entity.setSettlementAccountsJson("[]");
        var repository = mock(CompanySettingRepository.class);
        var mapper = mock(CompanySettingMapper.class);
        when(repository.findFirstByStatusAndDeletedFlagFalseOrderByIdAsc(StatusConstants.NORMAL)).thenReturn(Optional.of(entity));
        when(mapper.toResponse(eq(entity), any(BigDecimal.class), any())).thenAnswer(invocation -> {
            List<CompanySettlementAccountResponse> accounts = invocation.getArgument(2);
            return new CompanySettingResponse(1L, "公司A", "税号", "银行", "账号",
                    invocation.getArgument(1), accounts, "正常", null);
        });
        var service = new CompanySettingService(repository, new SnowflakeIdGenerator(1), mapper,
                mock(DashboardSummaryService.class), mock(NoRuleRepository.class), new ObjectMapper());

        var response = service.current();

        assertThat(response.settlementAccounts()).singleElement().satisfies(account -> {
            assertThat(account.bankName()).isEqualTo("银行");
            assertThat(account.bankAccount()).isEqualTo("账号");
        });
    }

    @Test
    void shouldReturnEmptyLegacyAccountWhenBankNameOrAccountMissing() {
        var repository = mock(CompanySettingRepository.class);
        var mapper = mock(CompanySettingMapper.class);
        CompanySetting missingBankName = createCompanySetting(1L);
        missingBankName.setSettlementAccountsJson(null);
        missingBankName.setBankName(null);
        CompanySetting blankBankAccount = createCompanySetting(2L);
        blankBankAccount.setSettlementAccountsJson(null);
        blankBankAccount.setBankAccount(" ");
        when(mapper.toResponse(any(CompanySetting.class), any(BigDecimal.class), any())).thenAnswer(invocation -> {
            List<CompanySettlementAccountResponse> accounts = invocation.getArgument(2);
            CompanySetting company = invocation.getArgument(0);
            return new CompanySettingResponse(company.getId(), company.getCompanyName(), company.getTaxNo(),
                    company.getBankName(), company.getBankAccount(), invocation.getArgument(1), accounts,
                    company.getStatus(), null);
        });
        var service = new CompanySettingService(repository, new SnowflakeIdGenerator(1), mapper,
                mock(DashboardSummaryService.class), mock(NoRuleRepository.class), new ObjectMapper());

        when(repository.findFirstByStatusAndDeletedFlagFalseOrderByIdAsc(StatusConstants.NORMAL))
                .thenReturn(Optional.of(missingBankName), Optional.of(blankBankAccount));

        assertThat(service.current().settlementAccounts()).isEmpty();
        assertThat(service.current().settlementAccounts()).isEmpty();
    }

    @Test
    void shouldFallbackToLegacyBankAccountWhenSettlementAccountsJsonIsBlank() {
        var entity = createCompanySetting(1L);
        entity.setSettlementAccountsJson(" ");
        var repository = mock(CompanySettingRepository.class);
        var mapper = mock(CompanySettingMapper.class);
        when(repository.findFirstByStatusAndDeletedFlagFalseOrderByIdAsc(StatusConstants.NORMAL)).thenReturn(Optional.of(entity));
        when(mapper.toResponse(eq(entity), any(BigDecimal.class), any())).thenAnswer(invocation -> {
            List<CompanySettlementAccountResponse> accounts = invocation.getArgument(2);
            return new CompanySettingResponse(1L, "公司A", "税号", "银行", "账号",
                    invocation.getArgument(1), accounts, "正常", null);
        });
        var service = new CompanySettingService(repository, new SnowflakeIdGenerator(1), mapper,
                mock(DashboardSummaryService.class), mock(NoRuleRepository.class), new ObjectMapper());

        var response = service.current();

        assertThat(response.settlementAccounts()).singleElement().satisfies(account -> {
            assertThat(account.bankName()).isEqualTo("银行");
            assertThat(account.bankAccount()).isEqualTo("账号");
        });
    }

    @Test
    void shouldThrowWhenSettlementAccountsJsonIsInvalid() {
        var entity = createCompanySetting(1L);
        entity.setSettlementAccountsJson("{invalid");
        var repository = mock(CompanySettingRepository.class);
        when(repository.findFirstByStatusAndDeletedFlagFalseOrderByIdAsc(StatusConstants.NORMAL)).thenReturn(Optional.of(entity));
        var service = new CompanySettingService(repository, new SnowflakeIdGenerator(1), mock(CompanySettingMapper.class),
                mock(DashboardSummaryService.class), mock(NoRuleRepository.class), new ObjectMapper());

        assertThatThrownBy(service::current)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("公司结算信息解析失败");
    }

    @Test
    void shouldCheckReferencesBeforeDeleteWhenGuardAvailable() {
        var entity = createCompanySetting(1L);
        var repository = mock(CompanySettingRepository.class);
        var referenceGuard = mock(MasterDataReferenceGuard.class);
        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(entity));
        when(repository.save(any(CompanySetting.class))).thenAnswer(invocation -> invocation.getArgument(0));
        var service = new CompanySettingService(repository, new SnowflakeIdGenerator(1), mock(CompanySettingMapper.class),
                mock(DashboardSummaryService.class), mock(NoRuleRepository.class), new ObjectMapper(), null, referenceGuard);

        service.delete(1L);

        verify(referenceGuard).assertNoReferences(eq("该结算主体"), any());
        verify(repository).save(entity);
        assertThat(entity.isDeletedFlag()).isTrue();
    }

    @Test
    void shouldDeleteWithoutReferenceGuard() {
        var entity = createCompanySetting(1L);
        var repository = mock(CompanySettingRepository.class);
        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(entity));
        when(repository.save(any(CompanySetting.class))).thenAnswer(invocation -> invocation.getArgument(0));
        var service = new CompanySettingService(repository, new SnowflakeIdGenerator(1), mock(CompanySettingMapper.class),
                mock(DashboardSummaryService.class), mock(NoRuleRepository.class), new ObjectMapper());

        service.delete(1L);

        verify(repository).save(entity);
        assertThat(entity.isDeletedFlag()).isTrue();
    }

    @Test
    void shouldFallbackToCurrentTaxRateWhenNoRuleRepositoryIsUnavailable() {
        var repository = mock(CompanySettingRepository.class);
        var current = createCompanySetting(1L);
        current.setTaxRate(new BigDecimal("0.0700"));
        when(repository.findFirstByStatusAndDeletedFlagFalseOrderByIdAsc(StatusConstants.NORMAL))
                .thenReturn(Optional.of(current));
        var service = new CompanySettingService(repository, new SnowflakeIdGenerator(1),
                mock(CompanySettingMapper.class), mock(DashboardSummaryService.class), null, new ObjectMapper());

        assertThat(service.resolveCurrentTaxRate()).isEqualByComparingTo("0.0700");
    }

    @Test
    void shouldWrapSettlementAccountSerializationFailure() throws Exception {
        var repository = mock(CompanySettingRepository.class);
        var objectMapper = mock(ObjectMapper.class);
        when(repository.existsByCompanyNameAndDeletedFlagFalse("公司A")).thenReturn(false);
        when(repository.findFirstByStatusAndDeletedFlagFalseOrderByIdAsc(StatusConstants.NORMAL)).thenReturn(Optional.empty());
        when(repository.findFirstByDeletedFlagFalseOrderByIdAsc()).thenReturn(Optional.empty());
        when(objectMapper.writeValueAsString(any()))
                .thenThrow(new JsonProcessingException("json") { });
        var service = new CompanySettingService(repository, new SnowflakeIdGenerator(1), mock(CompanySettingMapper.class),
                mock(DashboardSummaryService.class), mock(NoRuleRepository.class), objectMapper);

        assertThatThrownBy(() -> service.create(new CompanySettingRequest("公司A", "税号",
                List.of(new CompanySettlementAccountRequest(null, "账户", "银行", "账号", "通用", "正常", null)),
                "正常",
                null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("公司结算信息序列化失败");
    }

    @Test
    void shouldReturnNullCurrentWhenCompanyDoesNotExist() {
        var repository = mock(CompanySettingRepository.class);
        when(repository.findFirstByStatusAndDeletedFlagFalseOrderByIdAsc(StatusConstants.NORMAL)).thenReturn(Optional.empty());
        when(repository.findFirstByDeletedFlagFalseOrderByIdAsc()).thenReturn(Optional.empty());
        var service = new CompanySettingService(repository, new SnowflakeIdGenerator(1), mock(CompanySettingMapper.class),
                mock(DashboardSummaryService.class), null, new ObjectMapper());

        assertThat(service.current()).isNull();
    }

    @Test
    void shouldThrowNotFoundMessageWhenDetailMissing() {
        var repository = mock(CompanySettingRepository.class);
        when(repository.findByIdAndDeletedFlagFalse(99L)).thenReturn(Optional.empty());
        var service = new CompanySettingService(repository, new SnowflakeIdGenerator(1), mock(CompanySettingMapper.class),
                mock(DashboardSummaryService.class), null, new ObjectMapper());

        assertThatThrownBy(() -> service.detail(99L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("结算主体不存在");
    }

    @Test
    void shouldUsePositiveCurrentTaxRateWhenCreatingSettlementCompany() {
        var repository = mock(CompanySettingRepository.class);
        var mapper = mock(CompanySettingMapper.class);
        var savedCaptor = org.mockito.ArgumentCaptor.forClass(CompanySetting.class);
        CompanySetting current = createCompanySetting(1L);
        current.setTaxRate(new BigDecimal("0.0500"));
        when(repository.existsByCompanyNameAndDeletedFlagFalse("公司E")).thenReturn(false);
        when(repository.findFirstByStatusAndDeletedFlagFalseOrderByIdAsc(StatusConstants.NORMAL))
                .thenReturn(Optional.of(current));
        when(repository.save(savedCaptor.capture())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mapper.toResponse(any(CompanySetting.class), any(BigDecimal.class), any()))
                .thenReturn(new CompanySettingResponse(2L, "公司E", "税号", "", "", new BigDecimal("0.0500"),
                        List.of(), "正常", null));
        var service = new CompanySettingService(repository, new SnowflakeIdGenerator(1), mapper,
                mock(DashboardSummaryService.class), mock(NoRuleRepository.class), new ObjectMapper());

        service.create(new CompanySettingRequest("公司E", "税号", List.of(), "正常", null));

        assertThat(savedCaptor.getValue().getTaxRate()).isEqualByComparingTo("0.0500");
    }

    @Test
    void shouldFallbackToCurrentTaxRateWhenResponseEntityHasNoTaxRate() {
        var responseEntity = createCompanySetting(1L);
        responseEntity.setTaxRate(null);
        var current = createCompanySetting(2L);
        current.setTaxRate(new BigDecimal("0.0800"));
        var repository = mock(CompanySettingRepository.class);
        var mapper = mock(CompanySettingMapper.class);
        when(repository.findFirstByStatusAndDeletedFlagFalseOrderByIdAsc(StatusConstants.NORMAL))
                .thenReturn(Optional.of(responseEntity), Optional.of(current));
        when(mapper.toResponse(eq(responseEntity), any(BigDecimal.class), any())).thenAnswer(invocation ->
                new CompanySettingResponse(1L, "公司A", "税号", "银行", "账号",
                        invocation.getArgument(1), invocation.getArgument(2), "正常", null));
        var service = new CompanySettingService(repository, new SnowflakeIdGenerator(1), mapper,
                mock(DashboardSummaryService.class), mock(NoRuleRepository.class), new ObjectMapper());

        var result = service.current();

        assertThat(result.taxRate()).isEqualByComparingTo("0.0800");
    }

    @Test
    void shouldAllowSavingCurrentWhenInitialIdentityWasEmpty() {
        var repository = mock(CompanySettingRepository.class);
        var mapper = mock(CompanySettingMapper.class);
        var savedCaptor = org.mockito.ArgumentCaptor.forClass(CompanySetting.class);
        CompanySetting current = createCompanySetting(1L);
        current.setCompanyName(null);
        current.setTaxNo(null);
        when(repository.findFirstByStatusAndDeletedFlagFalseOrderByIdAsc(StatusConstants.NORMAL))
                .thenReturn(Optional.of(current));
        when(repository.save(savedCaptor.capture())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mapper.toResponse(any(CompanySetting.class), any(BigDecimal.class), any())).thenAnswer(invocation -> {
            CompanySetting setting = invocation.getArgument(0);
            return new CompanySettingResponse(setting.getId(), setting.getCompanyName(), setting.getTaxNo(),
                    setting.getBankName(), setting.getBankAccount(), invocation.getArgument(1), invocation.getArgument(2),
                    setting.getStatus(), setting.getRemark());
        });
        var service = new CompanySettingService(repository, new SnowflakeIdGenerator(1), mapper,
                mock(DashboardSummaryService.class), mock(NoRuleRepository.class), new ObjectMapper());

        service.saveCurrent(new CompanySettingRequest("公司A", "税号",
                List.of(new CompanySettlementAccountRequest(null, "账户", "银行", "账号", "通用", "正常", null)),
                "正常",
                null));

        assertThat(savedCaptor.getValue().getCompanyName()).isEqualTo("公司A");
        assertThat(savedCaptor.getValue().getTaxNo()).isEqualTo("税号");
    }

    @Test
    void shouldNormalizePartiallyBlankSettlementAccounts() {
        var repository = mock(CompanySettingRepository.class);
        var mapper = mock(CompanySettingMapper.class);
        var savedCaptor = org.mockito.ArgumentCaptor.forClass(CompanySetting.class);
        var accountsCaptor = org.mockito.ArgumentCaptor.forClass(List.class);
        when(repository.existsByCompanyNameAndDeletedFlagFalse("公司F")).thenReturn(false);
        when(repository.findFirstByStatusAndDeletedFlagFalseOrderByIdAsc(StatusConstants.NORMAL)).thenReturn(Optional.empty());
        when(repository.findFirstByDeletedFlagFalseOrderByIdAsc()).thenReturn(Optional.empty());
        when(repository.save(savedCaptor.capture())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mapper.toResponse(any(CompanySetting.class), any(BigDecimal.class), accountsCaptor.capture()))
                .thenReturn(new CompanySettingResponse(1L, "公司F", "税号", "银行A", "",
                        new BigDecimal("0.1300"), List.of(), "正常", null));
        var service = new CompanySettingService(repository, new SnowflakeIdGenerator(1), mapper,
                mock(DashboardSummaryService.class), mock(NoRuleRepository.class), new ObjectMapper());

        service.create(new CompanySettingRequest("公司F", "税号",
                List.of(
                        new CompanySettlementAccountRequest(null, " ", "银行A", " ", " ", " ", " 备注A "),
                        new CompanySettlementAccountRequest(null, " ", " ", "账号B", "销售", "禁用", null),
                        new CompanySettlementAccountRequest(null, " ", " ", " ", "采购", "正常", "备注C")
                ),
                "正常",
                null));

        @SuppressWarnings("unchecked")
        List<CompanySettlementAccountResponse> accounts = accountsCaptor.getValue();
        assertThat(savedCaptor.getValue().getBankName()).isEqualTo("银行A");
        assertThat(savedCaptor.getValue().getBankAccount()).isEmpty();
        assertThat(accounts).hasSize(3);
        assertThat(accounts.get(0)).satisfies(account -> {
            assertThat(account.bankName()).isEqualTo("银行A");
            assertThat(account.bankAccount()).isEmpty();
            assertThat(account.usageType()).isEqualTo("通用");
            assertThat(account.status()).isEqualTo(StatusConstants.NORMAL);
            assertThat(account.remark()).isEqualTo("备注A");
        });
        assertThat(accounts.get(1)).satisfies(account -> {
            assertThat(account.bankAccount()).isEqualTo("账号B");
            assertThat(account.usageType()).isEqualTo("销售");
            assertThat(account.status()).isEqualTo("禁用");
            assertThat(account.remark()).isEmpty();
        });
        assertThat(accounts.get(2)).satisfies(account -> {
            assertThat(account.bankAccount()).isEmpty();
            assertThat(account.remark()).isEqualTo("备注C");
        });
    }

    @Test
    void shouldFallbackToLegacyAccountWhenSettlementAccountsJsonReadsNull() {
        var entity = createCompanySetting(1L);
        entity.setSettlementAccountsJson("null");
        var repository = mock(CompanySettingRepository.class);
        var mapper = mock(CompanySettingMapper.class);
        when(repository.findFirstByStatusAndDeletedFlagFalseOrderByIdAsc(StatusConstants.NORMAL)).thenReturn(Optional.of(entity));
        when(mapper.toResponse(eq(entity), any(BigDecimal.class), any())).thenAnswer(invocation ->
                new CompanySettingResponse(1L, "公司A", "税号", "银行", "账号",
                        invocation.getArgument(1), invocation.getArgument(2), "正常", null));
        var service = new CompanySettingService(repository, new SnowflakeIdGenerator(1), mapper,
                mock(DashboardSummaryService.class), mock(NoRuleRepository.class), new ObjectMapper());

        var response = service.current();

        assertThat(response.settlementAccounts()).singleElement().satisfies(account -> {
            assertThat(account.bankName()).isEqualTo("银行");
            assertThat(account.bankAccount()).isEqualTo("账号");
        });
    }

    @Test
    void shouldReturnEmptyLegacyAccountWhenBankNameBlankOrBankAccountMissing() {
        var repository = mock(CompanySettingRepository.class);
        var mapper = mock(CompanySettingMapper.class);
        CompanySetting blankBankName = createCompanySetting(1L);
        blankBankName.setSettlementAccountsJson(null);
        blankBankName.setBankName(" ");
        CompanySetting missingBankAccount = createCompanySetting(2L);
        missingBankAccount.setSettlementAccountsJson(null);
        missingBankAccount.setBankAccount(null);
        when(repository.findFirstByStatusAndDeletedFlagFalseOrderByIdAsc(StatusConstants.NORMAL))
                .thenReturn(Optional.of(blankBankName), Optional.of(missingBankAccount));
        when(mapper.toResponse(any(CompanySetting.class), any(BigDecimal.class), any())).thenAnswer(invocation -> {
            CompanySetting company = invocation.getArgument(0);
            return new CompanySettingResponse(company.getId(), company.getCompanyName(), company.getTaxNo(),
                    company.getBankName(), company.getBankAccount(), invocation.getArgument(1), invocation.getArgument(2),
                    company.getStatus(), null);
        });
        var service = new CompanySettingService(repository, new SnowflakeIdGenerator(1), mapper,
                mock(DashboardSummaryService.class), mock(NoRuleRepository.class), new ObjectMapper());

        assertThat(service.current().settlementAccounts()).isEmpty();
        assertThat(service.current().settlementAccounts()).isEmpty();
    }

    @Test
    void shouldReturnEmptyWhenParsingNullTaxRate() throws Exception {
        var service = new CompanySettingService(mock(CompanySettingRepository.class), new SnowflakeIdGenerator(1),
                mock(CompanySettingMapper.class), mock(DashboardSummaryService.class), null, new ObjectMapper());
        var method = CompanySettingService.class.getDeclaredMethod("parseTaxRate", String.class);
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        Optional<BigDecimal> result = (Optional<BigDecimal>) method.invoke(service, new Object[]{null});

        assertThat(result).isEmpty();
    }

    private static CompanySetting createCompanySetting(Long id) {
        var cs = new CompanySetting();
        cs.setId(id);
        cs.setCompanyName("公司A");
        cs.setTaxNo("税号");
        cs.setBankName("银行");
        cs.setBankAccount("账号");
        cs.setTaxRate(new BigDecimal("0.13"));
        cs.setSettlementAccountsJson("[]");
        cs.setStatus("正常");
        return cs;
    }
}
