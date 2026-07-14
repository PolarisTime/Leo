package com.leo.erp.statement.freight.service;

import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.logistics.bill.repository.FreightBillRepository;
import com.leo.erp.statement.freight.repository.FreightStatementRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThatCode;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class FreightStatementSourceServicePostgresTest {

    @Autowired
    private FreightStatementRepository statementRepository;

    @Autowired
    private FreightBillRepository freightBillRepository;

    @Test
    void candidateSearchUsesOnlyFreightBillAttributes() {
        FreightStatementSourceService service = new FreightStatementSourceService(
                statementRepository,
                freightBillRepository
        );

        assertThatCode(() -> service.candidatePage(
                PageQuery.of(0, 20, null, null),
                PageFilter.of("candidate-search", StatusConstants.AUDITED, null, null)
        )).doesNotThrowAnyException();
    }
}
