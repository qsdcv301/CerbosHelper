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
        Optional<ResourceArgument> resourceArgument = resourceArgument(method, context);
        if (resourceArgument.isPresent()) {
            String idVariable = idVariableFor(resourceArgument.get().resourceKind(), context).orElse("");
            return Optional.of(new CerbosCheckSpec(action, "", resourceArgument.get().variableName(), "", idVariable, "", "findById"));
        }
        Optional<IdReference> idReference = idReference(method, context);
        return idReference.map(reference -> new CerbosCheckSpec(action, "", "", reference.resourceKind(), reference.variableName(), "", "findById"));
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

    private Optional<IdReference> idReference(Method method, CerbosMethodExpressionEvaluator.Context context) {
        for (String variableName : context.variableNames()) {
            if (variableName.startsWith("p") || variableName.startsWith("a")) {
                continue;
            }
            Object value = context.variable(variableName);
            if (value == null || !variableName.endsWith("Id")) {
                continue;
            }
            String token = variableName.substring(0, variableName.length() - "Id".length());
            Optional<String> resourceKind = registry.resourceKindFromToken(token);
            if (resourceKind.isPresent()) {
                return Optional.of(new IdReference(resourceKind.get(), variableName));
            }
        }
        return resourceKindFromMethodName(method.getName())
                .flatMap(resourceKind -> idVariableFor(resourceKind, context).map(idVariable -> new IdReference(resourceKind, idVariable)));
    }

    private Optional<String> resourceKindFromMethodName(String methodName) {
        String normalized = methodName.replaceFirst("^(findVisible|find|select|get|create|insert|save|update|modify|delete|remove)", "");
        return registry.resourceKindFromToken(normalized);
    }

    private Optional<String> idVariableFor(String resourceKind, CerbosMethodExpressionEvaluator.Context context) {
        String expected = resourceKind + "Id";
        if (context.variable(expected) != null) {
            return Optional.of(expected);
        }
        if (context.variable("id") != null) {
            return Optional.of("id");
        }
        return context.variableNames().stream()
                .filter(name -> !name.startsWith("p") && !name.startsWith("a"))
                .filter(name -> name.endsWith("Id"))
                .filter(name -> context.variable(name) != null)
                .findFirst();
    }

    private boolean isNamedParameter(String variableName, Method method) {
        return !variableName.startsWith("p") && !variableName.startsWith("a")
                && List.of(method.getParameters()).stream().anyMatch(parameter -> parameter.getName().equals(variableName));
    }

    private Optional<String> actionFor(String methodName) {
        if (startsWithAny(methodName, "find", "get", "select")) {
            return Optional.of("view");
        }
        if (startsWithAny(methodName, "create", "insert", "save")) {
            return Optional.of("create");
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

    private record IdReference(String resourceKind, String variableName) {
    }
}
