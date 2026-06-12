package io.cerboshelper.mybatis.rule;

import io.cerboshelper.mybatis.config.CerbosMethodRuleOptions;
import io.cerboshelper.mybatis.convention.CerbosCommonResourceRegistry;
import io.cerboshelper.mybatis.model.CerbosCommonDto;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.List;
import java.util.Optional;

public final class CerbosCheckRuleResolver {
    private final CerbosCommonResourceRegistry registry;
    private final CerbosMethodRuleOptions methodRules;

    public CerbosCheckRuleResolver(CerbosCommonResourceRegistry registry, CerbosMethodRuleOptions methodRules) {
        this.registry = registry;
        this.methodRules = methodRules == null ? new CerbosMethodRuleOptions() : methodRules;
    }

    public Optional<CerbosCommandCheck> resolveCommand(Method method, Object parameter) {
        String methodName = method.getName();
        if (methodRules.isExcluded(methodName)) {
            return Optional.empty();
        }
        for (CerbosMethodRuleOptions.CheckRule rule : methodRules.checkRules()) {
            if (!matches(methodName, rule.methodNamePrefixes())) {
                continue;
            }
            Optional<Object> resource = commandResource(parameter);
            if (resource.isPresent()) {
                return Optional.of(new CerbosCommandCheck(rule.action(), resource.get()));
            }
            if (registeredResourceMapper(method)) {
                throw new IllegalArgumentException("Cannot resolve Cerbos command resource for mapper method "
                        + method.getDeclaringClass().getName() + "." + method.getName()
                        + "(). Pass a registered CerbosCommonDto parameter containing ownerBy/ownerGroupBy.");
            }
            return Optional.empty();
        }
        return Optional.empty();
    }

    private Optional<Object> commandResource(Object parameter) {
        Optional<Object> resource = registeredResource(parameter);
        if (resource.isPresent()) {
            return resource;
        }
        if (parameter instanceof Map<?, ?> map) {
            for (Object value : map.values()) {
                resource = registeredResource(value);
                if (resource.isPresent()) {
                    return resource;
                }
            }
        }
        return Optional.empty();
    }

    private Optional<Object> registeredResource(Object value) {
        if (value instanceof CerbosCommonDto && registry.resourceKindForType(value.getClass()).isPresent()) {
            return Optional.of(value);
        }
        return Optional.empty();
    }

    private boolean registeredResourceMapper(Method method) {
        return registry.resourceKindFromToken(stripMapperSuffix(method.getDeclaringClass().getSimpleName())).isPresent();
    }

    private String stripMapperSuffix(String value) {
        return value != null && value.endsWith("Mapper") && value.length() > "Mapper".length()
                ? value.substring(0, value.length() - "Mapper".length())
                : value;
    }

    private boolean matches(String methodName, List<String> prefixes) {
        for (String prefix : prefixes) {
            if (methodName.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    public record CerbosCommandCheck(String action, Object resource) {
    }
}
