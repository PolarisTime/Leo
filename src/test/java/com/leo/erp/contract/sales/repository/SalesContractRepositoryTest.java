package com.leo.erp.contract.sales.repository;

import com.leo.erp.contract.sales.domain.entity.SalesContract;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = "spring.flyway.enabled=false")
@ActiveProfiles("test")
class SalesContractRepositoryTest {

    @Autowired
    private SalesContractRepository repository;

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
        entity.setCustomerName("客户A");
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
