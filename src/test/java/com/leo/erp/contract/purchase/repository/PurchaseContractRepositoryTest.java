package com.leo.erp.contract.purchase.repository;

import com.leo.erp.contract.purchase.domain.entity.PurchaseContract;
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
class PurchaseContractRepositoryTest {

    private static final long SUPPLIER_ID = 8_811_000_000_000_000_001L;
    private static final String SUPPLIER_CODE = "TEST-PC-REPOSITORY-SUPPLIER";

    @Autowired
    private PurchaseContractRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void insertSupplierFixture() {
        StableIdentityPostgresFixtures.insertSupplier(
                jdbcTemplate,
                SUPPLIER_ID,
                SUPPLIER_CODE,
                "供应商A"
        );
    }

    @Test
    void shouldSaveAndFindById() {
        var entity = createEntity(1L, "PC-001");
        repository.save(entity);

        Optional<PurchaseContract> found = repository.findById(1L);
        assertThat(found).isPresent();
        assertThat(found.get().getContractNo()).isEqualTo("PC-001");
    }

    @Test
    void shouldReturnTrueWhenContractNoExists() {
        var entity = createEntity(1L, "PC-001");
        repository.save(entity);

        boolean exists = repository.existsByContractNoAndDeletedFlagFalse("PC-001");
        assertThat(exists).isTrue();
    }

    @Test
    void shouldReturnFalseWhenContractNoNotExists() {
        boolean exists = repository.existsByContractNoAndDeletedFlagFalse("PC-999");
        assertThat(exists).isFalse();
    }

    @Test
    void shouldFindByIdAndDeletedFlagFalse() {
        var entity = createEntity(1L, "PC-001");
        repository.save(entity);

        Optional<PurchaseContract> found = repository.findByIdAndDeletedFlagFalse(1L);
        assertThat(found).isPresent();
        assertThat(found.get().getContractNo()).isEqualTo("PC-001");
    }

    @Test
    void shouldNotFindDeletedEntity() {
        var entity = createEntity(1L, "PC-001");
        entity.setDeletedFlag(true);
        repository.save(entity);

        Optional<PurchaseContract> found = repository.findByIdAndDeletedFlagFalse(1L);
        assertThat(found).isEmpty();
    }

    private static PurchaseContract createEntity(Long id, String contractNo) {
        var entity = new PurchaseContract();
        entity.setId(id);
        entity.setContractNo(contractNo);
        entity.setSupplierId(SUPPLIER_ID);
        entity.setSupplierCode(SUPPLIER_CODE);
        entity.setSupplierName("供应商A");
        entity.setSignDate(LocalDate.now());
        entity.setEffectiveDate(LocalDate.now());
        entity.setExpireDate(LocalDate.now().plusYears(1));
        entity.setBuyerName("采购甲");
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
