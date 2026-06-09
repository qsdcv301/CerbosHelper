package io.cerboshelper.mybatis.check;

import io.cerboshelper.mybatis.config.CerbosAutoCheckOptions;
import io.cerboshelper.mybatis.convention.CerbosCheckConventionResolver;
import io.cerboshelper.mybatis.support.CerbosMethodExpressionEvaluator;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@Aspect
public class CerbosAutoCheckAspect {
    static final String AUTO_CHECK_POINTCUT = "execution(* *(..))"
            + " && @within(org.springframework.stereotype.Service)"
            + " && !within(io.cerboshelper.mybatis..*)"
            + " && !within(org.springframework..*)"
            + " && !@within(org.springframework.context.annotation.Configuration)"
            + " && !@annotation(org.springframework.context.annotation.Bean)";

    private final CerbosCheckConventionResolver conventionResolver;
    private final CerbosResourceCheckExecutor checkExecutor;
    private final CerbosAutoCheckOptions autoCheck;
    private final Map<String, Pattern> patternCache = new ConcurrentHashMap<>();

    public CerbosAutoCheckAspect(CerbosCheckConventionResolver conventionResolver, CerbosResourceCheckExecutor checkExecutor) {
        this(conventionResolver, checkExecutor, new CerbosAutoCheckOptions());
    }

    public CerbosAutoCheckAspect(CerbosCheckConventionResolver conventionResolver, CerbosResourceCheckExecutor checkExecutor, CerbosAutoCheckOptions autoCheck) {
        this.conventionResolver = conventionResolver;
        this.checkExecutor = checkExecutor;
        this.autoCheck = autoCheck == null ? new CerbosAutoCheckOptions() : autoCheck;
    }

    @Around(AUTO_CHECK_POINTCUT)
    public Object check(ProceedingJoinPoint joinPoint) throws Throwable {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        Class<?> targetType = joinPoint.getTarget() != null ? joinPoint.getTarget().getClass() : method.getDeclaringClass();
        if (!shouldAutoCheck(targetType, method)) {
            return proceed(joinPoint);
        }
        CerbosMethodExpressionEvaluator.Context context = checkExecutor.context(method, joinPoint.getArgs());
        CerbosCheckSpec check = conventionResolver.resolve(method, context).orElse(null);
        if (check == null) {
            return proceed(joinPoint);
        }
        if ("view".equals(check.action())) {
            Object result = proceed(joinPoint);
            checkExecutor.authorizeReturnedResource(method, context, check, result);
            return result;
        }
        checkExecutor.authorize(method, joinPoint.getArgs(), context, check);
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

    boolean shouldAutoCheck(Class<?> targetType, Method method) {
        if (!autoCheck.isEnabled()) {
            return false;
        }
        List<String> classNames = List.of(
                targetType.getName(),
                targetType.getSimpleName(),
                method.getDeclaringClass().getName(),
                method.getDeclaringClass().getSimpleName()
        );
        if (!autoCheck.getIncludeClassNamePatterns().isEmpty()
                && !matchesAny(autoCheck.getIncludeClassNamePatterns(), classNames)) {
            return false;
        }
        if (matchesAny(autoCheck.getExcludeClassNamePatterns(), classNames)) {
            return false;
        }
        List<String> methodNames = List.of(method.getName());
        if (!autoCheck.getIncludeMethodNamePatterns().isEmpty()
                && !matchesAny(autoCheck.getIncludeMethodNamePatterns(), methodNames)) {
            return false;
        }
        return !matchesAny(autoCheck.getExcludeMethodNamePatterns(), methodNames);
    }

    private boolean matchesAny(List<String> patterns, List<String> values) {
        for (String pattern : patterns) {
            if (pattern == null || pattern.isBlank()) {
                continue;
            }
            Pattern compiled = patternCache.computeIfAbsent(pattern, Pattern::compile);
            for (String value : values) {
                if (value != null && compiled.matcher(value).find()) {
                    return true;
                }
            }
        }
        return false;
    }
}
