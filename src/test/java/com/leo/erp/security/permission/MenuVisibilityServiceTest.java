package com.leo.erp.security.permission;

import com.leo.erp.common.support.RedisJsonCacheSupport;
import com.leo.erp.system.menu.domain.entity.Menu;
import com.leo.erp.system.menu.repository.MenuRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MenuVisibilityServiceTest {

    @Test
    void shouldResolveVisibleMenuCodesWithParentChain() {
        MenuRepository menuRepository = menuRepository(
                menu("master-data", "主数据", null),
                menu("supplier", "供应商资料", "master-data"),
                menu("customer", "客户资料", "master-data")
        );
        MenuVisibilityService service = new MenuVisibilityService(menuRepository, Optional.empty());
        Map<String, Set<String>> permMap = Map.of("supplier", Set.of(ResourcePermissionCatalog.READ));

        Set<String> result = service.getVisibleMenuCodes(permMap);

        assertThat(result).contains("supplier", "master-data");
        assertThat(result).doesNotContain("customer");
    }

    @Test
    void shouldIncludeAllParentCodesInChain() {
        MenuRepository menuRepository = menuRepository(
                menu("root", "根", null),
                menu("master-data", "主数据", "root"),
                menu("supplier", "供应商资料", "master-data"),
                menu("other", "其他", null)
        );
        MenuVisibilityService service = new MenuVisibilityService(menuRepository, Optional.empty());
        Map<String, Set<String>> permMap = Map.of("supplier", Set.of(ResourcePermissionCatalog.READ));

        Set<String> result = service.getVisibleMenuCodes(permMap);

        assertThat(result).contains("supplier", "master-data", "root");
        assertThat(result).doesNotContain("other");
    }

    @Test
    void shouldReturnEmptySet_whenNoPermissions() {
        MenuRepository menuRepository = menuRepository(
                menu("dashboard", "仪表盘", null)
        );
        MenuVisibilityService service = new MenuVisibilityService(menuRepository, Optional.empty());

        Set<String> result = service.getVisibleMenuCodes(Map.of());

        assertThat(result).isEmpty();
    }

    @Test
    void shouldLoadActiveMenusFromRepository() {
        MenuRepository menuRepository = menuRepository(
                menu("dashboard", "仪表盘", null)
        );
        MenuVisibilityService service = new MenuVisibilityService(menuRepository, Optional.empty());

        List<Menu> menus = service.getActiveMenus();

        assertThat(menus).hasSize(1);
        assertThat(menus.get(0).getMenuCode()).isEqualTo("dashboard");
    }

    @Test
    void shouldLoadActiveMenusWhenCacheOptionalIsNull() {
        MenuRepository menuRepository = menuRepository(
                menu("dashboard", "仪表盘", null)
        );

        MenuVisibilityService service = new MenuVisibilityService(menuRepository, null);

        assertThat(service.getActiveMenus()).extracting(Menu::getMenuCode).containsExactly("dashboard");
    }

    @Test
    void shouldReturnEmptyList_whenRepositoryIsNull() {
        MenuVisibilityService service = new MenuVisibilityService(null, Optional.empty());

        List<Menu> menus = service.getActiveMenus();

        assertThat(menus).isEmpty();
    }

    @Test
    void shouldUseRedisCache_whenCacheSupportAvailable() {
        MenuRepository menuRepository = menuRepository(
                menu("dashboard", "仪表盘", null)
        );
        RedisJsonCacheSupport cacheSupport = mock(RedisJsonCacheSupport.class);
        when(cacheSupport.getOrLoad(any(), any(java.time.Duration.class),
                any(com.fasterxml.jackson.core.type.TypeReference.class), any()))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    java.util.function.Supplier<List<?>> supplier = invocation.getArgument(3);
                    return supplier.get();
                });
        MenuVisibilityService service = new MenuVisibilityService(menuRepository, Optional.of(cacheSupport));

        List<Menu> menus = service.getActiveMenus();

        assertThat(menus).hasSize(1);
        verify(cacheSupport).getOrLoad(org.mockito.ArgumentMatchers.startsWith("leo:menu:all:"),
                any(java.time.Duration.class), any(com.fasterxml.jackson.core.type.TypeReference.class), any());
    }

    @Test
    void shouldKeepFirstParentWhenDuplicateMenuCodeExists() {
        MenuRepository menuRepository = menuRepository(
                menu("first-root", "一级", null),
                menu("second-root", "二级", null),
                menu("supplier", "供应商资料", "first-root"),
                menu("supplier", "供应商资料重复", "second-root")
        );
        MenuVisibilityService service = new MenuVisibilityService(menuRepository, Optional.empty());

        Set<String> result = service.getVisibleMenuCodes(Map.of("supplier", Set.of(ResourcePermissionCatalog.READ)));

        assertThat(result).contains("supplier", "first-root");
        assertThat(result).doesNotContain("second-root");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "   ")
    void shouldUseDefaultCacheKeyWhenSignatureIsNullOrBlank(String signature) {
        MenuRepository menuRepository = menuRepositoryWithSignature(signature);
        RedisJsonCacheSupport cacheSupport = cacheSupportLoadingFromSupplier();
        MenuVisibilityService service = new MenuVisibilityService(menuRepository, Optional.of(cacheSupport));

        assertThat(service.getActiveMenus()).hasSize(1);

        verify(cacheSupport).getOrLoad(eq("leo:menu:all:default"),
                any(Duration.class), any(com.fasterxml.jackson.core.type.TypeReference.class), any());
    }

    @Test
    void shouldUseDefaultCacheKeyWhenSignatureLookupFails() {
        MenuRepository menuRepository = menuRepositoryThrowingSignature();
        RedisJsonCacheSupport cacheSupport = cacheSupportLoadingFromSupplier();
        MenuVisibilityService service = new MenuVisibilityService(menuRepository, Optional.of(cacheSupport));

        assertThat(service.getActiveMenus()).hasSize(1);

        verify(cacheSupport).getOrLoad(eq("leo:menu:all:default"),
                any(Duration.class), any(com.fasterxml.jackson.core.type.TypeReference.class), any());
    }

    @SuppressWarnings("unchecked")
    private MenuRepository menuRepository(Menu... menus) {
        return (MenuRepository) Proxy.newProxyInstance(
                MenuRepository.class.getClassLoader(),
                new Class[]{MenuRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByStatusAndDeletedFlagFalseOrderBySortOrder" -> List.of(menus);
                    case "activeMenuCacheSignature" -> "1:2026-06-29T18:00";
                    case "toString" -> "MenuRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private MenuRepository menuRepositoryWithSignature(String signature) {
        return menuRepository(List.of(menu("dashboard", "仪表盘", null)), signature, false);
    }

    private MenuRepository menuRepositoryThrowingSignature() {
        return menuRepository(List.of(menu("dashboard", "仪表盘", null)), null, true);
    }

    @SuppressWarnings("unchecked")
    private MenuRepository menuRepository(List<Menu> menus, String signature, boolean throwSignature) {
        return (MenuRepository) Proxy.newProxyInstance(
                MenuRepository.class.getClassLoader(),
                new Class[]{MenuRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByStatusAndDeletedFlagFalseOrderBySortOrder" -> menus;
                    case "activeMenuCacheSignature" -> {
                        if (throwSignature) {
                            throw new IllegalStateException("signature unavailable");
                        }
                        yield signature;
                    }
                    case "toString" -> "MenuRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private RedisJsonCacheSupport cacheSupportLoadingFromSupplier() {
        RedisJsonCacheSupport cacheSupport = mock(RedisJsonCacheSupport.class);
        when(cacheSupport.getOrLoad(any(), any(Duration.class),
                any(com.fasterxml.jackson.core.type.TypeReference.class), any()))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    java.util.function.Supplier<List<?>> supplier = invocation.getArgument(3);
                    return supplier.get();
                });
        return cacheSupport;
    }

    private Menu menu(String code, String name, String parentCode) {
        Menu menu = new Menu();
        menu.setMenuCode(code);
        menu.setMenuName(name);
        menu.setParentCode(parentCode);
        menu.setSortOrder(1);
        menu.setMenuType("M");
        menu.setStatus("正常");
        return menu;
    }
}
