package io.cerboshelper.mybatis;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.BeanFactory;

import java.lang.reflect.Method;

@Aspect
public class CerbosCheckAspect {
    private final CerbosAuthorizationClient authorizationClient;
    private final CerbosMethodExpressionEvaluator expressionEvaluator;

    public CerbosCheckAspect(CerbosAuthorizationClient authorizationClient, BeanFactory beanFactory) {
        this.authorizationClient = authorizationClient;
        this.expressionEvaluator = new CerbosMethodExpressionEvaluator(beanFactory);
    }

    @Around("@annotation(io.cerboshelper.mybatis.CerbosCheck) || @annotation(io.cerboshelper.mybatis.CerbosChecks)")
    public Object check(ProceedingJoinPoint joinPoint) throws Throwable {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        CerbosCheck[] checks = method.getAnnotationsByType(CerbosCheck.class);
        CerbosMethodExpressionEvaluator.Context context = expressionEvaluator.context(method, joinPoint.getArgs());
        for (CerbosCheck check : checks) {
            Object principal = expressionEvaluator.value(check.principal(), context);
            Object resource = expressionEvaluator.value(check.resource(), context);
            if (!authorizationClient.isAllowed(principal, resource, check.action())) {
                throw new SecurityException("Cerbos denied " + check.action());
            }
        }
        return joinPoint.proceed();
    }
}
