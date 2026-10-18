package com.leo.erp.system.printtemplate.service;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.security.permission.PermissionChecker;
import com.leo.erp.security.permission.PermissionCodes;
import com.leo.erp.system.printtemplate.repository.PrintTemplateRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 打印服务在数据加载前必须先过模块读权限的回归测试（集成点，使用真实 PrintRecordAuthorization）。
 */
@ExtendWith(MockitoExtension.class)
class PrintScriptServiceRecordAuthorizationTest {

    @Mock
    private PermissionChecker permissionChecker;

    @Mock
    private PrintTemplateRepository templateRepository;

    @Mock
    private PrintRecordDataProvider dataProvider;

    @Mock
    private PrintRecordEnricher recordEnricher;

    @Mock
    private PrintRecordLayoutPreparer layoutPreparer;

    @Mock
    private PrintLayoutLodopRenderer layoutLodopRenderer;

    @Mock
    private com.leo.erp.attachment.api.AttachmentRecordAccess recordAccessService;

    @Mock
    private PrintRuntimeProperties runtimeProperties;

    private PrintScriptService printScriptService;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        PrintRecordAuthorization authorization = new PrintRecordAuthorization(permissionChecker);
        printScriptService = new PrintScriptService(
                templateRepository, dataProvider, recordEnricher, layoutPreparer,
                layoutLodopRenderer, recordAccessService, runtimeProperties, authorization);
    }

    @Test
    void listPrintItems_shouldCheckModuleReadPermissionBeforeLoadingAnyRecord() {
        doThrow(new AccessDeniedException("缺少权限: sales-orders:read"))
                .when(permissionChecker).require(PermissionCodes.SALES_ORDERS_READ);

        assertThatThrownBy(() -> printScriptService.listPrintItems("sales-order", List.of(1L)))
                .isInstanceOf(AccessDeniedException.class);

        // 授权失败时不得触碰数据源：越权请求不能读取到任何记录数据
        verify(dataProvider, never()).listPrintItems(anyString(), org.mockito.ArgumentMatchers.anyList());
        verifyNoInteractions(recordAccessService);
    }

    @Test
    void pagePrintItems_shouldCheckModuleReadPermissionBeforePaging() {
        doThrow(new AccessDeniedException("缺少权限: sales-orders:read"))
                .when(permissionChecker).require(PermissionCodes.SALES_ORDERS_READ);

        assertThatThrownBy(() -> printScriptService.pagePrintItems("sales-order", List.of(1L), new PageQuery(0, 10, null, null)))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(dataProvider);
    }

    @Test
    void listPrintItems_shouldProceedWhenModuleReadPermissionGranted() {
        when(dataProvider.listPrintItems("sales-order", List.of(1L))).thenReturn(List.of());

        assertThat(printScriptService.listPrintItems("sales-order", List.of(1L))).isEmpty();

        verify(permissionChecker).require(PermissionCodes.SALES_ORDERS_READ);
        verify(dataProvider).listPrintItems("sales-order", List.of(1L));
    }
}
