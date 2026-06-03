package com.leo.erp.system.company.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.support.RedisJsonCacheSupport;
import com.leo.erp.common.support.SnowflakeIdGenerator;
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
    void shouldThrowException_whenCreatingDirectly() {
        var service = new CompanySettingService(null, null, null, null, null, null);

        assertThatThrownBy(() -> service.create(new CompanySettingRequest(
                null, null, List.of(), null, null
        ))).isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅允许通过首次初始化页面创建");
    }

    @Test
    void shouldThrowException_whenSaveCurrentWithoutEntity() {
        var repository = (CompanySettingRepository) Proxy.newProxyInstance(
                CompanySettingRepository.class.getClassLoader(),
                new Class[]{CompanySettingRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
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
                .hasMessageContaining("请先通过首次初始化页面");
    }

    @Test
    void shouldThrowException_whenCompanyNameChanged() {
        var repository = (CompanySettingRepository) Proxy.newProxyInstance(
                CompanySettingRepository.class.getClassLoader(),
                new Class[]{CompanySettingRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
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
