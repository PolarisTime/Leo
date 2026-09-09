package com.leo.erp.system.printtemplate.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.system.printtemplate.domain.entity.PrintTemplate;
import com.leo.erp.system.printtemplate.mapper.PrintTemplateMapper;
import com.leo.erp.system.printtemplate.repository.PrintTemplateRepository;
import com.leo.erp.system.printtemplate.web.dto.PrintTemplateRequest;
import com.leo.erp.system.printtemplate.web.dto.PrintTemplateResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PrintTemplateService 脱离模板方法继承后的行为边界测试（无状态模块，CrudStatusGuard.withoutStatus）。
 */
@ExtendWith(MockitoExtension.class)
class PrintTemplateServiceTest {

    @Mock
    private PrintTemplateRepository repository;

    @Mock
    private SnowflakeIdGenerator idGenerator;

    @Mock
    private PrintTemplateMapper printTemplateMapper;

    @Mock
    private PrintTemplateRequestNormalizer requestNormalizer;

    @Mock
    private PrintTemplateJsonUploadReader jsonUploadReader;

    @InjectMocks
    private PrintTemplateService service;

    private PrintTemplate entity() {
        PrintTemplate template = new PrintTemplate();
        template.setId(5L);
        template.setBillType("sales-order");
        template.setTemplateName("模板A");
        template.setTemplateCode("TPL_5");
        return template;
    }

    // ---------- create ----------

    @Test
    void create_shouldAssignSnowflakeId() {
        when(idGenerator.nextId()).thenReturn(100L);
        when(requestNormalizer.normalizeSettlementCompany(null))
                .thenReturn(new PrintTemplateRequestNormalizer.SettlementCompanySnapshot(null, null));
        PrintTemplateRequest request = new PrintTemplateRequest(
                "sales-order", "模板A", null, "<html/>", "PDF_FORM", "PDF", null, null, null, 1, null);

        service.create(request);

        verify(repository).save(argThat(template -> template.getId() == 100L));
    }

    @Test
    void create_shouldRejectDuplicateName() {
        when(idGenerator.nextId()).thenReturn(100L);
        when(requestNormalizer.normalizeSettlementCompany(null))
                .thenReturn(new PrintTemplateRequestNormalizer.SettlementCompanySnapshot(null, null));
        when(requestNormalizer.normalizeBillType("sales-order")).thenReturn("sales-order");
        when(requestNormalizer.normalizeTemplateName("模板A")).thenReturn("模板A");
        when(requestNormalizer.normalizeTemplateCode("TPL_100")).thenReturn("TPL_100");
        when(repository.existsByBillTypeAndSettlementCompanyIdAndTemplateNameAndDeletedFlagFalse(
                "sales-order", null, "模板A")).thenReturn(true);
        PrintTemplateRequest request = new PrintTemplateRequest(
                "sales-order", "模板A", null, "<html/>", "PDF_FORM", "PDF", null, null, null, 1, null);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已存在同名打印模板");

        verify(repository, never()).save(any(PrintTemplate.class));
    }

    // ---------- update ----------

    @Test
    void update_shouldRejectFileManagedTemplate() {
        PrintTemplate template = entity();
        template.setSyncMode("FILE");
        when(repository.findByIdAndDeletedFlagFalse(5L)).thenReturn(Optional.of(template));
        PrintTemplateRequest request = new PrintTemplateRequest(
                "sales-order", "模板A", "TPL_5", "<html/>", "PDF_FORM", "PDF", null, null, null, 1, null);

        assertThatThrownBy(() -> service.update(5L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("文件托管模板请通过上传 JSON");

        verify(repository, never()).save(any(PrintTemplate.class));
    }

    @Test
    void update_shouldRejectDuplicateNameOfOtherTemplate() {
        PrintTemplate template = entity();
        when(repository.findByIdAndDeletedFlagFalse(5L)).thenReturn(Optional.of(template));
        when(requestNormalizer.normalizeBillType("sales-order")).thenReturn("sales-order");
        when(requestNormalizer.normalizeTemplateName("模板B")).thenReturn("模板B");
        when(requestNormalizer.normalizeTemplateCode("TPL_5")).thenReturn("TPL_5");
        when(requestNormalizer.normalizeSettlementCompany(null))
                .thenReturn(new PrintTemplateRequestNormalizer.SettlementCompanySnapshot(null, null));
        when(repository.existsByBillTypeAndSettlementCompanyIdAndTemplateNameAndDeletedFlagFalse(
                "sales-order", null, "模板B")).thenReturn(true);
        PrintTemplateRequest request = new PrintTemplateRequest(
                "sales-order", "模板B", "TPL_5", "<html/>", "PDF_FORM", "PDF", null, null, null, 1, null);

        assertThatThrownBy(() -> service.update(5L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已存在同名打印模板");

        verify(repository, never()).save(any(PrintTemplate.class));
    }

    // ---------- updateStatus（无状态模块守卫） ----------

    @Test
    void updateStatus_shouldRejectBlankStatus() {
        when(repository.findByIdAndDeletedFlagFalse(5L)).thenReturn(Optional.of(entity()));

        assertThatThrownBy(() -> service.updateStatus(5L, " "))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("状态不能为空");
    }

    @Test
    void updateStatus_shouldRejectAnyStatusChange() {
        when(repository.findByIdAndDeletedFlagFalse(5L)).thenReturn(Optional.of(entity()));

        assertThatThrownBy(() -> service.updateStatus(5L, "已审核"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("当前模块不支持状态变更");
    }

    // ---------- uploadJson ----------

    @Test
    void uploadJson_shouldRejectNonPdfFormTemplate() {
        PrintTemplate template = entity();
        template.setTemplateType("LODOP");
        when(repository.findByIdAndDeletedFlagFalse(5L)).thenReturn(Optional.of(template));

        assertThatThrownBy(() -> service.uploadJson(5L, file()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅 PDF_FORM 模板支持上传 JSON");

        verify(repository, never()).save(any(PrintTemplate.class));
    }

    @Test
    void uploadJson_shouldBumpVersionAndClearSourceRef() {
        PrintTemplate template = entity();
        template.setTemplateType("PDF_FORM");
        template.setVersionNo(2);
        template.setSyncMode("FILE");
        template.setSourceRef("remote/template.json");
        template.setSourceChecksum("abc");
        when(repository.findByIdAndDeletedFlagFalse(5L)).thenReturn(Optional.of(template));
        when(requestNormalizer.normalizeTemplateType("PDF_FORM")).thenReturn("PDF_FORM");
        when(jsonUploadReader.read(any())).thenReturn("{}");

        service.uploadJson(5L, file());

        assertThat(template.getTemplateHtml()).isEqualTo("{}");
        assertThat(template.getVersionNo()).isEqualTo(3L);
        assertThat(template.getSyncMode()).isEqualTo("MANUAL");
        assertThat(template.getSourceRef()).isNull();
        assertThat(template.getSourceChecksum()).isNull();
        verify(repository).save(template);
    }

    // ---------- delete ----------

    @Test
    void delete_shouldSoftDelete() {
        PrintTemplate template = entity();
        when(repository.findByIdAndDeletedFlagFalse(5L)).thenReturn(Optional.of(template));

        service.delete(5L);

        assertThat(template.isDeletedFlag()).isTrue();
        verify(repository).save(template);
    }

    // ---------- detail ----------

    @Test
    void detail_shouldRejectMissingTemplate() {
        when(repository.findByIdAndDeletedFlagFalse(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.detail(5L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("打印模板不存在");
    }

    private MockMultipartFile file() {
        return new MockMultipartFile("file", "template.json", "application/json", "{}".getBytes());
    }
}
