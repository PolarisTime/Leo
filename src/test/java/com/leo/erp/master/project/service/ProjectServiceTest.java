package com.leo.erp.master.project.service;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.MasterDataReferenceGuard;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.master.project.domain.entity.Project;
import com.leo.erp.master.project.mapper.ProjectMapper;
import com.leo.erp.master.project.repository.ProjectRepository;
import com.leo.erp.master.project.web.dto.ProjectRequest;
import com.leo.erp.master.project.web.dto.ProjectResponse;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ProjectServiceTest {

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
    void shouldDelete_success() {
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

        verify(referenceGuard).assertNoReferences(eq("该项目"), any(java.util.List.class));
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
                p.getProjectManager(), p.getCustomerCode(),
                p.getStatus(), p.getRemark()
        );
    }
}
