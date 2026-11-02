package com.leo.erp.common.support;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 契约测试：JPQL 里写死的单据状态字面量必须与 {@link StatusConstants} 的取值一致。
 *
 * <p>背景：全仓有 20+ 处 {@code @Query} 直接用 {@code status = '已审核'} 这类字面量。
 * 这些查询没法直接引用常量（JPQL 是注解里的文本，拼常量会把查询拆得不可读），
 * 于是「改状态文案时漏改 SQL」成了静默缺陷：改完 {@link StatusConstants#AUDITED}，
 * 财务口径的查询仍按旧文案过滤，页面数字对不上却不报错。</p>
 *
 * <p>这里不重写查询，而是把两者的取值锁在一起：只要 SQL 里出现一个字面量不在常量表里，
 * 测试立刻红，并指出具体是哪个方法、哪个字面量。新增状态值时请先加到
 * {@link StatusConstants}，再改查询。</p>
 *
 * <p>覆盖边界：只检查 Spring Data {@code @Query} 注解；写在服务层 Java 字符串里的原生 SQL
 * （如应付款余额的统计 SQL）不在反射可见范围内，改动那些查询时仍需人工核对常量。</p>
 */
class SqlStatusLiteralContractTest {

    /** 匹配 `status = '已审核'`、`status<>'草稿'`、`status IN ('已审核','完成入库')`。 */
    private static final Pattern STATUS_LITERAL = Pattern.compile(
            "(?i)\\bstatus\\s*(?:=|<>|<>)??\\s*(?:in)?\\s*\\(?\\s*'([^']*)'",
            Pattern.DOTALL);

    /**
     * 不属于「单据状态文案」的例外取值，必须逐个写明来源；新增条目要在注释里说明为什么不是单据状态。
     */
    private static final Set<String> NON_DOCUMENT_STATUS_LITERALS = Set.of(
            // 用户账号状态用的是枚举名（auth 模块 u.status = 'NORMAL'），与单据状态文案（「正常」）不同源
            "NORMAL",
            // 采购订单列表的虚拟筛选值：'已删除' 映射到 deletedFlag = true，不是真实 status 取值
            "已删除"
    );

    @Test
    void queryStatusLiterals_mustMatchStatusConstants() throws Exception {
        Set<String> allowed = new LinkedHashSet<>(statusConstantValues());
        allowed.addAll(NON_DOCUMENT_STATUS_LITERALS);
        assertThat(allowed).as("StatusConstants 应有状态取值").isNotEmpty();

        Set<String> violations = new LinkedHashSet<>();
        Set<String> seen = new LinkedHashSet<>();

        for (Class<?> type : scanClasses()) {
            for (Method method : type.getDeclaredMethods()) {
                Query query = method.getAnnotation(Query.class);
                if (query == null) {
                    continue;
                }
                Matcher matcher = STATUS_LITERAL.matcher(query.value());
                while (matcher.find()) {
                    String literal = matcher.group(1);
                    seen.add(literal);
                    if (!allowed.contains(literal)) {
                        violations.add(type.getSimpleName() + "#" + method.getName()
                                + " -> '" + literal + "' 未登记在 StatusConstants");
                    }
                }
            }
        }

        assertThat(violations).isEmpty();
        assertThat(seen)
                .as("应扫描到 @Query 里的状态字面量（至少含「已审核」与另一种状态），否则扫描范围失效")
                .contains(StatusConstants.AUDITED)
                .hasSizeGreaterThanOrEqualTo(2);
    }

    private Set<String> statusConstantValues() throws IllegalAccessException {
        Set<String> values = new LinkedHashSet<>();
        for (Field field : StatusConstants.class.getDeclaredFields()) {
            int modifiers = field.getModifiers();
            if (Modifier.isPublic(modifiers) && Modifier.isStatic(modifiers)
                    && Modifier.isFinal(modifiers) && field.getType() == String.class) {
                values.add((String) field.get(null));
            }
        }
        return values;
    }

    /**
     * 直接遍历编译产物：{@code @Query} 标注在 Spring Data <b>接口</b>方法上，
     * 而 ClassPathScanningCandidateComponentProvider 默认只把「具体类」当候选，
     * 接口会被整体跳过（那样本测试会「零覆盖却通过」）。
     */
    private List<Class<?>> scanClasses() throws Exception {
        Path root = Paths.get("target", "classes");
        List<Class<?>> classes = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".class"))
                    .toList()) {
                String className = root.relativize(path).toString()
                        .replace(File.separatorChar, '.')
                        .replaceAll("\\.class$", "");
                if (className.contains("$") || className.startsWith("module-info")) {
                    continue;
                }
                classes.add(Class.forName(className));
            }
        }
        assertThat(classes).as("应能从 target/classes 扫描到业务类").isNotEmpty();
        return classes;
    }
}
