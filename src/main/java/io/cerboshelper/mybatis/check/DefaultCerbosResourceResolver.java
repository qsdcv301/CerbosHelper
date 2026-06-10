package io.cerboshelper.mybatis.check;

import io.cerboshelper.mybatis.convention.CerbosCommonResourceRegistry;
import io.cerboshelper.mybatis.model.CerbosCommonDto;
import io.cerboshelper.mybatis.support.CerbosMethodExpressionEvaluator;
import org.springframework.beans.factory.BeanFactory;

import java.util.ArrayList;
import java.util.List;

public class DefaultCerbosResourceResolver implements CerbosResourceResolver {
    private final CerbosMethodExpressionEvaluator expressionEvaluator;

    public DefaultCerbosResourceResolver(BeanFactory beanFactory, CerbosMethodExpressionEvaluator expressionEvaluator) {
        this(beanFactory, expressionEvaluator, new CerbosCommonResourceRegistry(List.of()));
    }

    public DefaultCerbosResourceResolver(BeanFactory beanFactory, CerbosMethodExpressionEvaluator expressionEvaluator, CerbosCommonResourceRegistry registry) {
        this.expressionEvaluator = expressionEvaluator;
    }

    @Override
    public List<Object> resolve(CerbosResourceResolutionRequest request) {
        CerbosCheckSpec check = request.check();
        CerbosMethodExpressionEvaluator.Context context = request.context();
        List<Object> resources = new ArrayList<>();
        if (!check.resource().isBlank()) {
            Object resource = tokenOrExpression(check.resource(), context);
            addResource(resources, resource);
            return resources;
        }
        for (Object arg : request.args()) {
            if (arg instanceof CerbosCommonDto) {
                addResource(resources, arg);
            }
        }
        Object resource = context.variable("resource");
        if (resource != null) {
            addResource(resources, resource);
        }
        return resources;
    }

    private void addResource(List<Object> resources, Object resource) {
        if (resource == null || resources.stream().anyMatch(existing -> existing == resource)) {
            return;
        }
        resources.add(resource);
    }

    private Object tokenOrExpression(String value, CerbosMethodExpressionEvaluator.Context context) {
        if (value.startsWith("#") || value.startsWith("@")) {
            return expressionEvaluator.value(value, context);
        }
        Object variable = context.variable(value);
        return variable != null ? variable : value;
    }
}
