package com.leo.erp.auth.service;

import com.leo.erp.auth.web.dto.UserSettingsSectionPayload;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 设置分片写入冲突（服务端已存的分片比本次提交更新）。
 *
 * <p>为什么单独建异常类型，而不是直接抛 {@link BusinessException}：冲突响应除 ProblemDetail
 * 标准字段外，还要带上<b>具体是哪些分片</b>与<b>服务端当前值</b>，客户端据此「采用服务端值 +
 * 提示已在其他设备更新」。标准 {@link BusinessException} 只有一个 message，装不下这两份数据。</p>
 *
 * <p>错误码复用 {@link ErrorCode#CONCURRENT_MODIFICATION}（409）：语义就是「你的基线过期了」，
 * 与乐观锁冲突同类；不新增错误码，避免错误码表膨胀。</p>
 */
public class UserSettingsConflictException extends BusinessException {

    private final List<String> conflictSections;
    private final Map<String, UserSettingsSectionPayload> serverSections;

    public UserSettingsConflictException(List<String> conflictSections,
                                        Map<String, UserSettingsSectionPayload> serverSections) {
        super(ErrorCode.CONCURRENT_MODIFICATION,
                "设置已在其他设备更新，请采用服务端值："
                        + String.join("、", conflictSections));
        this.conflictSections = List.copyOf(conflictSections);
        this.serverSections = Collections.unmodifiableMap(new LinkedHashMap<>(serverSections));
    }

    /** 发生冲突的分片 key，顺序与请求中的提交顺序一致。 */
    public List<String> getConflictSections() {
        return conflictSections;
    }

    /** 冲突分片在服务端的当前值（含 {@code value} 与两个时间戳）。 */
    public Map<String, UserSettingsSectionPayload> getServerSections() {
        return serverSections;
    }

    @Override
    public String toString() {
        return "UserSettingsConflictException{sections=" + conflictSections + "}";
    }
}
