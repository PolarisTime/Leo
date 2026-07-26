package com.leo.erp;

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
 * build, and fixed violations shrink the store automatically on local runs.</p>
 *
 * <p>This source is compiled into a temporary tools directory and is never packaged with the
 * application.</p>
 */
public final class ModuleBoundaryVerifier {

    private static final Logger LOGGER = LoggerFactory.getLogger(ModuleBoundaryVerifier.class);

    private static final String BASE_PACKAGE = "com.leo.erp";
    private static final String BASE_PACKAGE_PREFIX = BASE_PACKAGE + ".";
    private static final Path DEFAULT_STORE_PATH = Paths.get("config", "archunit-boundary-store");

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
        // CI 只读校验；本地运行允许 FreezingArchRule 在违规修复后自动收缩基线。
        configuration.setProperty("freeze.store.default.allowStoreUpdate", String.valueOf(!ci));
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
        ArchRule cyclicEdgeRule = ArchRuleDefinition.classes()
                .should(notContributeCyclicTopLevelModuleEdges())
                .as("top-level module dependency edges must not participate in cycles");
        ArchRule statusWriteRule = ArchRuleDefinition.classes()
                .should(onlyWriteEntityStatusFromSanctionedClasses())
                .as("entity status must only be written by ApplyService, CompletionSyncService or CrudStatusGuard classes");

        int restrictedTotal = countViolations(restrictedInternalsRule, classes);
        int cyclicEdgeTotal = countViolations(cyclicEdgeRule, classes);
        int statusWriteTotal = countViolations(statusWriteRule, classes);

        FreezingArchRule.freeze(restrictedInternalsRule).check(classes);
        FreezingArchRule.freeze(cyclicEdgeRule).check(classes);
        FreezingArchRule.freeze(statusWriteRule).check(classes);

        LOGGER.info(
                "Architecture boundary check passed: {} classes, {} baselined restricted dependencies, {} baselined cyclic edges, {} baselined status writes (store: {})",
                moduleClassCount, restrictedTotal, cyclicEdgeTotal, statusWriteTotal, storePath);
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

    /** 与退役的 Python 扫描器保持一致的受限内部类型判定：repository、domain.entity、web.dto。 */
    private static String restrictedKindOf(JavaClass target) {
        String packageName = target.getPackageName();
        if (!packageName.startsWith(BASE_PACKAGE_PREFIX)) {
            return null;
        }
        String[] segments = packageName.substring(BASE_PACKAGE_PREFIX.length()).split("\\.");
        for (int i = 1; i < segments.length; i++) {
            if ("repository".equals(segments[i])) {
                return "repository";
            }
            if (i + 1 < segments.length && "domain".equals(segments[i]) && "entity".equals(segments[i + 1])) {
                return "domain.entity";
            }
            if (i + 1 < segments.length && "web".equals(segments[i]) && "dto".equals(segments[i + 1])) {
                return "web.dto";
            }
        }
        return null;
    }

    private static ArchCondition<JavaClass> notDependOnRestrictedInternalsOfOtherTopLevelModules() {
        return new ArchCondition<>("not depend on restricted internals of other top-level modules") {
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
                    String kind = restrictedKindOf(target);
                    if (kind == null) {
                        continue;
                    }
                    // 描述中不含行号，保证基线条目在代码位移后仍稳定匹配。
                    String message = String.format(Locale.ROOT,
                            "restricted %s dependency: %s (module %s) -> %s (module %s)",
                            kind, clazz.getName(), originModule, target.getName(), targetModule);
                    events.add(SimpleConditionEvent.violated(clazz, message));
                }
            }
        };
    }

    /**
     * 单据状态写入白名单：迁移守卫之外的 setStatus 旁路会绕过 beforeStatusUpdate 业务校验，
     * 只允许聚合自己的 ApplyService、跨聚合完成同步服务和框架守卫写入实体状态。
     */
    private static ArchCondition<JavaClass> onlyWriteEntityStatusFromSanctionedClasses() {
        return new ArchCondition<>("only write entity status from sanctioned status-writing classes") {
            @Override
            public void check(JavaClass clazz, ConditionEvents events) {
                if (topLevelModuleOf(clazz) == null || isSanctionedStatusWriter(clazz)) {
                    return;
                }
                for (JavaMethodCall call : clazz.getMethodCallsFromSelf()) {
                    JavaClass target = call.getTargetOwner();
                    if (!"setStatus".equals(call.getName())
                            || !"domain.entity".equals(restrictedKindOf(target))) {
                        continue;
                    }
                    String message = String.format(Locale.ROOT,
                            "unsanctioned entity status write: %s -> %s.setStatus",
                            clazz.getName(), target.getName());
                    events.add(SimpleConditionEvent.violated(clazz, message));
                }
            }
        };
    }

    private static boolean isSanctionedStatusWriter(JavaClass clazz) {
        String simpleName = clazz.getSimpleName();
        return simpleName.endsWith("ApplyService")
                || simpleName.endsWith("CompletionSyncService")
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
