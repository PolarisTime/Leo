package com.leo.erp;

import com.leo.erp.common.architecture.SanctionedStatusWriter;
import com.tngtech.archunit.ArchConfiguration;
import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.EvaluationResult;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import com.tngtech.archunit.library.freeze.FreezingArchRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Build-time top-level module boundary gate backed by ArchUnit {@link FreezingArchRule}.
 *
 * <p>Replaces the retired Python import scanner with bytecode-level analysis while keeping the
 * same ratchet semantics: existing violations live in a committed store, new violations fail the
 * build, and the store is read-only by default. The store only changes when
 * {@code --write-baseline} is passed explicitly (and never under {@code CI=true}); this closes the
 * "local run silently refreezes new violations and the build passes" hole.</p>
 *
 * <p>This source is compiled into a temporary tools directory and is never packaged with the
 * application.</p>
 */
public final class ModuleBoundaryVerifier {

    private static final Logger LOGGER = LoggerFactory.getLogger(ModuleBoundaryVerifier.class);

    private static final String BASE_PACKAGE = "com.leo.erp";
    private static final String BASE_PACKAGE_PREFIX = BASE_PACKAGE + ".";
    private static final Path DEFAULT_STORE_PATH = Paths.get("config", "archunit-boundary-store");

    /**
     * 跨模块 {@code *.service} 依赖的显式例外（不是冻结基线，也不是 store）。
     *
     * <p>默认情况下，模块之间只能通过 {@code *.api} 端口、{@code *.web.dto} 或事件契约协作；
     * 直接依赖其它模块 {@code *.service} 内部实现属于"改一处断一片"。存量例外必须逐条登记在此，
     * 写明来源模块、目标模块、目标包前缀与理由，且一旦抽取端口完成必须删除；
     * 任何未登记的新增 service 依赖都会让门禁失败。</p>
     *
     * <p><b>当前为空</b>：`sales -> system.printtemplate.service` 的布局 DSL 例外已于
     * printtemplate 抽出 {@code system.printtemplate.api} 只读端口
     * （{@code PrintLayoutQuery} / {@code PrintExportFilenameQuery} / {@code PrintSpecLengthQuery}
     * + 不可变的 {@code PrintXlsxExportLayout} / {@code PrintItemOptions} / {@code PrintItemSplitter}）
     * 后删除。新增例外必须逐条登记并写清"为什么不走 api 端口"与解除条件。</p>
     */
    private static final List<AllowedServiceDependency> ALLOWED_SERVICE_DEPENDENCIES = List.of();

    private ModuleBoundaryVerifier() {
    }

    public static void main(String[] args) {
        boolean refreeze = false;
        if (args.length == 1 && "--refreeze".equals(args[0])) {
            refreeze = true;
        } else if (args.length != 0) {
            LOGGER.error("Usage: ModuleBoundaryVerifier [--refreeze]");
            System.exit(2);
        }

        boolean ci = "true".equalsIgnoreCase(System.getenv("CI"));
        if (refreeze && ci) {
            LOGGER.error("Refusing to refreeze the boundary baseline while CI=true");
            System.exit(2);
        }

        Path storePath = Paths.get(System.getProperty("leo.boundary.store", DEFAULT_STORE_PATH.toString()));
        ArchConfiguration configuration = ArchConfiguration.get();
        configuration.setProperty("freeze.store.default.path", storePath.toString());
        configuration.setProperty("freeze.store.default.allowStoreCreation", String.valueOf(refreeze));
        // 默认只读：本地运行也不再自动写基线，避免"新违规被静默冻结并通过"。
        // 只有显式 --write-baseline（脚本传 --refreeze）才允许创建/更新/收缩基线，且 CI 下拒绝。
        configuration.setProperty("freeze.store.default.allowStoreUpdate", String.valueOf(refreeze));
        configuration.setProperty("freeze.refreeze", String.valueOf(refreeze));

        JavaClasses classes = new ClassFileImporter().importPath("target/classes");
        long moduleClassCount = classes.stream()
                .filter(clazz -> clazz.getPackageName().startsWith(BASE_PACKAGE_PREFIX))
                .count();
        if (moduleClassCount == 0) {
            throw new IllegalStateException("No compiled classes found under " + BASE_PACKAGE
                    + "; run mvn compile before the boundary check");
        }

        ArchRule restrictedInternalsRule = ArchRuleDefinition.classes()
                .should(notDependOnRestrictedInternalsOfOtherTopLevelModules())
                .as("top-level modules must not depend on repository, domain.entity or web.dto internals of other top-level modules");
        ServiceInternalCondition serviceInternalCondition = new ServiceInternalCondition();
        ArchRule serviceInternalsRule = ArchRuleDefinition.classes()
                .should(serviceInternalCondition)
                .as("top-level modules must not depend on *.service internals of other top-level modules"
                        + " (api ports / web.dto / event contracts are the sanctioned cross-module path)");
        ArchRule cyclicEdgeRule = ArchRuleDefinition.classes()
                .should(notContributeCyclicTopLevelModuleEdges())
                .as("top-level module dependency edges must not participate in cycles");
        ArchRule statusWriteRule = ArchRuleDefinition.classes()
                .should(onlyWriteEntityStatusFromSanctionedClasses())
                .as("entity status must only be written by ApplyService, CompletionSyncService or CrudStatusGuard classes");

        int restrictedTotal = countViolations(restrictedInternalsRule, classes);
        EvaluationResult serviceInternalResult = serviceInternalsRule.evaluate(classes);
        int serviceInternalTotal = serviceInternalResult.getFailureReport().getDetails().size();
        int cyclicEdgeTotal = countViolations(cyclicEdgeRule, classes);
        int statusWriteTotal = countViolations(statusWriteRule, classes);

        FreezingArchRule.freeze(restrictedInternalsRule).check(classes);
        // 跨模块 service 依赖不进入冻结基线：存量例外必须显式登记在 ALLOWED_SERVICE_DEPENDENCIES 里
        // （带来源模块、目标模块、目标包前缀与理由），其余任何新增依赖立即失败——避免"先在本地
        // refreeze、再提交"的旧漏洞。这里只评估一次（计数与失败判定共用同一结果），避免条件被
        // 重复执行导致 allowlist 计数翻倍。
        if (serviceInternalResult.hasViolation()) {
            throw new AssertionError(serviceInternalResult.getFailureReport().toString());
        }
        FreezingArchRule.freeze(cyclicEdgeRule).check(classes);
        FreezingArchRule.freeze(statusWriteRule).check(classes);

        LOGGER.info(
                "Architecture boundary check passed: {} classes, {} baselined restricted dependencies, "
                        + "{} cross-module service dependencies, {} allowlisted service edges ({} exception rules), "
                        + "{} baselined cyclic edges, {} baselined status writes (store: {})",
                moduleClassCount,
                restrictedTotal,
                serviceInternalTotal,
                serviceInternalCondition.allowlistedEdges(),
                ALLOWED_SERVICE_DEPENDENCIES.size(),
                cyclicEdgeTotal,
                statusWriteTotal,
                storePath);
    }

    private static int countViolations(ArchRule rule, JavaClasses classes) {
        EvaluationResult result = rule.evaluate(classes);
        return result.getFailureReport().getDetails().size();
    }

    /** 顶层模块名：com.leo.erp 之后的第一个包段；不属于基础包时返回 null。 */
    private static String topLevelModuleOf(JavaClass clazz) {
        String packageName = clazz.getPackageName();
        if (!packageName.startsWith(BASE_PACKAGE_PREFIX)) {
            return null;
        }
        String remainder = packageName.substring(BASE_PACKAGE_PREFIX.length());
        int dot = remainder.indexOf('.');
        return dot < 0 ? remainder : remainder.substring(0, dot);
    }

    /**
     * 与退役的 Python 扫描器保持一致的受限内部类型判定：repository、domain.entity、web.dto。
     *
     * <p><b>平台层例外</b>：{@code common} 是平台模块，它的 {@code web.dto} 与 {@code api}
     * 是跨模块的对外契约（如 {@code StatusUpdateRequest}、{@code FileDownloadResponse}、
     * {@code LengthSpecCatalog}），其它模块依赖它们属于设计意图，而不是"依赖别人的内部实现"。
     * 2026-10-31 遗留审计发现冻结基线 158 条里有 42 条（27%）正是这类误报；若不放行，
     * 真正的结构性债务（InventoryBackfillService 72 条等）会被噪声淹没。此处只放行 common 的
     * 契约包，common 的 repository / domain.entity 仍按原规则受限。</p>
     */
    private static String restrictedKindOf(JavaClass target) {
        String packageName = target.getPackageName();
        if (!packageName.startsWith(BASE_PACKAGE_PREFIX)) {
            return null;
        }
        String[] segments = packageName.substring(BASE_PACKAGE_PREFIX.length()).split("\\.");
        String kind = null;
        for (int i = 1; i < segments.length && kind == null; i++) {
            if ("repository".equals(segments[i])) {
                kind = "repository";
            } else if (i + 1 < segments.length && "domain".equals(segments[i]) && "entity".equals(segments[i + 1])) {
                kind = "domain.entity";
            } else if (i + 1 < segments.length && "web".equals(segments[i]) && "dto".equals(segments[i + 1])) {
                kind = "web.dto";
            }
        }
        if (kind == null) {
            return null;
        }
        if ("common".equals(segments[0]) && isPlatformContractPackage(packageName, kind)) {
            return null;
        }
        return kind;
    }

    /** common 平台层对外契约包判定：{@code web.dto}（含子包）与 {@code common.api}（含子包）。 */
    private static boolean isPlatformContractPackage(String packageName, String kind) {
        return "web.dto".equals(kind)
                || packageName.startsWith(BASE_PACKAGE_PREFIX + "common.api");
    }

    private static ArchCondition<JavaClass> notDependOnRestrictedInternalsOfOtherTopLevelModules() {
        return new ArchCondition<>("not depend on restricted internals of other top-level modules") {
            @Override
            public void check(JavaClass clazz, ConditionEvents events) {
                String originModule = topLevelModuleOf(clazz);
                if (originModule == null) {
                    return;
                }
                /*
                 * 按 (kind, 源类, 目标类) 去重：getDirectDependenciesFromSelf() 对同一个目标类
                 * 会按引用次数重复产出（字段 + 方法调用 + 泛型签名各算一条），不去重时基线里
                 * 同一对类会堆成多行——2026-11-02 之前 32 条基线实为 9 个不同的类对。
                 */
                Set<String> messages = new LinkedHashSet<>();
                for (Dependency dependency : clazz.getDirectDependenciesFromSelf()) {
                    JavaClass target = dependency.getTargetClass().getBaseComponentType();
                    String targetModule = topLevelModuleOf(target);
                    if (targetModule == null || targetModule.equals(originModule)) {
                        continue;
                    }
                    String kind = restrictedKindOf(target);
                    if (kind == null) {
                        continue;
                    }
                    // 描述中不含行号，保证基线条目在代码位移后仍稳定匹配。
                    messages.add(String.format(Locale.ROOT,
                            "restricted %s dependency: %s (module %s) -> %s (module %s)",
                            kind, clazz.getName(), originModule, target.getName(), targetModule));
                }
                messages.forEach(message -> events.add(SimpleConditionEvent.violated(clazz, message)));
            }
        };
    }

    /**
     * 跨模块 {@code *.service} 依赖判定。
     *
     * <p><b>为什么单列</b>：原有三条规则只覆盖 {@code repository} / {@code domain.entity} / {@code web.dto}
     * 三种受限内部类型，跨模块直接依赖别人 {@code *.service} 实现（例如 {@code sales -> system} 的 15 条打印
     * 布局依赖）在两条门禁（ArchUnit 边界 + Modulith 仅校验 8 个显式模块）下都是静默的。</p>
     *
     * <p><b>放行范围</b>：</p>
     * <ul>
     *   <li>{@code *.api} 端口、{@code *.web.dto}、事件契约不在 {@code service} 包内，自然不受此规则约束；</li>
     *   <li>{@code common} 是平台模块（{@code CrudStatusGuard}、{@code CrudVisibilityPolicy}、
     *       {@code CrudOperationLogger}、{@code DocumentChargeItemService}、{@code Excel*} 等），其
     *       {@code service} 包按设计就是跨模块的平台能力，与"依赖别的业务模块内部实现"不是同一类问题，
     *       因此整体放行；{@code common} 的 repository / domain.entity 仍受原规则约束；</li>
     *   <li>显式登记在 {@link #ALLOWED_SERVICE_DEPENDENCIES} 的存量例外（每条都带模块、包前缀与理由）。</li>
     * </ul>
     *
     * <p>本规则<b>不</b>进入 FreezingArchRule：没有冻结基线可扩大，新增依赖一律当场失败。</p>
     */
    private static final class ServiceInternalCondition
            extends ArchCondition<JavaClass> {

        private int allowlistedEdges;

        private ServiceInternalCondition() {
            super("not depend on service internals of other top-level modules");
        }

        private int allowlistedEdges() {
            return allowlistedEdges;
        }

        @Override
        public void check(JavaClass clazz, ConditionEvents events) {
            String originModule = topLevelModuleOf(clazz);
            if (originModule == null) {
                return;
            }
            for (Dependency dependency : clazz.getDirectDependenciesFromSelf()) {
                JavaClass target = dependency.getTargetClass().getBaseComponentType();
                String targetModule = topLevelModuleOf(target);
                if (targetModule == null || targetModule.equals(originModule)) {
                    continue;
                }
                if ("common".equals(targetModule) || !isServiceInternalPackage(target.getPackageName())) {
                    continue;
                }
                if (isAllowedServiceDependency(originModule, targetModule, target.getPackageName())) {
                    allowlistedEdges++;
                    continue;
                }
                // 描述中不含行号，保证失败信息稳定可读，也不受代码位移影响。
                String message = String.format(Locale.ROOT,
                        "cross-module service dependency: %s (module %s) -> %s (module %s)",
                        clazz.getName(), originModule, target.getName(), targetModule);
                events.add(SimpleConditionEvent.violated(clazz, message));
            }
        }
    }

    /** {@code com.leo.erp.<module>.(...).service(...)} 形式的目标包；{@code common} 已在上游放行。 */
    private static boolean isServiceInternalPackage(String packageName) {
        if (!packageName.startsWith(BASE_PACKAGE_PREFIX)) {
            return false;
        }
        String[] segments = packageName.substring(BASE_PACKAGE_PREFIX.length()).split("\\.");
        for (int i = 1; i < segments.length; i++) {
            if ("service".equals(segments[i])) {
                return true;
            }
        }
        return false;
    }

    private static boolean isAllowedServiceDependency(String originModule, String targetModule, String targetPackage) {
        for (AllowedServiceDependency allowed : ALLOWED_SERVICE_DEPENDENCIES) {
            if (allowed.originModule().equals(originModule)
                    && allowed.targetModule().equals(targetModule)
                    && targetPackage.startsWith(allowed.targetPackagePrefix())) {
                return true;
            }
        }
        return false;
    }

    /** 跨模块 service 依赖的显式例外条目；{@code reason} 必须写清"为什么不走 api 端口"与解除条件。 */
    private record AllowedServiceDependency(
            String originModule,
            String targetModule,
            String targetPackagePrefix,
            String reason
    ) {
    }

    /**
     * 单据状态写入白名单：迁移守卫之外的 setStatus 旁路会绕过 beforeStatusUpdate 业务校验，
     * 只允许聚合自己的 ApplyService、跨聚合完成同步服务、框架守卫，以及显式登记的人工
     * 终结/回转服务写入实体状态。
     */
    private static ArchCondition<JavaClass> onlyWriteEntityStatusFromSanctionedClasses() {
        return new ArchCondition<>("only write entity status from sanctioned status-writing classes") {
            @Override
            public void check(JavaClass clazz, ConditionEvents events) {
                if (topLevelModuleOf(clazz) == null || isSanctionedStatusWriter(clazz)) {
                    return;
                }
                // 同一类对同一实体的多处 setStatus 只记一条：基线条目按类对计，不按调用点数计。
                Set<String> messages = new LinkedHashSet<>();
                for (JavaMethodCall call : clazz.getMethodCallsFromSelf()) {
                    JavaClass target = call.getTargetOwner();
                    if (!"setStatus".equals(call.getName())
                            || !"domain.entity".equals(restrictedKindOf(target))) {
                        continue;
                    }
                    messages.add(String.format(Locale.ROOT,
                            "unsanctioned entity status write: %s -> %s.setStatus",
                            clazz.getName(), target.getName()));
                }
                messages.forEach(message -> events.add(SimpleConditionEvent.violated(clazz, message)));
            }
        };
    }

    /**
     * 状态写白名单判定：<b>只认显式注解</b>，不再按类名后缀放行。
     *
     * <p>按后缀放行等于「改个类名就获得写状态权限」：新增一个 {@code *ApplyService} 会被静默豁免，
     * 评审 diff 里也看不出谁被放行。改为 {@link SanctionedStatusWriter} 后，豁免必须在代码里
     * 显式声明并写明理由，构建期门禁才认。</p>
     */
    private static boolean isSanctionedStatusWriter(JavaClass clazz) {
        return clazz.isAnnotatedWith(SanctionedStatusWriter.class)
                || "com.leo.erp.common.service.CrudStatusGuard".equals(clazz.getName());
    }

    private static ArchCondition<JavaClass> notContributeCyclicTopLevelModuleEdges() {        return new ArchCondition<>("not contribute dependency edges that participate in top-level module cycles") {
            private final Map<String, Set<String>> edges = new TreeMap<>();

            @Override
            public void check(JavaClass clazz, ConditionEvents events) {
                String originModule = topLevelModuleOf(clazz);
                if (originModule == null) {
                    return;
                }
                for (Dependency dependency : clazz.getDirectDependenciesFromSelf()) {
                    String targetModule = topLevelModuleOf(dependency.getTargetClass().getBaseComponentType());
                    if (targetModule != null && !targetModule.equals(originModule)) {
                        edges.computeIfAbsent(originModule, key -> new TreeSet<>()).add(targetModule);
                    }
                }
            }

            @Override
            public void finish(ConditionEvents events) {
                Map<String, Integer> componentByModule = stronglyConnectedComponents(edges);
                for (Map.Entry<String, Set<String>> entry : edges.entrySet()) {
                    String source = entry.getKey();
                    for (String target : entry.getValue()) {
                        Integer sourceComponent = componentByModule.get(source);
                        if (sourceComponent != null && sourceComponent.equals(componentByModule.get(target))) {
                            String message = String.format(Locale.ROOT,
                                    "cyclic module edge: %s -> %s", source, target);
                            events.add(SimpleConditionEvent.violated(source, message));
                        }
                    }
                }
            }
        };
    }

    /** 迭代版 Tarjan 强连通分量；模块数量级极小，直接在内存计算。 */
    private static Map<String, Integer> stronglyConnectedComponents(Map<String, Set<String>> edges) {
        Set<String> nodes = new TreeSet<>(edges.keySet());
        edges.values().forEach(nodes::addAll);

        Map<String, Integer> index = new HashMap<>();
        Map<String, Integer> lowLink = new HashMap<>();
        Map<String, Integer> componentByModule = new HashMap<>();
        Set<String> onStack = new HashSet<>();
        Deque<String> stack = new ArrayDeque<>();
        int[] nextIndex = {0};
        int[] nextComponent = {0};

        for (String node : nodes) {
            if (!index.containsKey(node)) {
                strongConnect(node, edges, index, lowLink, componentByModule, onStack, stack, nextIndex, nextComponent);
            }
        }
        return componentByModule;
    }

    private static void strongConnect(String root,
                                      Map<String, Set<String>> edges,
                                      Map<String, Integer> index,
                                      Map<String, Integer> lowLink,
                                      Map<String, Integer> componentByModule,
                                      Set<String> onStack,
                                      Deque<String> stack,
                                      int[] nextIndex,
                                      int[] nextComponent) {
        Deque<Frame> work = new ArrayDeque<>();
        work.push(openFrame(root, edges, index, lowLink, onStack, stack, nextIndex));

        while (!work.isEmpty()) {
            Frame frame = work.pop();
            String node = frame.node();
            int position = frame.position();
            boolean descended = false;
            while (position < frame.successors().size()) {
                String successor = frame.successors().get(position);
                position++;
                if (!index.containsKey(successor)) {
                    work.push(new Frame(node, frame.successors(), position));
                    work.push(openFrame(successor, edges, index, lowLink, onStack, stack, nextIndex));
                    descended = true;
                    break;
                }
                if (onStack.contains(successor)) {
                    lowLink.merge(node, index.get(successor), Math::min);
                }
            }
            if (descended) {
                continue;
            }
            if (lowLink.get(node).equals(index.get(node))) {
                int component = nextComponent[0]++;
                String member;
                do {
                    member = stack.pop();
                    onStack.remove(member);
                    componentByModule.put(member, component);
                } while (!member.equals(node));
            }
            Frame parent = work.peek();
            if (parent != null) {
                lowLink.merge(parent.node(), lowLink.get(node), Math::min);
            }
        }
    }

    private static Frame openFrame(String node,
                                   Map<String, Set<String>> edges,
                                   Map<String, Integer> index,
                                   Map<String, Integer> lowLink,
                                   Set<String> onStack,
                                   Deque<String> stack,
                                   int[] nextIndex) {
        index.put(node, nextIndex[0]);
        lowLink.put(node, nextIndex[0]);
        nextIndex[0]++;
        stack.push(node);
        onStack.add(node);
        return new Frame(node, List.copyOf(edges.getOrDefault(node, Set.of())), 0);
    }

    private record Frame(String node, List<String> successors, int position) {
    }
}
