package com.leo.erp.master;

import com.leo.erp.master.carrier.web.dto.CarrierRequest;
import com.leo.erp.master.carrier.web.dto.VehicleItem;
import com.leo.erp.master.customer.web.dto.CustomerRequest;
import com.leo.erp.master.supplier.web.dto.SupplierRequest;
import com.leo.erp.master.warehouse.web.dto.WarehouseRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 基础资料请求字段长度校验测试。
 *
 * <p>回归背景：客户/供应商/物流商/仓库四类资料此前只有必填校验、没有 {@code @Size}，
 * 超长文本要到写库阶段才被数据库拒绝。虽然全局异常处理器会把
 * {@code DataIntegrityViolationException} 映射为 422 并提示「字段长度或数值超出允许范围」，
 * 但错误定位不到具体字段、且要浪费一次数据库往返。本测试锁定这些字段在绑定阶段即按
 * 数据库列长失败。</p>
 *
 * <p>断言方式是「违规字段名集合」，不依赖具体错误消息文案，避免文案调整导致测试脆断。</p>
 */
class MasterDataRequestSizeValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private Set<String> violatedFields(Object target) {
        Set<ConstraintViolation<Object>> violations = validator.validate(target);
        return violations.stream().map(v -> v.getPropertyPath().toString()).collect(Collectors.toSet());
    }

    private String repeat(int length) {
        return "A".repeat(length);
    }

    private CustomerRequest customer(String name, String phone) {
        return new CustomerRequest("C-1", name, null, phone, null, null, null, null, null, "正常", null);
    }

    private SupplierRequest supplier(String name, String shortName) {
        return new SupplierRequest("S-1", name, shortName, null, null, null, "正常", null);
    }

    private WarehouseRequest warehouse(String name, String type) {
        return new WarehouseRequest("W-1", name, type, null, null, null, "正常", null);
    }

    private CarrierRequest carrier(String name, List<VehicleItem> vehicles) {
        return new CarrierRequest("L-1", name, null, null, null, vehicles, null, 1L, "正常", null);
    }

    // ---------- 客户 ----------

    @Test
    void customerNameOver128_shouldBeRejectedAtBindStage() {
        assertThat(violatedFields(customer(repeat(129), null))).contains("customerName");
    }

    @Test
    void customerNameAt128_shouldBeAccepted() {
        assertThat(violatedFields(customer(repeat(128), null))).isEmpty();
    }

    @Test
    void customerPhoneOver20_shouldBeRejected() {
        assertThat(violatedFields(customer("正常客户", repeat(21)))).contains("contactPhone");
    }

    // ---------- 供应商 ----------

    @Test
    void supplierNameOver128_shouldBeRejected() {
        assertThat(violatedFields(supplier(repeat(129), null))).contains("supplierName");
    }

    @Test
    void supplierShortNameOver64_shouldBeRejected() {
        // md_supplier.short_name 列长仅 64
        assertThat(violatedFields(supplier("正常供应商", repeat(65)))).contains("shortName");
    }

    // ---------- 仓库 ----------

    @Test
    void warehouseNameOver128_shouldBeRejected() {
        assertThat(violatedFields(warehouse(repeat(129), "第三方仓"))).contains("warehouseName");
    }

    @Test
    void warehouseTypeOver32_shouldBeRejected() {
        assertThat(violatedFields(warehouse("正常仓库", repeat(33)))).contains("warehouseType");
    }

    // ---------- 物流商 ----------

    @Test
    void carrierNameOver128_shouldBeRejected() {
        assertThat(violatedFields(carrier(repeat(129), null))).contains("carrierName");
    }

    @Test
    void vehicleRemarkOver64_shouldBeRejected() {
        // md_vehicle.remark 列长仅 64, 比多数资料的 255 更短, 容易漏
        VehicleItem item = new VehicleItem(null, "沪A1234", null, null, repeat(65));
        assertThat(violatedFields(carrier("正常物流商", List.of(item))))
                .contains("vehicles[0].remark");
    }

    @Test
    void vehiclePlateOver16_shouldBeRejected() {
        VehicleItem item = new VehicleItem(null, repeat(17), null, null, null);
        assertThat(violatedFields(carrier("正常物流商", List.of(item))))
                .contains("vehicles[0].plate");
    }

    @Test
    void validVehicle_shouldBeAccepted() {
        VehicleItem item = new VehicleItem(null, "沪A1234", "司机", "13900000000", "备注");
        assertThat(violatedFields(carrier("正常物流商", List.of(item)))).isEmpty();
    }

    // ---------- 不误伤：正常值必须通过 ----------

    @Test
    void normalValuesAcrossAllMasterData_shouldPass() {
        assertThat(violatedFields(customer("浙江景华建设有限公司", "13800000000"))).isEmpty();
        assertThat(violatedFields(supplier("正常供应商", "简称"))).isEmpty();
        assertThat(violatedFields(warehouse("升大库", "第三方仓"))).isEmpty();
        assertThat(violatedFields(carrier("正常物流商", null))).isEmpty();
    }
}
