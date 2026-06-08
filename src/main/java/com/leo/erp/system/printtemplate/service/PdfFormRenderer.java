package com.leo.erp.system.printtemplate.service;

import java.util.List;
import java.util.Map;

public interface PdfFormRenderer {

    String formCode();

    String defaultTemplatePath();

    Map<String, String> buildFields(Map<String, String> data, List<Map<String, String>> items);
}
