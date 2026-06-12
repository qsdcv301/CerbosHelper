package io.cerboshelper.mybatis.rule;

import io.cerboshelper.mybatis.config.CerbosMethodRuleOptions;
import io.cerboshelper.mybatis.convention.CerbosCommonResourceRegistry;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Optional;

public final class CerbosScopeRuleResolver {
    private final CerbosCommonResourceRegistry registry;
    private final CerbosMethodRuleOptions methodRules;

    public CerbosScopeRuleResolver(CerbosCommonResourceRegistry registry, CerbosMethodRuleOptions methodRules) {
        this.registry = registry;
        this.methodRules = methodRules == null ? new CerbosMethodRuleOptions() : methodRules;
    }

    public Optional<CerbosScopeRule> resolve(Method method) {
        String methodName = method.getName();
        if (methodRules.isExcluded(methodName)) {
            return Optional.empty();
        }
        for (CerbosMethodRuleOptions.ScopeRule rule : methodRules.scopeRules()) {
            Optional<String> matchedPrefix = matchedPrefix(methodName, rule.methodNamePrefixes());
            if (matchedPrefix.isEmpty()) {
                continue;
            }
            return resourceKindFromReturnType(method)
                    .or(() -> resourceKindFromIdListMethod(methodName, matchedPrefix.get()))
                    .map(resourceKind -> new CerbosScopeRule(resourceKind, rule.action()));
        }
        return Optional.empty();
    }

    private Optional<String> resourceKindFromReturnType(Method method) {
        Type returnType = method.getGenericReturnType();
        if (returnType instanceof ParameterizedType parameterizedType) {
            for (Type argument : parameterizedType.getActualTypeArguments()) {
                Optional<String> resourceKind = resourceKindFromType(argument);
                if (resourceKind.isPresent()) {
                    return resourceKind;
                }
            }
        }
        return resourceKindFromType(returnType);
    }

    private Optional<String> resourceKindFromType(Type type) {
        if (type instanceof Class<?> candidate) {
            return registry.resourceKindForType(candidate);
        }
        return Optional.empty();
    }

    public Optional<String> resourceKindForType(Class<?> type) {
        return registry.resourceKindForType(type);
    }

    private Optional<String> resourceKindFromIdListMethod(String methodName, String matchedPrefix) {
        if (!methodName.endsWith("Ids")) {
            return Optional.empty();
        }
        String token = methodName.substring(matchedPrefix.length(), methodName.length() - "Ids".length());
        return registry.resourceKindFromToken(singularize(token));
    }

    private String singularize(String token) {
        return token.endsWith("s") && token.length() > 1 ? token.substring(0, token.length() - 1) : token;
    }

    private Optional<String> matchedPrefix(String methodName, List<String> prefixes) {
        for (String prefix : prefixes) {
            if (methodName.startsWith(prefix)) {
                return Optional.of(prefix);
            }
        }
        return Optional.empty();
    }

    public record CerbosScopeRule(String resourceKind, String action) {
    }
}
