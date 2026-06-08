package io.cerboshelper.mybatis.convention;

import io.cerboshelper.mybatis.model.CerbosCommonDto;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class CerbosCommonResourceRegistry {
    public static final String OWNER_BY_ATTR = "ownerBy";
    public static final String OWNER_ORG_BY_ATTR = "ownerOrgBy";
    public static final String OWNER_BY_COLUMN = "owner_by";
    public static final String OWNER_ORG_BY_COLUMN = "owner_org_by";

    private final List<CerbosCommonResource> resources;
    private final Map<String, String> idPropertiesByKind = new ConcurrentHashMap<>();

    public CerbosCommonResourceRegistry(List<Class<?>> commonResourceTypes) {
        Map<String, CerbosCommonResource> byKind = new LinkedHashMap<>();
        for (Class<?> resourceType : commonResourceTypes) {
            if (!isConcreteCommonDto(resourceType)) {
                continue;
            }
            CerbosCommonResource resource = defaultResource(resourceType);
            byKind.put(resource.resourceKind(), resource);
        }
        this.resources = List.copyOf(byKind.values());
    }

    public static boolean isCommonResourceType(Class<?> type) {
        return type != null && CerbosCommonDto.class.isAssignableFrom(type);
    }

    public List<CerbosCommonResource> resources() {
        return resources;
    }

    public Optional<CerbosCommonResource> resourceForType(Class<?> type) {
        if (type == null) {
            return Optional.empty();
        }
        return resources.stream()
                .filter(resource -> resource.resourceType().isAssignableFrom(type) || type.isAssignableFrom(resource.resourceType()))
                .findFirst()
                .or(() -> isConcreteCommonDto(type) ? Optional.of(defaultResource(type)) : Optional.empty());
    }

    public Optional<CerbosCommonResource> resourceForKind(String resourceKind) {
        if (resourceKind == null || resourceKind.isBlank()) {
            return Optional.empty();
        }
        return resources.stream()
                .filter(resource -> resource.resourceKind().equals(resourceKind))
                .findFirst();
    }

    public Optional<String> resourceKindForType(Class<?> type) {
        return resourceForType(type).map(CerbosCommonResource::resourceKind);
    }

    public Optional<String> idPropertyForType(Class<?> type) {
        return resourceForType(type)
                .flatMap(resource -> idPropertyForKind(resource.resourceKind()));
    }

    public Optional<String> idPropertyForKind(String resourceKind) {
        if (resourceKind == null || resourceKind.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(idPropertiesByKind.get(resourceKind));
    }

    public void registerIdProperty(Class<?> resourceType, String idProperty) {
        resourceForType(resourceType).ifPresent(resource -> registerIdProperty(resource.resourceKind(), idProperty));
    }

    public void registerIdProperty(String resourceKind, String idProperty) {
        if (resourceKind == null || resourceKind.isBlank() || idProperty == null || idProperty.isBlank()) {
            return;
        }
        String normalizedProperty = idProperty.trim();
        String existing = idPropertiesByKind.putIfAbsent(resourceKind, normalizedProperty);
        if (existing != null && !existing.equals(normalizedProperty)) {
            throw new IllegalStateException("Conflicting Cerbos id properties for resourceKind=" + resourceKind
                    + ": " + existing + " vs " + normalizedProperty
                    + ". Use consistent MyBatis resultMap <id property=\"...\"> declarations or provide a custom resolver.");
        }
    }

    public Optional<Object> resourceId(Object resource) {
        if (resource == null) {
            return Optional.empty();
        }
        Optional<String> resultMapIdProperty = idPropertyForType(resource.getClass());
        if (resultMapIdProperty.isPresent()) {
            Optional<Object> value = readProperty(resource, resultMapIdProperty.get());
            if (value.isPresent()) {
                return value;
            }
        }
        return readProperty(resource, "id");
    }

    public Optional<String> resourceKindFromToken(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        String normalized = decapitalize(token);
        return resourceForKind(normalized)
                .map(CerbosCommonResource::resourceKind)
                .or(() -> resources.stream()
                        .filter(resource -> resource.resourceType().getSimpleName().equalsIgnoreCase(token))
                        .map(CerbosCommonResource::resourceKind)
                        .findFirst());
    }

    public List<String> resourceKinds() {
        List<String> kinds = new ArrayList<>();
        resources.forEach(resource -> kinds.add(resource.resourceKind()));
        return kinds;
    }

    private CerbosCommonResource defaultResource(Class<?> resourceType) {
        String resourceKind = decapitalize(stripDtoSuffix(resourceType.getSimpleName()));
        return new CerbosCommonResource(resourceKind, resourceType, resourceKind);
    }

    private Optional<Object> readProperty(Object resource, String propertyName) {
        if (resource == null || propertyName == null || propertyName.isBlank()) {
            return Optional.empty();
        }
        for (String methodName : getterNames(propertyName)) {
            try {
                Method method = resource.getClass().getMethod(methodName);
                if (method.getParameterCount() == 0) {
                    return Optional.ofNullable(method.invoke(resource));
                }
            } catch (ReflectiveOperationException | IllegalArgumentException ignored) {
            }
        }
        for (Class<?> current = resource.getClass(); current != null && current != Object.class; current = current.getSuperclass()) {
            try {
                Field field = current.getDeclaredField(propertyName);
                if (Modifier.isStatic(field.getModifiers())) {
                    return Optional.empty();
                }
                field.setAccessible(true);
                return Optional.ofNullable(field.get(resource));
            } catch (ReflectiveOperationException | IllegalArgumentException ignored) {
            }
        }
        return Optional.empty();
    }

    private List<String> getterNames(String propertyName) {
        String suffix = propertyName.substring(0, 1).toUpperCase(Locale.ROOT) + propertyName.substring(1);
        return List.of("get" + suffix, "is" + suffix, propertyName);
    }

    private static boolean isConcreteCommonDto(Class<?> type) {
        return isCommonResourceType(type) && !type.isInterface() && !Modifier.isAbstract(type.getModifiers());
    }

    private static String decapitalize(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        if (value.length() > 1 && Character.isUpperCase(value.charAt(0)) && Character.isUpperCase(value.charAt(1))) {
            return value;
        }
        return value.substring(0, 1).toLowerCase(Locale.ROOT) + value.substring(1);
    }

    private static String stripDtoSuffix(String value) {
        return value != null && value.endsWith("Dto") && value.length() > "Dto".length()
                ? value.substring(0, value.length() - "Dto".length())
                : value;
    }
}
