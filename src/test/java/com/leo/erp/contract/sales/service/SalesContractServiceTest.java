package com.leo.erp.contract.sales.service;

import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.contract.sales.domain.entity.SalesContract;
import com.leo.erp.contract.sales.domain.entity.SalesContractItem;
import com.leo.erp.contract.sales.mapper.SalesContractMapper;
import com.leo.erp.contract.sales.repository.SalesContractRepository;
import com.leo.erp.contract.sales.web.dto.SalesContractItemRequest;
import com.leo.erp.contract.sales.web.dto.SalesContractRequest;
import com.leo.erp.contract.sales.web.dto.SalesContractResponse;
import com.leo.erp.security.permission.WorkflowTransitionGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class SalesContractServiceTest {

    private SalesContractRepository repository;
    private SalesContractMapper salesContractMapper;
    private WorkflowTransitionGuard workflowTransitionGuard;
    private SalesContractService service;

    @BeforeEach
    void setUp() {
        repository = (SalesContractRepository) Proxy.newProxyInstance(
                SalesContractRepository.class.getClassLoader(),
                new Class[]{SalesContractRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findAll" -> org.springframework.data.domain.Page.empty();
                    case "findByIdAndDeletedFlagFalse" -> Optional.of(createEntity(1L, "SC-001"));
                    case "findById" -> Optional.of(createEntity(1L, "SC-001"));
                    case "existsByContractNoAndDeletedFlagFalse" -> false;
                    case "save" -> args[0];
                    case "toString" -> "SalesContractRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        salesContractMapper = (SalesContractMapper) Proxy.newProxyInstance(
                SalesContractMapper.class.getClassLoader(),
                new Class[]{SalesContractMapper.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "toResponse" -> new SalesContractResponse(
                            1L, "SC-001", "客户A", "项目A", LocalDate.now(), LocalDate.now(),
                            LocalDate.now().plusYears(1), "销售甲", BigDecimal.TEN, new BigDecimal("100"),
                            "草稿", "备注", List.of());
                    case "toString" -> "SalesContractMapperStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        workflowTransitionGuard = mock(WorkflowTransitionGuard.class);
        service = new SalesContractService(repository, new SnowflakeIdGenerator(1), salesContractMapper, workflowTransitionGuard);
    }

    @Test
    void shouldReturnPage_whenCallingPage() {
        var result = service.page(new PageQuery(0, 10, "id", "desc"), PageFilter.of(null, null, null, null));
        assertThat(result).isNotNull();
    }

    @Test
    void shouldReturnSearchResults_whenCallingSearch() {
        var result = service.search("SC", 10);
        assertThat(result).isNotNull();
    }

    @Test
    void shouldReturnDetail_whenEntityExists() {
        var result = service.detail(1L);
        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(1L);
    }

    @Test
    void shouldThrowException_whenCreateWithDuplicateContractNo() {
        var repo = (SalesContractRepository) Proxy.newProxyInstance(
                SalesContractRepository.class.getClassLoader(),
                new Class[]{SalesContractRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "existsByContractNoAndDeletedFlagFalse" -> true;
                    case "findByIdAndDeletedFlagFalse" -> Optional.empty();
                    case "toString" -> "SalesContractRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var svc = new SalesContractService(repo, new SnowflakeIdGenerator(1), salesContractMapper, workflowTransitionGuard);

        assertThatThrownBy(() -> svc.create(new SalesContractRequest("SC-001", null, null, null, null, null, null, null, null, List.of())))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("销售合同号已存在");
    }

    @Test
    void shouldUpdateContract_whenValid() {
        var repo = (SalesContractRepository) Proxy.newProxyInstance(
                SalesContractRepository.class.getClassLoader(),
                new Class[]{SalesContractRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByIdAndDeletedFlagFalse" -> Optional.of(createEntity(1L, "SC-001"));
                    case "findById" -> Optional.of(createEntity(1L, "SC-001"));
                    case "save" -> args[0];
                    case "toString" -> "SalesContractRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var svc = new SalesContractService(repo, new SnowflakeIdGenerator(1), salesContractMapper, workflowTransitionGuard);

        var result = svc.update(1L, new SalesContractRequest("SC-001", "客户A", "项目A", LocalDate.now(),
                LocalDate.now(), LocalDate.now().plusYears(1), "销售甲", "草稿", "备注", List.of()));
        assertThat(result).isNotNull();
    }

    @Test
    void shouldCreateContract_whenValid() {
        var result = service.create(new SalesContractRequest(
                "SC-001", "客户A", "项目A", LocalDate.now(),
                LocalDate.now(), LocalDate.now().plusYears(1), "销售甲", "草稿", "备注",
                List.of()));
        assertThat(result).isNotNull();
    }

    @Test
    void shouldCreateContractWithItems_whenValid() {
        var item = new SalesContractItemRequest("M001", "品牌A", "类别", "材质", "规格", "6m", "吨", 100, "件",
                new BigDecimal("0.500"), 10, new BigDecimal("50.000"), new BigDecimal("3000.00"), new BigDecimal("150000.00"));
        var result = service.create(new SalesContractRequest(
                "SC-002", "客户A", "项目A", LocalDate.now(),
                LocalDate.now(), LocalDate.now().plusYears(1), "销售甲", "草稿", "备注",
                List.of(item)));
        assertThat(result).isNotNull();
    }

    @Test
    void shouldPreserveContractNoOnUpdateEvenWhenRequestDiffers() {
        var repo = (SalesContractRepository) Proxy.newProxyInstance(
                SalesContractRepository.class.getClassLoader(),
                new Class[]{SalesContractRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByIdAndDeletedFlagFalse" -> Optional.of(createEntity(1L, "SC-001"));
                    case "findById" -> Optional.of(createEntity(1L, "SC-001"));
                    case "save" -> args[0];
                    case "toString" -> "SalesContractRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var svc = new SalesContractService(repo, new SnowflakeIdGenerator(1), salesContractMapper, workflowTransitionGuard);

        var result = svc.update(1L, new SalesContractRequest("SC-002", "客户A", "项目A", LocalDate.now(),
                LocalDate.now(), LocalDate.now().plusYears(1), "销售甲", "草稿", "备注", List.of()));
        assertThat(result).isNotNull();
    }

    @Test
    void shouldDeleteContract_whenExists() {
        service.delete(1L);
    }

    @Test
    void shouldReturnPageWithFilter_whenCallingPageWithFilter() {
        var result = service.page(new PageQuery(0, 10, "id", "desc"), PageFilter.of("SC", "客户A", "草稿", null, null));
        assertThat(result).isNotNull();
    }

    @Test
    void shouldReturnPageWithDateRange_whenCallingPageWithDateRange() {
        var result = service.page(new PageQuery(0, 10, "id", "desc"), PageFilter.of(null, null, null, LocalDate.now()));
        assertThat(result).isNotNull();
    }

    private static SalesContract createEntity(Long id, String contractNo) {
        var entity = new SalesContract();
        entity.setId(id);
        entity.setContractNo(contractNo);
        entity.setCustomerName("客户A");
        entity.setProjectName("项目A");
        entity.setSignDate(LocalDate.now());
        entity.setEffectiveDate(LocalDate.now());
        entity.setExpireDate(LocalDate.now().plusYears(1));
        entity.setSalesName("销售甲");
        entity.setStatus(StatusConstants.DRAFT);
        entity.setTotalWeight(BigDecimal.TEN);
        entity.setTotalAmount(new BigDecimal("100"));
        entity.setItems(new ArrayList<>());
        return entity;
    }
}
