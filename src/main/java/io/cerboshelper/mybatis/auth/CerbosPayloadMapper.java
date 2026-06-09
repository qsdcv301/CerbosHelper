package io.cerboshelper.mybatis.auth;

import io.cerboshelper.mybatis.convention.CerbosCommonResourceRegistry;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CerbosPayloadMapper {
    private final CerbosHelperProperties properties;
    private final CerbosCommonResourceRegistry registry;

    public CerbosPayloadMapper(CerbosHelperProperties properties) {
        this(properties, new CerbosCommonResourceRegistry(List.of()));
    }

    public CerbosPayloadMapper(CerbosHelperProperties properties, CerbosCommonResourceRegistry registry) {
        this.properties = properties;
        this.registry = registry;
    }

    public Map<String, Object> principalPayload(Object principal) {
        if (principal instanceof CerbosPrincipalEnvelope envelope) {
            return Map.of(
                    "id", envelope.id(),
                    "policyVersion", envelope.policyVersion(),
                    "roles", envelope.roles(),
                    "attr", envelope.attr()
            );
        }
        Map<String, Object> attr = attributes(principal);
        return Map.of(
                "id", String.valueOf(requireAny(attr, "id")),
                "policyVersion", properties.getPolicyVersion(),
                "roles", properties.getPrincipalRoles(),
                "attr", attr
        );
    }

    public Map<String, Object> resourcePayload(Object resource) {
        String resourceKind = registry.resourceKindForType(resource.getClass())
                .orElseThrow(() -> new IllegalArgumentException("Cerbos resource must extend CerbosCommonDto: " + resource.getClass().getName()));
        Map<String, Object> attr = attributes(resource);
        Object resourceId = requireResourceId(resource);
        return Map.of(
                "id", String.valueOf(resourceId),
                "kind", resourceKind,
                "policyVersion", properties.getPolicyVersion(),
                "attr", attr
        );
    }

    public String resourceId(Object resource) {
        return String.valueOf(requireResourceId(resource));
    }

    private Object requireResourceId(Object resource) {
        java.util.Optional<Object> annotatedId = registry.resourceId(resource);
        if (annotatedId.isPresent()) {
            return annotatedId.get();
        }
        throw new IllegalArgumentException("Missing Cerbos resource id. Add @CerbosId to the protected DTO id field/method/record component.");
    }

    private Map<String, Object> attributes(Object value) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        Class<?> type = value.getClass();
        if (type.isRecord()) {
            for (RecordComponent component : type.getRecordComponents()) {
                attributes.put(component.getName(), invoke(component.getAccessor(), value));
            }
            addAnnotatedMethods(value, attributes);
            return attributes;
        }

        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (field.isSynthetic() || Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                field.setAccessible(true);
                try {
                    attributes.putIfAbsent(field.getName(), field.get(value));
                } catch (IllegalAccessException exception) {
                    throw new IllegalStateException("Cannot read Cerbos attribute field: " + field.getName(), exception);
                }
            }
        }
        addAnnotatedMethods(value, attributes);
        return attributes;
    }

    private void addAnnotatedMethods(Object value, Map<String, Object> attributes) {
        for (Method method : value.getClass().getMethods()) {
            if (method.getDeclaringClass() == Object.class || method.getParameterCount() != 0) {
                continue;
            }
            String propertyName = methodNameToProperty(method.getName());
            if (attributes.containsKey(propertyName)) {
                continue;
            }
            attributes.put(propertyName, invoke(method, value));
        }
    }

    private Object requireAny(Map<String, Object> attributes, String... names) {
        for (String name : names) {
            Object value = attributes.get(name);
            if (value != null) {
                return value;
            }
        }
        throw new IllegalArgumentException("Missing Cerbos id attribute. Expected one of " + List.of(names));
    }

    private Object invoke(Method method, Object target) {
        try {
            return method.invoke(target);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot read Cerbos attribute method: " + method.getName(), exception);
        }
    }

    private String methodNameToProperty(String methodName) {
        if (methodName.startsWith("get") && methodName.length() > 3) {
            return decapitalize(methodName.substring(3));
        }
        if (methodName.startsWith("is") && methodName.length() > 2) {
            return decapitalize(methodName.substring(2));
        }
        return methodName;
    }

    private String decapitalize(String value) {
        if (value.isEmpty()) {
            return value;
        }
        return value.substring(0, 1).toLowerCase(Locale.ROOT) + value.substring(1);
    }

}
