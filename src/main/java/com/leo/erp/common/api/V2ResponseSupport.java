package com.leo.erp.common.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;

/**
 * V2 响应构件：创建资源（201 + Location）与无响应体成功（204）。
 *
 * <p><b>使用边界</b>：{@link #created(String, Object)} 只能用于「真正创建了可回读资源」的
 * {@code POST /resources}；状态迁移（审核/完成/确认/强制结单）、同步命令（回填/重建/回滚/预览/打印导出）
 * 一律用 {@code ResponseEntity.ok(body)}（或 202/204），不得借 201 冒充当创建。
 * 响应体没有 {@code id} 时本方法仍返回 201 但<b>不会</b>发 Location——此时客户端无法回读资源，
 * 属于契约缺陷；新增端点若响应体确实带不回 id，应改用 200（命令语义），而不是沿用本方法。</p>
 */
public final class V2ResponseSupport {

    private V2ResponseSupport() {
    }

    /**
     * 创建资源：201 + {@code Location: /api/v2/{resourcePath}/{id}}。
     *
     * @param resourcePath 资源集合路径（如 {@code /sales-orders}）
     * @param body         新建资源的表示；需提供 {@code id()} / {@code getId()} 才能发出 Location
     */
    public static <T> ResponseEntity<T> created(String resourcePath, T body) {
        return createdBody(resourcePath, body);
    }

    public static ResponseEntity<Void> noContent() {
        return ResponseEntity.noContent().build();
    }

    private static <T> ResponseEntity<T> createdBody(String resourcePath, T body) {
        String id = resolveId(body);
        if (id == null) {
            return ResponseEntity.status(HttpStatus.CREATED).body(body);
        }
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path(ApiVersion.V2_PREFIX)
                .path(resourcePath)
                .pathSegment(id)
                .build()
                .toUri();
        return ResponseEntity.created(location).body(body);
    }

    private static String resolveId(Object body) {
        if (body == null) {
            return null;
        }
        Object id = invokeIdAccessor(body, "id");
        if (id == null) {
            id = invokeIdAccessor(body, "getId");
        }
        return id == null ? null : id.toString();
    }

    private static Object invokeIdAccessor(Object body, String methodName) {
        try {
            Method method = body.getClass().getMethod(methodName);
            return method.invoke(body);
        } catch (NoSuchMethodException ex) {
            return null;
        } catch (IllegalAccessException | InvocationTargetException ex) {
            throw new IllegalStateException("无法读取新建资源标识", ex);
        }
    }
}
