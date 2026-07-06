package com.leo.erp.common.web;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class ApiRouteNamingNormalizationTest {

    @ParameterizedTest
    @CsvSource({
            "com.leo.erp.system.department.web.DepartmentController,options,/departments,/options,/option",
            "com.leo.erp.master.warehouse.web.WarehouseController,options,/warehouses,/options,/option",
            "com.leo.erp.master.customer.web.CustomerController,options,/customers,/options,/option",
            "com.leo.erp.master.supplier.web.SupplierController,options,/suppliers,/options,/option",
            "com.leo.erp.master.carrier.web.CarrierController,options,/carriers,/options,/option",
            "com.leo.erp.master.material.web.MaterialCategoryController,options,/material-categories,/options,/option",
            "com.leo.erp.auth.web.ApiKeyAdminController,userOptions,/auth/api-keys,/user-options,/user-option",
            "com.leo.erp.auth.web.ApiKeyAdminController,resourceOptions,/auth/api-keys,/resource-options,/resource-option",
            "com.leo.erp.auth.web.ApiKeyAdminController,actionOptions,/auth/api-keys,/action-options,/action-option",
            "com.leo.erp.system.role.web.RoleSettingController,listPermissionOptions,/role-settings,/permission-options,/permission-option",
            "com.leo.erp.statement.supplier.web.SupplierStatementController,candidates,/supplier-statements,/candidates,/candidate",
            "com.leo.erp.statement.customer.web.CustomerStatementController,candidates,/customer-statements,/candidates,/candidate",
            "com.leo.erp.statement.freight.web.FreightStatementController,candidates,/freight-statements,/candidates,/candidate",
            "com.leo.erp.purchase.order.web.PurchaseOrderController,importCandidates,/purchase-orders,/import-candidates,/import-candidate",
            "com.leo.erp.logistics.bill.web.FreightBillController,importCandidates,/freight-bills,/import-candidates,/import-candidate",
            "com.leo.erp.sales.order.web.SalesOrderController,outboundImportCandidates,/sales-orders,/outbound-import-candidates,/outbound-import-candidate",
            "com.leo.erp.master.material.web.MaterialController,materialGrades,/materials,/grades,/grade"
    })
    void shouldExposeOnlyPluralCollectionRoute(String controllerName,
                                               String methodName,
                                               String controllerPath,
                                               String expectedMethodPath,
                                               String legacyMethodPath) throws ClassNotFoundException {
        Class<?> controllerClass = Class.forName(controllerName);
        Method method = declaredMethod(controllerClass, methodName);

        assertThat(controllerClass.getAnnotation(RequestMapping.class).value())
                .containsExactly(controllerPath);
        assertThat(method.getAnnotation(GetMapping.class).value())
                .containsExactly(expectedMethodPath)
                .doesNotContain(legacyMethodPath);
    }

    private Method declaredMethod(Class<?> controllerClass, String methodName) {
        return java.util.Arrays.stream(controllerClass.getDeclaredMethods())
                .filter(method -> method.getName().equals(methodName))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing method " + methodName + " on " + controllerClass.getName()));
    }
}
