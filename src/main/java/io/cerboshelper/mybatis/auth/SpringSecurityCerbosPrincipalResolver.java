package io.cerboshelper.mybatis.auth;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public class SpringSecurityCerbosPrincipalResolver implements CerbosPrincipalResolver {
    @Override
    public Optional<Object> currentPrincipal() {
        Object authentication = authentication();
        if (authentication == null || !authenticated(authentication)) {
            return Optional.empty();
        }
        String name = stringValue(invoke(authentication, "getName")).orElse("");
        if (name.isBlank() || "anonymousUser".equals(name)) {
            return Optional.empty();
        }
        List<String> roles = authorities(authentication);
        Map<String, Object> attr = new LinkedHashMap<>();
        attr.put("userId", name);
        attr.put("username", name);
        attr.put("principalName", name);
        attr.put("authorities", roles);
        Object principal = invoke(authentication, "getPrincipal");
        putAll(attr, attributes(principal));
        putAllPrefixed(attr, "authentication", attributes(authentication));
        putAllPrefixed(attr, "details", attributes(invoke(authentication, "getDetails")));
        return Optional.of(new CerbosPrincipalEnvelope(name, roles.isEmpty() ? List.of("authenticated") : roles, attr, "default"));
    }

    private Object authentication() {
        try {
            Class<?> holderType = Class.forName("org.springframework.security.core.context.SecurityContextHolder");
            Object context = holderType.getMethod("getContext").invoke(null);
            return context == null ? null : context.getClass().getMethod("getAuthentication").invoke(context);
        } catch (ReflectiveOperationException exception) {
            return null;
        }
    }

    private boolean authenticated(Object authentication) {
        Object value = invoke(authentication, "isAuthenticated");
        return value instanceof Boolean authenticated && authenticated;
    }

    private List<String> authorities(Object authentication) {
        Object authorities = invoke(authentication, "getAuthorities");
        if (!(authorities instanceof Iterable<?> iterable)) {
            return List.of();
        }
        List<String> roles = new ArrayList<>();
        for (Object authority : iterable) {
            stringValue(invoke(authority, "getAuthority")).filter(value -> !value.isBlank()).ifPresent(roles::add);
        }
        return List.copyOf(roles);
    }

    private Map<String, Object> attributes(Object source) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        if (source == null || source instanceof String || isSimpleValue(source)) {
            return attributes;
        }
        if (source instanceof CerbosPrincipalEnvelope envelope) {
            attributes.putAll(envelope.attr());
            return attributes;
        }
        if (source instanceof Map<?, ?> map) {
            map.forEach((key, value) -> {
                if (key != null && supportedValue(value)) {
                    attributes.put(String.valueOf(key), value);
                }
            });
            return attributes;
        }
        Class<?> type = source.getClass();
        if (type.isRecord()) {
            for (java.lang.reflect.RecordComponent component : type.getRecordComponents()) {
                Object value = invoke(source, component.getName());
                if (supportedValue(value)) {
                    attributes.put(component.getName(), value);
                }
            }
            return attributes;
        }
        for (Method method : type.getMethods()) {
            if (method.getDeclaringClass() == Object.class || method.getParameterCount() != 0) {
                continue;
            }
            String propertyName = methodNameToProperty(method.getName());
            if (propertyName == null || attributes.containsKey(propertyName)) {
                continue;
            }
            Object value = invoke(source, method.getName());
            if (supportedValue(value)) {
                attributes.put(propertyName, value);
            }
        }
        return attributes;
    }

    private void putAll(Map<String, Object> target, Map<String, Object> source) {
        source.forEach(target::put);
    }

    private void putAllPrefixed(Map<String, Object> target, String prefix, Map<String, Object> source) {
        source.forEach((key, value) -> target.put(prefix + key.substring(0, 1).toUpperCase(Locale.ROOT) + key.substring(1), value));
    }

    private String methodNameToProperty(String methodName) {
        if (methodName.startsWith("get") && methodName.length() > 3) {
            return decapitalize(methodName.substring(3));
        }
        if (methodName.startsWith("is") && methodName.length() > 2) {
            return decapitalize(methodName.substring(2));
        }
        return null;
    }

    private String decapitalize(String value) {
        return value.substring(0, 1).toLowerCase(Locale.ROOT) + value.substring(1);
    }

    private boolean supportedValue(Object value) {
        if (value == null || isSimpleValue(value)) {
            return true;
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream().allMatch(item -> item == null || isSimpleValue(item));
        }
        return value instanceof Map<?, ?> map && map.values().stream().allMatch(item -> item == null || isSimpleValue(item));
    }

    private boolean isSimpleValue(Object value) {
        return value instanceof String
                || value instanceof Number
                || value instanceof Boolean
                || value instanceof Character
                || value instanceof Enum<?>;
    }

    private Object invoke(Object target, String methodName) {
        if (target == null) {
            return null;
        }
        try {
            Method method = target.getClass().getMethod(methodName);
            if (method.getParameterCount() != 0) {
                return null;
            }
            return method.invoke(target);
        } catch (ReflectiveOperationException exception) {
            return null;
        }
    }

    private Optional<String> stringValue(Object value) {
        return value == null ? Optional.empty() : Optional.of(String.valueOf(value));
    }

}
