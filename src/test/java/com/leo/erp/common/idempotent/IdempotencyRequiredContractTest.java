package com.leo.erp.common.idempotent;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 契约测试：凡含写映射（{@code @PostMapping}/{@code @PutMapping}/{@code @PatchMapping}/
 * {@code @DeleteMapping}，或 {@code @RequestMapping(method=POST|PUT|PATCH|DELETE)}）的
 * {@code @RestController}，必须在**类级或方法级**标注 {@link IdempotencyRequired}，
 * 或落入显式 allowlist（必须写明为何此类写操作天然不需要幂等键）。
 *
 * <p>回归背景（质量报告 P1-4）：全仓绝大多数写控制器已类级强制幂等键，并有
 * 「本控制器已标注」的点状断言（如 {@code V2InventoryControllerTest}）；但断言是逐控制器的，
 * 新写一个控制器忘了标注不会有任何测试失败——报价单、销售合同、采购订单明细、
 * 报价项目配置、模块导出这 5 个控制器就是这样漏掉的。本测试把「漏配」变成全仓扫描级的单测失败。</p>
 *
 * <p>allowlist 只接受「写操作本身就是幂等语义」的租约/锁类资源；因前端暂未补
 * {@code X-Idempotency-Key} 而豁免属于契约破坏，不能作为放行理由。</p>
 */
class IdempotencyRequiredContractTest {

    /** 写映射的判定口径：这 4 个注解全部属于「可能产生副作用」的写入口。 */
    private static final Set<RequestMethod> WRITE_METHODS =
            EnumSet.of(RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.DELETE);

    /**
     * 显式 allowlist：类名 → 豁免理由。
     *
     * <p>当前仅 1 项。加入前必须确认「该写操作重复执行与执行一次结果相同」且不涉及
     * 单据/余额/库存等可累积副作用。</p>
     */
    private static final Map<String, String> ALLOWLIST = Map.of(
            "V2QuoteSheetEditLockController",
            "编辑锁是租约（lease）资源：签出/续约在同一持锁人上是幂等续期（本人已持有则续约，"
                    + "他人未过期 409、已过期可抢占），释放（DELETE）同样幂等。"
                    + "前端在持锁期间按周期轮询续约，若强制 Idempotency-Key 会改变续约语义"
                    + "并可能让编辑锁提前过期；该资源的重复提交不会产生可累积副作用。"
    );

    /**
     * 方法级 allowlist：{@code 类名#方法名} → 豁免理由。
     *
     * <p>只用于「该方法重复执行与执行一次结果相同」或「重复提交本身无害」的端点；
     * 类级 allowlist 管不到的零散端点放这里。</p>
     */
    private static final Map<String, String> METHOD_ALLOWLIST = Map.of(
            "V2AuthController#refresh",
            "刷新令牌是「用 refresh token 换一组新令牌」的流转，不是幂等创建："
                    + "同一幂等键重放会命中首次响应缓存，客户端拿到的仍是被消费过的旧令牌；"
                    + "且刷新在 401 自动重试链上由多个并发请求触发，强制幂等键会互相顶掉。"
                    + "登录与登出已标注 @IdempotencyRequired。"
    );

    @Test
    void everyRestEndpointWithWriteMappingMustRequireIdempotencyKey() throws Exception {
        List<Class<?>> controllers = scanControllers();
        List<String> violations = new ArrayList<>();
        List<String> annotated = new ArrayList<>();
        int controllersWithWrites = 0;

        for (Class<?> controller : controllers) {
            List<Method> writeMethods = writeMethodsOf(controller);
            if (writeMethods.isEmpty()) {
                continue;
            }
            controllersWithWrites++;
            if (controller.isAnnotationPresent(IdempotencyRequired.class)) {
                annotated.add(controller.getSimpleName());
                continue;
            }
            String simpleName = controller.getSimpleName();
            if (ALLOWLIST.containsKey(simpleName)) {
                continue;
            }
            for (Method method : writeMethods) {
                if (method.isAnnotationPresent(IdempotencyRequired.class)) {
                    continue;
                }
                if (METHOD_ALLOWLIST.containsKey(simpleName + "#" + method.getName())) {
                    continue;
                }
                violations.add(simpleName + "#" + method.getName()
                        + " 含写映射但类与方法都没有 @IdempotencyRequired");
            }
        }

        assertThat(controllersWithWrites)
                .as("应扫描到含写映射的 @RestController，否则本契约测试没有覆盖任何写端点")
                .isGreaterThan(20);
        assertThat(annotated)
                .as("类级 @IdempotencyRequired 的控制器数量异常，检查扫描口径或注解被误删")
                .isNotEmpty();
        assertThat(violations).isEmpty();
    }

    @Test
    void allowlistEntriesMustStillExistAndStayMinimal() throws Exception {
        Set<String> controllerNames = new LinkedHashSet<>();
        Set<String> writeEndpoints = new LinkedHashSet<>();
        for (Class<?> controller : scanControllers()) {
            controllerNames.add(controller.getSimpleName());
            for (Method method : writeMethodsOf(controller)) {
                writeEndpoints.add(controller.getSimpleName() + "#" + method.getName());
            }
        }

        Map<String, String> stale = new TreeMap<>();
        ALLOWLIST.forEach((name, reason) -> {
            if (!controllerNames.contains(name)) {
                stale.put(name, reason);
            }
        });
        METHOD_ALLOWLIST.forEach((endpoint, reason) -> {
            if (!writeEndpoints.contains(endpoint)) {
                stale.put(endpoint, reason);
            }
        });

        assertThat(stale)
                .as("allowlist 里的控制器/端点已不存在或改名，请删除对应豁免")
                .isEmpty();
        assertThat(ALLOWLIST)
                .as("类级 allowlist 只应保留「写操作天然幂等」的租约类资源，不应因前端未补请求头而扩张")
                .hasSize(1);
        assertThat(METHOD_ALLOWLIST)
                .as("方法级 allowlist 只应保留「重复执行无副作用」的零散端点")
                .hasSize(1);
    }

    /** 收集类上所有「写映射」方法（含 {@code @RequestMapping(method=POST|PUT|PATCH|DELETE)} 写法）。 */
    private static List<Method> writeMethodsOf(Class<?> controller) {
        List<Method> writeMethods = new ArrayList<>();
        for (Method method : controller.getDeclaredMethods()) {
            if (method.isSynthetic() || method.isBridge()) {
                continue;
            }
            if (isWriteMapping(method)) {
                writeMethods.add(method);
            }
        }
        return writeMethods;
    }

    private static boolean isWriteMapping(Method method) {
        if (method.isAnnotationPresent(PostMapping.class)
                || method.isAnnotationPresent(PutMapping.class)
                || method.isAnnotationPresent(PatchMapping.class)
                || method.isAnnotationPresent(DeleteMapping.class)) {
            return true;
        }
        RequestMapping mapping = method.getAnnotation(RequestMapping.class);
        if (mapping == null) {
            return false;
        }
        return Arrays.stream(mapping.method()).anyMatch(WRITE_METHODS::contains);
    }

    private List<Class<?>> scanControllers() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));

        List<Class<?>> controllers = new ArrayList<>();
        for (BeanDefinition candidate : scanner.findCandidateComponents("com.leo.erp")) {
            Class<?> type = Class.forName(candidate.getBeanClassName());
            if (Modifier.isAbstract(type.getModifiers())) {
                continue;
            }
            // 类名里的 `$` 是嵌套类：本仓的 @RestController 都是顶层类，
            // 嵌套的推进器（如测试里探针控制器）不属于对外契约面。测试类
            // 也在同一 classpath 上，不加这条会把它们当成生产控制器要求幂等键。
            if (type.getName().indexOf('$') >= 0) {
                continue;
            }
            controllers.add(type);
        }
        assertThat(controllers)
                .as("应能扫描到 @RestController，否则本契约测试没有覆盖任何控制器")
                .isNotEmpty();
        return controllers;
    }
}
