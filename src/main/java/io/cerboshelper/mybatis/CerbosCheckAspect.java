package io.cerboshelper.mybatis;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.BeanFactory;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Aspect
public class CerbosCheckAspect {
    private final CerbosAuthorizationClient authorizationClient;
    private final BeanFactory beanFactory;
    private final CerbosPrincipalResolver principalResolver;
    private final CerbosMethodExpressionEvaluator expressionEvaluator;

    public CerbosCheckAspect(CerbosAuthorizationClient authorizationClient, BeanFactory beanFactory, CerbosPrincipalResolver principalResolver) {
        this.authorizationClient = authorizationClient;
        this.beanFactory = beanFactory;
        this.principalResolver = principalResolver;
        this.expressionEvaluator = new CerbosMethodExpressionEvaluator(beanFactory);
    }

    @Around("@annotation(io.cerboshelper.mybatis.CerbosCheck) || @annotation(io.cerboshelper.mybatis.CerbosChecks)")
    public Object check(ProceedingJoinPoint joinPoint) throws Throwable {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        CerbosCheck[] checks = method.getAnnotationsByType(CerbosCheck.class);
        CerbosMethodExpressionEvaluator.Context context = expressionEvaluator.context(method, joinPoint.getArgs());
        for (CerbosCheck check : checks) {
            Object principal = expressionEvaluator.principal(check.principal(), context, principalResolver);
            for (Object resource : resolveResources(check, method, joinPoint.getArgs(), context)) {
                if (!authorizationClient.isAllowed(principal, resource, check.action())) {
                    throw new SecurityException("Cerbos denied action=" + check.action()
                            + ", principal=" + describe(principal)
                            + ", resource=" + describe(resource));
                }
            }
        }
        return joinPoint.proceed();
    }

    private List<Object> resolveResources(CerbosCheck check, Method method, Object[] args, CerbosMethodExpressionEvaluator.Context context) {
        List<Object> resources = new ArrayList<>();
        inferExistingResource(check, context).ifPresent(resources::add);
        if (!check.resource().isBlank()) {
            Object resource = tokenOrExpression(check.resource(), context);
            addResource(resources, applyIdIfPossible(resource, check, context));
            return requireResources(method, resources);
        }
        for (Object arg : args) {
            if (arg != null && arg.getClass().isAnnotationPresent(CerbosResource.class)) {
                addResource(resources, applyIdIfPossible(arg, check, context));
            }
        }
        Object resource = context.variable("resource");
        if (resource != null) {
            addResource(resources, applyIdIfPossible(resource, check, context));
        }
        return requireResources(method, resources);
    }

    private void addResource(List<Object> resources, Object resource) {
        if (resource == null || resources.stream().anyMatch(existing -> existing == resource)) {
            return;
        }
        resources.add(resource);
    }

    private List<Object> requireResources(Method method, List<Object> resources) {
        if (resources.isEmpty()) {
            throw new IllegalArgumentException("Cannot resolve Cerbos resource for " + method.getDeclaringClass().getName() + "." + method.getName()
                    + "(). Use one of: a @CerbosResource method argument, a resourceId-style parameter such as documentId with documentMapper.findById(...), "
                    + "@CerbosCheck(resourceKind = \"...\", id = \"...\", mapper = \"...\", finder = \"...\"), or @CerbosCheck(resource = \"...\"). "
                    + "If parameter names are not visible, enable Java compiler option -parameters.");
        }
        return resources;
    }

    private Optional<Object> inferExistingResource(CerbosCheck check, CerbosMethodExpressionEvaluator.Context context) {
        IdReference idReference = idReference(check, context).orElse(null);
        if (idReference == null) {
            return Optional.empty();
        }
        if (!beanFactory.containsBean(idReference.mapperBeanName())) {
            throw new IllegalArgumentException("Cannot resolve Cerbos resource mapper bean '" + idReference.mapperBeanName()
                    + "' for resourceKind=" + idReference.resourceKind()
                    + ", id=" + idReference.id()
                    + ". Rename the mapper bean to " + idReference.resourceKind() + "Mapper or set @CerbosCheck(mapper = \"...\").");
        }
        Object mapper = beanFactory.getBean(idReference.mapperBeanName());
        return Optional.of(unwrap(invoke(mapper, idReference.finderName(), idReference.id()), idReference));
    }

    private Optional<IdReference> idReference(CerbosCheck check, CerbosMethodExpressionEvaluator.Context context) {
        if (!check.resourceKind().isBlank() && !check.id().isBlank()) {
            String resourceKind = check.resourceKind();
            return Optional.of(new IdReference(
                    resourceKind,
                    mapperBeanName(check, resourceKind),
                    finderName(check),
                    tokenOrExpression(check.id(), context)
            ));
        }
        if (!check.id().isBlank()) {
            Object id = tokenOrExpression(check.id(), context);
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

    private String mapperBeanName(CerbosCheck check, String resourceKind) {
        if (!check.mapper().isBlank()) {
            return check.mapper();
        }
        return resourceKind + "Mapper";
    }

    private String finderName(CerbosCheck check) {
        return check.finder().isBlank() ? "findById" : check.finder();
    }

    private Optional<String> inferResourceKindForId(CerbosMethodExpressionEvaluator.Context context) {
        Object resource = context.variable("resource");
        if (resource != null && resource.getClass().isAnnotationPresent(CerbosResource.class)) {
            return Optional.of(resource.getClass().getAnnotation(CerbosResource.class).kind());
        }
        return context.variableNames().stream()
                .map(context::variable)
                .filter(value -> value != null && value.getClass().isAnnotationPresent(CerbosResource.class))
                .map(value -> value.getClass().getAnnotation(CerbosResource.class).kind())
                .findFirst();
    }

    private Object tokenOrExpression(String value, CerbosMethodExpressionEvaluator.Context context) {
        if (value.startsWith("#") || value.startsWith("@")) {
            return expressionEvaluator.value(value, context);
        }
        Object variable = context.variable(value);
        return variable != null ? variable : value;
    }

    private Object applyIdIfPossible(Object resource, CerbosCheck check, CerbosMethodExpressionEvaluator.Context context) {
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
                            + ". Check that withId accepts the same id type as @CerbosCheck(id = \"...\").", exception);
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
                + "(...). Set @CerbosCheck(mapper = \"...\", finder = \"...\") if this project does not use the default mapper lookup convention.");
    }

    private Object unwrap(Object value, IdReference idReference) {
        if (value instanceof Optional<?> optional) {
            return optional.orElseThrow(() -> new IllegalArgumentException("Cannot resolve Cerbos resource. "
                    + idReference.mapperBeanName() + "." + idReference.finderName()
                    + "(" + idReference.id() + ") returned Optional.empty()."));
        }
        return value;
    }

    private String describe(Object value) {
        if (value == null) {
            return "null";
        }
        String id = readId(value).map(Object::toString).orElse("unknown");
        CerbosResource resource = value.getClass().getAnnotation(CerbosResource.class);
        if (resource != null) {
            return value.getClass().getSimpleName() + "(kind=" + resource.kind() + ", id=" + id + ")";
        }
        return value.getClass().getSimpleName() + "(id=" + id + ")";
    }

    private Optional<Object> readId(Object value) {
        for (String methodName : List.of("id", "getId")) {
            try {
                Method method = value.getClass().getMethod(methodName);
                return Optional.ofNullable(method.invoke(value));
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return Optional.empty();
    }

    private String decapitalize(String value) {
        if (value.isEmpty()) {
            return value;
        }
        return value.substring(0, 1).toLowerCase(Locale.ROOT) + value.substring(1);
    }

    private record IdReference(String resourceKind, String mapperBeanName, String finderName, Object id) {
    }
}
