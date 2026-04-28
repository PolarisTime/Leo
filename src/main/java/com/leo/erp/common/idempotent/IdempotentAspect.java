package com.leo.erp.common.idempotent;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Aspect
@Component
public class IdempotentAspect {

    private static final Logger log = LoggerFactory.getLogger(IdempotentAspect.class);
    private static final ExpressionParser SPEL_PARSER = new SpelExpressionParser();

    private final IdempotentKeyService idempotentKeyService;

    public IdempotentAspect(IdempotentKeyService idempotentKeyService) {
        this.idempotentKeyService = idempotentKeyService;
    }

    @Around("@annotation(idempotent)")
    public Object enforceIdempotency(ProceedingJoinPoint joinPoint, Idempotent idempotent) throws Throwable {
        String rawKey = resolveKey(joinPoint, idempotent.key());
        if (rawKey == null || rawKey.isBlank()) {
            return joinPoint.proceed();
        }

        String idempotentKey = joinPoint.getSignature().toShortString() + ":" + rawKey;
        Duration ttl = Duration.ofSeconds(idempotent.ttlSeconds());

        if (!idempotentKeyService.tryAcquire(idempotentKey, ttl)) {
            idempotentKeyService.throwIfDuplicate(idempotentKey);
        }

        try {
            Object result = joinPoint.proceed();
            idempotentKeyService.markCompleted(idempotentKey, "completed", ttl);
            return result;
        } catch (Throwable ex) {
            idempotentKeyService.release(idempotentKey);
            throw ex;
        }
    }

    private String resolveKey(ProceedingJoinPoint joinPoint, String keyExpression) {
        StandardEvaluationContext context = new StandardEvaluationContext();
        Object[] args = joinPoint.getArgs();
        String[] paramNames = ((org.aspectj.lang.reflect.MethodSignature) joinPoint.getSignature()).getParameterNames();
        if (paramNames != null) {
            for (int i = 0; i < paramNames.length; i++) {
                context.setVariable(paramNames[i], args[i]);
            }
        }
        return SPEL_PARSER.parseExpression(keyExpression).getValue(context, String.class);
    }
}
