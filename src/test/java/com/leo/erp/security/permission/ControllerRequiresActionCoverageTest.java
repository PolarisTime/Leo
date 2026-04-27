package com.leo.erp.security.permission;

import com.leo.erp.common.web.PublicAccess;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ControllerRequiresPermissionCoverageTest {

    private static final String BASE_PACKAGE = "com.leo.erp";

    private static final Map<String, Set<String>> ALLOWED_PUBLIC_ENDPOINTS = Map.of(
            "com.leo.erp.auth.web.AuthController", Set.of("login", "login2fa", "refresh", "logout", "ping"),
            "com.leo.erp.system.menu.web.MenuController", Set.of("tree"),
            "com.leo.erp.system.web.HealthPageController", Set.of("health")
    );

    @Test
    void nonPublicControllerEndpointsShouldDeclareRequiresPermission() throws Exception {
        Set<String> violations = new TreeSet<>();
        for (Class<?> controllerClass : scanControllerClasses()) {
            for (Method method : controllerClass.getDeclaredMethods()) {
                if (!isRequestHandler(method)) {
                    continue;
                }
                if (isAllowedPublicEndpoint(controllerClass, method)) {
                    continue;
                }
                if (method.isAnnotationPresent(PublicAccess.class) || controllerClass.isAnnotationPresent(PublicAccess.class)) {
                    continue;
                }
                if (!method.isAnnotationPresent(RequiresPermission.class)) {
                    violations.add(controllerClass.getName() + "#" + method.getName());
                }
            }
        }
        assertTrue(violations.isEmpty(), () -> "Missing @RequiresPermission on endpoints: " + violations);
    }

    private Set<Class<?>> scanControllerClasses() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        scanner.addIncludeFilter(new AnnotationTypeFilter(Controller.class));

        Set<Class<?>> classes = new TreeSet<>((left, right) -> left.getName().compareTo(right.getName()));
        for (BeanDefinition beanDefinition : scanner.findCandidateComponents(BASE_PACKAGE)) {
            classes.add(Class.forName(beanDefinition.getBeanClassName()));
        }
        return classes;
    }

    private boolean isRequestHandler(Method method) {
        return method.isAnnotationPresent(RequestMapping.class)
                || method.isAnnotationPresent(GetMapping.class)
                || method.isAnnotationPresent(PostMapping.class)
                || method.isAnnotationPresent(PutMapping.class)
                || method.isAnnotationPresent(DeleteMapping.class)
                || method.isAnnotationPresent(PatchMapping.class);
    }

    private boolean isAllowedPublicEndpoint(Class<?> controllerClass, Method method) {
        return ALLOWED_PUBLIC_ENDPOINTS
                .getOrDefault(controllerClass.getName(), Set.of())
                .contains(method.getName());
    }
}
