package com.leo.erp.master.customer.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.leo.erp.common.support.RedisJsonCacheSupport;
import com.leo.erp.master.customer.mapper.CustomerMapper;
import com.leo.erp.master.customer.repository.CustomerRepository;
import com.leo.erp.master.customer.web.dto.CustomerOptionResponse;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CustomerServiceTest {

    @Test
    void shouldReturnCustomerOptionsFromRedisWhenCached() {
        CustomerRepository repository = mock(CustomerRepository.class);
        RedisJsonCacheSupport redisJsonCacheSupport = mock(RedisJsonCacheSupport.class);
        CustomerOptionResponse cached = new CustomerOptionResponse(
                1L,
                "客户甲 / 项目A",
                "客户甲",
                "C001",
                "客户甲",
                "项目A",
                "XMA"
        );
        when(redisJsonCacheSupport.getOrLoad(
                eq("leo:customer:all"),
                any(Duration.class),
                any(TypeReference.class),
                any(Supplier.class)
        )).thenReturn(List.of(cached));

        CustomerService service = new CustomerService(
                repository,
                null,
                mock(CustomerMapper.class),
                redisJsonCacheSupport
        );

        assertThat(service.listActiveOptions()).containsExactly(cached);
        verify(repository, never()).findByDeletedFlagFalseOrderByCustomerCodeAsc();
    }
}
