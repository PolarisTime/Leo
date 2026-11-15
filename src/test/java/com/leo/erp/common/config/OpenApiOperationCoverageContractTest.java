package com.leo.erp.common.config;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.io.Resource;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 契约测试：所有对外 HTTP 端点（{@code @RestController} 上带
 * {@code @RequestMapping}/{@code @GetMapping}/{@code @PostMapping}/{@code @PutMapping}/
 * {@code @PatchMapping}/{@code @DeleteMapping} 的方法）必须标注
 * {@link Operation} 且 {@code summary} 非空。
 *
 * <p>回归背景（2026-11-14 后端审计 P2 #8）：OpenAPI {@code @Operation} 覆盖仅 58.7%，
 * springdoc 生成的接口文档里大量端点只有路径、没有摘要与语义说明；且缺口是"逐控制器"的，
 * 新写一个端点忘了标注不会有任何测试失败。本测试把"漏标注"变成全仓扫描级的单测失败。</p>
 *
 * <p>{@code @PublicAccess} 的端点（运行时配置、健康检查、版本号）同样属于对外契约面，
 * 不加豁免：匿名可访问不代表可以不描述。</p>
 *
 * <p>失败信息按「简单类名#方法名」排序输出，便于一次拿到精确缺口清单。</p>
 */
class OpenApiOperationCoverageContractTest {

    @Test
    void everyHttpEndpointMustDeclareOperationWithSummary() throws Exception {
        List<Class<?>> controllers = scanControllers();

        Set<String> missingOperation = new TreeSet<>();
        Set<String> blankSummary = new TreeSet<>();
        int endpointCount = 0;

        for (Class<?> controller : controllers) {
            for (Method method : controller.getDeclaredMethods()) {
                if (!isHttpEndpoint(method)) {
                    continue;
                }
                endpointCount++;
                String endpoint = controller.getSimpleName() + "#" + method.getName();
                Operation operation = method.getAnnotation(Operation.class);
                if (operation == null) {
                    missingOperation.add(endpoint);
                    continue;
                }
                if (operation.summary() == null || operation.summary().isBlank()) {
                    blankSummary.add(endpoint);
                }
            }
        }

        assertThat(endpointCount)
                .as("应扫描到足够多的 HTTP 端点，否则本契约测试没有覆盖真实接口面")
                .isGreaterThan(150);
        assertThat(missingOperation)
                .as("以下端点缺少 @Operation（OpenAPI 摘要）：%s", missingOperation)
                .isEmpty();
        assertThat(blankSummary)
                .as("以下端点的 @Operation.summary 为空：%s", blankSummary)
                .isEmpty();
    }

    /**
     * 二进制/文件类端点必须声明具体媒体类型：返回 {@link Resource}、{@code byte[]} 或
     * {@code ResponseEntity<Resource>}/{@code ResponseEntity<byte[]>} 的端点，如果既没有在 mapping 上
     * 声明 {@code produces}，也没有在 {@code @Operation.responses} 里声明响应媒体类型，
     * springdoc 会把它描述成 JSON，前端与网关都会按错误类型处理响应。
     *
     * <p>本项目文件端点统一由响应头动态下发 {@code Content-Type}，允许三种合规写法：
     * 在 mapping 上声明具体媒体类型、在 {@code @Operation.responses} 的 {@code @Content} 里声明，
     * 或对内容类型随文件变化的端点使用通配 {@code * / *}（{@code MediaType.ALL_VALUE}）。
     * 检测口径只要求"有声明"，且不得声明为 JSON。</p>
     */
    @Test
    void binaryEndpointsMustDeclareConcreteMediaType() throws Exception {
        Set<String> missingMediaType = new TreeSet<>();
        Set<String> jsonMediaType = new TreeSet<>();
        int binaryEndpoints = 0;

        for (Class<?> controller : scanControllers()) {
            for (Method method : controller.getDeclaredMethods()) {
                if (!isHttpEndpoint(method) || !returnsBinaryPayload(method)) {
                    continue;
                }
                binaryEndpoints++;
                String endpoint = controller.getSimpleName() + "#" + method.getName();
                List<String> mediaTypes = declaredResponseMediaTypes(method);
                if (mediaTypes.isEmpty()) {
                    missingMediaType.add(endpoint);
                } else if (mediaTypes.stream().allMatch(OpenApiOperationCoverageContractTest::isJsonMediaType)) {
                    jsonMediaType.add(endpoint + " -> " + mediaTypes);
                }
            }
        }

        assertThat(binaryEndpoints)
                .as("应扫描到二进制/文件端点，否则本测试没有覆盖文件下载契约")
                .isGreaterThan(5);
        assertThat(missingMediaType)
                .as("以下二进制/文件端点未声明响应媒体类型，OpenAPI 会把响应描述成 JSON：%s", missingMediaType)
                .isEmpty();
        assertThat(jsonMediaType)
                .as("以下二进制/文件端点的响应媒体类型被声明成 JSON：%s", jsonMediaType)
                .isEmpty();
    }

    /** 方法级 mapping 注解集合（包含 {@code @RequestMapping}，无论是否限定 HTTP 方法）。 */
    private static boolean isHttpEndpoint(Method method) {
        if (method.isSynthetic() || method.isBridge()) {
            return false;
        }
        return method.isAnnotationPresent(RequestMapping.class)
                || method.isAnnotationPresent(GetMapping.class)
                || method.isAnnotationPresent(PostMapping.class)
                || method.isAnnotationPresent(PutMapping.class)
                || method.isAnnotationPresent(PatchMapping.class)
                || method.isAnnotationPresent(DeleteMapping.class);
    }

    private static boolean returnsBinaryPayload(Method method) {
        Class<?> raw = method.getReturnType();
        if (raw == byte[].class || Resource.class.isAssignableFrom(raw)) {
            return true;
        }
        if (!ResponseEntity.class.isAssignableFrom(raw)) {
            return false;
        }
        Type generic = method.getGenericReturnType();
        if (!(generic instanceof ParameterizedType parameterized)) {
            return false;
        }
        Type[] arguments = parameterized.getActualTypeArguments();
        if (arguments.length != 1) {
            return false;
        }
        Type body = arguments[0];
        if (body instanceof Class<?> bodyClass) {
            return bodyClass == byte[].class || Resource.class.isAssignableFrom(bodyClass);
        }
        return false;
    }

    private static List<String> producesOf(Method method) {
        List<String> produces = new ArrayList<>();
        GetMapping get = method.getAnnotation(GetMapping.class);
        if (get != null) {
            produces.addAll(List.of(get.produces()));
        }
        PostMapping post = method.getAnnotation(PostMapping.class);
        if (post != null) {
            produces.addAll(List.of(post.produces()));
        }
        PutMapping put = method.getAnnotation(PutMapping.class);
        if (put != null) {
            produces.addAll(List.of(put.produces()));
        }
        PatchMapping patch = method.getAnnotation(PatchMapping.class);
        if (patch != null) {
            produces.addAll(List.of(patch.produces()));
        }
        DeleteMapping delete = method.getAnnotation(DeleteMapping.class);
        if (delete != null) {
            produces.addAll(List.of(delete.produces()));
        }
        RequestMapping request = method.getAnnotation(RequestMapping.class);
        if (request != null) {
            produces.addAll(List.of(request.produces()));
        }
        return produces;
    }

    /**
     * 响应媒体类型声明：mapping 上的 {@code produces}、{@code @Operation.responses()} 里的
     * {@code @Content}，以及方法级 {@code @ApiResponses} 容器里的 {@code @Content}。
     */
    private static List<String> declaredResponseMediaTypes(Method method) {
        List<String> mediaTypes = new ArrayList<>(producesOf(method));
        Operation operation = method.getAnnotation(Operation.class);
        if (operation != null) {
            collectMediaTypes(operation.responses(), mediaTypes);
        }
        ApiResponses responses = method.getAnnotation(ApiResponses.class);
        if (responses != null) {
            collectMediaTypes(responses.value(), mediaTypes);
        }
        return mediaTypes;
    }

    private static void collectMediaTypes(ApiResponse[] responses, List<String> mediaTypes) {
        for (ApiResponse response : responses) {
            for (Content content : response.content()) {
                String mediaType = content.mediaType();
                if (mediaType != null && !mediaType.isBlank()) {
                    mediaTypes.add(mediaType.trim());
                }
            }
        }
    }

    private static boolean isJsonMediaType(String mediaType) {
        String normalized = mediaType.toLowerCase(Locale.ROOT);
        return normalized.equals("application/json") || normalized.contains("+json");
    }

    private List<Class<?>> scanControllers() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));

        List<Class<?>> controllers = new ArrayList<>();
        for (BeanDefinition candidate : scanner.findCandidateComponents("com.leo.erp")) {
            Class<?> type = Class.forName(candidate.getBeanClassName());
            if (Modifier.isAbstract(type.getModifiers())) {
                continue;
            }
            // 类名里的 `$` 是嵌套类：本仓生产控制器都是顶层类，测试探针控制器
            // （CacheControlShallowEtagFilterTest 等）是嵌套类，不属于对外契约面。
            if (type.getName().indexOf('$') >= 0) {
                continue;
            }
            controllers.add(type);
        }
        assertThat(controllers)
                .as("应能扫描到 @RestController，否则本契约测试没有覆盖任何控制器")
                .isNotEmpty();
        return controllers;
    }
}
