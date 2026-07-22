package com.leo.erp.system.printtemplate.service;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import java.util.Base64;
import java.util.Map;

@Service
public class PrintOutputService {

    private final PrintScriptService printScriptService;
    private final PrintPdfFormService printPdfFormService;
    private final PrintExportFilenameService filenameService;

    public PrintOutputService(
            PrintScriptService printScriptService,
            PrintPdfFormService printPdfFormService,
            PrintExportFilenameService filenameService
    ) {
        this.printScriptService = printScriptService;
        this.printPdfFormService = printPdfFormService;
        this.filenameService = filenameService;
    }

    public PrintOutput generateFromRecord(
            String templateId,
            String moduleKey,
            Long recordId,
            PrintRenderOptions options
    ) {
        Map<String, Object> payload = printScriptService.generateFromRecord(templateId, moduleKey, recordId, options);
        if ("PDF_FORM".equals(String.valueOf(payload.getOrDefault("templateType", "")))) {
            byte[] pdf = printPdfFormService.generateFromPayload(payload);
            return PrintOutput.pdf(
                    payload,
                    Base64.getEncoder().encodeToString(pdf),
                    MediaType.APPLICATION_PDF_VALUE,
                    filenameService.fromPrintData(
                            data(payload.get("data")),
                            String.valueOf(payload.getOrDefault("businessNo", "")),
                            "pdf"
                    )
            );
        }
        return PrintOutput.fromPayload(payload);
    }

    private Map<?, ?> data(Object value) {
        return value instanceof Map<?, ?> map ? map : Map.of();
    }
}
