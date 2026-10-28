package com.leo.erp.system.operationlog.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.erp.common.mapper.StrictMapperConfig;
import com.leo.erp.system.operationlog.domain.entity.OperationLog;
import com.leo.erp.system.operationlog.web.dto.OperationLogResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 操作日志实体 → 响应映射。
 *
 * <p>实体里的 {@code requestBody} 是数据库 jsonb 列的文本形式, 对外契约要求 JSON 对象,
 * 因此这里用注入的 {@code ObjectMapper} 解析; 解析失败返回 null(历史/异常数据不影响整页查询)。</p>
 */
@Mapper(config = StrictMapperConfig.class)
public abstract class OperationLogMapper {

    private ObjectMapper objectMapper;

    /**
     * 用 setter 注入而不是构造注入: MapStruct 生成的实现类只有无参构造,
     * 抽象类构造带参会与生成代码冲突; 继承的 setter 由 Spring 在初始化时注入。
     */
    @Autowired
    void setObjectMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Mapping(target = "requestBody", source = "requestBody", qualifiedByName = "requestBodyJson")
    public abstract OperationLogResponse toResponse(OperationLog entity);

    @Named("requestBodyJson")
    protected JsonNode toRequestBodyJson(String requestBody) {
        if (requestBody == null || requestBody.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(requestBody);
        } catch (JsonProcessingException ex) {
            return null;
        }
    }
}
