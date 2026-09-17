package com.leo.erp.sales.contract.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.erp.common.config.JacksonConfig;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.sales.contract.domain.entity.SalesContract;
import com.leo.erp.sales.contract.repository.SalesContractRepository;
import com.leo.erp.sales.contract.repository.SalesOrderContractMetricsRepository;
import com.leo.erp.sales.contract.web.dto.SalesContractCheckResponse;
import com.leo.erp.sales.contract.web.dto.SalesContractResponse;
import com.leo.erp.sales.order.domain.entity.SalesOrder;
import com.leo.erp.sales.order.repository.SalesOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 真实 PostgreSQL 累计口径回归(默认跳过, 设置 {@code LEO_TEST_POSTGRES=true} 才执行)。
 * <p>覆盖: 已审核/已发出/归档合同求和、草稿与作废不计入、已删除合同/订单不计入、排除当前订单、
 * 跨项目不计入、边界刚好等于/超出, 以及雪花 ID 持久化与字符串出参。</p>
 * <p>用例自带隔离的客户/项目主数据(测试事务回滚), 不依赖开发库既有业务数据。</p>
 */
@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.flyway.enabled=false",
        "spring.jpa.show-sql=false",
        "spring.jpa.properties.hibernate.default_batch_fetch_size=50"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "LEO_TEST_POSTGRES", matches = "true")
class SalesOrderContractCheckPostgresTest {

    private static final long CUSTOMER_ID = 941000000000000001L;
    private static final long PROJECT_ID = 941000000000000011L;
    private static final long OTHER_PROJECT_ID = 941000000000000012L;
    private static final long CONTRACT_AUDITED = 941000000000000101L;
    private static final long CONTRACT_ISSUED = 941000000000000102L;
    private static final long CONTRACT_ARCHIVED = 941000000000000103L;
    private static final long CONTRACT_VOIDED = 941000000000000104L;
    private static final long CONTRACT_DRAFT = 941000000000000105L;
    private static final long CONTRACT_DELETED = 941000000000000106L;
    private static final long ORDER_1 = 941000000000000201L;
    private static final long ORDER_2 = 941000000000000202L;
    private static final long ORDER_DELETED = 941000000000000203L;
    private static final long ORDER_OTHER_PROJECT = 941000000000000204L;

    @Autowired
    private SalesContractRepository contractRepository;

    @Autowired
    private SalesOrderRepository salesOrderRepository;

    @Autowired
    private SalesOrderContractMetricsRepository metricsRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void seedMasterData() {
        jdbc.update("""
                insert into md_customer
                    (id, customer_code, customer_name, project_name, status, created_by, created_name, created_at,
                     deleted_flag, version)
                values (?, ?, 'PG客户', 'PG项目', '正常', 0, 'system', now(), false, 0)
                """, CUSTOMER_ID, "PG-C-" + CUSTOMER_ID);
        insertProject(PROJECT_ID);
        insertProject(OTHER_PROJECT_ID);
    }

    @Test
    void check_sumsAuditedIssuedArchivedContracts_ignoringDraftVoidedDeletedAndOtherProjects() {
        persistContract(CONTRACT_AUDITED, StatusConstants.AUDITED, "100.00", "10.00000000", PROJECT_ID, false);
        persistContract(CONTRACT_ISSUED, StatusConstants.ISSUED, "40.00", "4.00000000", PROJECT_ID, false);
        persistContract(CONTRACT_ARCHIVED, StatusConstants.ARCHIVED, "10.00", "1.00000000", PROJECT_ID, false);
        persistContract(CONTRACT_VOIDED, StatusConstants.VOIDED, "999.00", "999.00000000", PROJECT_ID, false);
        persistContract(CONTRACT_DRAFT, StatusConstants.DRAFT, "888.00", "888.00000000", PROJECT_ID, false);
        persistContract(CONTRACT_DELETED, StatusConstants.AUDITED, "777.00", "77.00000000", PROJECT_ID, true);
        persistOrder(ORDER_1, PROJECT_ID, "30.00", "3.00000000", false);
        persistOrder(ORDER_2, PROJECT_ID, "20.00", "2.00000000", false);
        persistOrder(ORDER_DELETED, PROJECT_ID, "500.00", "50.00000000", true);
        persistOrder(ORDER_OTHER_PROJECT, OTHER_PROJECT_ID, "777.00", "77.00000000", false);
        contractRepository.flush();
        salesOrderRepository.flush();

        SalesContractCheckResponse response = service().check(
                PROJECT_ID, new BigDecimal("10.00"), new BigDecimal("1.00000000"), ORDER_2);

        assertThat(response.hasContract()).isTrue();
        assertThat(response.contractAmount()).isEqualByComparingTo("150.00");
        assertThat(response.contractTonnage()).isEqualByComparingTo("15.00000000");
        assertThat(response.usedAmount()).isEqualByComparingTo("40.00");
        assertThat(response.usedTonnage()).isEqualByComparingTo("4.00000000");
        assertThat(response.remainingAmount()).isEqualByComparingTo("110.00");
        assertThat(response.remainingTonnage()).isEqualByComparingTo("11.00000000");
        assertThat(response.exceededAmount()).isEqualByComparingTo("0");
        assertThat(response.exceededTonnage()).isEqualByComparingTo("0");
    }

    @Test
    void check_noQuotaContract_returnsZerosEvenWhenOrdersExist() {
        persistContract(CONTRACT_VOIDED, StatusConstants.VOIDED, "999.00", "999.00000000", PROJECT_ID, false);
        persistContract(CONTRACT_DRAFT, StatusConstants.DRAFT, "888.00", "888.00000000", PROJECT_ID, false);
        persistOrder(ORDER_1, PROJECT_ID, "30.00", "3.00000000", false);
        contractRepository.flush();
        salesOrderRepository.flush();

        SalesContractCheckResponse response = service().check(PROJECT_ID,
                new BigDecimal("50.00"), BigDecimal.ONE, null);

        assertThat(response.hasContract()).isFalse();
        assertThat(response.contractAmount()).isEqualByComparingTo("0");
        assertThat(response.usedAmount()).isEqualByComparingTo("0");
        assertThat(response.remainingAmount()).isEqualByComparingTo("0");
        assertThat(response.exceededAmount()).isEqualByComparingTo("0");
        assertThat(response.message()).contains("未关联有效销售合同");
    }

    @Test
    void check_exactlyAtBoundary_isNotExceeded_andOneCentOverIsExceeded() {
        persistContract(CONTRACT_AUDITED, StatusConstants.AUDITED, "100.00", "10.00000000", PROJECT_ID, false);
        persistOrder(ORDER_1, PROJECT_ID, "90.00", "9.00000000", false);
        contractRepository.flush();
        salesOrderRepository.flush();

        SalesContractCheckResponse exact = service().check(
                PROJECT_ID, new BigDecimal("10.00"), new BigDecimal("1.00000000"), null);
        assertThat(exact.remainingAmount()).isEqualByComparingTo("0");
        assertThat(exact.exceededAmount()).isEqualByComparingTo("0");
        assertThat(exact.message()).isEqualTo("合同额度充足");

        SalesContractCheckResponse over = service().check(
                PROJECT_ID, new BigDecimal("10.01"), new BigDecimal("1.00000000"), null);
        assertThat(over.remainingAmount()).isEqualByComparingTo("-0.01");
        assertThat(over.exceededAmount()).isEqualByComparingTo("0.01");
        assertThat(over.message()).contains("已超出合同金额");
    }

    @Test
    void archivedContract_referenceProbe_reflectsActiveOrders() {
        persistContract(CONTRACT_ARCHIVED, StatusConstants.ARCHIVED, "10.00", "1.00000000", PROJECT_ID, false);
        persistOrder(ORDER_DELETED, PROJECT_ID, "500.00", "50.00000000", true);
        contractRepository.flush();
        salesOrderRepository.flush();

        assertThat(metricsRepository.existsByProjectIdAndDeletedFlagFalse(PROJECT_ID)).isFalse();

        persistOrder(ORDER_1, PROJECT_ID, "30.00", "3.00000000", false);
        salesOrderRepository.flush();

        assertThat(metricsRepository.existsByProjectIdAndDeletedFlagFalse(PROJECT_ID)).isTrue();
    }

    @Test
    void snowflakeId_isPersistedAndEmittedAsDecimalString() throws Exception {
        long largeId = 9223372036854775807L;
        persistContract(largeId, StatusConstants.AUDITED, "1.00", "1.00000000", PROJECT_ID, false);
        contractRepository.flush();

        SalesContract stored = contractRepository.findById(largeId).orElseThrow();
        assertThat(stored.getId()).isEqualTo(largeId);

        SalesContractResponse response =
                new SalesContractResponse(
                        stored.getId(), stored.getContractNo(), stored.getName(),
                        stored.getCustomerId(), stored.getCustomerName(),
                        stored.getProjectId(), stored.getProjectName(),
                        stored.getSignDate(), stored.getStartDate(), stored.getEndDate(),
                        stored.getTotalAmount(), stored.getTotalTonnage(), stored.getStatus(),
                        stored.getRemark(), null, null, stored.getVersion());

        ObjectMapper objectMapper = objectMapper();
        String json = objectMapper.writeValueAsString(response);

        assertThat(json).contains("\"id\":\"9223372036854775807\"");
        assertThat(json).contains("\"customerId\":\"941000000000000001\"");
        assertThat(json).contains("\"projectId\":\"941000000000000011\"");
    }

    private ObjectMapper objectMapper() {
        Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
        new JacksonConfig("Asia/Shanghai").jackson2ObjectMapperBuilderCustomizer().customize(builder);
        return builder.build();
    }

    private SalesOrderContractCheckService service() {
        return new SalesOrderContractCheckService(contractRepository, metricsRepository);
    }

    private void insertProject(long projectId) {
        jdbc.update("""
                insert into md_project
                    (id, project_code, project_name, customer_code, status, created_by, created_name, created_at,
                     deleted_flag, version, customer_id)
                values (?, ?, 'PG项目', ?, '正常', 0, 'system', now(), false, 0, ?)
                """, projectId, "PG-P-" + projectId, "PG-C-" + CUSTOMER_ID, CUSTOMER_ID);
    }

    private void persistContract(Long id, String status, String amount, String tonnage,
                                 Long projectId, boolean deleted) {
        SalesContract contract = new SalesContract();
        contract.setId(id);
        contract.setContractNo("PG-HT-" + id);
        contract.setName("PG合同");
        contract.setCustomerId(CUSTOMER_ID);
        contract.setCustomerName("PG客户");
        contract.setProjectId(projectId);
        contract.setProjectName("PG项目");
        contract.setSignDate(LocalDate.of(2026, 1, 1));
        contract.setTotalAmount(new BigDecimal(amount));
        contract.setTotalTonnage(new BigDecimal(tonnage));
        contract.setStatus(status);
        contract.setDeletedFlag(deleted);
        contract.setCreatedAt(LocalDateTime.now());
        contract.setCreatedBy(0L);
        contract.setCreatedName("system");
        contractRepository.save(contract);
    }

    private void persistOrder(Long id, Long projectId, String amount, String weight, boolean deleted) {
        SalesOrder order = new SalesOrder();
        order.setId(id);
        order.setOrderNo("PG-SO-" + id);
        order.setCustomerId(CUSTOMER_ID);
        order.setCustomerName("PG客户");
        order.setProjectName("PG项目");
        order.setProjectId(projectId);
        order.setDeliveryDate(LocalDate.of(2026, 1, 1));
        order.setSalesName("系统");
        order.setTotalAmount(new BigDecimal(amount));
        order.setTotalWeight(new BigDecimal(weight));
        order.setStatus(StatusConstants.DRAFT);
        order.setDeletedFlag(deleted);
        order.setCreatedAt(LocalDateTime.now());
        order.setCreatedBy(0L);
        order.setCreatedName("system");
        salesOrderRepository.save(order);
    }
}
