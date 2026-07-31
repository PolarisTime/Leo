package com.leo.erp.common.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;

public final class V2ResponseSupport {

    private V2ResponseSupport() {
    }

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
