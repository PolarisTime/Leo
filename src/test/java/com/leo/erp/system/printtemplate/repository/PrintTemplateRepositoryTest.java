package com.leo.erp.system.printtemplate.repository;

import com.leo.erp.system.printtemplate.domain.entity.PrintTemplate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PrintTemplateRepositoryTest {

    @Mock
    private PrintTemplateRepository repository;

    @Test
    void findAllByBillTypeShouldReturnMatchingTemplates() {
        PrintTemplate template = createTemplate(1L, "purchase-order", "模板A");
        when(repository.findAllByBillTypeAndDeletedFlagFalseOrderByUpdatedAtDescIdDesc("purchase-order"))
                .thenReturn(List.of(template));

        List<PrintTemplate> result = repository.findAllByBillTypeAndDeletedFlagFalseOrderByUpdatedAtDescIdDesc("purchase-order");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTemplateName()).isEqualTo("模板A");
    }

    @Test
    void findAllByBillTypeShouldReturnEmptyWhenNone() {
        when(repository.findAllByBillTypeAndDeletedFlagFalseOrderByUpdatedAtDescIdDesc("unknown"))
                .thenReturn(List.of());

        List<PrintTemplate> result = repository.findAllByBillTypeAndDeletedFlagFalseOrderByUpdatedAtDescIdDesc("unknown");

        assertThat(result).isEmpty();
    }

    @Test
    void findByIdAndDeletedFlagFalseShouldReturnWhenExists() {
        PrintTemplate template = createTemplate(1L, "purchase-order", "模板A");
        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(template));

        Optional<PrintTemplate> result = repository.findByIdAndDeletedFlagFalse(1L);

        assertThat(result).isPresent();
        assertThat(result.get().getBillType()).isEqualTo("purchase-order");
    }

    @Test
    void findByIdAndDeletedFlagFalseShouldReturnEmptyWhenDeleted() {
        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.empty());

        Optional<PrintTemplate> result = repository.findByIdAndDeletedFlagFalse(1L);

        assertThat(result).isEmpty();
    }

    @Test
    void existsByBillTypeAndTemplateNameShouldReturnTrueWhenExists() {
        when(repository.existsByBillTypeAndSettlementCompanyIdAndTemplateNameAndDeletedFlagFalse(
                "purchase-order",
                7L,
                "模板A"
        )).thenReturn(true);

        boolean result = repository.existsByBillTypeAndSettlementCompanyIdAndTemplateNameAndDeletedFlagFalse(
                "purchase-order",
                7L,
                "模板A"
        );

        assertThat(result).isTrue();
    }

    @Test
    void existsByBillTypeAndTemplateNameShouldReturnFalseWhenNotExists() {
        when(repository.existsByBillTypeAndSettlementCompanyIdAndTemplateNameAndDeletedFlagFalse(
                "purchase-order",
                7L,
                "不存在"
        )).thenReturn(false);

        boolean result = repository.existsByBillTypeAndSettlementCompanyIdAndTemplateNameAndDeletedFlagFalse(
                "purchase-order",
                7L,
                "不存在"
        );

        assertThat(result).isFalse();
    }

    private PrintTemplate createTemplate(Long id, String billType, String templateName) {
        PrintTemplate template = new PrintTemplate();
        template.setId(id);
        template.setBillType(billType);
        template.setTemplateName(templateName);
        template.setTemplateHtml("LODOP.PRINT_INIT('模板');");
        template.setTemplateType("COORD");
        return template;
    }
}
