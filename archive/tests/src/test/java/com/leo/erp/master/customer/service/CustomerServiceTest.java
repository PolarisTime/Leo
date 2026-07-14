package com.leo.erp.master.customer.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.config.CacheConfig;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.MasterDataReferenceGuard;
import com.leo.erp.common.support.MasterDataReferenceGuard.ReferenceCheck;
import com.leo.erp.common.support.RedisJsonCacheSupport;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.master.customer.domain.entity.Customer;
import com.leo.erp.master.customer.mapper.CustomerMapper;
import com.leo.erp.master.customer.repository.CustomerRepository;
import com.leo.erp.master.customer.web.dto.CustomerOptionResponse;
import com.leo.erp.master.customer.web.dto.CustomerRequest;
import com.leo.erp.master.customer.web.dto.CustomerResponse;
import com.leo.erp.system.company.domain.entity.CompanySetting;
import com.leo.erp.system.company.service.CompanySettingService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CustomerServiceTest {

    @Test
    void shouldLoadCustomerOptionsThroughSpringCachePath() {
        CustomerRepository repository = mock(CustomerRepository.class);
        RedisJsonCacheSupport redisJsonCacheSupport = mock(RedisJsonCacheSupport.class);
        Customer customer = createCustomer(1L, "C001");
        when(repository.findByDeletedFlagFalseAndStatusOrderByCustomerCodeAsc(StatusConstants.NORMAL))
                .thenReturn(List.of(customer));

        CustomerService service = new CustomerService(repository, new SnowflakeIdGenerator(1),
                mock(CustomerMapper.class), redisJsonCacheSupport);

        List<CustomerOptionResponse> result = service.listActiveOptions();

        assertThat(result).singleElement().satisfies(option -> {
            assertThat(option.customerCode()).isEqualTo("C001");
            assertThat(option.label()).isEqualTo("客户甲 / 项目A");
        });
    }

    @Test
    void shouldLoadOptionsDirectly_whenRedisIsNull() {
        var repository = (CustomerRepository) Proxy.newProxyInstance(
                CustomerRepository.class.getClassLoader(),
                new Class[]{CustomerRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByDeletedFlagFalseAndStatusOrderByCustomerCodeAsc" -> List.of(createCustomer(1L, "C001"));
                    case "toString" -> "CustomerRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var service = new CustomerService(repository, new SnowflakeIdGenerator(1), null);

        var result = service.listActiveOptions();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).customerCode()).isEqualTo("C001");
    }

    @Test
    void shouldRefreshCache_whenCachedOptionsAreEmptyButActiveCustomersExist() {
        CustomerRepository repository = mock(CustomerRepository.class);
        Customer customer = createCustomer(1L, "C001");
        when(repository.findByDeletedFlagFalseAndStatusOrderByCustomerCodeAsc(StatusConstants.NORMAL))
                .thenReturn(List.of(customer));
        RedisJsonCacheSupport redisJsonCacheSupport = mock(RedisJsonCacheSupport.class);

        CustomerService service = new CustomerService(repository, new SnowflakeIdGenerator(1),
                mock(CustomerMapper.class), redisJsonCacheSupport);

        List<CustomerOptionResponse> result = service.listActiveOptions();

        assertThat(result).hasSize(1);
        verify(redisJsonCacheSupport, never()).delete(anyString());
        verify(redisJsonCacheSupport, never()).write(eq("leo:customer:all"), eq(result), any(Duration.class));
    }

    @Test
    void shouldReturnCachedEmptyOptions_whenCacheAndDatabaseAreEmpty() {
        CustomerRepository repository = mock(CustomerRepository.class);
        when(repository.findByDeletedFlagFalseAndStatusOrderByCustomerCodeAsc(StatusConstants.NORMAL))
                .thenReturn(List.of());
        RedisJsonCacheSupport redisJsonCacheSupport = mock(RedisJsonCacheSupport.class);

        CustomerService service = new CustomerService(repository, new SnowflakeIdGenerator(1),
                mock(CustomerMapper.class), redisJsonCacheSupport);

        List<CustomerOptionResponse> result = service.listActiveOptions();

        assertThat(result).isEmpty();
        verify(redisJsonCacheSupport, never()).write(anyString(), any(), any(Duration.class));
    }

    @Test
    void shouldReturnCacheName() {
        CustomerService service = new CustomerService(mock(CustomerRepository.class), new SnowflakeIdGenerator(1),
                mock(CustomerMapper.class));

        assertThat(service.cacheName()).isEqualTo("leo:customer:all");
    }

    @Test
    void shouldRefreshCustomerCacheDuringHealthCheck_whenCachedSizeDiffersFromDatabase() {
        CustomerRepository repository = mock(CustomerRepository.class);
        Customer customer = createCustomer(1L, "C001");
        when(repository.findByDeletedFlagFalseAndStatusOrderByCustomerCodeAsc(StatusConstants.NORMAL))
                .thenReturn(List.of(customer));
        RedisJsonCacheSupport redisJsonCacheSupport = mock(RedisJsonCacheSupport.class);

        CustomerService service = new CustomerService(repository, new SnowflakeIdGenerator(1),
                mock(CustomerMapper.class), redisJsonCacheSupport);

        var result = service.verifyAndRefreshCache();

        assertThat(result.cacheName()).isEqualTo("leo:customer:all");
        assertThat(result.currentSize()).isZero();
        assertThat(result.refreshedSize()).isEqualTo(1);
        assertThat(result.refreshed()).isTrue();
        verify(redisJsonCacheSupport).write(eq("leo:customer:all"), any(), any(Duration.class));
    }

    @Test
    void shouldRefreshActualSpringCustomerCacheDuringHealthCheck() {
        CustomerRepository repository = mock(CustomerRepository.class);
        when(repository.findByDeletedFlagFalseAndStatusOrderByCustomerCodeAsc(StatusConstants.NORMAL))
                .thenReturn(List.of(createCustomer(1L, "C001")));
        RedisJsonCacheSupport legacyCache = mock(RedisJsonCacheSupport.class);
        CustomerService service = new CustomerService(repository, new SnowflakeIdGenerator(1),
                mock(CustomerMapper.class), legacyCache);
        var cacheManager = new ConcurrentMapCacheManager(CacheConfig.CACHE_OPTIONS);
        cacheManager.getCache(CacheConfig.CACHE_OPTIONS).put("leo:customer:all", List.of("stale"));
        service.setCacheManager(cacheManager);

        var result = service.verifyAndRefreshCache();

        assertThat(result.cacheName()).isEqualTo("options::leo:customer:all");
        assertThat(result.refreshed()).isTrue();
        assertThat(cacheManager.getCache(CacheConfig.CACHE_OPTIONS).get("leo:customer:all", List.class))
                .singleElement()
                .isInstanceOf(CustomerOptionResponse.class);
        verify(legacyCache, never()).write(anyString(), any(), any());
    }

    @Test
    void shouldRefreshCustomerCacheDuringHealthCheck_whenDatabaseContentChanged() {
        CustomerRepository repository = mock(CustomerRepository.class);
        Customer customer = createCustomer(1L, "C001");
        customer.setCustomerName("客户新名称");
        when(repository.findByDeletedFlagFalseAndStatusOrderByCustomerCodeAsc(StatusConstants.NORMAL))
                .thenReturn(List.of(customer));
        RedisJsonCacheSupport redisJsonCacheSupport = mock(RedisJsonCacheSupport.class);
        CustomerOptionResponse cached = new CustomerOptionResponse(
                1L, "客户旧名称 / 项目A", "客户旧名称", "C001", "客户旧名称", "项目A", "XMA"
        );
        when(redisJsonCacheSupport.read(
                eq("leo:customer:all"),
                any(TypeReference.class)
        )).thenReturn(Optional.of(List.of(cached)));

        CustomerService service = new CustomerService(repository, new SnowflakeIdGenerator(1),
                mock(CustomerMapper.class), redisJsonCacheSupport);

        var result = service.verifyAndRefreshCache();

        assertThat(result.currentSize()).isEqualTo(1);
        assertThat(result.refreshedSize()).isEqualTo(1);
        assertThat(result.refreshed()).isTrue();
        verify(redisJsonCacheSupport).write(eq("leo:customer:all"), any(), any(Duration.class));
    }

    @Test
    void shouldDeleteStaleCustomerCacheDuringHealthCheck_whenDatabaseHasNoActiveCustomers() {
        CustomerRepository repository = mock(CustomerRepository.class);
        when(repository.findByDeletedFlagFalseAndStatusOrderByCustomerCodeAsc(StatusConstants.NORMAL))
                .thenReturn(List.of());
        RedisJsonCacheSupport redisJsonCacheSupport = mock(RedisJsonCacheSupport.class);
        CustomerOptionResponse cached = new CustomerOptionResponse(
                1L, "旧客户 / 项目A", "旧客户", "C001", "旧客户", "项目A", "XMA"
        );
        when(redisJsonCacheSupport.read(
                eq("leo:customer:all"),
                any(TypeReference.class)
        )).thenReturn(Optional.of(List.of(cached)));

        CustomerService service = new CustomerService(repository, new SnowflakeIdGenerator(1),
                mock(CustomerMapper.class), redisJsonCacheSupport);

        var result = service.verifyAndRefreshCache();

        assertThat(result.currentSize()).isEqualTo(1);
        assertThat(result.refreshedSize()).isZero();
        assertThat(result.refreshed()).isTrue();
        verify(redisJsonCacheSupport).delete("leo:customer:all");
        verify(redisJsonCacheSupport, never()).write(any(), any(), any(Duration.class));
    }

    @Test
    void shouldReturnPage_whenCallingPage() {
        var repository = (CustomerRepository) Proxy.newProxyInstance(
                CustomerRepository.class.getClassLoader(),
                new Class[]{CustomerRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findAll" -> org.springframework.data.domain.Page.empty();
                    case "toString" -> "CustomerRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var service = new CustomerService(repository, new SnowflakeIdGenerator(1), null);

        var result = service.page(new PageQuery(0, 10, "id", "desc"), null, null);

        assertThat(result).isNotNull();
    }

    @Test
    void shouldThrowException_whenCreateWithDuplicateCode() {
        var repository = (CustomerRepository) Proxy.newProxyInstance(
                CustomerRepository.class.getClassLoader(),
                new Class[]{CustomerRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "existsByCustomerCodeAndDeletedFlagFalse" -> true;
                    case "toString" -> "CustomerRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var service = new CustomerService(repository, new SnowflakeIdGenerator(1), null);

        assertThatThrownBy(() -> service.create(new CustomerRequest("C001", "客户甲", null, null, null, null, "项目A", null, null, 1L, "正常", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("客户编码已存在");
    }

    @Test
    void shouldThrowException_whenUpdateWithChangedDuplicateCode() {
        var repository = (CustomerRepository) Proxy.newProxyInstance(
                CustomerRepository.class.getClassLoader(),
                new Class[]{CustomerRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByIdAndDeletedFlagFalse" -> Optional.of(createCustomer(1L, "C001"));
                    case "existsByCustomerCodeAndDeletedFlagFalse" -> true;
                    case "toString" -> "CustomerRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var service = new CustomerService(repository, new SnowflakeIdGenerator(1), null);

        assertThatThrownBy(() -> service.update(1L, new CustomerRequest("C002", "客户乙", null, null, null, null, "项目B", null, null, 1L, "正常", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("客户编码已存在");
    }

    @Test
    void shouldReturnDetail_whenEntityExists() {
        var repository = (CustomerRepository) Proxy.newProxyInstance(
                CustomerRepository.class.getClassLoader(),
                new Class[]{CustomerRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByIdAndDeletedFlagFalse" -> Optional.of(createCustomer(1L, "C001"));
                    case "findById" -> Optional.of(createCustomer(1L, "C001"));
                    case "toString" -> "CustomerRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var mapper = (CustomerMapper) Proxy.newProxyInstance(
                CustomerMapper.class.getClassLoader(),
                new Class[]{CustomerMapper.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "toResponse" -> new CustomerResponse(1L, "C001", "客户甲", null, null, null, null, "项目A", null, null, "正常", null);
                    case "toString" -> "CustomerMapperStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var service = new CustomerService(repository, new SnowflakeIdGenerator(1), mapper);

        var result = service.detail(1L);

        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(1L);
    }

    @Test
    void shouldEvictCache_whenSavingWithRedis() {
        var repository = (CustomerRepository) Proxy.newProxyInstance(
                CustomerRepository.class.getClassLoader(),
                new Class[]{CustomerRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByIdAndDeletedFlagFalse" -> Optional.of(createCustomer(1L, "C001"));
                    case "save" -> args[0];
                    case "existsByCustomerCodeAndDeletedFlagFalse" -> false;
                    case "toString" -> "CustomerRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var cache = mock(RedisJsonCacheSupport.class);
        var mapper = (CustomerMapper) Proxy.newProxyInstance(
                CustomerMapper.class.getClassLoader(),
                new Class[]{CustomerMapper.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "toResponse" -> new CustomerResponse(1L, null, null, null, null, null, null, null, null, null, null, null);
                    case "toString" -> "CustomerMapperStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var service = new CustomerService(repository, new SnowflakeIdGenerator(1), mapper, cache);

        var request = new CustomerRequest("C001", "客户甲", null, null, null, null, "项目A", null, null, 1L, "正常", null);
        var result = service.update(1L, request);

        assertThat(result).isNotNull();
        verify(cache, never()).deleteAfterCommit("leo:customer:all");
    }

    @Test
    void shouldCreate_success() {
        var repository = (CustomerRepository) Proxy.newProxyInstance(
                CustomerRepository.class.getClassLoader(),
                new Class[]{CustomerRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "existsByCustomerCodeAndDeletedFlagFalse" -> false;
                    case "save" -> args[0];
                    case "toString" -> "CustomerRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var mapper = (CustomerMapper) Proxy.newProxyInstance(
                CustomerMapper.class.getClassLoader(),
                new Class[]{CustomerMapper.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "toResponse" -> toResponse((Customer) args[0]);
                    case "toString" -> "CustomerMapperStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var service = new CustomerService(repository, new SnowflakeIdGenerator(1), mapper);

        var request = new CustomerRequest("C001", "客户甲", "张三", "13800138000", "北京", "月结", "项目A", "项A", "地址", 1L, "正常", "备注");
        var result = service.create(request);

        assertThat(result).isNotNull();
        assertThat(result.customerCode()).isEqualTo("C001");
    }

    @Test
    void shouldUpdate_successWithSameCode() {
        var repository = (CustomerRepository) Proxy.newProxyInstance(
                CustomerRepository.class.getClassLoader(),
                new Class[]{CustomerRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByIdAndDeletedFlagFalse" -> Optional.of(createCustomer(1L, "C001"));
                    case "save" -> args[0];
                    case "toString" -> "CustomerRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var mapper = (CustomerMapper) Proxy.newProxyInstance(
                CustomerMapper.class.getClassLoader(),
                new Class[]{CustomerMapper.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "toResponse" -> toResponse((Customer) args[0]);
                    case "toString" -> "CustomerMapperStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var service = new CustomerService(repository, new SnowflakeIdGenerator(1), mapper);

        var request = new CustomerRequest("C001", "客户乙", null, null, null, null, "项目B", null, null, 1L, "正常", null);
        var result = service.update(1L, request);

        assertThat(result).isNotNull();
        assertThat(result.customerName()).isEqualTo("客户乙");
    }

    @Test
    void shouldUpdate_successWithDifferentCode() {
        var repository = (CustomerRepository) Proxy.newProxyInstance(
                CustomerRepository.class.getClassLoader(),
                new Class[]{CustomerRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByIdAndDeletedFlagFalse" -> Optional.of(createCustomer(1L, "C001"));
                    case "existsByCustomerCodeAndDeletedFlagFalse" -> false;
                    case "save" -> args[0];
                    case "toString" -> "CustomerRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var mapper = (CustomerMapper) Proxy.newProxyInstance(
                CustomerMapper.class.getClassLoader(),
                new Class[]{CustomerMapper.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "toResponse" -> toResponse((Customer) args[0]);
                    case "toString" -> "CustomerMapperStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var service = new CustomerService(repository, new SnowflakeIdGenerator(1), mapper);

        var request = new CustomerRequest("C002", "客户乙", null, null, null, null, "项目B", null, null, 1L, "正常", null);
        var result = service.update(1L, request);

        assertThat(result).isNotNull();
    }

    @Test
    void shouldThrowException_whenDetailNotFound() {
        var repository = (CustomerRepository) Proxy.newProxyInstance(
                CustomerRepository.class.getClassLoader(),
                new Class[]{CustomerRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByIdAndDeletedFlagFalse" -> Optional.empty();
                    case "findById" -> Optional.empty();
                    case "toString" -> "CustomerRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var service = new CustomerService(repository, new SnowflakeIdGenerator(1), null);

        assertThatThrownBy(() -> service.detail(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("客户不存在");
    }

    @Test
    void shouldCheckStableCustomerIdentityBeforeDelete() {
        var repository = (CustomerRepository) Proxy.newProxyInstance(
                CustomerRepository.class.getClassLoader(),
                new Class[]{CustomerRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByIdAndDeletedFlagFalse" -> Optional.of(createCustomer(1L, "C001"));
                    case "findById" -> Optional.of(createCustomer(1L, "C001"));
                    case "save" -> args[0];
                    case "toString" -> "CustomerRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var referenceGuard = mock(MasterDataReferenceGuard.class);
        var service = new CustomerService(repository, new SnowflakeIdGenerator(1), null, null, referenceGuard);

        service.delete(1L);

        ArgumentCaptor<List<ReferenceCheck>> captor = ArgumentCaptor.forClass(List.class);
        verify(referenceGuard).assertNoReferences(eq("该客户"), captor.capture());
        assertThat(captor.getValue())
                .extracting(ReferenceCheck::tableName, ReferenceCheck::columnName, ReferenceCheck::value)
                .containsExactly(
                        tuple("md_project", "customer_id", 1L),
                        tuple("so_sales_order", "customer_id", 1L),
                        tuple("ct_sales_contract", "customer_id", 1L),
                        tuple("so_sales_outbound", "customer_id", 1L),
                        tuple("fm_invoice_issue", "customer_id", 1L),
                        tuple("st_customer_statement", "customer_id", 1L),
                        tuple("st_customer_statement_item", "customer_id", 1L),
                        tuple("fm_receipt", "customer_id", 1L),
                        tuple("lg_freight_bill_item", "customer_id", 1L),
                        tuple("st_freight_statement_item", "customer_id", 1L),
                        tuple("fm_ledger_adjustment", "counterparty_id", 1L)
                );
        assertThat(captor.getValue().getLast()).satisfies(check -> {
            assertThat(check.extraCondition()).isEqualTo("counterparty_type = ?");
            assertThat(check.extraArguments()).containsExactly("客户");
        });
    }

    @Test
    void shouldDeleteWithoutReferenceGuard() {
        var repository = (CustomerRepository) Proxy.newProxyInstance(
                CustomerRepository.class.getClassLoader(),
                new Class[]{CustomerRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByIdAndDeletedFlagFalse" -> Optional.of(createCustomer(1L, "C001"));
                    case "findById" -> Optional.of(createCustomer(1L, "C001"));
                    case "save" -> args[0];
                    case "toString" -> "CustomerRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var service = new CustomerService(repository, new SnowflakeIdGenerator(1), null);

        service.delete(1L);
    }

    @Test
    void shouldThrowException_whenDeleteWithReferences() {
        var repository = (CustomerRepository) Proxy.newProxyInstance(
                CustomerRepository.class.getClassLoader(),
                new Class[]{CustomerRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByIdAndDeletedFlagFalse" -> Optional.of(createCustomer(1L, "C001"));
                    case "findById" -> Optional.of(createCustomer(1L, "C001"));
                    case "toString" -> "CustomerRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var referenceGuard = mock(MasterDataReferenceGuard.class);
        doThrow(new BusinessException(ErrorCode.BUSINESS_ERROR, "该客户已被业务或主数据引用"))
                .when(referenceGuard).assertNoReferences(eq("该客户"), any(List.class));
        var service = new CustomerService(repository, new SnowflakeIdGenerator(1), null, null, referenceGuard);

        assertThatThrownBy(() -> service.delete(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("该客户已被业务或主数据引用");
    }

    @Test
    void shouldEvictCache_whenCreatingWithRedis() {
        var repository = (CustomerRepository) Proxy.newProxyInstance(
                CustomerRepository.class.getClassLoader(),
                new Class[]{CustomerRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "existsByCustomerCodeAndDeletedFlagFalse" -> false;
                    case "save" -> args[0];
                    case "toString" -> "CustomerRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var cache = mock(RedisJsonCacheSupport.class);
        var mapper = (CustomerMapper) Proxy.newProxyInstance(
                CustomerMapper.class.getClassLoader(),
                new Class[]{CustomerMapper.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "toResponse" -> new CustomerResponse(1L, null, null, null, null, null, null, null, null, null, null, null);
                    case "toString" -> "CustomerMapperStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var service = new CustomerService(repository, new SnowflakeIdGenerator(1), mapper, cache);

        var request = new CustomerRequest("C001", "客户甲", null, null, null, null, "项目A", null, null, 1L, "正常", null);
        service.create(request);

        verify(cache, never()).deleteAfterCommit("leo:customer:all");
    }

    @Test
    void shouldResolveSettlementCompany_whenCreatingWithCompanyService() {
        var repository = (CustomerRepository) Proxy.newProxyInstance(
                CustomerRepository.class.getClassLoader(),
                new Class[]{CustomerRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "existsByCustomerCodeAndDeletedFlagFalse" -> false;
                    case "save" -> args[0];
                    case "toString" -> "CustomerRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var mapper = (CustomerMapper) Proxy.newProxyInstance(
                CustomerMapper.class.getClassLoader(),
                new Class[]{CustomerMapper.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "toResponse" -> toResponse((Customer) args[0]);
                    case "toString" -> "CustomerMapperStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var company = new CompanySetting();
        company.setId(99L);
        company.setCompanyName("默认结算主体");
        var companySettingService = mock(CompanySettingService.class);
        when(companySettingService.requireActiveSettlementCompany(99L)).thenReturn(company);
        var service = new CustomerService(repository, new SnowflakeIdGenerator(1), mapper,
                null, null, companySettingService);

        var request = new CustomerRequest("C001", "客户甲", null, null, null, null,
                "项目A", null, null, 99L, "正常", null);
        CustomerResponse response = service.create(request);

        assertThat(response.defaultSettlementCompanyId()).isEqualTo(99L);
        assertThat(response.defaultSettlementCompanyName()).isEqualTo("默认结算主体");
    }

    @Test
    void shouldTestOptionLabel_withDifferentProjectNames() throws Exception {
        var service = new CustomerService(null, new SnowflakeIdGenerator(1), null);
        Method optionLabel = CustomerService.class.getDeclaredMethod("optionLabel", Customer.class);
        optionLabel.setAccessible(true);

        Customer c1 = new Customer();
        c1.setCustomerName("客户甲");
        c1.setProjectName("项目A");
        assertThat(optionLabel.invoke(service, c1)).isEqualTo("客户甲 / 项目A");

        Customer c2 = new Customer();
        c2.setCustomerName("客户甲");
        c2.setProjectName(null);
        assertThat(optionLabel.invoke(service, c2)).isEqualTo("客户甲");

        Customer c3 = new Customer();
        c3.setCustomerName("客户甲");
        c3.setProjectName("  ");
        assertThat(optionLabel.invoke(service, c3)).isEqualTo("客户甲");

        Customer c4 = new Customer();
        c4.setCustomerName("客户甲");
        c4.setProjectName("客户甲");
        assertThat(optionLabel.invoke(service, c4)).isEqualTo("客户甲");
    }

    @Test
    void shouldDeclareSpringCacheAnnotationsForOptions() throws Exception {
        Method readMethod = CustomerService.class.getDeclaredMethod("listActiveOptions");
        Cacheable cacheable = readMethod.getAnnotation(Cacheable.class);
        assertThat(cacheable.value()).containsExactly(CacheConfig.CACHE_OPTIONS);
        assertThat(cacheable.key()).isEqualTo("'leo:customer:all'");

        Method createMethod = CustomerService.class.getDeclaredMethod("create", CustomerRequest.class);
        Method updateMethod = CustomerService.class.getDeclaredMethod("update", Long.class, CustomerRequest.class);
        Method updateStatusMethod = CustomerService.class.getDeclaredMethod("updateStatus", Long.class, String.class);
        Method deleteMethod = CustomerService.class.getDeclaredMethod("delete", Long.class);

        assertThat(createMethod.getAnnotation(CacheEvict.class).value()).containsExactly(CacheConfig.CACHE_OPTIONS);
        assertThat(createMethod.getAnnotation(CacheEvict.class).key()).isEqualTo("'leo:customer:all'");
        assertThat(updateMethod.getAnnotation(CacheEvict.class).key()).isEqualTo("'leo:customer:all'");
        assertThat(updateStatusMethod.getAnnotation(CacheEvict.class).key()).isEqualTo("'leo:customer:all'");
        assertThat(deleteMethod.getAnnotation(CacheEvict.class).key()).isEqualTo("'leo:customer:all'");
    }

    private static Customer createCustomer(Long id, String code) {
        Customer c = new Customer();
        c.setId(id);
        c.setCustomerCode(code);
        c.setCustomerName("客户甲");
        c.setProjectName("项目A");
        c.setStatus(StatusConstants.NORMAL);
        return c;
    }

    private static CustomerResponse toResponse(Customer c) {
        return new CustomerResponse(
                c.getId(), c.getCustomerCode(), c.getCustomerName(),
                c.getContactName(), c.getContactPhone(), c.getCity(),
                c.getSettlementMode(), c.getProjectName(), c.getProjectNameAbbr(),
                c.getProjectAddress(), c.getDefaultSettlementCompanyId(),
                c.getDefaultSettlementCompanyName(), c.getStatus(), c.getRemark()
        );
    }
}
