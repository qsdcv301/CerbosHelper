package io.cerboshelper.mybatis.convention;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class CerbosScopeConventionResolver {
    private final CerbosCommonResourceRegistry registry;

    public CerbosScopeConventionResolver(CerbosCommonResourceRegistry registry) {
        this.registry = registry;
    }

    public Optional<CerbosScopeConvention> resolve(Method method) {
        String methodName = method.getName();
        if (isExcluded(methodName)) {
            return Optional.empty();
        }
        if (!isReadMethod(methodName)) {
            return Optional.empty();
        }
        return resourceKindFromReturnType(method)
                .or(() -> resourceKindFromIdListMethod(methodName))
                .map(resourceKind -> new CerbosScopeConvention(resourceKind, "view"));
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

    private Optional<String> resourceKindFromIdListMethod(String methodName) {
        String lowerName = methodName.toLowerCase(Locale.ROOT);
        if (!lowerName.endsWith("ids")) {
            return Optional.empty();
        }
        String token = methodName
                .replaceFirst("^(find|select|list|search)", "")
                .replaceFirst("Ids$", "");
        return registry.resourceKindFromToken(singularize(token));
    }

    private String singularize(String token) {
        return token.endsWith("s") && token.length() > 1 ? token.substring(0, token.length() - 1) : token;
    }

    private boolean isExcluded(String methodName) {
        return methodName.startsWith("findAll")
                || methodName.startsWith("selectAll")
                || methodName.startsWith("listAll")
                || methodName.startsWith("debug")
                || methodName.startsWith("trace")
                || methodName.startsWith("admin")
                || methodName.endsWith("ById")
                || methodName.equals("findById")
                || methodName.equals("selectById")
                || methodName.equals("getById");
    }

    private boolean isReadMethod(String methodName) {
        return List.of("find", "select", "list", "search").stream().anyMatch(methodName::startsWith);
    }

    public record CerbosScopeConvention(String resourceKind, String action) {
    }
}
