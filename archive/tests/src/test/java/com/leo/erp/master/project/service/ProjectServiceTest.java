package com.leo.erp.master.project.service;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.MasterDataReferenceGuard;
import com.leo.erp.common.support.MasterDataReferenceGuard.ReferenceCheck;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.master.customer.domain.entity.Customer;
import com.leo.erp.master.customer.repository.CustomerRepository;
import com.leo.erp.master.project.domain.entity.Project;
import com.leo.erp.master.project.mapper.ProjectMapper;
import com.leo.erp.master.project.repository.ProjectRepository;
import com.leo.erp.master.project.web.dto.ProjectRequest;
import com.leo.erp.master.project.web.dto.ProjectOptionResponse;
import com.leo.erp.master.project.web.dto.ProjectResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectServiceTest {

    @Test
    void shouldListActiveProjectOptionsFilteredByCustomerIdentity() {
        ProjectRepository projectRepository = mock(ProjectRepository.class);
        CustomerRepository customerRepository = mock(CustomerRepository.class);
        Customer customer = new Customer();
        customer.setId(10L);
        customer.setCustomerCode("C001");
        Project project = new Project();
        project.setId(20L);
        project.setProjectCode("P001");
        project.setProjectName("项目A");
        project.setProjectNameAbbr("项A");
        project.setCustomerId(null);
        project.setCustomerCode("C001");
        when(customerRepository.findByIdAndDeletedFlagFalse(10L)).thenReturn(Optional.of(customer));
        when(projectRepository.findActiveOptionsByCustomerIdentity(
                10L, "C001", StatusConstants.NORMAL)).thenReturn(java.util.List.of(project));
        var service = new ProjectService(
                new SnowflakeIdGenerator(1), projectRepository, null, null, customerRepository);

        java.util.List<ProjectOptionResponse> result = service.listActiveOptions(10L);

        assertThat(result).singleElement().satisfies(option -> {
            assertThat(option.id()).isEqualTo(20L);
            assertThat(option.value()).isEqualTo(20L);
            assertThat(option.label()).isEqualTo("P001 / 项目A");
            assertThat(option.customerId()).isEqualTo(10L);
            assertThat(option.customerCode()).isEqualTo("C001");
            assertThat(option.projectCode()).isEqualTo("P001");
            assertThat(option.projectName()).isEqualTo("项目A");
        });
    }

    @Test
    void shouldPersistCustomerIdAndAuthoritativeCustomerCode() {
        var repository = (ProjectRepository) Proxy.newProxyInstance(
                ProjectRepository.class.getClassLoader(),
                new Class[]{ProjectRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "existsByProjectCodeAndDeletedFlagFalse" -> false;
                    case "save" -> args[0];
                    case "toString" -> "ProjectRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var mapper = (ProjectMapper) Proxy.newProxyInstance(
                ProjectMapper.class.getClassLoader(),
                new Class[]{ProjectMapper.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "toResponse" -> toResponse((Project) args[0]);
                    case "toString" -> "ProjectMapperStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        CustomerRepository customerRepository = mock(CustomerRepository.class);
        Customer customer = new Customer();
        customer.setId(10L);
        customer.setCustomerCode("C001");
        when(customerRepository.findByIdAndDeletedFlagFalse(10L)).thenReturn(Optional.of(customer));
        var service = new ProjectService(
                new SnowflakeIdGenerator(1), repository, mapper, null, customerRepository);
        var request = new ProjectRequest(
                "P001", "项目A", "项A", "地址", "张三", 10L, "C001", "正常", "备注");

        ProjectResponse result = service.create(request);

        assertThat(result.customerId()).isEqualTo(10L);
        assertThat(result.customerCode()).isEqualTo("C001");
    }

    @Test
    void shouldRejectMismatchedCustomerIdAndCode() {
        ProjectRepository projectRepository = mock(ProjectRepository.class);
        when(projectRepository.save(any(Project.class))).thenAnswer(invocation -> invocation.getArgument(0));
        ProjectMapper projectMapper = mock(ProjectMapper.class);
        when(projectMapper.toResponse(any(Project.class)))
                .thenAnswer(invocation -> toResponse(invocation.getArgument(0)));
        CustomerRepository customerRepository = mock(CustomerRepository.class);
        Customer customer = new Customer();
        customer.setId(10L);
        customer.setCustomerCode("C001");
        when(customerRepository.findByIdAndDeletedFlagFalse(10L)).thenReturn(Optional.of(customer));
        var service = new ProjectService(
                new SnowflakeIdGenerator(1), projectRepository, projectMapper, null, customerRepository);
        var request = new ProjectRequest(
                "P001", "项目A", "项A", "地址", "张三", 10L, "C999", "正常", "备注");

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("客户ID与客户编码不一致");
    }

    @Test
    void shouldResolveLegacyCustomerCodeToCustomerId() {
        ProjectRepository projectRepository = mock(ProjectRepository.class);
        when(projectRepository.save(any(Project.class))).thenAnswer(invocation -> invocation.getArgument(0));
        ProjectMapper projectMapper = mock(ProjectMapper.class);
        when(projectMapper.toResponse(any(Project.class)))
                .thenAnswer(invocation -> toResponse(invocation.getArgument(0)));
        CustomerRepository customerRepository = mock(CustomerRepository.class);
        Customer customer = new Customer();
        customer.setId(10L);
        customer.setCustomerCode("C001");
        when(customerRepository.findByCustomerCodeAndDeletedFlagFalse("C001"))
                .thenReturn(Optional.of(customer));
        var service = new ProjectService(
                new SnowflakeIdGenerator(1), projectRepository, projectMapper, null, customerRepository);
        var request = new ProjectRequest(
                "P001", "项目A", "项A", "地址", "张三", "C001", "正常", "备注");

        ch.qos.logback.classic.Logger logger =
                (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(ProjectService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        ProjectResponse result;
        try {
            result = service.create(request);
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }

        assertThat(result.customerId()).isEqualTo(10L);
        assertThat(result.customerCode()).isEqualTo("C001");
        assertThat(appender.list)
                .extracting(ILoggingEvent::getFormattedMessage)
                .anyMatch(message -> message.contains("field=customerId")
                        && message.contains("reason=legacy-customer-code")
                        && message.contains("resolvedId=10"));
    }

    @Test
    void shouldReturnPage_whenCallingPage() {
        var repository = (ProjectRepository) Proxy.newProxyInstance(
                ProjectRepository.class.getClassLoader(),
                new Class[]{ProjectRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findAll" -> org.springframework.data.domain.Page.empty();
                    case "toString" -> "ProjectRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var service = new ProjectService(new SnowflakeIdGenerator(1), repository, null);

        var result = service.page(new PageQuery(0, 10, "id", "desc"), null, null);

        assertThat(result).isNotNull();
    }

    @Test
    void shouldThrowException_whenCreateWithDuplicateCode() {
        var repository = (ProjectRepository) Proxy.newProxyInstance(
                ProjectRepository.class.getClassLoader(),
                new Class[]{ProjectRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "existsByProjectCodeAndDeletedFlagFalse" -> true;
                    case "toString" -> "ProjectRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var service = new ProjectService(new SnowflakeIdGenerator(1), repository, null);

        assertThatThrownBy(() -> service.create(new ProjectRequest("P001", null, null, null, null, null, null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("项目编码已存在");
    }

    @Test
    void shouldThrowException_whenUpdateWithChangedDuplicateCode() {
        var repository = (ProjectRepository) Proxy.newProxyInstance(
                ProjectRepository.class.getClassLoader(),
                new Class[]{ProjectRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByIdAndDeletedFlagFalse" -> Optional.of(createProject(1L, "P001"));
                    case "existsByProjectCodeAndDeletedFlagFalse" -> true;
                    case "toString" -> "ProjectRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var service = new ProjectService(new SnowflakeIdGenerator(1), repository, null);

        assertThatThrownBy(() -> service.update(1L, new ProjectRequest("P002", null, null, null, null, null, null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("项目编码已存在");
    }

    @Test
    void shouldReturnDetail_whenEntityExists() {
        var repository = (ProjectRepository) Proxy.newProxyInstance(
                ProjectRepository.class.getClassLoader(),
                new Class[]{ProjectRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByIdAndDeletedFlagFalse" -> Optional.of(createProject(1L, "P001"));
                    case "findById" -> Optional.of(createProject(1L, "P001"));
                    case "toString" -> "ProjectRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var mapper = (ProjectMapper) Proxy.newProxyInstance(
                ProjectMapper.class.getClassLoader(),
                new Class[]{ProjectMapper.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "toResponse" -> new ProjectResponse(1L, "P001", "项目A", null, null, null, null, null, null);
                    case "toString" -> "ProjectMapperStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var service = new ProjectService(new SnowflakeIdGenerator(1), repository, mapper);

        var result = service.detail(1L);

        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(1L);
    }

    @Test
    void shouldCreate_success() {
        var repository = (ProjectRepository) Proxy.newProxyInstance(
                ProjectRepository.class.getClassLoader(),
                new Class[]{ProjectRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "existsByProjectCodeAndDeletedFlagFalse" -> false;
                    case "save" -> args[0];
                    case "toString" -> "ProjectRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var mapper = (ProjectMapper) Proxy.newProxyInstance(
                ProjectMapper.class.getClassLoader(),
                new Class[]{ProjectMapper.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "toResponse" -> toResponse((Project) args[0]);
                    case "toString" -> "ProjectMapperStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var service = new ProjectService(new SnowflakeIdGenerator(1), repository, mapper);

        var request = new ProjectRequest("P001", "项目A", "项A", "地址", "张三", "C001", "正常", "备注");
        var result = service.create(request);

        assertThat(result).isNotNull();
        assertThat(result.projectCode()).isEqualTo("P001");
        assertThat(result.projectName()).isEqualTo("项目A");
    }

    @Test
    void shouldUpdate_successWithSameCode() {
        var repository = (ProjectRepository) Proxy.newProxyInstance(
                ProjectRepository.class.getClassLoader(),
                new Class[]{ProjectRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByIdAndDeletedFlagFalse" -> Optional.of(createProject(1L, "P001"));
                    case "save" -> args[0];
                    case "toString" -> "ProjectRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var mapper = (ProjectMapper) Proxy.newProxyInstance(
                ProjectMapper.class.getClassLoader(),
                new Class[]{ProjectMapper.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "toResponse" -> toResponse((Project) args[0]);
                    case "toString" -> "ProjectMapperStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var service = new ProjectService(new SnowflakeIdGenerator(1), repository, mapper);

        var request = new ProjectRequest("P001", "项目B", null, null, null, null, "正常", null);
        var result = service.update(1L, request);

        assertThat(result).isNotNull();
        assertThat(result.projectName()).isEqualTo("项目B");
    }

    @Test
    void shouldUpdate_successWithDifferentCode() {
        var repository = (ProjectRepository) Proxy.newProxyInstance(
                ProjectRepository.class.getClassLoader(),
                new Class[]{ProjectRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByIdAndDeletedFlagFalse" -> Optional.of(createProject(1L, "P001"));
                    case "existsByProjectCodeAndDeletedFlagFalse" -> false;
                    case "save" -> args[0];
                    case "toString" -> "ProjectRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var mapper = (ProjectMapper) Proxy.newProxyInstance(
                ProjectMapper.class.getClassLoader(),
                new Class[]{ProjectMapper.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "toResponse" -> toResponse((Project) args[0]);
                    case "toString" -> "ProjectMapperStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var service = new ProjectService(new SnowflakeIdGenerator(1), repository, mapper);

        var request = new ProjectRequest("P002", "项目B", null, null, null, null, "正常", null);
        var result = service.update(1L, request);

        assertThat(result).isNotNull();
    }

    @Test
    void shouldThrowException_whenDetailNotFound() {
        var repository = (ProjectRepository) Proxy.newProxyInstance(
                ProjectRepository.class.getClassLoader(),
                new Class[]{ProjectRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByIdAndDeletedFlagFalse" -> Optional.empty();
                    case "findById" -> Optional.empty();
                    case "toString" -> "ProjectRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var service = new ProjectService(new SnowflakeIdGenerator(1), repository, null);

        assertThatThrownBy(() -> service.detail(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("项目不存在");
    }

    @Test
    void shouldCheckStableProjectIdentityBeforeDelete() {
        var repository = (ProjectRepository) Proxy.newProxyInstance(
                ProjectRepository.class.getClassLoader(),
                new Class[]{ProjectRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByIdAndDeletedFlagFalse" -> Optional.of(createProject(1L, "P001"));
                    case "findById" -> Optional.of(createProject(1L, "P001"));
                    case "save" -> args[0];
                    case "toString" -> "ProjectRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var referenceGuard = mock(MasterDataReferenceGuard.class);
        var service = new ProjectService(new SnowflakeIdGenerator(1), repository, null, referenceGuard);

        service.delete(1L);

        ArgumentCaptor<List<ReferenceCheck>> captor = ArgumentCaptor.forClass(List.class);
        verify(referenceGuard).assertNoReferences(eq("该项目"), captor.capture());
        assertThat(captor.getValue())
                .extracting(ReferenceCheck::tableName, ReferenceCheck::columnName, ReferenceCheck::value)
                .containsExactly(
                        tuple("so_sales_order", "project_id", 1L),
                        tuple("ct_sales_contract", "project_id", 1L),
                        tuple("so_sales_outbound", "project_id", 1L),
                        tuple("fm_invoice_issue", "project_id", 1L),
                        tuple("st_customer_statement", "project_id", 1L),
                        tuple("st_customer_statement_item", "project_id", 1L),
                        tuple("fm_receipt", "project_id", 1L),
                        tuple("lg_freight_bill_item", "project_id", 1L),
                        tuple("st_freight_statement_item", "project_id", 1L),
                        tuple("fm_ledger_adjustment", "project_id", 1L)
                );
    }

    @Test
    void shouldDeleteWithoutReferenceGuard() {
        var repository = (ProjectRepository) Proxy.newProxyInstance(
                ProjectRepository.class.getClassLoader(),
                new Class[]{ProjectRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByIdAndDeletedFlagFalse" -> Optional.of(createProject(1L, "P001"));
                    case "findById" -> Optional.of(createProject(1L, "P001"));
                    case "save" -> args[0];
                    case "toString" -> "ProjectRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var service = new ProjectService(new SnowflakeIdGenerator(1), repository, null);

        service.delete(1L);
    }

    @Test
    void shouldThrowException_whenDeleteWithReferences() {
        var repository = (ProjectRepository) Proxy.newProxyInstance(
                ProjectRepository.class.getClassLoader(),
                new Class[]{ProjectRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByIdAndDeletedFlagFalse" -> Optional.of(createProject(1L, "P001"));
                    case "findById" -> Optional.of(createProject(1L, "P001"));
                    case "toString" -> "ProjectRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var referenceGuard = mock(MasterDataReferenceGuard.class);
        doThrow(new BusinessException(ErrorCode.BUSINESS_ERROR, "该项目已被业务或主数据引用"))
                .when(referenceGuard).assertNoReferences(eq("该项目"), any(java.util.List.class));
        var service = new ProjectService(new SnowflakeIdGenerator(1), repository, null, referenceGuard);

        assertThatThrownBy(() -> service.delete(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("该项目已被业务或主数据引用");
    }

    private static Project createProject(Long id, String code) {
        Project p = new Project();
        p.setId(id);
        p.setProjectCode(code);
        p.setProjectName("项目A");
        p.setStatus("正常");
        return p;
    }

    private static ProjectResponse toResponse(Project p) {
        return new ProjectResponse(
                p.getId(), p.getProjectCode(), p.getProjectName(),
                p.getProjectNameAbbr(), p.getProjectAddress(),
                p.getProjectManager(), p.getCustomerId(), p.getCustomerCode(),
                p.getStatus(), p.getRemark()
        );
    }
}
