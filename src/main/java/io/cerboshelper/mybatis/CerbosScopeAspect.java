package io.cerboshelper.mybatis;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.BeanFactory;

import java.lang.reflect.Method;

@Aspect
public class CerbosScopeAspect {
    private final CerbosMethodExpressionEvaluator expressionEvaluator;

    public CerbosScopeAspect(BeanFactory beanFactory) {
        this.expressionEvaluator = new CerbosMethodExpressionEvaluator(beanFactory);
    }

    @Around("@annotation(io.cerboshelper.mybatis.CerbosScope)")
    public Object scope(ProceedingJoinPoint joinPoint) {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        CerbosScope cerbosScope = method.getAnnotation(CerbosScope.class);
        CerbosMethodExpressionEvaluator.Context context = expressionEvaluator.context(method, joinPoint.getArgs());
        Object principal = expressionEvaluator.principal(cerbosScope.principal(), context);
        return CerbosScopeContext.with(principal, cerbosScope.action(), () -> proceed(joinPoint));
    }

    private Object proceed(ProceedingJoinPoint joinPoint) {
        try {
            return joinPoint.proceed();
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Throwable throwable) {
            throw new IllegalStateException("Cerbos scoped method failed", throwable);
        }
    }
}
