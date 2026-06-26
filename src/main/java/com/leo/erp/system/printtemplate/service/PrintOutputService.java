package com.leo.erp.system.printtemplate.service;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import java.util.Base64;
import java.util.Map;

@Service
public class PrintOutputService {

    private final PrintScriptService printScriptService;
    private final PrintPdfFormService printPdfFormService;

    public PrintOutputService(PrintScriptService printScriptService, PrintPdfFormService printPdfFormService) {
        this.printScriptService = printScriptService;
        this.printPdfFormService = printPdfFormService;
    }

    public PrintOutput generateFromRecord(String templateId, String moduleKey, Long recordId, PrintRenderOptions options) {
        Map<String, Object> payload = printScriptService.generateFromRecord(templateId, moduleKey, recordId, options);
        if ("PDF_FORM".equals(String.valueOf(payload.getOrDefault("templateType", "")))) {
            byte[] pdf = printPdfFormService.generateFromPayload(payload);
            return PrintOutput.pdf(
                    payload,
                    Base64.getEncoder().encodeToString(pdf),
                    MediaType.APPLICATION_PDF_VALUE,
                    "print.pdf"
            );
        }
        return PrintOutput.fromPayload(payload);
    }
}
