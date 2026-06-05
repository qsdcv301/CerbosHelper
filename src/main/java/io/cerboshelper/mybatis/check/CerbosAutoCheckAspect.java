package io.cerboshelper.mybatis.check;

import io.cerboshelper.mybatis.convention.CerbosCheckConventionResolver;
import io.cerboshelper.mybatis.support.CerbosMethodExpressionEvaluator;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;

import java.lang.reflect.Method;

@Aspect
public class CerbosAutoCheckAspect {
    static final String AUTO_CHECK_POINTCUT = "execution(* *(..))"
            + " && @within(org.springframework.stereotype.Service)"
            + " && !within(io.cerboshelper.mybatis..*)"
            + " && !within(org.springframework..*)"
            + " && !@within(org.springframework.context.annotation.Configuration)"
            + " && !@annotation(org.springframework.context.annotation.Bean)";

    private final CerbosCheckConventionResolver conventionResolver;
    private final CerbosCheckAspect checkAspect;

    public CerbosAutoCheckAspect(CerbosCheckConventionResolver conventionResolver, CerbosCheckAspect checkAspect) {
        this.conventionResolver = conventionResolver;
        this.checkAspect = checkAspect;
    }

    @Around(AUTO_CHECK_POINTCUT)
    public Object check(ProceedingJoinPoint joinPoint) throws Throwable {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        CerbosMethodExpressionEvaluator.Context context = checkAspect.context(method, joinPoint.getArgs());
        CerbosCheckSpec check = conventionResolver.resolve(method, context).orElse(null);
        if (check != null) {
            checkAspect.authorize(method, joinPoint.getArgs(), context, check);
        }
        return proceed(joinPoint);
    }

    private Object proceed(ProceedingJoinPoint joinPoint) {
        try {
            return joinPoint.proceed();
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Throwable throwable) {
            throw new IllegalStateException("Cerbos auto check method failed", throwable);
        }
    }
}
