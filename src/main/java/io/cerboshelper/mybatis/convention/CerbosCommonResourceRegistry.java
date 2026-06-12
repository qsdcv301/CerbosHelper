package io.cerboshelper.mybatis.convention;

import io.cerboshelper.mybatis.model.CerbosCommonDto;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class CerbosCommonResourceRegistry {
    public static final String OWNER_BY_ATTR = "ownerBy";
    public static final String OWNER_GROUP_BY_ATTR = "ownerGroupBy";
    public static final String OWNER_BY_COLUMN = "owner_by";
    public static final String OWNER_GROUP_BY_COLUMN = "owner_group_by";

    private final List<CerbosCommonResource> resources;

    public CerbosCommonResourceRegistry(List<Class<?>> commonResourceTypes) {
        this(defaultResources(commonResourceTypes));
    }

    public static CerbosCommonResourceRegistry configured(List<CerbosCommonResource> resources) {
        return new CerbosCommonResourceRegistry(configuredResources(resources == null ? List.of() : resources));
    }

    private CerbosCommonResourceRegistry(Map<String, CerbosCommonResource> byKind) {
        this.resources = List.copyOf(byKind.values());
    }

    private static Map<String, CerbosCommonResource> defaultResources(List<Class<?>> commonResourceTypes) {
        Map<String, CerbosCommonResource> byKind = new LinkedHashMap<>();
        for (Class<?> resourceType : commonResourceTypes) {
            if (!isConcreteCommonDto(resourceType)) {
                continue;
            }
            CerbosCommonResource resource = defaultResource(resourceType);
            byKind.put(resource.resourceKind(), resource);
        }
        return byKind;
    }

    private static Map<String, CerbosCommonResource> configuredResources(List<CerbosCommonResource> commonResources) {
        Map<String, CerbosCommonResource> byKind = new LinkedHashMap<>();
        for (CerbosCommonResource resource : commonResources) {
            if (resource == null || !isConcreteCommonDto(resource.resourceType())) {
                continue;
            }
            byKind.put(resource.resourceKind(), resource);
        }
        return byKind;
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
                .findFirst();
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

    private static CerbosCommonResource defaultResource(Class<?> resourceType) {
        String resourceKind = decapitalize(stripDtoSuffix(resourceType.getSimpleName()));
        return new CerbosCommonResource(resourceKind, resourceType, resourceKind);
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
