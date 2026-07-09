package com.leo.erp.system.printtemplate.service;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

record PrintPdfFormPayload(
        JsonNode root,
        Map<String, String> data,
        List<Map<String, String>> items
) {
}
