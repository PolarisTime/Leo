package com.leo.erp.search.service;

import com.leo.erp.search.repository.GlobalSearchDocument;
import com.leo.erp.search.repository.GlobalSearchDocumentRepository;
import com.leo.erp.search.repository.GlobalSearchModuleAccess;
import com.leo.erp.search.web.GlobalSearchResponse;
import com.leo.erp.security.permission.DataScopeContext;
import com.leo.erp.security.permission.ModulePermissionGuard;
import com.leo.erp.security.permission.PermissionService;
import com.leo.erp.security.permission.ResourcePermissionCatalog;
import com.leo.erp.security.support.SecurityPrincipal;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
    void searchIncludesLedgerAdjustmentModule() {
        PermissionService permissionService = mock(PermissionService.class);
        GlobalSearchDocumentRepository documentRepository = mock(GlobalSearchDocumentRepository.class);
        GlobalSearchService service = createService(permissionService, documentRepository);
        authenticate(1L);

        when(permissionService.can(anyLong(), anyString(), anyString())).thenReturn(false);
        when(permissionService.can(1L, "ledger-adjustment", ResourcePermissionCatalog.READ)).thenReturn(true);
        when(permissionService.getUserDataScope(1L, "ledger-adjustment", ResourcePermissionCatalog.READ))
                .thenReturn(ResourcePermissionCatalog.SCOPE_SELF);
        when(permissionService.getDataScopeOwnerUserIds(1L, ResourcePermissionCatalog.SCOPE_SELF))
                .thenReturn(Set.of(1L));
        when(documentRepository.search(eq("TZ20260001"), isNull(), eq(20), anyList()))
                .thenReturn(List.of(new GlobalSearchDocument(
                        "ledger-adjustment",
                        3L,
                        "TZ20260001",
                        "客户甲 / 项目甲 / 已审核",
                        false
                )));

        List<GlobalSearchResponse> results = service.search("TZ20260001", 20, List.of("ledger-adjustment"));

        ArgumentCaptor<List<GlobalSearchModuleAccess>> accessCaptor = ArgumentCaptor.forClass(List.class);
        verify(documentRepository).search(eq("TZ20260001"), isNull(), eq(20), accessCaptor.capture());
        assertThat(accessCaptor.getValue()).singleElement().satisfies(access -> {
            assertThat(access.moduleKey()).isEqualTo("ledger-adjustment");
            assertThat(access.ownerUserIds()).containsExactly(1L);
        });
        assertThat(results).singleElement().satisfies(item -> {
            assertThat(item.moduleKey()).isEqualTo("ledger-adjustment");
            assertThat(item.title()).isEqualTo("台账调整单");
            assertThat(item.primaryNo()).isEqualTo("TZ20260001");
        });
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
