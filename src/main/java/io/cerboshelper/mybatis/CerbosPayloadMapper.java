package io.cerboshelper.mybatis;

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

    public CerbosPayloadMapper(CerbosHelperProperties properties) {
        this.properties = properties;
    }

    public Map<String, Object> principalPayload(Object principal) {
        Map<String, Object> attr = attributes(principal);
        return Map.of(
                "id", String.valueOf(requireAny(attr, "id")),
                "policyVersion", properties.getPolicyVersion(),
                "roles", properties.getPrincipalRoles(),
                "attr", attr
        );
    }

    public Map<String, Object> resourcePayload(Object resource) {
        CerbosResource annotation = resource.getClass().getAnnotation(CerbosResource.class);
        if (annotation == null) {
            throw new IllegalArgumentException("@CerbosResource is required on " + resource.getClass().getName());
        }
        Map<String, Object> attr = attributes(resource);
        return Map.of(
                "id", String.valueOf(requireAny(attr, "id")),
                "kind", annotation.kind(),
                "policyVersion", properties.getPolicyVersion(),
                "attr", attr
        );
    }

    public String resourceId(Object resource) {
        return String.valueOf(requireAny(attributes(resource), "id"));
    }

    private Map<String, Object> attributes(Object value) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        Class<?> type = value.getClass();
        if (type.isRecord()) {
            for (RecordComponent component : type.getRecordComponents()) {
                CerbosAttribute attribute = component.getAnnotation(CerbosAttribute.class);
                if (attribute != null && attribute.ignore()) {
                    continue;
                }
                attributes.put(attributeName(component.getName(), attribute), invoke(component.getAccessor(), value));
            }
            addAnnotatedMethods(value, attributes);
            return attributes;
        }

        for (Field field : type.getDeclaredFields()) {
            if (field.isSynthetic() || Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            CerbosAttribute attribute = field.getAnnotation(CerbosAttribute.class);
            if (attribute != null && attribute.ignore()) {
                continue;
            }
            field.setAccessible(true);
            try {
                attributes.put(attributeName(field.getName(), attribute), field.get(value));
            } catch (IllegalAccessException exception) {
                throw new IllegalStateException("Cannot read Cerbos attribute field: " + field.getName(), exception);
            }
        }
        addAnnotatedMethods(value, attributes);
        return attributes;
    }

    private void addAnnotatedMethods(Object value, Map<String, Object> attributes) {
        for (Method method : value.getClass().getMethods()) {
            CerbosAttribute attribute = method.getAnnotation(CerbosAttribute.class);
            if (attribute == null || attribute.ignore()) {
                continue;
            }
            attributes.put(attributeName(methodNameToProperty(method.getName()), attribute), invoke(method, value));
        }
    }

    private String attributeName(String defaultName, CerbosAttribute attribute) {
        return attribute != null && !attribute.value().isBlank() ? attribute.value() : defaultName;
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
