package com.leo.erp.master.material.service;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.master.material.domain.entity.MaterialCategory;
import com.leo.erp.master.material.mapper.MaterialCategoryMapper;
import com.leo.erp.master.material.repository.MaterialCategoryRepository;
import com.leo.erp.master.material.web.dto.MaterialCategoryOptionResponse;
import com.leo.erp.master.material.web.dto.MaterialCategoryRequest;
import com.leo.erp.master.material.web.dto.MaterialCategoryResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MaterialCategoryServiceTest {

    @Test
    void shouldReturnPage_whenCallingPage() {
        var repository = (MaterialCategoryRepository) Proxy.newProxyInstance(
                MaterialCategoryRepository.class.getClassLoader(),
                new Class[]{MaterialCategoryRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findAll" -> org.springframework.data.domain.Page.empty();
                    case "toString" -> "MaterialCategoryRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var mapper = (MaterialCategoryMapper) Proxy.newProxyInstance(
                MaterialCategoryMapper.class.getClassLoader(),
                new Class[]{MaterialCategoryMapper.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "toResponse" -> new MaterialCategoryResponse(null, null, null, null, null, null, null, null, null);
                    case "toOptionResponse" -> new MaterialCategoryOptionResponse(null, null, null, null, null);
                    case "toString" -> "MaterialCategoryMapperStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var service = new MaterialCategoryService(repository, new SnowflakeIdGenerator(1), mapper);

        var result = service.page(new PageQuery(0, 10, "id", "desc"), null, null);

        assertThat(result).isNotNull();
    }

    @Test
    void shouldReturnOptions_whenCallingOptions() {
        var repository = (MaterialCategoryRepository) Proxy.newProxyInstance(
                MaterialCategoryRepository.class.getClassLoader(),
                new Class[]{MaterialCategoryRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByStatusAndDeletedFlagFalseOrderBySortOrderAscIdAsc" -> List.of(createCategory(1L, "C001"));
                    case "toString" -> "MaterialCategoryRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var mapper = (MaterialCategoryMapper) Proxy.newProxyInstance(
                MaterialCategoryMapper.class.getClassLoader(),
                new Class[]{MaterialCategoryMapper.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "toOptionResponse" -> new MaterialCategoryOptionResponse("C001", "钢材", null, null, null);
                    case "toString" -> "MaterialCategoryMapperStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var service = new MaterialCategoryService(repository, new SnowflakeIdGenerator(1), mapper);

        var result = service.options();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).value()).isEqualTo("C001");
    }

    @Test
    void shouldThrowException_whenCreateWithDuplicateCode() {
        var repository = (MaterialCategoryRepository) Proxy.newProxyInstance(
                MaterialCategoryRepository.class.getClassLoader(),
                new Class[]{MaterialCategoryRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByCategoryCodeAndDeletedFlagFalse" -> Optional.of(createCategory(1L, "C001"));
                    case "toString" -> "MaterialCategoryRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var service = new MaterialCategoryService(repository, new SnowflakeIdGenerator(1), null);

        assertThatThrownBy(() -> service.create(new MaterialCategoryRequest("C001", null, null, null, null, null, null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("类别编码已存在");
    }

    @Test
    void shouldThrowException_whenUpdateWithChangedDuplicateCode() {
        var repository = (MaterialCategoryRepository) Proxy.newProxyInstance(
                MaterialCategoryRepository.class.getClassLoader(),
                new Class[]{MaterialCategoryRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByIdAndDeletedFlagFalse" -> Optional.of(createCategory(1L, "C001"));
                    case "findByCategoryCodeAndDeletedFlagFalse" -> Optional.of(createCategory(2L, "C002"));
                    case "toString" -> "MaterialCategoryRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var service = new MaterialCategoryService(repository, new SnowflakeIdGenerator(1), null);

        assertThatThrownBy(() -> service.update(1L, new MaterialCategoryRequest("C002", null, null, null, null, null, null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("类别编码已存在");
    }

    @Test
    void shouldThrowException_whenCategoryCodeIsNull() {
        var repository = (MaterialCategoryRepository) Proxy.newProxyInstance(
                MaterialCategoryRepository.class.getClassLoader(),
                new Class[]{MaterialCategoryRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByCategoryCodeAndDeletedFlagFalse" -> Optional.empty();
                    case "toString" -> "MaterialCategoryRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var service = new MaterialCategoryService(repository, new SnowflakeIdGenerator(1), null);

        assertThatThrownBy(() -> service.create(new MaterialCategoryRequest(null, null, null, null, null, null, null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("类别编码不能为空");
    }

    @Test
    void shouldCreate_success() {
        var repository = (MaterialCategoryRepository) Proxy.newProxyInstance(
                MaterialCategoryRepository.class.getClassLoader(),
                new Class[]{MaterialCategoryRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByCategoryCodeAndDeletedFlagFalse" -> Optional.empty();
                    case "save" -> args[0];
                    case "toString" -> "MaterialCategoryRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var mapper = (MaterialCategoryMapper) Proxy.newProxyInstance(
                MaterialCategoryMapper.class.getClassLoader(),
                new Class[]{MaterialCategoryMapper.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "toResponse" -> toResponse((MaterialCategory) args[0]);
                    case "toString" -> "MaterialCategoryMapperStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var service = new MaterialCategoryService(repository, new SnowflakeIdGenerator(1), mapper);

        var request = new MaterialCategoryRequest(
                "C001", "钢材", 1, true,
                new BigDecimal("3.00"), new BigDecimal("4.00"),
                "正常", "备注");
        var result = service.create(request);

        assertThat(result).isNotNull();
        assertThat(result.categoryCode()).isEqualTo("C001");
        assertThat(result.categoryName()).isEqualTo("钢材");
        assertThat(result.purchaseWeighOverTolerancePercent()).isEqualByComparingTo("3.00");
        assertThat(result.purchaseWeighUnderTolerancePercent()).isEqualByComparingTo("4.00");
    }

    @Test
    void shouldUpdate_successWithSameCode() {
        var repository = (MaterialCategoryRepository) Proxy.newProxyInstance(
                MaterialCategoryRepository.class.getClassLoader(),
                new Class[]{MaterialCategoryRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByIdAndDeletedFlagFalse" -> Optional.of(createCategory(1L, "C001"));
                    case "save" -> args[0];
                    case "toString" -> "MaterialCategoryRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var mapper = (MaterialCategoryMapper) Proxy.newProxyInstance(
                MaterialCategoryMapper.class.getClassLoader(),
                new Class[]{MaterialCategoryMapper.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "toResponse" -> toResponse((MaterialCategory) args[0]);
                    case "toString" -> "MaterialCategoryMapperStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var service = new MaterialCategoryService(repository, new SnowflakeIdGenerator(1), mapper);

        var request = new MaterialCategoryRequest("C001", "新钢材", 2, false, null, null, "停用", "新备注");
        var result = service.update(1L, request);

        assertThat(result).isNotNull();
        assertThat(result.categoryName()).isEqualTo("新钢材");
    }

    @Test
    void shouldUpdate_successWithDifferentCode() {
        var repository = (MaterialCategoryRepository) Proxy.newProxyInstance(
                MaterialCategoryRepository.class.getClassLoader(),
                new Class[]{MaterialCategoryRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByIdAndDeletedFlagFalse" -> Optional.of(createCategory(1L, "C001"));
                    case "findByCategoryCodeAndDeletedFlagFalse" -> Optional.empty();
                    case "save" -> args[0];
                    case "toString" -> "MaterialCategoryRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var mapper = (MaterialCategoryMapper) Proxy.newProxyInstance(
                MaterialCategoryMapper.class.getClassLoader(),
                new Class[]{MaterialCategoryMapper.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "toResponse" -> toResponse((MaterialCategory) args[0]);
                    case "toString" -> "MaterialCategoryMapperStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var service = new MaterialCategoryService(repository, new SnowflakeIdGenerator(1), mapper);

        var request = new MaterialCategoryRequest("C002", "钢材", 1, false, null, null, "正常", null);
        var result = service.update(1L, request);

        assertThat(result).isNotNull();
    }

    @Test
    void shouldThrowException_whenDetailNotFound() {
        var repository = (MaterialCategoryRepository) Proxy.newProxyInstance(
                MaterialCategoryRepository.class.getClassLoader(),
                new Class[]{MaterialCategoryRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByIdAndDeletedFlagFalse" -> Optional.empty();
                    case "toString" -> "MaterialCategoryRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var service = new MaterialCategoryService(repository, new SnowflakeIdGenerator(1), null);

        assertThatThrownBy(() -> service.detail(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("商品类别不存在");
    }

    @Test
    void shouldDelete_success() {
        var repository = (MaterialCategoryRepository) Proxy.newProxyInstance(
                MaterialCategoryRepository.class.getClassLoader(),
                new Class[]{MaterialCategoryRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByIdAndDeletedFlagFalse" -> Optional.of(createCategory(1L, "C001"));
                    case "save" -> args[0];
                    case "toString" -> "MaterialCategoryRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var service = new MaterialCategoryService(repository, new SnowflakeIdGenerator(1), null);

        service.delete(1L);
    }

    @Test
    void shouldTruncateLongCategoryCode() {
        var repository = (MaterialCategoryRepository) Proxy.newProxyInstance(
                MaterialCategoryRepository.class.getClassLoader(),
                new Class[]{MaterialCategoryRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByCategoryCodeAndDeletedFlagFalse" -> Optional.empty();
                    case "save" -> args[0];
                    case "toString" -> "MaterialCategoryRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var mapper = (MaterialCategoryMapper) Proxy.newProxyInstance(
                MaterialCategoryMapper.class.getClassLoader(),
                new Class[]{MaterialCategoryMapper.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "toResponse" -> toResponse((MaterialCategory) args[0]);
                    case "toString" -> "MaterialCategoryMapperStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var service = new MaterialCategoryService(repository, new SnowflakeIdGenerator(1), mapper);

        String longCode = "A".repeat(50);
        var request = new MaterialCategoryRequest(longCode, "钢材", 1, false, null, null, null, null);
        var result = service.create(request);

        assertThat(result.categoryCode()).hasSize(32);
    }

    @Test
    void shouldApplyDefaultValues() {
        var repository = (MaterialCategoryRepository) Proxy.newProxyInstance(
                MaterialCategoryRepository.class.getClassLoader(),
                new Class[]{MaterialCategoryRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByCategoryCodeAndDeletedFlagFalse" -> Optional.empty();
                    case "save" -> args[0];
                    case "toString" -> "MaterialCategoryRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var mapper = (MaterialCategoryMapper) Proxy.newProxyInstance(
                MaterialCategoryMapper.class.getClassLoader(),
                new Class[]{MaterialCategoryMapper.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "toResponse" -> toResponse((MaterialCategory) args[0]);
                    case "toString" -> "MaterialCategoryMapperStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var service = new MaterialCategoryService(repository, new SnowflakeIdGenerator(1), mapper);

        var request = new MaterialCategoryRequest("C001", "钢材", null, null, null, null, null, "  ");
        var result = service.create(request);

        assertThat(result).isNotNull();
        assertThat(result.purchaseWeighOverTolerancePercent()).isEqualByComparingTo("5.00");
        assertThat(result.purchaseWeighUnderTolerancePercent()).isEqualByComparingTo("5.00");
    }

    @Test
    void shouldNormalizeBlankStatusToNormal() {
        var repository = (MaterialCategoryRepository) Proxy.newProxyInstance(
                MaterialCategoryRepository.class.getClassLoader(),
                new Class[]{MaterialCategoryRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByCategoryCodeAndDeletedFlagFalse" -> Optional.empty();
                    case "save" -> args[0];
                    case "toString" -> "MaterialCategoryRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var mapper = (MaterialCategoryMapper) Proxy.newProxyInstance(
                MaterialCategoryMapper.class.getClassLoader(),
                new Class[]{MaterialCategoryMapper.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "toResponse" -> toResponse((MaterialCategory) args[0]);
                    case "toString" -> "MaterialCategoryMapperStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var service = new MaterialCategoryService(repository, new SnowflakeIdGenerator(1), mapper);

        var request = new MaterialCategoryRequest("C001", "钢材", null, null, null, null, "  ", null);
        var result = service.create(request);

        assertThat(result.status()).isEqualTo("正常");
    }

    @Test
    void shouldThrowException_whenCategoryNameIsNull() {
        var repository = (MaterialCategoryRepository) Proxy.newProxyInstance(
                MaterialCategoryRepository.class.getClassLoader(),
                new Class[]{MaterialCategoryRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByCategoryCodeAndDeletedFlagFalse" -> Optional.empty();
                    case "toString" -> "MaterialCategoryRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var service = new MaterialCategoryService(repository, new SnowflakeIdGenerator(1), null);

        assertThatThrownBy(() -> service.create(new MaterialCategoryRequest("C001", null, null, null, null, null, null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("类别名称不能为空");
    }

    @Test
    void shouldTestNormalizeCode_viaReflection() throws Exception {
        var service = new MaterialCategoryService(null, new SnowflakeIdGenerator(1), null);
        Method normalizeCode = MaterialCategoryService.class.getDeclaredMethod("normalizeCode", String.class);
        normalizeCode.setAccessible(true);

        assertThat(normalizeCode.invoke(service, "ABC")).isEqualTo("ABC");
        String truncated = (String) normalizeCode.invoke(service, "A".repeat(50));
        assertThat(truncated).hasSize(32);
    }

    @Test
    void shouldTestOptional_viaReflection() throws Exception {
        var service = new MaterialCategoryService(null, new SnowflakeIdGenerator(1), null);
        Method optional = MaterialCategoryService.class.getDeclaredMethod("optional", String.class);
        optional.setAccessible(true);

        assertThat(optional.invoke(service, (Object) null)).isNull();
        assertThat(optional.invoke(service, "")).isNull();
        assertThat(optional.invoke(service, "  ")).isNull();
        assertThat(optional.invoke(service, "value")).isEqualTo("value");
        assertThat(optional.invoke(service, " value ")).isEqualTo("value");
    }

    @Test
    void shouldTestRequired_viaReflection() throws Exception {
        var service = new MaterialCategoryService(null, new SnowflakeIdGenerator(1), null);
        Method required = MaterialCategoryService.class.getDeclaredMethod("required", String.class, String.class);
        required.setAccessible(true);

        assertThat(required.invoke(service, "value", "字段")).isEqualTo("value");
        assertThat(required.invoke(service, " value ", "字段")).isEqualTo("value");

        try {
            required.invoke(service, (String) null, "字段");
        } catch (java.lang.reflect.InvocationTargetException e) {
            assertThat(e.getCause()).isInstanceOf(BusinessException.class);
        }
        try {
            required.invoke(service, "  ", "字段");
        } catch (java.lang.reflect.InvocationTargetException e) {
            assertThat(e.getCause()).isInstanceOf(BusinessException.class);
        }
    }

    @Test
    void shouldRejectTolerancePercentBelowZeroOrAboveOneHundred() {
        var repository = (MaterialCategoryRepository) Proxy.newProxyInstance(
                MaterialCategoryRepository.class.getClassLoader(),
                new Class[]{MaterialCategoryRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByCategoryCodeAndDeletedFlagFalse" -> Optional.empty();
                    case "toString" -> "MaterialCategoryRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var service = new MaterialCategoryService(repository, new SnowflakeIdGenerator(1), null);

        assertThatThrownBy(() -> service.create(new MaterialCategoryRequest(
                "C001", "钢材", 1, false, new BigDecimal("-0.01"), null, "正常", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("采购过磅上差百分比必须在0到100之间");
        assertThatThrownBy(() -> service.create(new MaterialCategoryRequest(
                "C001", "钢材", 1, false, null, new BigDecimal("100.01"), "正常", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("采购过磅下差百分比必须在0到100之间");
    }

    private static MaterialCategory createCategory(Long id, String code) {
        MaterialCategory category = new MaterialCategory();
        category.setId(id);
        category.setCategoryCode(code);
        category.setCategoryName("钢材");
        category.setSortOrder(1);
        category.setStatus("正常");
        return category;
    }

    private static MaterialCategoryResponse toResponse(MaterialCategory c) {
        return new MaterialCategoryResponse(
                c.getId(), c.getCategoryCode(), c.getCategoryName(),
                c.getSortOrder(), c.getPurchaseWeighRequired(),
                c.getPurchaseWeighOverTolerancePercent(),
                c.getPurchaseWeighUnderTolerancePercent(),
                c.getStatus(), c.getRemark()
        );
    }
}
