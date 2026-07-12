package com.leo.erp.contract.sales.repository;

import com.leo.erp.contract.sales.domain.entity.SalesContract;
import com.leo.erp.testsupport.StableIdentityPostgresFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class SalesContractRepositoryTest {

    private static final long CUSTOMER_ID = 8_812_000_000_000_000_001L;
    private static final long PROJECT_ID = 8_812_000_000_000_000_002L;
    private static final String CUSTOMER_CODE = "TEST-SC-REPOSITORY-CUSTOMER";

    @Autowired
    private SalesContractRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void insertPartyFixtures() {
        StableIdentityPostgresFixtures.insertCustomer(
                jdbcTemplate,
                CUSTOMER_ID,
                CUSTOMER_CODE,
                "客户A",
                "项目A"
        );
        StableIdentityPostgresFixtures.insertProject(
                jdbcTemplate,
                PROJECT_ID,
                "TEST-SC-REPOSITORY-PROJECT",
                "项目A",
                CUSTOMER_ID,
                CUSTOMER_CODE
        );
    }

    @Test
    void shouldSaveAndFindById() {
        var entity = createEntity(1L, "SC-001");
        repository.save(entity);

        Optional<SalesContract> found = repository.findById(1L);
        assertThat(found).isPresent();
        assertThat(found.get().getContractNo()).isEqualTo("SC-001");
    }

    @Test
    void shouldReturnTrueWhenContractNoExists() {
        var entity = createEntity(1L, "SC-001");
        repository.save(entity);

        boolean exists = repository.existsByContractNoAndDeletedFlagFalse("SC-001");
        assertThat(exists).isTrue();
    }

    @Test
    void shouldReturnFalseWhenContractNoNotExists() {
        boolean exists = repository.existsByContractNoAndDeletedFlagFalse("SC-999");
        assertThat(exists).isFalse();
    }

    @Test
    void shouldFindByIdAndDeletedFlagFalse() {
        var entity = createEntity(1L, "SC-001");
        repository.save(entity);

        Optional<SalesContract> found = repository.findByIdAndDeletedFlagFalse(1L);
        assertThat(found).isPresent();
        assertThat(found.get().getContractNo()).isEqualTo("SC-001");
    }

    @Test
    void shouldNotFindDeletedEntity() {
        var entity = createEntity(1L, "SC-001");
        entity.setDeletedFlag(true);
        repository.save(entity);

        Optional<SalesContract> found = repository.findByIdAndDeletedFlagFalse(1L);
        assertThat(found).isEmpty();
    }

    private static SalesContract createEntity(Long id, String contractNo) {
        var entity = new SalesContract();
        entity.setId(id);
        entity.setContractNo(contractNo);
        entity.setCustomerId(CUSTOMER_ID);
        entity.setCustomerCode(CUSTOMER_CODE);
        entity.setCustomerName("客户A");
        entity.setProjectId(PROJECT_ID);
        entity.setProjectName("项目A");
        entity.setSignDate(LocalDate.now());
        entity.setEffectiveDate(LocalDate.now());
        entity.setExpireDate(LocalDate.now().plusYears(1));
        entity.setSalesName("销售甲");
        entity.setStatus("草稿");
        entity.setTotalWeight(BigDecimal.TEN);
        entity.setTotalAmount(new BigDecimal("100"));
        entity.setItems(new ArrayList<>());
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        entity.setCreatedBy(0L);
        return entity;
    }
}
