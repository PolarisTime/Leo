package com.leo.erp.common.architecture;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 显式登记「允许直接写单据实体状态」的类。
 *
 * <p>状态写入必须经过 {@code beforeStatusUpdate} 业务校验；绕过守卫直接 {@code setStatus}
 * 会让「审核中被改回草稿」「已完成单据被重开」这类非法流转成立。构建期门禁
 * （{@code scripts/ModuleBoundaryVerifier.java}）据此判定：只有标注本注解的类（以及
 * {@code CrudStatusGuard} 本身）可以调用跨模块受限实体的 {@code setStatus}。</p>
 *
 * <p><b>为什么用注解而不是类名后缀</b>：早期白名单按 {@code *ApplyService} /
 * {@code *CompletionSyncService} / {@code *ForceCloseService} 后缀放行，等于「改个类名就获得
 * 写状态权限」——新增一个同名后缀类会被静默放行，评审时也看不出谁被豁免。注解要求在代码里
 * 显式声明并写明理由，评审 diff 一定能看到。</p>
 *
 * <p>新增成员的判据：该类的状态写入本身就是受控业务入口（自带前置状态校验与下游守卫），
 * 而不是某个服务里顺手改一下状态。理由要写清「为什么不能走 ApplyService / 状态机」。</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface SanctionedStatusWriter {

    /** 允许直接写实体状态的理由；必须具体，不得为空。 */
    String value();
}
