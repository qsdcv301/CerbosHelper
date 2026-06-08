package io.cerboshelper.mybatis.check;

import io.cerboshelper.mybatis.convention.CerbosCommonResourceRegistry;
import io.cerboshelper.mybatis.model.CerbosCommonDto;
import io.cerboshelper.mybatis.support.CerbosMethodExpressionEvaluator;
import org.springframework.beans.factory.BeanFactory;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class DefaultCerbosResourceResolver implements CerbosResourceResolver {
    private final BeanFactory beanFactory;
    private final CerbosMethodExpressionEvaluator expressionEvaluator;
    private final CerbosCommonResourceRegistry registry;

    public DefaultCerbosResourceResolver(BeanFactory beanFactory, CerbosMethodExpressionEvaluator expressionEvaluator) {
        this(beanFactory, expressionEvaluator, new CerbosCommonResourceRegistry(List.of()));
    }

    public DefaultCerbosResourceResolver(BeanFactory beanFactory, CerbosMethodExpressionEvaluator expressionEvaluator, CerbosCommonResourceRegistry registry) {
        this.beanFactory = beanFactory;
        this.expressionEvaluator = expressionEvaluator;
        this.registry = registry == null ? new CerbosCommonResourceRegistry(List.of()) : registry;
    }

    @Override
    public List<Object> resolve(CerbosResourceResolutionRequest request) {
        CerbosCheckSpec check = request.check();
        CerbosMethodExpressionEvaluator.Context context = request.context();
        List<Object> resources = new ArrayList<>();
        Optional<Object> existingResource = inferExistingResource(check, context);
        if (existingResource.isPresent() && usesExistingResource(check.action())) {
            resources.add(existingResource.get());
            return resources;
        }
        existingResource.ifPresent(resources::add);
        if (!check.resource().isBlank()) {
            Object resource = tokenOrExpression(check.resource(), context);
            addResource(resources, applyIdIfPossible(resource, check, context));
            return resources;
        }
        for (Object arg : request.args()) {
            if (arg instanceof CerbosCommonDto) {
                addResource(resources, applyIdIfPossible(arg, check, context));
            }
        }
        Object resource = context.variable("resource");
        if (resource != null) {
            addResource(resources, applyIdIfPossible(resource, check, context));
        }
        return resources;
    }

    private boolean usesExistingResource(String action) {
        return "view".equals(action) || "update".equals(action) || "delete".equals(action);
    }

    private void addResource(List<Object> resources, Object resource) {
        if (resource == null || resources.stream().anyMatch(existing -> existing == resource)) {
            return;
        }
        resources.add(resource);
    }

    private Optional<Object> inferExistingResource(CerbosCheckSpec check, CerbosMethodExpressionEvaluator.Context context) {
        IdReference idReference = idReference(check, context).orElse(null);
        if (idReference == null) {
            return Optional.empty();
        }
        if (!beanFactory.containsBean(idReference.mapperBeanName())) {
            throw new IllegalArgumentException("Cannot resolve Cerbos resource mapper bean '" + idReference.mapperBeanName()
                    + "' for resourceKind=" + idReference.resourceKind()
                    + ", id=" + idReference.id()
                    + ". Register a custom CerbosResourceResolver bean or provide a {resource}Mapper.findById(...) bean.");
        }
        Object mapper = beanFactory.getBean(idReference.mapperBeanName());
        return Optional.of(unwrap(invoke(mapper, idReference.finderName(), idReference.id()), idReference));
    }

    private Optional<IdReference> idReference(CerbosCheckSpec check, CerbosMethodExpressionEvaluator.Context context) {
        if (!check.resourceKind().isBlank() && !check.id().isBlank()) {
            String resourceKind = check.resourceKind();
            Object id = idValue(check.id(), context);
            requireResolvedId(id, check.id(), resourceKind);
            return Optional.of(new IdReference(
                    resourceKind,
                    mapperBeanName(check, resourceKind),
                    finderName(check),
                    id
            ));
        }
        if (!check.id().isBlank()) {
            Object id = idValue(check.id(), context);
            requireResolvedId(id, check.id(), "");
            return inferResourceKindForId(context)
                    .map(resourceKind -> new IdReference(resourceKind, mapperBeanName(check, resourceKind), finderName(check), id));
        }
        return context.variableNames().stream()
                .filter(name -> name.endsWith("Id") && name.length() > 2)
                .filter(name -> context.variable(name) != null)
                .map(name -> {
                    String resourceKind = decapitalize(name.substring(0, name.length() - 2));
                    return new IdReference(resourceKind, mapperBeanName(check, resourceKind), finderName(check), context.variable(name));
                })
                .filter(reference -> beanFactory.containsBean(reference.mapperBeanName()))
                .findFirst();
    }

    private void requireResolvedId(Object id, String idExpression, String resourceKind) {
        if (id == null) {
            throw new IllegalArgumentException("Cannot resolve Cerbos resource id from '" + idExpression + "'"
                    + (resourceKind == null || resourceKind.isBlank() ? "" : " for resourceKind=" + resourceKind)
                    + ". Ensure the protected DTO exposes id/getId for update/delete/view checks.");
        }
    }

    private String mapperBeanName(CerbosCheckSpec check, String resourceKind) {
        if (!check.mapper().isBlank()) {
            return check.mapper();
        }
        return resourceKind + "Mapper";
    }

    private String finderName(CerbosCheckSpec check) {
        return check.finder().isBlank() ? "findById" : check.finder();
    }

    private Optional<String> inferResourceKindForId(CerbosMethodExpressionEvaluator.Context context) {
        Object resource = context.variable("resource");
        if (resource != null && CerbosCommonResourceRegistry.isCommonResourceType(resource.getClass())) {
            return Optional.of(defaultResourceKind(resource.getClass()));
        }
        return context.variableNames().stream()
                .map(context::variable)
                .filter(value -> value != null && CerbosCommonResourceRegistry.isCommonResourceType(value.getClass()))
                .map(value -> defaultResourceKind(value.getClass()))
                .findFirst();
    }

    private Object tokenOrExpression(String value, CerbosMethodExpressionEvaluator.Context context) {
        if (value.startsWith("#") || value.startsWith("@")) {
            return expressionEvaluator.value(value, context);
        }
        Object variable = context.variable(value);
        return variable != null ? variable : value;
    }

    private Object idValue(String value, CerbosMethodExpressionEvaluator.Context context) {
        IdResolution registeredId = registeredResourceId(value, context);
        if (registeredId.resolved()) {
            return registeredId.value();
        }
        Object id = tokenOrExpression(value, context);
        if (id instanceof CerbosCommonDto commonResource) {
            return registry.resourceId(commonResource).orElse(null);
        }
        return id;
    }

    private IdResolution registeredResourceId(String value, CerbosMethodExpressionEvaluator.Context context) {
        if (value == null || !value.startsWith("#") || value.startsWith("@")) {
            return IdResolution.unresolved();
        }
        String expression = value.substring(1);
        int separator = expression.indexOf('.');
        String variableName = separator >= 0 ? expression.substring(0, separator) : expression;
        String propertyName = separator >= 0 ? expression.substring(separator + 1) : "";
        if (variableName.isBlank() || variableName.contains("[") || variableName.contains("]") || propertyName.contains(".")) {
            return IdResolution.unresolved();
        }
        Object resource = context.variable(variableName);
        if (!(resource instanceof CerbosCommonDto)) {
            return IdResolution.unresolved();
        }
        if (!propertyName.isBlank()) {
            Optional<String> registeredProperty = registry.idPropertyForType(resource.getClass());
            if (registeredProperty.isEmpty() || !registeredProperty.get().equals(propertyName)) {
                return IdResolution.unresolved();
            }
        }
        return IdResolution.resolved(registry.resourceId(resource).orElse(null));
    }

    private Object applyIdIfPossible(Object resource, CerbosCheckSpec check, CerbosMethodExpressionEvaluator.Context context) {
        if (resource == null || check.id().isBlank()) {
            return resource;
        }
        Object id = tokenOrExpression(check.id(), context);
        try {
            Method withId = resource.getClass().getMethod("withId", id.getClass());
            return withId.invoke(resource, id);
        } catch (ReflectiveOperationException ignored) {
        }
        for (Method method : resource.getClass().getMethods()) {
            if (method.getName().equals("withId") && method.getParameterCount() == 1) {
                try {
                    return method.invoke(resource, id);
                } catch (ReflectiveOperationException exception) {
                    throw new IllegalStateException("Cannot apply Cerbos resource id using withId(...) on " + resource.getClass().getName()
                            + ". Register a custom CerbosResourceResolver bean if this project uses a different immutable-copy pattern.", exception);
                }
            }
        }
        return resource;
    }

    private Object invoke(Object target, String methodName, Object arg) {
        for (Method method : target.getClass().getMethods()) {
            if (method.getName().equals(methodName) && method.getParameterCount() == 1) {
                try {
                    return method.invoke(target, arg);
                } catch (ReflectiveOperationException exception) {
                    throw new IllegalStateException("Cannot invoke " + target.getClass().getName() + "." + methodName
                            + "(...) while resolving Cerbos resource id=" + arg
                            + ". Check mapper/finder visibility and argument type.", exception);
                }
            }
        }
        throw new IllegalArgumentException("Cannot find " + target.getClass().getName() + "." + methodName
                + "(...). Register a custom CerbosResourceResolver bean or provide a {resource}Mapper.findById(...) method.");
    }

    private Object unwrap(Object value, IdReference idReference) {
        if (value instanceof Optional<?> optional) {
            return optional.orElseThrow(() -> new IllegalArgumentException("Cannot resolve Cerbos resource. "
                    + idReference.mapperBeanName() + "." + idReference.finderName()
                    + "(" + idReference.id() + ") returned Optional.empty()."));
        }
        return value;
    }

    private String decapitalize(String value) {
        if (value.isEmpty()) {
            return value;
        }
        return value.substring(0, 1).toLowerCase(Locale.ROOT) + value.substring(1);
    }

    private String defaultResourceKind(Class<?> resourceType) {
        return decapitalize(stripDtoSuffix(resourceType.getSimpleName()));
    }

    private String stripDtoSuffix(String value) {
        return value != null && value.endsWith("Dto") && value.length() > "Dto".length()
                ? value.substring(0, value.length() - "Dto".length())
                : value;
    }

    private record IdReference(String resourceKind, String mapperBeanName, String finderName, Object id) {
    }

    private record IdResolution(boolean resolved, Object value) {
        private static IdResolution unresolved() {
            return new IdResolution(false, null);
        }

        private static IdResolution resolved(Object value) {
            return new IdResolution(true, value);
        }
    }
}
