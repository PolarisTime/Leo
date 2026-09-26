package com.leo.erp.system.printtemplate.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.system.printtemplate.domain.entity.PrintTemplate;
import com.leo.erp.system.printtemplate.domain.entity.ProjectPrintPreference;
import com.leo.erp.system.printtemplate.repository.PrintTemplateRepository;
import com.leo.erp.system.printtemplate.repository.ProjectPrintPreferenceRepository;
import com.leo.erp.system.printtemplate.web.dto.ProjectPrintPreferenceRequest;
import com.leo.erp.system.printtemplate.web.dto.ProjectPrintPreferenceResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectPrintPreferenceServiceTest {

    @Mock
    private ProjectPrintPreferenceRepository repository;

    @Mock
    private PrintTemplateRepository printTemplateRepository;

    @Mock
    private PrintTemplateRequestNormalizer requestNormalizer;

    @Mock
    private SnowflakeIdGenerator idGenerator;

    @InjectMocks
    private ProjectPrintPreferenceService service;

    private PrintTemplate template() {
        PrintTemplate template = new PrintTemplate();
        template.setId(88L);
        template.setBillType("sales-order");
        template.setTemplateName("销售订单A4");
        return template;
    }

    @Test
    void find_shouldReturnEmptyWhenProjectNull() {
        assertThat(service.find(null, "sales-order")).isEmpty();
        verify(repository, never()).findByProjectIdAndBillTypeAndDeletedFlagFalse(any(), any());
    }

    @Test
    void find_shouldReturnRememberedPreference() {
        when(requestNormalizer.normalizeBillType("sales-order")).thenReturn("sales-order");
        ProjectPrintPreference preference = new ProjectPrintPreference();
        preference.setProjectId(9L);
        preference.setBillType("sales-order");
        preference.setTemplateId(88L);
        preference.setTemplateName("销售订单A4");
        when(repository.findByProjectIdAndBillTypeAndDeletedFlagFalse(9L, "sales-order"))
                .thenReturn(Optional.of(preference));

        Optional<ProjectPrintPreferenceResponse> result = service.find(9L, "sales-order");

        assertThat(result).isPresent();
        assertThat(result.get().templateId()).isEqualTo(88L);
        assertThat(result.get().templateName()).isEqualTo("销售订单A4");
    }

    @Test
    void remember_shouldInsertNewPreferenceWithSnowflakeId() {
        when(requestNormalizer.normalizeBillType("sales-order")).thenReturn("sales-order");
        when(printTemplateRepository.findByIdAndDeletedFlagFalse(88L)).thenReturn(Optional.of(template()));
        when(repository.findByProjectIdAndBillTypeAndDeletedFlagFalse(9L, "sales-order"))
                .thenReturn(Optional.empty());
        when(idGenerator.nextId()).thenReturn(1000L);
        when(repository.save(any(ProjectPrintPreference.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ProjectPrintPreferenceResponse response =
                service.remember(new ProjectPrintPreferenceRequest(9L, "sales-order", 88L));

        assertThat(response.templateId()).isEqualTo(88L);
        verify(repository).save(argThat(preference -> preference.getId() == 1000L
                && preference.getProjectId() == 9L
                && preference.getTemplateId() == 88L
                && "销售订单A4".equals(preference.getTemplateName())));
    }

    @Test
    void remember_shouldUpdateExistingPreferenceKeepingId() {
        when(requestNormalizer.normalizeBillType("sales-order")).thenReturn("sales-order");
        when(printTemplateRepository.findByIdAndDeletedFlagFalse(88L)).thenReturn(Optional.of(template()));
        ProjectPrintPreference existing = new ProjectPrintPreference();
        existing.setId(500L);
        existing.setProjectId(9L);
        existing.setBillType("sales-order");
        existing.setTemplateId(1L);
        existing.setTemplateName("旧模板");
        when(repository.findByProjectIdAndBillTypeAndDeletedFlagFalse(9L, "sales-order"))
                .thenReturn(Optional.of(existing));
        when(repository.save(any(ProjectPrintPreference.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.remember(new ProjectPrintPreferenceRequest(9L, "sales-order", 88L));

        verify(idGenerator, never()).nextId();
        verify(repository).save(argThat(preference -> preference.getId() == 500L
                && preference.getTemplateId() == 88L
                && "销售订单A4".equals(preference.getTemplateName())));
    }

    @Test
    void remember_shouldRejectMissingTemplate() {
        when(requestNormalizer.normalizeBillType("sales-order")).thenReturn("sales-order");
        when(printTemplateRepository.findByIdAndDeletedFlagFalse(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                service.remember(new ProjectPrintPreferenceRequest(9L, "sales-order", 999L)))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
        verify(repository, never()).save(any(ProjectPrintPreference.class));
    }
}
