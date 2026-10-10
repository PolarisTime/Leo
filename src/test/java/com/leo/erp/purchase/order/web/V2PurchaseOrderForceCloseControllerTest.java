package com.leo.erp.purchase.order.web;

import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.purchase.order.service.PurchaseOrderForceCloseService;
import com.leo.erp.purchase.order.service.PurchaseOrderOptionService;
import com.leo.erp.purchase.order.service.PurchaseOrderPickupListService;
import com.leo.erp.purchase.order.service.PurchaseOrderService;
import com.leo.erp.purchase.order.service.PurchaseOrderWarehouseRecommendationService;
import com.leo.erp.purchase.order.web.dto.PurchaseOrderForceCloseRequest;
import com.leo.erp.purchase.order.web.dto.PurchaseOrderResponse;
import com.leo.erp.security.permission.PermissionCodes;
import com.leo.erp.security.permission.RequirePermission;
import com.leo.erp.security.support.SecurityPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 采购订单强制结单端点契约测试: 201/204 语义、Location 指向子资源、操作人透传、
 * 以及权限码必须挂在两个端点上(避免被误放宽为编辑权)。
 */
@ExtendWith(MockitoExtension.class)
class V2PurchaseOrderForceCloseControllerTest {

    @Mock
    private PurchaseOrderService purchaseOrderService;

    @Mock
    private PurchaseOrderPickupListService pickupListService;

    @Mock
    private PurchaseOrderWarehouseRecommendationService warehouseRecommendationService;

    @Mock
    private PurchaseOrderOptionService purchaseOrderOptionService;

    @Mock
    private PurchaseOrderForceCloseService forceCloseService;

    private V2PurchaseOrderController controller;

    private static final SecurityPrincipal OPERATOR =
            SecurityPrincipal.authenticated(9L, "admin_prod", 0L);

    @BeforeEach
    void setUp() {
        controller = new V2PurchaseOrderController(
                purchaseOrderService,
                pickupListService,
                warehouseRecommendationService,
                purchaseOrderOptionService,
                forceCloseService
        );
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(new MockHttpServletRequest()));
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    private PurchaseOrderResponse response() {
        return new PurchaseOrderResponse(
                1L, "PO-1", null, null, "供应商", null, null, null, null,
                null, null, StatusConstants.PURCHASE_COMPLETED, false, null, List.of(), List.of()
        );
    }

    @Test
    void forceClose_shouldReturnCreatedWithSubResourceLocation() {
        when(forceCloseService.forceClose(eq(1L), eq("剩余 1 件报废"), eq(OPERATOR)))
                .thenReturn(response());

        ResponseEntity<PurchaseOrderResponse> result = controller.forceClose(
                1L,
                new PurchaseOrderForceCloseRequest("剩余 1 件报废"),
                OPERATOR
        );

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getHeaders().getLocation()).isNotNull();
        // 子资源无独立可回读路径: Location 指向父订单(与销售订单 completions 一致)
        assertThat(result.getHeaders().getLocation().getPath())
                .endsWith("/v2.0/purchase-orders/1");
        verify(forceCloseService).forceClose(1L, "剩余 1 件报废", OPERATOR);
    }

    @Test
    void cancelForceClose_shouldReturnNoContent() {
        ResponseEntity<Void> result = controller.cancelForceClose(1L, OPERATOR);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(result.getBody()).isNull();
        verify(forceCloseService).cancelForceClose(1L, OPERATOR);
    }

    @Test
    void forceCloseEndpoints_shouldRequireForceClosePermission() throws Exception {
        assertPermission("forceClose", Long.class, PurchaseOrderForceCloseRequest.class, SecurityPrincipal.class);
        assertPermission("cancelForceClose", Long.class, SecurityPrincipal.class);
    }

    private void assertPermission(String methodName, Class<?>... parameterTypes) throws Exception {
        Method method = V2PurchaseOrderController.class
                .getDeclaredMethod(methodName, parameterTypes);
        RequirePermission annotation = method.getAnnotation(RequirePermission.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).containsExactly(PermissionCodes.PURCHASE_ORDERS_FORCE_CLOSE);
    }
}
