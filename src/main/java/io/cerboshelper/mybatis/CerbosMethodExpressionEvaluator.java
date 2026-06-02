package io.cerboshelper.mybatis;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.context.expression.BeanFactoryResolver;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.lang.reflect.Method;

final class CerbosMethodExpressionEvaluator {
    private final BeanFactory beanFactory;
    private final ExpressionParser expressionParser = new SpelExpressionParser();
    private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

    CerbosMethodExpressionEvaluator(BeanFactory beanFactory) {
        this.beanFactory = beanFactory;
    }

    Context context(Method method, Object[] args) {
        StandardEvaluationContext evaluationContext = new StandardEvaluationContext();
        evaluationContext.setBeanResolver(new BeanFactoryResolver(beanFactory));
        String[] parameterNames = parameterNameDiscoverer.getParameterNames(method);
        if (parameterNames != null) {
            for (int index = 0; index < parameterNames.length; index++) {
                evaluationContext.setVariable(parameterNames[index], args[index]);
            }
        }
        for (int index = 0; index < args.length; index++) {
            evaluationContext.setVariable("p" + index, args[index]);
            evaluationContext.setVariable("a" + index, args[index]);
        }
        return new Context(evaluationContext);
    }

    Object value(String expression, Context context) {
        return expressionParser.parseExpression(expression).getValue(context.evaluationContext);
    }

    Object principal(String expression, Context context) {
        if (!expression.isBlank()) {
            return value(expression, context);
        }
        Object principal = context.variable("principal");
        if (principal != null) {
            return principal;
        }
        throw new IllegalArgumentException("Cannot resolve Cerbos principal. Provide a parameter named principal or set principal expression explicitly.");
    }

    static final class Context {
        private final StandardEvaluationContext evaluationContext;

        private Context(StandardEvaluationContext evaluationContext) {
            this.evaluationContext = evaluationContext;
        }

        void setVariable(String name, Object value) {
            evaluationContext.setVariable(name, value);
        }

        Object variable(String name) {
            return evaluationContext.lookupVariable(name);
        }
    }
}
