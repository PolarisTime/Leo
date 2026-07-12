package com.leo.erp.common.support;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

@Service
public class MasterDataReferenceGuard {

    private static final Pattern SQL_IDENTIFIER = Pattern.compile("[a-z][a-z0-9_]*");

    private final JdbcTemplate jdbc;

    public MasterDataReferenceGuard(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void assertNoReferences(String masterDataLabel, List<ReferenceCheck> references) {
        assertNoReferences(masterDataLabel, "删除", references);
    }

    public void assertNoReferences(String masterDataLabel,
                                   String action,
                                   List<ReferenceCheck> references) {
        for (ReferenceCheck reference : references) {
            if (reference.hasBlankValue()) {
                continue;
            }
            Long count = jdbc.queryForObject(reference.sql(), Long.class, reference.arguments());
            if (count != null && count > 0) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                        masterDataLabel + "已被业务或主数据引用，不能" + action + "（"
                                + reference.tableName() + "." + reference.columnName()
                                + " 中有 " + count + " 条记录）");
            }
        }
    }

    public record ReferenceCheck(
            String tableName,
            String columnName,
            Object value,
            boolean activeOnly,
            String extraCondition,
            List<Object> extraArguments
    ) {

        public ReferenceCheck {
            validateIdentifier(tableName, "tableName");
            validateIdentifier(columnName, "columnName");
            extraArguments = extraArguments == null ? List.of() : List.copyOf(extraArguments);
        }

        public static ReferenceCheck active(String tableName, String columnName, Object value) {
            return new ReferenceCheck(tableName, columnName, value, true, null, List.of());
        }

        public static ReferenceCheck any(String tableName, String columnName, Object value) {
            return new ReferenceCheck(tableName, columnName, value, false, null, List.of());
        }

        public static ReferenceCheck when(String tableName,
                                          String columnName,
                                          Object value,
                                          String extraCondition,
                                          Object... extraArguments) {
            return new ReferenceCheck(
                    tableName,
                    columnName,
                    value,
                    false,
                    Objects.requireNonNull(extraCondition, "extraCondition"),
                    List.of(extraArguments)
            );
        }

        public static ReferenceCheck activeWhen(String tableName,
                                                String columnName,
                                                Object value,
                                                String extraCondition,
                                                Object... extraArguments) {
            return new ReferenceCheck(
                    tableName,
                    columnName,
                    value,
                    true,
                    Objects.requireNonNull(extraCondition, "extraCondition"),
                    List.of(extraArguments)
            );
        }

        public static ReferenceCheck ofActiveParent(String tableName,
                                                    String columnName,
                                                    Object value,
                                                    String parentTableName,
                                                    String parentForeignKey) {
            return when(
                    tableName,
                    columnName,
                    value,
                    activeParentCondition(tableName, parentTableName, parentForeignKey)
            );
        }

        public static ReferenceCheck legacyOfActiveParent(String tableName,
                                                          String snapshotColumn,
                                                          Object snapshotValue,
                                                          String identityColumn,
                                                          String parentTableName,
                                                          String parentForeignKey) {
            validateIdentifier(identityColumn, "identityColumn");
            return when(
                    tableName,
                    snapshotColumn,
                    snapshotValue,
                    identityColumn + " IS NULL AND "
                            + activeParentCondition(tableName, parentTableName, parentForeignKey)
            );
        }

        private static String activeParentCondition(String tableName,
                                                    String parentTableName,
                                                    String parentForeignKey) {
            validateIdentifier(tableName, "tableName");
            validateIdentifier(parentTableName, "parentTableName");
            validateIdentifier(parentForeignKey, "parentForeignKey");
            return "EXISTS (SELECT 1 FROM " + parentTableName + " parent "
                    + "WHERE parent.id = " + tableName + "." + parentForeignKey
                    + " AND parent.deleted_flag = false)";
        }

        private boolean hasBlankValue() {
            if (value == null) {
                return true;
            }
            return value instanceof String text && text.isBlank();
        }

        private String sql() {
            StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM ")
                    .append(tableName)
                    .append(" WHERE ");
            if (activeOnly) {
                sql.append("deleted_flag = false AND ");
            }
            sql.append(columnName).append(" = ?");
            if (extraCondition != null && !extraCondition.isBlank()) {
                sql.append(" AND ").append(extraCondition);
            }
            return sql.toString();
        }

        private Object[] arguments() {
            List<Object> args = new ArrayList<>(extraArguments.size() + 1);
            args.add(value);
            args.addAll(extraArguments);
            return args.toArray();
        }

        private static void validateIdentifier(String value, String label) {
            if (value == null || !SQL_IDENTIFIER.matcher(value).matches()) {
                throw new IllegalArgumentException(label + " must be a trusted SQL identifier");
            }
        }
    }
}
