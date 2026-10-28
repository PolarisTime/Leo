package com.leo.erp.system.operationlog.support;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;

/**
 * 在异常被 {@code GlobalExceptionHandler} 解析为 ProblemDetail 之前, 把结构化错误写入 request attribute。
 *
 * <p>实现方式: 以最高优先级排在 Spring MVC 的异常解析链最前面, 记录错误后<b>返回 null</b>,
 * 表示"未解析", 由既有的 {@code ExceptionHandlerExceptionResolver} 继续生成对外错误响应,
 * 因此不改变任何现有错误契约; 之后 {@code OperationLogCommandRecorder} 从 attribute 读取
 * {@code errorCode}/{@code errorMessage}。</p>
 *
 * <p>只做采集, 任何异常都被吞掉, 不能影响主请求的错误处理。</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class OperationLogErrorCaptureResolver implements HandlerExceptionResolver {

    @Override
    public ModelAndView resolveException(HttpServletRequest request,
                                         HttpServletResponse response,
                                         Object handler,
                                         Exception ex) {
        try {
            OperationLogErrorAttributes.capture(request, ex);
        } catch (RuntimeException captureFailure) {
            // 采集失败不能影响主请求的错误响应; 返回 null 让后续 resolver 正常处理
        }
        return null;
    }
}
