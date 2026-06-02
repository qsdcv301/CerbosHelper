package io.cerboshelper.mybatis;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.context.expression.BeanFactoryResolver;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.lang.reflect.Method;

@Aspect
public class CerbosCheckAspect {
    private final CerbosAuthorizationClient authorizationClient;
    private final BeanFactory beanFactory;
    private final ExpressionParser expressionParser = new SpelExpressionParser();
    private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

    public CerbosCheckAspect(CerbosAuthorizationClient authorizationClient, BeanFactory beanFactory) {
        this.authorizationClient = authorizationClient;
        this.beanFactory = beanFactory;
    }

    @Around("@annotation(io.cerboshelper.mybatis.CerbosCheck) || @annotation(io.cerboshelper.mybatis.CerbosChecks)")
    public Object check(ProceedingJoinPoint joinPoint) throws Throwable {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        CerbosCheck[] checks = method.getAnnotationsByType(CerbosCheck.class);
        StandardEvaluationContext context = evaluationContext(method, joinPoint.getArgs());
        for (CerbosCheck check : checks) {
            Object principal = expressionParser.parseExpression(check.principal()).getValue(context);
            Object resource = expressionParser.parseExpression(check.resource()).getValue(context);
            if (!authorizationClient.isAllowed(principal, resource, check.action())) {
                throw new SecurityException("Cerbos denied " + check.action());
            }
        }
        return joinPoint.proceed();
    }

    private StandardEvaluationContext evaluationContext(Method method, Object[] args) {
        StandardEvaluationContext context = new StandardEvaluationContext();
        context.setBeanResolver(new BeanFactoryResolver(beanFactory));
        String[] parameterNames = parameterNameDiscoverer.getParameterNames(method);
        if (parameterNames != null) {
            for (int index = 0; index < parameterNames.length; index++) {
                context.setVariable(parameterNames[index], args[index]);
            }
        }
        for (int index = 0; index < args.length; index++) {
            context.setVariable("p" + index, args[index]);
            context.setVariable("a" + index, args[index]);
        }
        return context;
    }
}
