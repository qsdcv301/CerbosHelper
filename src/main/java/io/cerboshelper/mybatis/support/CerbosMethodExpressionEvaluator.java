package io.cerboshelper.mybatis.support;

import io.cerboshelper.mybatis.auth.CerbosPrincipalResolver;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.context.expression.BeanFactoryResolver;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class CerbosMethodExpressionEvaluator {
    private final BeanFactory beanFactory;
    private final ExpressionParser expressionParser = new SpelExpressionParser();
    private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

    public CerbosMethodExpressionEvaluator(BeanFactory beanFactory) {
        this.beanFactory = beanFactory;
    }

    public Context context(Method method, Object[] args) {
        StandardEvaluationContext evaluationContext = new StandardEvaluationContext();
        evaluationContext.setBeanResolver(new BeanFactoryResolver(beanFactory));
        Map<String, Object> variables = new LinkedHashMap<>();
        String[] parameterNames = parameterNameDiscoverer.getParameterNames(method);
        if (parameterNames != null) {
            for (int index = 0; index < parameterNames.length; index++) {
                evaluationContext.setVariable(parameterNames[index], args[index]);
                variables.put(parameterNames[index], args[index]);
            }
        }
        for (int index = 0; index < args.length; index++) {
            evaluationContext.setVariable("p" + index, args[index]);
            evaluationContext.setVariable("a" + index, args[index]);
            variables.put("p" + index, args[index]);
            variables.put("a" + index, args[index]);
        }
        return new Context(evaluationContext, variables);
    }

    public Object value(String expression, Context context) {
        return expressionParser.parseExpression(expression).getValue(context.evaluationContext);
    }

    public Object principal(String expression, Context context, CerbosPrincipalResolver principalResolver) {
        if (!expression.isBlank()) {
            return value(expression, context);
        }
        Object principal = context.variable("principal");
        if (principal != null) {
            return principal;
        }
        Object resolvedPrincipal = principalResolver.currentPrincipal().orElse(null);
        if (resolvedPrincipal != null) {
            return resolvedPrincipal;
        }
        Object firstArgument = context.variable("p0");
        if (firstArgument != null) {
            return firstArgument;
        }
        throw new IllegalArgumentException("Cannot resolve Cerbos principal. Register a CerbosPrincipalResolver bean for the current application user, "
                + "or provide one of: @CerbosCheck(principal = \"...\"), @CerbosScope(principal = \"...\"), a method parameter named principal, or a first argument principal. "
                + "If parameter names are not visible, enable Java compiler option -parameters.");
    }

    public static final class Context {
        private final StandardEvaluationContext evaluationContext;
        private final Map<String, Object> variables;

        private Context(StandardEvaluationContext evaluationContext, Map<String, Object> variables) {
            this.evaluationContext = evaluationContext;
            this.variables = variables;
        }

        public void setVariable(String name, Object value) {
            evaluationContext.setVariable(name, value);
        }

        public Object variable(String name) {
            return evaluationContext.lookupVariable(name);
        }

        public Set<String> variableNames() {
            return variables.keySet();
        }
    }

}
