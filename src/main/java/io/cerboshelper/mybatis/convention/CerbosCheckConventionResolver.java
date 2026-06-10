package io.cerboshelper.mybatis.convention;

import io.cerboshelper.mybatis.check.CerbosCheckSpec;
import io.cerboshelper.mybatis.model.CerbosCommonDto;
import io.cerboshelper.mybatis.support.CerbosMethodExpressionEvaluator;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class CerbosCheckConventionResolver {
    private final CerbosCommonResourceRegistry registry;

    public CerbosCheckConventionResolver(CerbosCommonResourceRegistry registry) {
        this.registry = registry;
    }

    public Optional<CerbosCheckSpec> resolve(Method method, CerbosMethodExpressionEvaluator.Context context) {
        String action = actionFor(method.getName()).orElse(null);
        if (action == null || isExcluded(method.getName())) {
            return Optional.empty();
        }
        if ("view".equals(action)) {
            return Optional.of(new CerbosCheckSpec(action, "", ""));
        }
        Optional<ResourceArgument> resourceArgument = resourceArgument(method, context);
        return resourceArgument.map(argument -> new CerbosCheckSpec(action, "", argument.variableName()));
    }

    private Optional<ResourceArgument> resourceArgument(Method method, CerbosMethodExpressionEvaluator.Context context) {
        for (String variableName : context.variableNames()) {
            Object value = context.variable(variableName);
            if (value instanceof CerbosCommonDto) {
                Optional<String> resourceKind = registry.resourceKindForType(value.getClass());
                if (resourceKind.isPresent() && isNamedParameter(variableName, method)) {
                    return Optional.of(new ResourceArgument(variableName, resourceKind.get()));
                }
            }
        }
        return Optional.empty();
    }

    private boolean isNamedParameter(String variableName, Method method) {
        return !variableName.startsWith("p") && !variableName.startsWith("a")
                && List.of(method.getParameters()).stream().anyMatch(parameter -> parameter.getName().equals(variableName));
    }

    private Optional<String> actionFor(String methodName) {
        if (startsWithAny(methodName, "find", "get", "select")) {
            return Optional.of("view");
        }
        if (startsWithAny(methodName, "update", "modify")) {
            return Optional.of("update");
        }
        if (startsWithAny(methodName, "delete", "remove")) {
            return Optional.of("delete");
        }
        return Optional.empty();
    }

    private boolean isExcluded(String methodName) {
        String lower = methodName.toLowerCase(Locale.ROOT);
        return startsWithAny(methodName, "findAll", "selectAll", "listAll", "debug", "trace", "admin")
                || lower.contains("debug")
                || lower.contains("trace");
    }

    private boolean startsWithAny(String value, String... prefixes) {
        for (String prefix : prefixes) {
            if (value.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private record ResourceArgument(String variableName, String resourceKind) {
    }

}
