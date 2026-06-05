package io.cerboshelper.mybatis.sql;

import io.cerboshelper.mybatis.convention.CerbosCommonResource;
import io.cerboshelper.mybatis.convention.CerbosCommonResourceRegistry;

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
            String resourceKind = decapitalize(resourceType.getSimpleName());
            return resource(resourceKind, resourceType, resourceKind);
        }

        public Builder resource(String resourceKind, Class<?> resourceType) {
            return resource(resourceKind, resourceType, "");
        }

        public Builder resource(String resourceKind, Class<?> resourceType, String sqlAlias) {
            if (resourceKind == null || resourceKind.isBlank()) {
                throw new IllegalArgumentException("resourceKind must not be blank");
            }
            columnsByKind.put(resourceKind, inspect(resourceType, qualifier(resourceKind, sqlAlias)));
            return this;
        }

        public Builder resource(CerbosCommonResource resource) {
            columnsByKind.put(resource.resourceKind(), inspect(resource.resourceType(), resource.sqlAlias()));
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

        private Map<String, String> inspect(Class<?> resourceType, String sqlAlias) {
            Map<String, String> columns = new LinkedHashMap<>();
            String qualifier = sqlAlias == null || sqlAlias.isBlank() ? "" : sqlAlias;
            if (resourceType.isRecord()) {
                for (RecordComponent component : resourceType.getRecordComponents()) {
                    addColumn(columns, component.getName(), null, qualifier);
                }
                return columns;
            }

            for (Class<?> current = resourceType; current != null && current != Object.class; current = current.getSuperclass()) {
                for (Field field : current.getDeclaredFields()) {
                    if (field.isSynthetic() || Modifier.isStatic(field.getModifiers())) {
                        continue;
                    }
                    addColumn(columns, field.getName(), null, qualifier);
                }
            }
            for (Method method : resourceType.getMethods()) {
                if (method.getDeclaringClass() == Object.class || method.getParameterCount() != 0) {
                    continue;
                }
                String propertyName = methodNameToProperty(method.getName());
                if (columns.containsKey(RESOURCE_ATTR_PREFIX + propertyName)) {
                    continue;
                }
                addColumn(columns, propertyName, null, qualifier);
            }
            return columns;
        }

        private String qualifier(String resourceKind, String sqlAlias) {
            return sqlAlias == null || sqlAlias.isBlank() ? resourceKind : sqlAlias;
        }

        private void addColumn(Map<String, String> columns, String attributeName, String explicitColumn, String qualifier) {
            String defaultColumn = switch (attributeName) {
                case CerbosCommonResourceRegistry.OWNER_BY_ATTR -> CerbosCommonResourceRegistry.OWNER_BY_COLUMN;
                case CerbosCommonResourceRegistry.OWNER_ORG_BY_ATTR -> CerbosCommonResourceRegistry.OWNER_ORG_BY_COLUMN;
                default -> camelToSnake(attributeName);
            };
            String column = explicitColumn != null && !explicitColumn.isBlank()
                    ? explicitColumn
                    : qualify(qualifier, defaultColumn);
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
