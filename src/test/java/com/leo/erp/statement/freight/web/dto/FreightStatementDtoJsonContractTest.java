package com.leo.erp.statement.freight.web.dto;

import com.leo.erp.common.config.JacksonConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FreightStatement 包 DTO 对外 JSON 契约冻结测试。
 * <p>
 * 使用生产 JacksonConfig customizer（Long→String、BigDecimal 定标、ISO 日期），
 * 逐字节冻结 Request/Response 的序列化与反序列化契约，
 * 作为未来 DTO 去重改造（如 {@code @JsonUnwrapped}）的前后对比基线。
 */
class FreightStatementDtoJsonContractTest {

    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        Jackson2ObjectMapperBuilder builder = Jackson2ObjectMapperBuilder.json();
        new JacksonConfig("Asia/Shanghai").jackson2ObjectMapperBuilderCustomizer().customize(builder);
        objectMapper = builder.build();
    }

    private FreightStatementItemResponse sampleItemResponse() {
        return new FreightStatementItemResponse(
                121212121212121212L, 1, "FB-2026-001", 888777666555444333L, "结算公司A",
                666555444333222111L, "客户乙", 777666555444333222L, "项目丙",
                444333222111000999L, "MAT-001", "螺纹钢", "品牌X", "分类Y", "材质Z", "Φ12",
                "9", 100, "件", new BigDecimal("0.028"), 12, "B20260901",
                "b20260901", new BigDecimal("2.8"), 333222111000444555L, "仓库D",
                111222333444555666L, 131313131313131313L, 141414141414141414L,
                new BigDecimal("350"), new BigDecimal("980"),
                LocalDate.of(2026, 9, 1));
    }

    @Test
    void responseSerializationIsByteStable() throws Exception {
        FreightStatementResponse response = new FreightStatementResponse(
                1234567890123456789L, "FS-2026-001", "CARR-001", "承运商E", 888777666555444333L, "结算公司A",
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31),
                new BigDecimal("28"), new BigDecimal("9800"),
                new BigDecimal("5000"), new BigDecimal("4800"),
                "PENDING_APPROVAL", false, "a.pdf", List.of(), "备注",
                List.of(sampleItemResponse()), 151515151515151515L);

        String json = objectMapper.writeValueAsString(response);

        assertThat(json).isEqualTo("{\"id\":\"1234567890123456789\",\"statementNo\":\"FS-2026-001\","
                + "\"carrierCode\":\"CARR-001\",\"carrierName\":\"承运商E\","
                + "\"settlementCompanyId\":\"888777666555444333\",\"settlementCompanyName\":\"结算公司A\","
                + "\"startDate\":\"2026-08-01\",\"endDate\":\"2026-08-31\","
                + "\"totalWeight\":28.00000000,\"totalFreight\":9800.00,"
                + "\"paidAmount\":5000.00,\"unpaidAmount\":4800.00,\"status\":\"PENDING_APPROVAL\","
                + "\"deletedFlag\":false,\"attachment\":\"a.pdf\",\"attachments\":[],\"remark\":\"备注\","
                + "\"items\":[{\"id\":\"121212121212121212\",\"lineNo\":1,\"sourceNo\":\"FB-2026-001\","
                + "\"settlementCompanyId\":\"888777666555444333\",\"settlementCompanyName\":\"结算公司A\","
                + "\"customerId\":\"666555444333222111\",\"customerName\":\"客户乙\","
                + "\"projectId\":\"777666555444333222\",\"projectName\":\"项目丙\","
                + "\"materialId\":\"444333222111000999\",\"materialCode\":\"MAT-001\","
                + "\"materialName\":\"螺纹钢\",\"brand\":\"品牌X\",\"category\":\"分类Y\","
                + "\"material\":\"材质Z\",\"spec\":\"Φ12\",\"length\":\"9\",\"quantity\":100,"
                + "\"quantityUnit\":\"件\",\"pieceWeightTon\":0.02800000,\"piecesPerBundle\":12,"
                + "\"batchNo\":\"B20260901\",\"batchNoNormalized\":\"b20260901\","
                + "\"weightTon\":2.80000000,\"warehouseId\":\"333222111000444555\","
                + "\"warehouseName\":\"仓库D\",\"sourceFreightBillId\":\"111222333444555666\","
                + "\"sourceFreightBillItemId\":\"131313131313131313\","
                + "\"sourceSalesOrderItemId\":\"141414141414141414\","
                + "\"sourceFreightBillUnitPrice\":350.00,\"sourceFreightBillTotalFreight\":980.00,"
                + "\"sourceFreightBillTime\":\"2026-09-01\"}],\"carrierId\":\"151515151515151515\"}");
    }

    @Test
    void requestDeserializationIsFieldStable() throws Exception {
        String json = "{\"statementNo\":\"FS-2026-001\",\"carrierCode\":\"CARR-001\",\"carrierName\":\"承运商E\","
                + "\"settlementCompanyId\":\"888777666555444333\",\"settlementCompanyName\":\"结算公司A\","
                + "\"startDate\":\"2026-08-01\",\"endDate\":\"2026-08-31\","
                + "\"totalWeight\":28.00000000,\"totalFreight\":9800.00,"
                + "\"paidAmount\":5000.00,\"unpaidAmount\":4800.00,\"status\":\"PENDING_APPROVAL\","
                + "\"attachment\":\"a.pdf\",\"remark\":\"备注\","
                + "\"items\":[{\"id\":\"121212121212121212\",\"sourceNo\":\"FB-2026-001\","
                + "\"settlementCompanyId\":\"888777666555444333\",\"settlementCompanyName\":\"结算公司A\","
                + "\"customerId\":\"666555444333222111\",\"customerName\":\"客户乙\","
                + "\"projectId\":\"777666555444333222\",\"projectName\":\"项目丙\","
                + "\"materialId\":\"444333222111000999\",\"materialCode\":\"MAT-001\","
                + "\"materialName\":\"螺纹钢\",\"brand\":\"品牌X\",\"category\":\"分类Y\","
                + "\"material\":\"材质Z\",\"spec\":\"Φ12\",\"length\":\"9\",\"quantity\":100,"
                + "\"quantityUnit\":\"件\",\"pieceWeightTon\":0.02800000,\"piecesPerBundle\":12,"
                + "\"batchNo\":\"B20260901\",\"weightTon\":2.80000000,"
                + "\"warehouseId\":\"333222111000444555\",\"warehouseName\":\"仓库D\","
                + "\"sourceFreightBillId\":\"111222333444555666\","
                + "\"sourceFreightBillItemId\":\"131313131313131313\","
                + "\"sourceSalesOrderItemId\":\"141414141414141414\"}],"
                + "\"carrierId\":\"151515151515151515\",\"audit\":true}";

        FreightStatementRequest request = objectMapper.readValue(json, FreightStatementRequest.class);

        assertThat(request.statementNo()).isEqualTo("FS-2026-001");
        assertThat(request.carrierCode()).isEqualTo("CARR-001");
        assertThat(request.carrierName()).isEqualTo("承运商E");
        assertThat(request.settlementCompanyId()).isEqualTo(888777666555444333L);
        assertThat(request.settlementCompanyName()).isEqualTo("结算公司A");
        assertThat(request.startDate()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(request.endDate()).isEqualTo(LocalDate.of(2026, 8, 31));
        assertThat(request.totalWeight()).isEqualByComparingTo("28.00000000");
        assertThat(request.totalFreight()).isEqualByComparingTo("9800.00");
        assertThat(request.paidAmount()).isEqualByComparingTo("5000.00");
        assertThat(request.unpaidAmount()).isEqualByComparingTo("4800.00");
        assertThat(request.status()).isEqualTo("PENDING_APPROVAL");
        assertThat(request.attachment()).isEqualTo("a.pdf");
        assertThat(request.remark()).isEqualTo("备注");
        assertThat(request.carrierId()).isEqualTo(151515151515151515L);
        assertThat(request.audit()).isTrue();
        assertThat(request.items()).hasSize(1);
        FreightStatementItemRequest item = request.items().get(0);
        assertThat(item.id()).isEqualTo(121212121212121212L);
        assertThat(item.sourceNo()).isEqualTo("FB-2026-001");
        assertThat(item.settlementCompanyId()).isEqualTo(888777666555444333L);
        assertThat(item.settlementCompanyName()).isEqualTo("结算公司A");
        assertThat(item.customerId()).isEqualTo(666555444333222111L);
        assertThat(item.customerName()).isEqualTo("客户乙");
        assertThat(item.projectId()).isEqualTo(777666555444333222L);
        assertThat(item.projectName()).isEqualTo("项目丙");
        assertThat(item.materialId()).isEqualTo(444333222111000999L);
        assertThat(item.materialCode()).isEqualTo("MAT-001");
        assertThat(item.materialName()).isEqualTo("螺纹钢");
        assertThat(item.brand()).isEqualTo("品牌X");
        assertThat(item.category()).isEqualTo("分类Y");
        assertThat(item.material()).isEqualTo("材质Z");
        assertThat(item.spec()).isEqualTo("Φ12");
        assertThat(item.length()).isEqualTo("9");
        assertThat(item.quantity()).isEqualTo(100);
        assertThat(item.quantityUnit()).isEqualTo("件");
        assertThat(item.pieceWeightTon()).isEqualByComparingTo("0.028");
        assertThat(item.piecesPerBundle()).isEqualTo(12);
        assertThat(item.batchNo()).isEqualTo("B20260901");
        assertThat(item.weightTon()).isEqualByComparingTo("2.8");
        assertThat(item.warehouseId()).isEqualTo(333222111000444555L);
        assertThat(item.warehouseName()).isEqualTo("仓库D");
        assertThat(item.sourceFreightBillId()).isEqualTo(111222333444555666L);
        assertThat(item.sourceFreightBillItemId()).isEqualTo(131313131313131313L);
        assertThat(item.sourceSalesOrderItemId()).isEqualTo(141414141414141414L);
    }

    /**
     * 反序列化会按字段名重新应用 BigDecimal 定标（如 amount: 1000 → 1000.00），
     * record equals 对 scale 敏感，因此以再序列化 JSON 逐字节一致为等价标准。
     */
    @Test
    void responseRoundTripKeepsJsonStable() throws Exception {
        FreightStatementResponse response = new FreightStatementResponse(
                1234567890123456789L, "FS-2026-001", "CARR-001", "承运商E", 888777666555444333L, "结算公司A",
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31),
                new BigDecimal("28"), new BigDecimal("9800"),
                new BigDecimal("5000"), new BigDecimal("4800"),
                "PENDING_APPROVAL", false, "a.pdf", List.of(), "备注",
                List.of(sampleItemResponse()), 151515151515151515L);

        FreightStatementResponse deserialized = objectMapper.readValue(
                objectMapper.writeValueAsString(response), FreightStatementResponse.class);

        assertThat(objectMapper.writeValueAsString(deserialized))
                .isEqualTo(objectMapper.writeValueAsString(response));
    }
}
