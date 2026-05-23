package com.leo.erp.system.printtemplate.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.system.printtemplate.domain.entity.PrintTemplate;
import com.leo.erp.system.printtemplate.repository.PrintTemplateRepository;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Securely generate CLODOP print scripts on the backend.
 * Only allows white-listed LODOP API methods; all user data is escaped.
 */
@Service
public class PrintScriptService {

    private static final Pattern SAFE_METHOD = Pattern.compile(
            "^\\s*LODOP\\.(SET_|ADD_|NewPage|SET_PRINT|SELECT_|DELETE_)[A-Za-z_]*\\s*\\(");
    private static final Set<String> DISALLOWED_METHODS = Set.of(
            "GET_FILE", "SEND_PRINT_RAWDATA", "WRITE_PORT_DATA"
    );
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{(\\w+)\\}\\}");

    private final PrintTemplateRepository templateRepository;

    public PrintScriptService(PrintTemplateRepository templateRepository) {
        this.templateRepository = templateRepository;
    }

    public String generate(String templateId, Map<String, String> data) {
        PrintTemplate template = templateRepository.findByIdAndDeletedFlagFalse(Long.parseLong(templateId))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "打印模板不存在"));

        // 1. Validate input data (type + length)
        for (var entry : data.entrySet()) {
            String value = entry.getValue();
            if (value == null) continue;
            if (value.length() > 2000) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "字段 " + entry.getKey() + " 内容过长，最大2000字符");
            }
        }

        // 2. Replace {{fieldName}} placeholders with escaped values
        String script = PLACEHOLDER.matcher(template.getTemplateHtml()).replaceAll(mr -> {
            String key = mr.group(1);
            String value = data.getOrDefault(key, "");
            return escapeJs(value);
        });

        // 3. Validate each line is a safe LODOP method call
        for (String line : script.split(";\\r?\\n?")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || !trimmed.startsWith("LODOP.")) continue;
            if (!SAFE_METHOD.matcher(trimmed).find()) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "模板包含不安全的调用: " + truncated(trimmed));
            }
            for (String disallowed : DISALLOWED_METHODS) {
                if (trimmed.toUpperCase(Locale.ROOT).contains(disallowed)) {
                    throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                            "模板包含被禁用的方法: " + disallowed);
                }
            }
        }

        return script;
    }

    /**
     * Escape a string value for safe embedding in JS single-quoted string literals.
     */
    static String escapeJs(String value) {
        if (value == null) return "";
        StringBuilder sb = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\'' -> sb.append("\\'");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '<'   -> sb.append("\\x3c");
                case '>'   -> sb.append("\\x3e");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\x%02x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }

    private String truncated(String s) {
        return s.length() > 80 ? s.substring(0, 80) + "..." : s;
    }
}
