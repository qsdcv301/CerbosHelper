package io.cerboshelper.mybatis.sql;

import io.cerboshelper.mybatis.annotation.CerbosAttribute;
import io.cerboshelper.mybatis.annotation.CerbosResource;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class CerbosResourceColumns implements CerbosResourceColumnRegistry {
    private static final String RESOURCE_ATTR_PREFIX = "request.resource.attr.";

    private final Map<String, Map<String, String>> columnsByKind;

    private CerbosResourceColumns(Map<String, Map<String, String>> columnsByKind) {
        this.columnsByKind = columnsByKind;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public Optional<String> columnFor(String resourceKind, String cerbosVariable) {
        Map<String, String> columns = columnsByKind.get(resourceKind);
        if (columns == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(columns.get(cerbosVariable));
    }

    public static final class Builder {
        private final Map<String, Map<String, String>> columnsByKind = new LinkedHashMap<>();

        public Builder resource(Class<?> resourceType) {
            CerbosResource resource = resourceType.getAnnotation(CerbosResource.class);
            if (resource == null) {
                throw new IllegalArgumentException("@CerbosResource is required on " + resourceType.getName());
            }
            return resource(resource.kind(), resourceType, resource.sqlAlias());
        }

        public Builder resource(String resourceKind, Class<?> resourceType) {
            return resource(resourceKind, resourceType, "");
        }

        public Builder resource(String resourceKind, Class<?> resourceType, String sqlAlias) {
            if (resourceKind == null || resourceKind.isBlank()) {
                throw new IllegalArgumentException("resourceKind must not be blank");
            }
            columnsByKind.put(resourceKind, inspect(resourceKind, resourceType, sqlAlias));
            return this;
        }

        public Builder column(String resourceKind, String cerbosVariable, String sqlColumn) {
            columnsByKind.computeIfAbsent(resourceKind, ignored -> new LinkedHashMap<>())
                    .put(cerbosVariable, sqlColumn);
            return this;
        }

        public CerbosResourceColumns build() {
            Map<String, Map<String, String>> copy = new LinkedHashMap<>();
            columnsByKind.forEach((kind, columns) -> copy.put(kind, Map.copyOf(columns)));
            return new CerbosResourceColumns(Map.copyOf(copy));
        }

        private Map<String, String> inspect(String resourceKind, Class<?> resourceType, String sqlAlias) {
            Map<String, String> columns = new LinkedHashMap<>();
            String qualifier = sqlAlias == null || sqlAlias.isBlank() ? resourceKind : sqlAlias;
            if (resourceType.isRecord()) {
                for (RecordComponent component : resourceType.getRecordComponents()) {
                    CerbosAttribute attribute = component.getAnnotation(CerbosAttribute.class);
                    if (attribute != null && attribute.ignore()) {
                        continue;
                    }
                    addColumn(columns, component.getName(), attribute, qualifier);
                }
                return columns;
            }

            for (Field field : resourceType.getDeclaredFields()) {
                if (field.isSynthetic() || Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                CerbosAttribute attribute = field.getAnnotation(CerbosAttribute.class);
                if (attribute != null && attribute.ignore()) {
                    continue;
                }
                addColumn(columns, field.getName(), attribute, qualifier);
            }
            for (Method method : resourceType.getMethods()) {
                CerbosAttribute attribute = method.getAnnotation(CerbosAttribute.class);
                if (attribute == null || attribute.ignore()) {
                    continue;
                }
                addColumn(columns, methodNameToProperty(method.getName()), attribute, qualifier);
            }
            return columns;
        }

        private void addColumn(Map<String, String> columns, String defaultAttributeName, CerbosAttribute attribute, String qualifier) {
            String attributeName = attribute != null && !attribute.value().isBlank()
                    ? attribute.value()
                    : defaultAttributeName;
            String column = attribute != null && !attribute.column().isBlank()
                    ? attribute.column()
                    : qualify(qualifier, camelToSnake(attributeName));
            columns.put(RESOURCE_ATTR_PREFIX + attributeName, column);
        }

        private String qualify(String qualifier, String column) {
            if (qualifier == null || qualifier.isBlank()) {
                return column;
            }
            return qualifier + "." + column;
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

        private String camelToSnake(String value) {
            StringBuilder builder = new StringBuilder();
            for (int index = 0; index < value.length(); index++) {
                char current = value.charAt(index);
                if (Character.isUpperCase(current)) {
                    if (index > 0) {
                        builder.append('_');
                    }
                    builder.append(Character.toLowerCase(current));
                } else {
                    builder.append(current);
                }
            }
            return builder.toString();
        }
    }
}
