package com.leo.erp.system.printtemplate.service;

import java.util.List;
import java.util.Map;

record PrintRecordData(
        Map<String, String> data,
        List<Map<String, String>> items
) {
}
