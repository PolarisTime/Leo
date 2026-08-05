package com.leo.erp.system.printtemplate.service;

import com.leo.erp.system.printtemplate.domain.entity.PrintTemplate;
import org.springframework.stereotype.Service;

/**
 * 打印模板状态写入的收敛入口。
 * <p>
 * 满足架构门禁约束：实体状态只能由 ApplyService 类写入；文件同步（登记/停用）与
 * CRUD 更新统一经由此类变更 ACTIVE/DISABLED 状态。
 */
@Service
public class PrintTemplateApplyService {

    static final String STATUS_ACTIVE = "ACTIVE";
    static final String STATUS_DISABLED = "DISABLED";

    public void activate(PrintTemplate template) {
        template.setStatus(STATUS_ACTIVE);
    }

    public void disable(PrintTemplate template) {
        template.setStatus(STATUS_DISABLED);
    }

    public boolean isDisabled(PrintTemplate template) {
        return STATUS_DISABLED.equals(template.getStatus());
    }
}
