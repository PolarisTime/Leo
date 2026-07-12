package com.leo.erp.statement;

import com.leo.erp.statement.customer.repository.CustomerStatementRepository;
import com.leo.erp.statement.freight.repository.FreightStatementRepository;
import com.leo.erp.statement.supplier.repository.SupplierStatementRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class StatementSourceIdentityRepositoryPostgresTest {

    private static final long NON_EXISTENT_SOURCE_ID = -1L;

    @Autowired
    private CustomerStatementRepository customerStatementRepository;

    @Autowired
    private SupplierStatementRepository supplierStatementRepository;

    @Autowired
    private FreightStatementRepository freightStatementRepository;

    @Test
    void shouldExecuteTypedSourceIdentityProjections() {
        assertThat(customerStatementRepository
                .findOccupiedSourceSalesOrderIdsExcludingCurrentStatement(null))
                .doesNotContainNull();
        assertThat(customerStatementRepository
                .findMatchingOccupiedSourceSalesOrderIdsExcludingCurrentStatement(
                        List.of(NON_EXISTENT_SOURCE_ID),
                        null
                ))
                .isEmpty();

        assertThat(supplierStatementRepository
                .findOccupiedSourceInboundIdsExcludingCurrentStatement(null))
                .doesNotContainNull();
        assertThat(supplierStatementRepository
                .findMatchingOccupiedSourceInboundIdsExcludingCurrentStatement(
                        List.of(NON_EXISTENT_SOURCE_ID),
                        null
                ))
                .isEmpty();

        assertThat(freightStatementRepository
                .findOccupiedSourceFreightBillIdsExcludingCurrentStatement(null))
                .doesNotContainNull();
        assertThat(freightStatementRepository
                .findMatchingOccupiedSourceFreightBillIdsExcludingCurrentStatement(
                        List.of(NON_EXISTENT_SOURCE_ID),
                        null
                ))
                .isEmpty();
    }
}
