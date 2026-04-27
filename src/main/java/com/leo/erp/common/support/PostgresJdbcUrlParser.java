package com.leo.erp.common.support;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;

import java.net.URI;

public final class PostgresJdbcUrlParser {

    private static final String PREFIX = "jdbc:postgresql://";
    private static final int DEFAULT_PORT = 5432;

    private PostgresJdbcUrlParser() {
    }

    public static ParsedJdbcUrl parse(String jdbcUrl) {
        if (jdbcUrl == null || jdbcUrl.isBlank() || !jdbcUrl.startsWith(PREFIX)) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "PostgreSQL JDBC URL 配置错误");
        }

        try {
            URI uri = URI.create("postgresql://" + jdbcUrl.substring(PREFIX.length()));
            String host = uri.getHost();
            int port = uri.getPort() > 0 ? uri.getPort() : DEFAULT_PORT;
            String path = uri.getPath();
            if (host == null || host.isBlank() || path == null || path.isBlank() || "/".equals(path)) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "PostgreSQL JDBC URL 配置错误");
            }
            String database = path.startsWith("/") ? path.substring(1) : path;
            if (database.isBlank()) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "PostgreSQL JDBC URL 配置错误");
            }
            return new ParsedJdbcUrl(host, port, database);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "PostgreSQL JDBC URL 配置错误");
        }
    }

    public record ParsedJdbcUrl(String host, int port, String database) {
    }
}
