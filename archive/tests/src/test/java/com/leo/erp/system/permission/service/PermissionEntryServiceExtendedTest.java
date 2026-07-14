package com.leo.erp.system.permission.service;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.system.permission.web.dto.CatalogEntryResponse;
import com.leo.erp.system.permission.web.dto.PermissionEntryResponse;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PermissionEntryServiceExtendedTest {

    @Test
    void shouldReturnResourcePermissionCatalog() {
        PermissionEntryService service = new PermissionEntryService(null, null);

        List<CatalogEntryResponse> catalog = service.catalog();

        assertThat(catalog).isNotEmpty();
        assertThat(catalog)
                .extracting(CatalogEntryResponse::code)
                .contains("purchase-order", "sales-order", "role");
    }

    @Test
    void shouldThrowWhenPermissionEntryNotFound() {
        PermissionEntryService service = new PermissionEntryService(null, null);

        assertThatThrownBy(() -> service.detail(999999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("权限不存在");
    }

    @Test
    void shouldReturnAllEntriesWhenKeywordNull() {
        PermissionEntryService service = new PermissionEntryService(null, null);

        Page<PermissionEntryResponse> page = service.page(PageQuery.of(0, 200, "id", "asc"), null);

        assertThat(page.getTotalElements()).isGreaterThan(0);
    }

    @Test
    void shouldFilterByPermissionCodeKeyword() {
        PermissionEntryService service = new PermissionEntryService(null, null);

        Page<PermissionEntryResponse> page = service.page(PageQuery.of(0, 100, "id", "asc"), "purchase-order:read");

        assertThat(page.getContent()).isNotEmpty();
        assertThat(page.getContent())
                .extracting(PermissionEntryResponse::permissionCode)
                .allMatch(code -> code.contains("purchase-order"));
    }

    @Test
    void shouldSortByPermissionCodeAscending() {
        PermissionEntryService service = new PermissionEntryService(null, null);

        List<PermissionEntryResponse> records = service.page(
                PageQuery.of(0, 100, "permissionCode", "asc"), null
        ).getContent();

        for (int i = 1; i < records.size(); i++) {
            String prev = records.get(i - 1).permissionCode();
            String curr = records.get(i).permissionCode();
            assertThat(prev.compareToIgnoreCase(curr)).isLessThanOrEqualTo(0);
        }
    }

    @Test
    void shouldSortByPermissionName() {
        PermissionEntryService service = new PermissionEntryService(null, null);

        List<PermissionEntryResponse> records = service.page(
                PageQuery.of(0, 100, "permissionName", "asc"), null
        ).getContent();

        assertThat(records).isNotEmpty();
    }

    @Test
    void shouldSortByStatus() {
        PermissionEntryService service = new PermissionEntryService(null, null);

        List<PermissionEntryResponse> records = service.page(
                PageQuery.of(0, 100, "status", "desc"), null
        ).getContent();

        assertThat(records).isNotEmpty();
    }

    @Test
    void shouldSortByIdWhenSortByIsNull() {
        PermissionEntryService service = new PermissionEntryService(null, null);

        List<PermissionEntryResponse> records = service.page(
                PageQuery.of(0, 100, null, "asc"), null
        ).getContent();

        for (int i = 1; i < records.size(); i++) {
            long prev = records.get(i - 1).id();
            long curr = records.get(i).id();
            assertThat(prev).isLessThanOrEqualTo(curr);
        }
    }

    @Test
    void shouldSortDescendingByDefault() {
        PermissionEntryService service = new PermissionEntryService(null, null);

        List<PermissionEntryResponse> records = service.page(
                PageQuery.of(0, 100, "permissionCode", "desc"), null
        ).getContent();

        for (int i = 1; i < records.size(); i++) {
            String prev = records.get(i - 1).permissionCode();
            String curr = records.get(i).permissionCode();
            assertThat(prev.compareToIgnoreCase(curr)).isGreaterThanOrEqualTo(0);
        }
    }

    @Test
    void shouldReturnEmptyPageWhenBeyondTotalPages() {
        PermissionEntryService service = new PermissionEntryService(null, null);

        Page<PermissionEntryResponse> page = service.page(PageQuery.of(9999, 10, "id", "asc"), null);

        assertThat(page.getContent()).isEmpty();
    }
}
