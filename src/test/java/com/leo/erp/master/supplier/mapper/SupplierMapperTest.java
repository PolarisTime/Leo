package com.leo.erp.master.supplier.mapper;

import com.leo.erp.master.supplier.domain.entity.Supplier;
import com.leo.erp.master.supplier.web.dto.SupplierResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SupplierMapperTest {

    private final SupplierMapper mapper = new SupplierMapperImpl();

    @Test
    void shouldMapAllFieldsToResponse() {
        Supplier supplier = new Supplier();
        supplier.setId(1L);
        supplier.setSupplierCode("S001");
        supplier.setSupplierName("供应商甲");
        supplier.setContactName("张三");
        supplier.setContactPhone("13800138000");
        supplier.setCity("北京");
        supplier.setStatus("正常");
        supplier.setRemark("测试备注");

        SupplierResponse response = mapper.toResponse(supplier);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.supplierCode()).isEqualTo("S001");
        assertThat(response.supplierName()).isEqualTo("供应商甲");
        assertThat(response.contactName()).isEqualTo("张三");
        assertThat(response.contactPhone()).isEqualTo("13800138000");
        assertThat(response.city()).isEqualTo("北京");
        assertThat(response.status()).isEqualTo("正常");
        assertThat(response.remark()).isEqualTo("测试备注");
    }

    @Test
    void shouldMapNullFieldsToNull() {
        Supplier supplier = new Supplier();
        supplier.setId(1L);
        supplier.setSupplierCode("S001");

        SupplierResponse response = mapper.toResponse(supplier);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.supplierCode()).isEqualTo("S001");
        assertThat(response.contactName()).isNull();
        assertThat(response.city()).isNull();
    }
}
