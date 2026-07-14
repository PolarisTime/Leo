package com.leo.erp.search.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.search.repository.GlobalSearchDocument;
import com.leo.erp.search.repository.GlobalSearchDocumentRepository;
import com.leo.erp.search.repository.GlobalSearchModuleAccess;
import com.leo.erp.search.web.GlobalSearchResponse;
import com.leo.erp.security.permission.DataScopeContext;
import com.leo.erp.security.permission.ModulePermissionGuard;
import com.leo.erp.security.permission.PermissionService;
import com.leo.erp.security.permission.ResourcePermissionCatalog;
import com.leo.erp.security.support.SecurityPrincipal;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class GlobalSearchServiceTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        DataScopeContext.clear();
    }

    @Test
    void searchSuspendsOuterTransactionToKeepSearchFailuresIsolated() throws Exception {
        Transactional annotation = GlobalSearchService.class
                .getMethod("search", String.class, int.class, List.class)
                .getAnnotation(Transactional.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.propagation()).isEqualTo(Propagation.NOT_SUPPORTED);
    }

    @Test
    void searchOverloadUsesDefaultModulesAndClampsLimit() {
        PermissionService permissionService = mock(PermissionService.class);
        ModulePermissionGuard modulePermissionGuard = mock(ModulePermissionGuard.class);
        GlobalSearchDocumentRepository documentRepository = mock(GlobalSearchDocumentRepository.class);
        GlobalSearchService service = new GlobalSearchService(
                permissionService,
                modulePermissionGuard,
                documentRepository
        );
        authenticate(1L);

        when(modulePermissionGuard.requireResourcePermission(any(), anyString(), eq(ResourcePermissionCatalog.READ)))
                .thenAnswer(invocation -> new ModulePermissionGuard.PermissionCheck(
                        invocation.getArgument(1, String.class),
                        invocation.getArgument(1, String.class),
                        ResourcePermissionCatalog.READ
                ));
        when(permissionService.getUserDataScope(eq(1L), anyString(), eq(ResourcePermissionCatalog.READ)))
                .thenReturn(ResourcePermissionCatalog.SCOPE_ALL);
        when(documentRepository.search(eq("keyword"), isNull(), eq(50), anyList()))
                .thenReturn(List.of(new GlobalSearchDocument(
                        "purchase-order",
                        1L,
                        "CG20260001",
                        "供应商甲",
                        false
                )));

        List<GlobalSearchResponse> results = service.search(" keyword ", 99);

        ArgumentCaptor<List<GlobalSearchModuleAccess>> accessCaptor = ArgumentCaptor.forClass(List.class);
        verify(documentRepository).search(eq("keyword"), isNull(), eq(50), accessCaptor.capture());
        assertThat(accessCaptor.getValue())
                .hasSize(14)
                .allSatisfy(access -> assertThat(access.allDataScope()).isTrue());
        assertThat(results).singleElement().satisfies(item -> {
            assertThat(item.moduleKey()).isEqualTo("purchase-order");
            assertThat(item.title()).isEqualTo("采购订单");
        });
    }

    @Test
    void searchReturnsEmptyForBlankKeywordBeforeResolvingPermissions() {
        PermissionService permissionService = mock(PermissionService.class);
        ModulePermissionGuard modulePermissionGuard = mock(ModulePermissionGuard.class);
        GlobalSearchDocumentRepository documentRepository = mock(GlobalSearchDocumentRepository.class);
        GlobalSearchService service = new GlobalSearchService(
                permissionService,
                modulePermissionGuard,
                documentRepository
        );

        assertThat(service.search(null, 20, List.of("purchase-order"))).isEmpty();
        assertThat(service.search("   ", 20, List.of("purchase-order"))).isEmpty();

        verifyNoInteractions(permissionService, modulePermissionGuard, documentRepository);
    }

    @Test
    void searchAppliesPerModulePermissionAndDataScope() {
        PermissionService permissionService = mock(PermissionService.class);
        GlobalSearchDocumentRepository documentRepository = mock(GlobalSearchDocumentRepository.class);
        GlobalSearchService service = createService(permissionService, documentRepository);
        authenticate(1L);

        when(permissionService.can(anyLong(), anyString(), anyString())).thenReturn(false);
        when(permissionService.can(1L, "purchase-order", ResourcePermissionCatalog.READ)).thenReturn(true);
        when(permissionService.getUserDataScope(1L, "purchase-order", ResourcePermissionCatalog.READ))
                .thenReturn(ResourcePermissionCatalog.SCOPE_DEPARTMENT);
        when(permissionService.getDataScopeOwnerUserIds(1L, ResourcePermissionCatalog.SCOPE_DEPARTMENT))
                .thenReturn(Set.of(1L, 2L));
        when(documentRepository.search(eq("CG20260001"), isNull(), eq(20), anyList()))
                .thenReturn(List.of(new GlobalSearchDocument(
                        "purchase-order",
                        1L,
                        "CG20260001",
                        "供应商甲 / 采购A / 已审核",
                        false
                )));

        List<GlobalSearchResponse> results = service.search("CG20260001", 20, List.of("purchase-order", "sales-order"));

        ArgumentCaptor<List<GlobalSearchModuleAccess>> accessCaptor = ArgumentCaptor.forClass(List.class);
        verify(documentRepository).search(eq("CG20260001"), isNull(), eq(20), accessCaptor.capture());
        assertThat(accessCaptor.getValue()).singleElement().satisfies(access -> {
            assertThat(access.moduleKey()).isEqualTo("purchase-order");
            assertThat(access.ownerUserIds()).containsExactlyInAnyOrder(1L, 2L);
        });
        assertThat(results).singleElement().satisfies(item -> {
            assertThat(item.moduleKey()).isEqualTo("purchase-order");
            assertThat(item.primaryNo()).isEqualTo("CG20260001");
            assertThat(item.summary()).isEqualTo("供应商甲 / 采购A / 已审核");
            assertThat(item.matchedByTrackId()).isFalse();
        });
        assertThat(DataScopeContext.current()).isNull();
    }

    @Test
    void searchNormalizesCommaSeparatedModuleKeysAndLimitFloor() {
        PermissionService permissionService = mock(PermissionService.class);
        GlobalSearchDocumentRepository documentRepository = mock(GlobalSearchDocumentRepository.class);
        GlobalSearchService service = createService(permissionService, documentRepository);
        authenticate(1L);

        when(permissionService.can(anyLong(), anyString(), anyString())).thenReturn(false);
        when(permissionService.can(1L, "purchase-order", ResourcePermissionCatalog.READ)).thenReturn(true);
        when(permissionService.getUserDataScope(1L, "purchase-order", ResourcePermissionCatalog.READ))
                .thenReturn(ResourcePermissionCatalog.SCOPE_ALL);
        when(documentRepository.search(eq("CG20260001"), isNull(), eq(1), anyList()))
                .thenReturn(List.of(new GlobalSearchDocument(
                        "purchase-order",
                        1L,
                        "CG20260001",
                        "供应商甲",
                        false
                )));

        List<GlobalSearchResponse> results = service.search(
                "CG20260001",
                0,
                Arrays.asList(null, " purchase-order,unknown ", "", "sales-order")
        );

        ArgumentCaptor<List<GlobalSearchModuleAccess>> accessCaptor = ArgumentCaptor.forClass(List.class);
        verify(documentRepository).search(eq("CG20260001"), isNull(), eq(1), accessCaptor.capture());
        assertThat(accessCaptor.getValue()).singleElement().satisfies(access ->
                assertThat(access.moduleKey()).isEqualTo("purchase-order"));
        assertThat(results).hasSize(1);
    }

    @Test
    void searchUsesDefaultModulesWhenModuleKeysAreEmpty() {
        PermissionService permissionService = mock(PermissionService.class);
        ModulePermissionGuard modulePermissionGuard = mock(ModulePermissionGuard.class);
        GlobalSearchDocumentRepository documentRepository = mock(GlobalSearchDocumentRepository.class);
        GlobalSearchService service = new GlobalSearchService(
                permissionService,
                modulePermissionGuard,
                documentRepository
        );
        authenticate(1L);

        when(modulePermissionGuard.requireResourcePermission(any(), anyString(), eq(ResourcePermissionCatalog.READ)))
                .thenAnswer(invocation -> new ModulePermissionGuard.PermissionCheck(
                        invocation.getArgument(1, String.class),
                        invocation.getArgument(1, String.class),
                        ResourcePermissionCatalog.READ
                ));
        when(permissionService.getUserDataScope(eq(1L), anyString(), eq(ResourcePermissionCatalog.READ)))
                .thenReturn(ResourcePermissionCatalog.SCOPE_ALL);
        when(documentRepository.search(eq("keyword"), isNull(), eq(20), anyList()))
                .thenReturn(List.of());

        service.search("keyword", 20, List.of());

        ArgumentCaptor<List<GlobalSearchModuleAccess>> accessCaptor = ArgumentCaptor.forClass(List.class);
        verify(documentRepository).search(eq("keyword"), isNull(), eq(20), accessCaptor.capture());
        assertThat(accessCaptor.getValue()).hasSize(14);
    }

    @Test
    void searchThrowsWhenAuthenticationPrincipalHasUnexpectedType() {
        PermissionService permissionService = mock(PermissionService.class);
        GlobalSearchDocumentRepository documentRepository = mock(GlobalSearchDocumentRepository.class);
        GlobalSearchService service = createService(permissionService, documentRepository);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("leo", "", List.of())
        );

        assertThatThrownBy(() -> service.search("CG20260001", 20, List.of("purchase-order")))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(ErrorCode.UNAUTHORIZED))
                .hasMessageContaining("未登录");
    }

    @Test
    void searchReturnsEmptyWhenEveryRequestedModuleIsForbidden() {
        PermissionService permissionService = mock(PermissionService.class);
        GlobalSearchDocumentRepository documentRepository = mock(GlobalSearchDocumentRepository.class);
        GlobalSearchService service = createService(permissionService, documentRepository);
        authenticate(1L);

        when(permissionService.can(anyLong(), anyString(), anyString())).thenReturn(false);

        List<GlobalSearchResponse> results = service.search("CG20260001", 20, List.of("purchase-order"));

        assertThat(results).isEmpty();
        verify(documentRepository, never()).search(anyString(), isNull(), anyInt(), anyList());
    }

    @Test
    void searchUsesRepositoryTrackIdLookupForLongNumericKeyword() {
        PermissionService permissionService = mock(PermissionService.class);
        GlobalSearchDocumentRepository documentRepository = mock(GlobalSearchDocumentRepository.class);
        GlobalSearchService service = createService(permissionService, documentRepository);
        authenticate(1L);

        when(permissionService.can(anyLong(), anyString(), anyString())).thenReturn(false);
        when(permissionService.can(1L, "purchase-order", ResourcePermissionCatalog.READ)).thenReturn(true);
        when(permissionService.getUserDataScope(1L, "purchase-order", ResourcePermissionCatalog.READ))
                .thenReturn(ResourcePermissionCatalog.SCOPE_SELF);
        when(permissionService.getDataScopeOwnerUserIds(1L, ResourcePermissionCatalog.SCOPE_SELF))
                .thenReturn(Set.of(1L));
        when(documentRepository.search(eq("1914876201459236001"), eq(1914876201459236001L), eq(20), anyList()))
                .thenReturn(List.of(new GlobalSearchDocument(
                        "purchase-order",
                        1914876201459236001L,
                        "CG20260001",
                        "供应商甲 / 采购A / 已审核",
                        true
                )));

        List<GlobalSearchResponse> results = service.search("1914876201459236001", 20, List.of("purchase-order"));

        verify(documentRepository).search(eq("1914876201459236001"), eq(1914876201459236001L), eq(20), anyList());
        assertThat(results).singleElement().satisfies(item -> {
            assertThat(item.trackId()).isEqualTo("1914876201459236001");
            assertThat(item.primaryNo()).isEqualTo("CG20260001");
            assertThat(item.matchedByTrackId()).isTrue();
        });
        assertThat(DataScopeContext.current()).isNull();
    }

    @Test
    void searchSkipsRepositoryForOverflowTrackId() {
        PermissionService permissionService = mock(PermissionService.class);
        GlobalSearchDocumentRepository documentRepository = mock(GlobalSearchDocumentRepository.class);
        GlobalSearchService service = createService(permissionService, documentRepository);

        List<GlobalSearchResponse> results = service.search("999999999999999999999999", 20, List.of("purchase-order"));

        assertThat(results).isEmpty();
        verify(documentRepository, never()).search(anyString(), anyLong(), anyInt(), anyList());
    }

    @Test
    void searchThrowsWhenSecurityContextDoesNotContainPrincipal() {
        PermissionService permissionService = mock(PermissionService.class);
        GlobalSearchDocumentRepository documentRepository = mock(GlobalSearchDocumentRepository.class);
        GlobalSearchService service = createService(permissionService, documentRepository);

        assertThatThrownBy(() -> service.search("CG20260001", 20, List.of("purchase-order")))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(ErrorCode.UNAUTHORIZED))
                .hasMessageContaining("未登录");

        verifyNoInteractions(documentRepository);
    }

    @Test
    void searchPropagatesNonForbiddenPermissionFailure() {
        PermissionService permissionService = mock(PermissionService.class);
        ModulePermissionGuard modulePermissionGuard = mock(ModulePermissionGuard.class);
        GlobalSearchDocumentRepository documentRepository = mock(GlobalSearchDocumentRepository.class);
        GlobalSearchService service = new GlobalSearchService(
                permissionService,
                modulePermissionGuard,
                documentRepository
        );
        authenticate(1L);

        when(modulePermissionGuard.requireResourcePermission(any(), eq("purchase-order"), eq(ResourcePermissionCatalog.READ)))
                .thenThrow(new BusinessException(ErrorCode.INTERNAL_ERROR, "权限配置错误"));

        assertThatThrownBy(() -> service.search("CG20260001", 20, List.of("purchase-order")))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(ErrorCode.INTERNAL_ERROR))
                .hasMessageContaining("权限配置错误");

        verifyNoInteractions(documentRepository);
    }

    @Test
    void searchTreatsNonBusinessResourceAsAllDataScope() {
        PermissionService permissionService = mock(PermissionService.class);
        ModulePermissionGuard modulePermissionGuard = mock(ModulePermissionGuard.class);
        GlobalSearchDocumentRepository documentRepository = mock(GlobalSearchDocumentRepository.class);
        GlobalSearchService service = new GlobalSearchService(
                permissionService,
                modulePermissionGuard,
                documentRepository
        );
        authenticate(1L);

        when(modulePermissionGuard.requireResourcePermission(any(), eq("purchase-order"), eq(ResourcePermissionCatalog.READ)))
                .thenReturn(new ModulePermissionGuard.PermissionCheck(
                        "purchase-order",
                        "company-setting",
                        ResourcePermissionCatalog.READ
                ));
        when(documentRepository.search(eq("CG20260001"), isNull(), eq(20), anyList()))
                .thenReturn(List.of());

        service.search("CG20260001", 20, List.of("purchase-order"));

        ArgumentCaptor<List<GlobalSearchModuleAccess>> accessCaptor = ArgumentCaptor.forClass(List.class);
        verify(documentRepository).search(eq("CG20260001"), isNull(), eq(20), accessCaptor.capture());
        assertThat(accessCaptor.getValue()).singleElement().satisfies(access -> {
            assertThat(access.moduleKey()).isEqualTo("purchase-order");
            assertThat(access.ownerUserIds()).isNull();
        });
        verifyNoInteractions(permissionService);
    }

    @Test
    void searchFallsBackToPrincipalWhenDataScopeOwnersAreUnavailable() {
        PermissionService permissionService = mock(PermissionService.class);
        GlobalSearchDocumentRepository documentRepository = mock(GlobalSearchDocumentRepository.class);
        GlobalSearchService service = createService(permissionService, documentRepository);
        authenticate(9L);

        when(permissionService.can(9L, "purchase-order", ResourcePermissionCatalog.READ)).thenReturn(true);
        when(permissionService.getUserDataScope(9L, "purchase-order", ResourcePermissionCatalog.READ))
                .thenReturn(ResourcePermissionCatalog.SCOPE_DEPARTMENT);
        when(permissionService.getDataScopeOwnerUserIds(9L, ResourcePermissionCatalog.SCOPE_DEPARTMENT))
                .thenReturn(null);
        when(documentRepository.search(eq("CG20260001"), isNull(), eq(20), anyList()))
                .thenReturn(List.of());

        service.search("CG20260001", 20, List.of("purchase-order"));

        ArgumentCaptor<List<GlobalSearchModuleAccess>> accessCaptor = ArgumentCaptor.forClass(List.class);
        verify(documentRepository).search(eq("CG20260001"), isNull(), eq(20), accessCaptor.capture());
        assertThat(accessCaptor.getValue()).singleElement().satisfies(access ->
                assertThat(access.ownerUserIds()).containsExactly(9L));
    }

    @Test
    void searchFiltersDocumentsWithoutRecordIdAndDefaultsBlankFields() {
        PermissionService permissionService = mock(PermissionService.class);
        GlobalSearchDocumentRepository documentRepository = mock(GlobalSearchDocumentRepository.class);
        GlobalSearchService service = createService(permissionService, documentRepository);
        authenticate(1L);

        when(permissionService.can(1L, "purchase-order", ResourcePermissionCatalog.READ)).thenReturn(true);
        when(permissionService.getUserDataScope(1L, "purchase-order", ResourcePermissionCatalog.READ))
                .thenReturn(ResourcePermissionCatalog.SCOPE_ALL);
        when(documentRepository.search(eq("CG20260001"), isNull(), eq(20), anyList()))
                .thenReturn(List.of(
                        new GlobalSearchDocument("purchase-order", null, "IGNORED", "ignored", false),
                        new GlobalSearchDocument("unknown-module", 8L, "   ", null, false)
                ));

        List<GlobalSearchResponse> results = service.search("CG20260001", 20, List.of("purchase-order"));

        assertThat(results).singleElement().satisfies(item -> {
            assertThat(item.moduleKey()).isEqualTo("unknown-module");
            assertThat(item.title()).isEqualTo("unknown-module");
            assertThat(item.trackId()).isEqualTo("8");
            assertThat(item.primaryNo()).isEqualTo("8");
            assertThat(item.summary()).isEmpty();
        });
    }

    @Test
    void searchHonorsRequestedModuleKeys() {
        PermissionService permissionService = mock(PermissionService.class);
        GlobalSearchDocumentRepository documentRepository = mock(GlobalSearchDocumentRepository.class);
        GlobalSearchService service = createService(permissionService, documentRepository);
        authenticate(1L);

        when(permissionService.can(anyLong(), anyString(), anyString())).thenReturn(false);
        when(permissionService.can(1L, "sales-order", ResourcePermissionCatalog.READ)).thenReturn(true);
        when(permissionService.getUserDataScope(1L, "sales-order", ResourcePermissionCatalog.READ))
                .thenReturn(ResourcePermissionCatalog.SCOPE_ALL);
        when(documentRepository.search(eq("XS20260001"), isNull(), eq(20), anyList()))
                .thenReturn(List.of(new GlobalSearchDocument(
                        "sales-order",
                        2L,
                        "XS20260001",
                        "客户甲 / 项目甲 / 已审核",
                        false
                )));

        List<GlobalSearchResponse> results = service.search("XS20260001", 20, List.of("sales-order"));

        ArgumentCaptor<List<GlobalSearchModuleAccess>> accessCaptor = ArgumentCaptor.forClass(List.class);
        verify(documentRepository).search(eq("XS20260001"), isNull(), eq(20), accessCaptor.capture());
        assertThat(accessCaptor.getValue()).singleElement().satisfies(access -> {
            assertThat(access.moduleKey()).isEqualTo("sales-order");
            assertThat(access.ownerUserIds()).isNull();
        });
        assertThat(results).singleElement().satisfies(item ->
                assertThat(item.moduleKey()).isEqualTo("sales-order"));
    }

    @Test
    void searchExcludesDisabledLedgerAdjustmentModule() {
        PermissionService permissionService = mock(PermissionService.class);
        GlobalSearchDocumentRepository documentRepository = mock(GlobalSearchDocumentRepository.class);
        GlobalSearchService service = createService(permissionService, documentRepository);
        authenticate(1L);

        List<GlobalSearchResponse> results = service.search("TZ20260001", 20, List.of("ledger-adjustment"));

        assertThat(results).isEmpty();
        verifyNoInteractions(documentRepository);
    }

    @Test
    void privateHelpersHandleNullInputsDefensively() throws Exception {
        GlobalSearchService service = new GlobalSearchService(
                mock(PermissionService.class),
                mock(ModulePermissionGuard.class),
                mock(GlobalSearchDocumentRepository.class)
        );
        Method resolveModuleKeys = GlobalSearchService.class.getDeclaredMethod("resolveModuleKeys", Set.class);
        Method isLikelyTrackId = GlobalSearchService.class.getDeclaredMethod("isLikelyTrackId", String.class);
        resolveModuleKeys.setAccessible(true);
        isLikelyTrackId.setAccessible(true);

        assertThat((List<String>) resolveModuleKeys.invoke(service, new Object[]{null})).hasSize(14);
        assertThat((Boolean) isLikelyTrackId.invoke(service, new Object[]{null})).isFalse();
    }

    private GlobalSearchService createService(PermissionService permissionService,
                                              GlobalSearchDocumentRepository documentRepository) {
        return new GlobalSearchService(
                permissionService,
                new ModulePermissionGuard(permissionService),
                documentRepository
        );
    }

    private void authenticate(Long userId) {
        SecurityPrincipal principal = SecurityPrincipal.authenticated(
                userId,
                "leo",
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, "", principal.getAuthorities())
        );
    }
}
