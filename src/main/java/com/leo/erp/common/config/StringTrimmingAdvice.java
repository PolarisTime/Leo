package com.leo.erp.common.config;

import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.beans.propertyeditors.StringTrimmerEditor;

/**
 * 查询参数、路径参数与表单绑定的字符串统一 trim。
 *
 * <p>请求体 JSON 的字符串归一化由 {@link com.leo.erp.common.json.TrimmingStringDeserializer} 处理；
 * 此处的 WebDataBinder 编辑器覆盖非 JSON 的绑定来源（@RequestParam/@PathVariable/@ModelAttribute），
 * 使长度类约束在归一化后的值上判定，避免首尾空格导致的误判。
 *
 * <p>空串保持为空串（{@code emptyAsNull=false}），不改变既有必填/可空语义。
 */
@ControllerAdvice
public class StringTrimmingAdvice {

    @InitBinder
    public void trimStringBindings(WebDataBinder binder) {
        binder.registerCustomEditor(String.class, new StringTrimmerEditor(false));
    }
}
