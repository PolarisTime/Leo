package com.leo.erp.system.company.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.support.RedisJsonCacheSupport;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.system.company.domain.entity.CompanySetting;
import com.leo.erp.system.company.mapper.CompanySettingMapper;
import com.leo.erp.system.company.repository.CompanySettingRepository;
import com.leo.erp.system.company.web.dto.CompanySettingRequest;
import com.leo.erp.system.company.web.dto.CompanySettlementAccountRequest;
import com.leo.erp.system.dashboard.service.DashboardSummaryService;
import com.leo.erp.system.norule.repository.NoRuleRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;

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
        var service = new CompanySettingService(repository, null, null, null, null, null);

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
        var service = new CompanySettingService(repository, null, null, null, null, null);

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
        var service = new CompanySettingService(repository, null, null, null, null, null);

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
        var service = new CompanySettingService(repository, null, null, null, null, null);

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
        var service = new CompanySettingService(repository, null, null, null, null, null);

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
                CompanySettingService.CURRENT_TAX_RATE_CACHE_KEY
        ));
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
