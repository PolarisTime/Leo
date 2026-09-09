package com.leo.erp.system.printtemplate.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.support.ModuleCatalog;
import com.leo.erp.system.company.domain.entity.CompanySetting;
import com.leo.erp.system.company.repository.CompanySettingRepository;
import com.leo.erp.system.printtemplate.domain.entity.PrintTemplate;
import com.leo.erp.system.printtemplate.web.dto.PrintTemplateRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * PrintTemplateRequestNormalizer 边界测试：字段规范化、
 * LODOP 指令白名单、危险脚本黑名单与结算主体解析。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PrintTemplateRequestNormalizerTest {

    @Mock
    private CompanySettingRepository companySettingRepository;

    @Mock
    private ModuleCatalog moduleCatalog;

    @Mock
    private PrintPdfFormTemplateValidator pdfFormTemplateValidator;

    @Mock
    private PrintRuntimeProperties runtimeProperties;

    @InjectMocks
    private PrintTemplateRequestNormalizer normalizer;

    private void allowModule(String billType) {
        when(moduleCatalog.containsModule(billType)).thenReturn(true);
        when(runtimeProperties.printableModules()).thenReturn(List.of(billType));
    }

    private PrintTemplateRequest request(String billType, String name, String code, String html,
                                         String templateType, String engine) {
        return new PrintTemplateRequest(billType, name, code, html, templateType, engine,
                null, null, null, null, null);
    }

    // ---------- billType ----------

    @Test
    void normalizeBillType_shouldRejectBlank() {
        assertThatThrownBy(() -> normalizer.normalizeBillType(" "))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("适用页面不能为空");
    }

    @Test
    void normalizeBillType_shouldRejectNull() {
        assertThatThrownBy(() -> normalizer.normalizeBillType(null))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void normalizeBillType_shouldRejectUnknownModule() {
        when(moduleCatalog.containsModule("unknown")).thenReturn(false);

        assertThatThrownBy(() -> normalizer.normalizeBillType("unknown"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("适用页面不合法");
    }

    @Test
    void normalizeBillType_shouldRejectNonPrintableModule() {
        when(moduleCatalog.containsModule("known")).thenReturn(true);
        when(runtimeProperties.printableModules()).thenReturn(List.of());

        assertThatThrownBy(() -> normalizer.normalizeBillType("known"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("适用页面不合法");
    }

    @Test
    void normalizeBillType_shouldTrim() {
        allowModule("sales-order");

        assertThat(normalizer.normalizeBillType(" sales-order ")).isEqualTo("sales-order");
    }

    // ---------- 模板名称/编码 ----------

    @Test
    void normalizeTemplateName_shouldRejectBlank() {
        assertThatThrownBy(() -> normalizer.normalizeTemplateName("  "))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("模板名称不能为空");
    }

    @Test
    void normalizeTemplateName_shouldTrim() {
        assertThat(normalizer.normalizeTemplateName(" 出库单 ")).isEqualTo("出库单");
    }

    @Test
    void normalizeTemplateCode_shouldRejectBlank() {
        assertThatThrownBy(() -> normalizer.normalizeTemplateCode(null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("模板编码不能为空");
    }

    @Test
    void normalizeTemplateCode_shouldUppercaseAndReplaceIllegalChars() {
        assertThat(normalizer.normalizeTemplateCode(" abc-01 ")).isEqualTo("ABC-01");
        assertThat(normalizer.normalizeTemplateCode("ab.c#d")).isEqualTo("AB_C_D");
    }

    // ---------- 模板类型/引擎 ----------

    @Test
    void normalizeTemplateType_shouldDefaultToCoord() {
        assertThat(normalizer.normalizeTemplateType(null)).isEqualTo("COORD");
        assertThat(normalizer.normalizeTemplateType(" ")).isEqualTo("COORD");
    }

    @Test
    void normalizeTemplateType_shouldRejectUnknown() {
        assertThatThrownBy(() -> normalizer.normalizeTemplateType("HTML"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("COORD 或 PDF_FORM");
    }

    @Test
    void apply_shouldRejectEngineMismatchForPdfForm() {
        allowModule("sales-order");

        assertThatThrownBy(() -> normalizer.apply(new PrintTemplate(),
                request("sales-order", "模板", "T1", "{}", "PDF_FORM", "LODOP")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("PDF_FORM 模板必须使用 PDF_FORM 引擎");
    }

    @Test
    void apply_shouldRejectEngineMismatchForCoord() {
        allowModule("sales-order");

        assertThatThrownBy(() -> normalizer.apply(new PrintTemplate(),
                request("sales-order", "模板", "T1", "<p/>", "COORD", "PDF_FORM")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("COORD 模板必须使用 LODOP 引擎");
    }

    @Test
    void apply_shouldRejectUnknownEngine() {
        allowModule("sales-order");

        assertThatThrownBy(() -> normalizer.apply(new PrintTemplate(),
                request("sales-order", "模板", "T1", "<p/>", "COORD", "FLASH")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("渲染引擎仅支持");
    }

    @Test
    void apply_shouldDefaultEngineByTemplateType() {
        allowModule("sales-order");
        PrintTemplate coordTemplate = new PrintTemplate();
        normalizer.apply(coordTemplate,
                request("sales-order", "模板", "T1", "<p/>", null, null));
        assertThat(coordTemplate.getEngine()).isEqualTo("LODOP");

        PrintTemplate pdfTemplate = new PrintTemplate();
        when(runtimeProperties.defaultPdfFormLayout("sales-order")).thenReturn("nonexistent-layout.json");

        assertThatThrownBy(() -> normalizer.apply(pdfTemplate,
                request("sales-order", "模板", "T2", "", "PDF_FORM", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("读取 PDF_FORM 默认布局失败");
    }

    // ---------- LODOP 内容校验 ----------

    @Test
    void apply_shouldRejectDangerousLodopScript() {
        allowModule("sales-order");

        assertThatThrownBy(() -> normalizer.apply(new PrintTemplate(),
                request("sales-order", "模板", "T1", "eval(x)", "COORD", "LODOP")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不允许的脚本或危险标签");
    }

    @Test
    void apply_shouldRejectNonWhitelistedLodopMethod() {
        allowModule("sales-order");

        assertThatThrownBy(() -> normalizer.apply(new PrintTemplate(),
                request("sales-order", "模板", "T1", "LODOP.DOSomething();", "COORD", "LODOP")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不允许的 LODOP 指令");
    }

    @Test
    void apply_shouldAllowWhitelistedLodopMethod() {
        allowModule("sales-order");

        assertThatCode(() -> normalizer.apply(new PrintTemplate(),
                request("sales-order", "模板", "T1", "LODOP.PRINT_INITA();", "COORD", "LODOP")))
                .doesNotThrowAnyException();
    }

    @Test
    void apply_shouldAllowPlainTextWithoutLodopCalls() {
        allowModule("sales-order");

        assertThatCode(() -> normalizer.apply(new PrintTemplate(),
                request("sales-order", "模板", "T1", "纯文本内容", "COORD", "LODOP")))
                .doesNotThrowAnyException();
    }

    // ---------- PDF_FORM 内容与底版 ----------

    @Test
    void apply_shouldValidatePdfFormContent() {
        allowModule("sales-order");
        when(runtimeProperties.defaultPdfFormLayout(anyString())).thenReturn("layout.json");

        assertThatCode(() -> normalizer.apply(new PrintTemplate(),
                request("sales-order", "模板", "T1", " { } ", "PDF_FORM", "PDF_FORM")))
                .doesNotThrowAnyException();
        verify(pdfFormTemplateValidator).validate("{ }");
    }

    @Test
    void apply_shouldRejectAssetRefTraversal() {
        allowModule("sales-order");
        PrintTemplateRequest traversal = new PrintTemplateRequest(
                "sales-order", "模板", "T1", "{}", "PDF_FORM", "PDF_FORM",
                "../evil.pdf", null, null, null, null);

        assertThatThrownBy(() -> normalizer.apply(new PrintTemplate(), traversal))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("PDF 底版资源路径不合法");

        PrintTemplateRequest absolute = new PrintTemplateRequest(
                "sales-order", "模板", "T1", "{}", "PDF_FORM", "PDF_FORM",
                "/evil.pdf", null, null, null, null);

        assertThatThrownBy(() -> normalizer.apply(new PrintTemplate(), absolute))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void apply_shouldIgnoreAssetRefForCoord() {
        allowModule("sales-order");
        PrintTemplateRequest coordWithAsset = new PrintTemplateRequest(
                "sales-order", "模板", "T1", "<p/>", "COORD", null,
                "ignored.pdf", null, null, null, null);
        PrintTemplate entity = new PrintTemplate();

        normalizer.apply(entity, coordWithAsset);

        assertThat(entity.getAssetRef()).isNull();
    }

    // ---------- 版本号/状态 ----------

    @Test
    void apply_shouldNormalizeVersionAndStatus() {
        allowModule("sales-order");
        PrintTemplate entity = new PrintTemplate();
        normalizer.apply(entity,
                new PrintTemplateRequest("sales-order", "模板", "T1", "<p/>", "COORD", null,
                        null, null, null, null, "disabled"));

        assertThat(entity.getStatus()).isEqualTo("DISABLED");
        assertThat(entity.getVersionNo()).isEqualTo(1);
    }

    @Test
    void apply_shouldRejectNonPositiveVersion() {
        allowModule("sales-order");

        assertThatThrownBy(() -> normalizer.apply(new PrintTemplate(),
                new PrintTemplateRequest("sales-order", "模板", "T1", "<p/>", "COORD", null,
                        null, null, null, 0, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("模板版本号必须大于 0");
    }

    @Test
    void apply_shouldFillAllNormalizedFields() {
        allowModule("sales-order");
        PrintTemplate entity = new PrintTemplate();

        normalizer.apply(entity, request(" sales-order ", " 模板 ", " t1 ", " <p/> ", "COORD", null));

        assertThat(entity.getBillType()).isEqualTo("sales-order");
        assertThat(entity.getTemplateName()).isEqualTo("模板");
        assertThat(entity.getTemplateCode()).isEqualTo("T1");
        assertThat(entity.getTemplateHtml()).isEqualTo("<p/>");
        assertThat(entity.getTemplateType()).isEqualTo("COORD");
        assertThat(entity.getEngine()).isEqualTo("LODOP");
        assertThat(entity.getAssetRef()).isNull();
    }

    // ---------- 结算主体 ----------

    @Test
    void normalizeSettlementCompany_shouldReturnNullSnapshotWhenAbsent() {
        var snapshot = normalizer.normalizeSettlementCompany(null);

        assertThat(snapshot.id()).isNull();
        assertThat(snapshot.name()).isNull();
        verifyNoInteractions(companySettingRepository);
    }

    @Test
    void normalizeSettlementCompany_shouldRejectMissingCompany() {
        when(companySettingRepository.findByIdAndDeletedFlagFalse(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> normalizer.normalizeSettlementCompany(9L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("结算主体不存在");
    }

    @Test
    void normalizeSettlementCompany_shouldReturnCompanySnapshot() {
        CompanySetting company = new CompanySetting();
        company.setId(9L);
        company.setCompanyName("结算公司A");
        when(companySettingRepository.findByIdAndDeletedFlagFalse(9L)).thenReturn(Optional.of(company));

        var snapshot = normalizer.normalizeSettlementCompany(9L);

        assertThat(snapshot.id()).isEqualTo(9L);
        assertThat(snapshot.name()).isEqualTo("结算公司A");
    }
}
