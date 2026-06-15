package com.leo.erp.system.printtemplate.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class PrintPdfFormService {

    private final PrintScriptService printScriptService;
    private final PrintPdfFormPayloadParser payloadParser;
    private final PrintPdfFormRenderer renderer;

    @Autowired
    public PrintPdfFormService(PrintScriptService printScriptService,
                               PrintPdfFormPayloadParser payloadParser,
                               PrintPdfFormRenderer renderer) {
        this.printScriptService = printScriptService;
        this.payloadParser = payloadParser;
        this.renderer = renderer;
    }

    PrintPdfFormService(PrintScriptService printScriptService, PrintPdfFormTemplateValidator templateValidator) {
        this(
                printScriptService,
                new PrintPdfFormPayloadParser(templateValidator),
                defaultRenderer()
        );
    }

    private static PrintPdfFormRenderer defaultRenderer() {
        PrintPdfFormValueResolver valueResolver = new PrintPdfFormValueResolver();
        PrintPdfDrawingSupport drawing = new PrintPdfDrawingSupport();
        return new PrintPdfFormRenderer(
                valueResolver,
                new PrintPdfFontFactory(),
                drawing,
                new PrintPdfPageContentRenderer(valueResolver, drawing),
                new PrintPdfTableRenderer(valueResolver, drawing)
        );
    }

    public byte[] generateFromRecord(String templateId, String moduleKey, Long recordId) {
        Map<String, Object> payload = printScriptService.generateFromRecord(templateId, moduleKey, recordId);
        return generateFromPayload(payload);
    }

    public byte[] generateFromPayload(Map<String, Object> payload) {
        return renderer.render(payloadParser.parse(payload));
    }
}
