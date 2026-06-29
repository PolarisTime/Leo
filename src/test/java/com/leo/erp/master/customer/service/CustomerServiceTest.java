package com.leo.erp.master.customer.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.MasterDataReferenceGuard;
import com.leo.erp.common.support.RedisJsonCacheSupport;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.master.customer.domain.entity.Customer;
import com.leo.erp.master.customer.mapper.CustomerMapper;
import com.leo.erp.master.customer.repository.CustomerRepository;
import com.leo.erp.master.customer.web.dto.CustomerOptionResponse;
import com.leo.erp.master.customer.web.dto.CustomerRequest;
import com.leo.erp.master.customer.web.dto.CustomerResponse;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CustomerServiceTest {

    @Test
    void shouldReturnCustomerOptionsFromRedisWhenCached() {
        CustomerRepository repository = mock(CustomerRepository.class);
        RedisJsonCacheSupport redisJsonCacheSupport = mock(RedisJsonCacheSupport.class);
        CustomerOptionResponse cached = new CustomerOptionResponse(
                1L, "客户甲 / 项目A", "客户甲", "C001", "客户甲", "项目A", "XMA"
        );
        when(redisJsonCacheSupport.getOrLoad(
                eq("leo:customer:all"),
                any(Duration.class),
                any(TypeReference.class),
                any(Supplier.class)
        )).thenReturn(List.of(cached));

        CustomerService service = new CustomerService(repository, null, mock(CustomerMapper.class), redisJsonCacheSupport);

        assertThat(service.listActiveOptions()).containsExactly(cached);
        verify(repository, never()).findByDeletedFlagFalseOrderByCustomerCodeAsc();
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
        verify(cache).delete("leo:customer:all");
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
    void shouldDelete_success() {
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

        verify(referenceGuard).assertNoReferences(eq("该客户"), any(List.class));
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

        verify(cache).delete("leo:customer:all");
    }

    @Test
    void shouldTestOptionLabel_withDifferentProjectNames() throws Exception {
        var service = new CustomerService(null, null, null);
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
                c.getProjectAddress(), c.getStatus(), c.getRemark()
        );
    }
}
