package com.leo.erp.master.project;

import com.leo.erp.master.project.domain.entity.Project;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V220「配置归并」的真实库验收（默认跳过，需 {@code LEO_TEST_POSTGRES=true}）。
 *
 * <p>两条路径都要成立：</p>
 * <ol>
 *   <li>迁移已由 Flyway 应用（正常情况）→ 直接校验结果；</li>
 *   <li>尚未应用（本地刚拉起）→ 在本测试事务内执行同一份脚本，校验后回滚，dev 库不受影响。</li>
 * </ol>
 *
 * <p>校验点：5 列的类型/默认值契约、标量回填、品牌清单回填 + 力度置 OFF、品牌品种汇总、
 * 占位品牌「基准价」在三处彻底消失、以及 JSONB 字段经实体读写的往返一致性。</p>
 */
@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.flyway.enabled=false",
        "spring.jpa.show-sql=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "LEO_TEST_POSTGRES", matches = "true")
class ProjectQuoteConfigMergePostgresTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager entityManager;

    @Test
    void migration_shouldAddQuoteColumnsWithContractTypes() {
        ensureMigrated();

        assertThat(columnType("quote_products")).isEqualTo("text");
        assertThat(columnType("quote_brand_categories")).isEqualTo("jsonb");
        assertThat(columnType("quote_length_premium")).isEqualTo("numeric(12,2)");
        assertThat(columnType("quote_hrb400e_fallback")).isEqualTo("boolean");
        assertThat(columnType("quote_designated_brands")).isEqualTo("character varying(512)");
        // 代取开关必须 NOT NULL + 默认 false：历史数据不能出现「未定义」的第三态
        assertThat(jdbc.queryForObject(
                "select is_nullable from information_schema.columns"
                        + " where table_name = 'md_project' and column_name = 'quote_hrb400e_fallback'",
                String.class)).isEqualTo("NO");
        assertThat(jdbc.queryForObject(
                "select column_default from information_schema.columns"
                        + " where table_name = 'md_project' and column_name = 'quote_hrb400e_fallback'",
                String.class)).isEqualTo("false");
        // 每列都要有 COMMENT（运维/数据分析靠它理解口径）
        assertThat(jdbc.queryForList(
                "select col_description('public.md_project'::regclass, ordinal_position)"
                        + " from information_schema.columns"
                        + " where table_name = 'md_project' and column_name like 'quote_%'",
                String.class)).allMatch(comment -> comment != null && !comment.isBlank());
    }

    @Test
    void migration_shouldBackfillScalarsFromLegacyConfig() {
        ensureMigrated();

        Integer mismatched = jdbc.queryForObject(
                "select count(*) from mk_quote_project_config config"
                        + " join md_project project on project.id = config.project_id"
                        + " where config.deleted_flag = false"
                        + "   and (project.quote_length_premium is distinct from config.length_premium"
                        + "     or project.quote_hrb400e_fallback is distinct from config.hrb400e_fallback"
                        + "     or project.quote_products is distinct from config.products)",
                Integer.class);
        assertThat(mismatched).isZero();
    }

    @Test
    void migration_shouldBackfillAllowedBrandsAndDisableEnforcement() {
        ensureMigrated();

        // 只在比价配置里配过品牌的项目：allowed_brands 必须按 sort_order 回填成配置品牌清单，
        // 且力度为 OFF（保留清单但不点亮采购/销售的品牌校验）
        Integer mismatched = jdbc.queryForObject(
                "select count(*) from ("
                        + "  select config.project_id,"
                        + "         string_agg(brand.brand_name, ',' order by brand.sort_order, brand.id) as brands"
                        + "    from mk_quote_project_config config"
                        + "    join mk_quote_project_brand brand on brand.config_id = config.id"
                        + "   where config.deleted_flag = false and brand.brand_name <> '基准价'"
                        + "   group by config.project_id) expected"
                        + " join md_project project on project.id = expected.project_id"
                        + " where project.allowed_brand_severity = 'OFF'"
                        + "   and project.allowed_brands is distinct from expected.brands",
                Integer.class);
        assertThat(mismatched).isZero();
        // 有配置品牌的项目不能再出现「资料里没品牌」的状态（否则比价页会退化成全部品牌）
        Integer stillEmpty = jdbc.queryForObject(
                "select count(*) from md_project project"
                        + " where exists (select 1 from mk_quote_project_config config"
                        + "                  join mk_quote_project_brand brand on brand.config_id = config.id"
                        + "                 where config.project_id = project.id"
                        + "                   and config.deleted_flag = false"
                        + "                   and brand.brand_name <> '基准价')"
                        + "   and (project.allowed_brands is null or btrim(project.allowed_brands) = '')",
                Integer.class);
        assertThat(stillEmpty).isZero();
    }

    @Test
    void migration_shouldAggregateBrandCategories() {
        ensureMigrated();

        Integer mismatched = jdbc.queryForObject(
                "select count(*) from ("
                        + "  select config.project_id,"
                        + "         jsonb_object_agg(brand.brand_name, brand.categories_json) as categories"
                        + "    from mk_quote_project_config config"
                        + "    join (select row.config_id, row.brand_name,"
                        + "                 to_jsonb(array(select btrim(token) from unnest("
                        + "                     string_to_array(row.categories, ',')) as token"
                        + "                   where btrim(token) <> '')) as categories_json"
                        + "            from mk_quote_project_brand row"
                        + "           where row.brand_name <> '基准价' and row.categories is not null) brand"
                        + "      on brand.config_id = config.id"
                        + "   where config.deleted_flag = false and jsonb_array_length(brand.categories_json) > 0"
                        + "   group by config.project_id) expected"
                        + " join md_project project on project.id = expected.project_id"
                        + " where project.quote_brand_categories is distinct from expected.categories",
                Integer.class);
        assertThat(mismatched).isZero();
    }

    @Test
    void migration_shouldRemoveBaselinePricePlaceholderEverywhere() {
        ensureMigrated();

        assertThat(jdbc.queryForObject(
                "select count(*) from mk_quote_project_brand where brand_name = '基准价'", Integer.class)).isZero();
        assertThat(jdbc.queryForObject(
                "select count(*) from md_project where allowed_brands like '%基准价%'", Integer.class)).isZero();
        assertThat(jdbc.queryForObject(
                "select count(*) from md_project where quote_designated_brands like '%基准价%'", Integer.class))
                .isZero();
        assertThat(jdbc.queryForObject(
                "select count(*) from md_project where quote_brand_categories ? '基准价'", Integer.class)).isZero();
    }

    @Test
    void projectQuoteFields_shouldRoundTripThroughJsonbColumn() {
        ensureMigrated();

        Project project = new Project();
        project.setId(900000000000009001L);
        project.setProjectCode("P-QUOTE-JSONB-1");
        project.setProjectName("V220 往返测试项目");
        // 客户身份有外键（customer_id, customer_code）：复用库里真实客户，不伪造
        java.util.List<Map<String, Object>> customers = jdbc.queryForList(
                "select id, customer_code from md_customer where deleted_flag = false order by id limit 1");
        org.junit.jupiter.api.Assumptions.assumeFalse(customers.isEmpty(), "库里没有可用客户，跳过往返用例");
        project.setCustomerId(((Number) customers.get(0).get("id")).longValue());
        project.setCustomerCode(String.valueOf(customers.get(0).get("customer_code")));
        project.setStatus("NORMAL");
        project.setPriceDateBasis("DELIVERY");
        project.setPriceDateOffsetDays(0);
        project.setPriceClosedSettleDirection("PREV");
        project.setPriceClosedSettleDays(1);
        project.setPricePeriodPolicy("AUTO");
        // 审计列由 AuditingEntityListener 填充，@DataJpaTest 未装配审计配置 → 手写，避免 NOT NULL 违规
        project.setCreatedBy(0L);
        project.setCreatedAt(java.time.LocalDateTime.now());
        project.setAllowedBrands("中天,萍钢");
        project.setQuoteProducts("螺纹钢|HRB400E|10|9米");
        project.setQuoteBrandCategories("{\"中天\":[\"直条\",\"盘螺\"]}");
        project.setQuoteLengthPremium(new BigDecimal("28.00"));
        project.setQuoteHrb400eFallback(true);
        project.setQuoteDesignatedBrands("中天");

        entityManager.persist(project);
        entityManager.flush();
        entityManager.clear();

        Project reloaded = entityManager.find(Project.class, project.getId());
        assertThat(reloaded).isNotNull();
        assertThat(reloaded.getQuoteProducts()).isEqualTo("螺纹钢|HRB400E|10|9米");
        // JSONB 回读是规范化后的文本，按 jsonb 语义比较
        assertThat(jdbc.queryForObject(
                "select quote_brand_categories::text from md_project where id = ?", String.class,
                project.getId())).isEqualTo("{\"中天\": [\"直条\", \"盘螺\"]}");
        assertThat(reloaded.getQuoteLengthPremium()).isEqualByComparingTo("28.00");
        assertThat(reloaded.isQuoteHrb400eFallback()).isTrue();
        assertThat(reloaded.getQuoteDesignatedBrands()).isEqualTo("中天");
    }

    // ------------------------------------------------------------------

    /** 未迁移则在本事务内执行 V220（随后回滚），已迁移则直接校验结果。 */
    private void ensureMigrated() {
        if (columnType("quote_products") != null) {
            return;
        }
        String script = readMigration();
        for (String statement : splitStatements(script)) {
            jdbc.execute(statement);
        }
    }

    private String readMigration() {
        try (var stream = getClass().getClassLoader()
                .getResourceAsStream("db/migration/V220__project_quote_config_merge.sql")) {
            assertThat(stream).as("V220 迁移脚本必须在 classpath 上").isNotNull();
            return new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException ex) {
            throw new IllegalStateException("读取 V220 迁移脚本失败", ex);
        }
    }

    /** 去掉注释后按分号切分（脚本内没有函数体，简单切分足够）。 */
    private static List<String> splitStatements(String script) {
        StringBuilder withoutComments = new StringBuilder();
        for (String line : script.split("\n")) {
            String trimmed = line.strip();
            if (trimmed.isEmpty() || trimmed.startsWith("--")) {
                continue;
            }
            withoutComments.append(line).append('\n');
        }
        return java.util.Arrays.stream(withoutComments.toString().split(";"))
                .map(String::strip)
                .filter(statement -> !statement.isEmpty())
                .toList();
    }

    private String columnType(String column) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "select data_type, character_maximum_length, numeric_precision, numeric_scale, udt_name"
                        + " from information_schema.columns"
                        + " where table_name = 'md_project' and column_name = ?", column);
        if (rows.isEmpty()) {
            return null;
        }
        Map<String, Object> row = rows.get(0);
        String dataType = String.valueOf(row.get("data_type"));
        return switch (dataType) {
            case "character varying" -> "character varying(" + row.get("character_maximum_length") + ")";
            case "numeric" -> "numeric(" + row.get("numeric_precision") + "," + row.get("numeric_scale") + ")";
            case "USER-DEFINED" -> String.valueOf(row.get("udt_name"));
            default -> dataType;
        };
    }
}
