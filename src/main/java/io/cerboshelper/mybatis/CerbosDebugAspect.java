package io.cerboshelper.mybatis;

import com.fasterxml.jackson.databind.JsonNode;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;

import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Aspect
public class CerbosDebugAspect {
    private static final Logger log = LoggerFactory.getLogger(CerbosDebugAspect.class);

    private final CerbosAuthorizationClient authorizationClient;
    private final CerbosPlanToSqlConverter planToSqlConverter;
    private final BeanFactory beanFactory;
    private final CerbosMethodExpressionEvaluator expressionEvaluator;

    public CerbosDebugAspect(CerbosAuthorizationClient authorizationClient, CerbosPlanToSqlConverter planToSqlConverter, BeanFactory beanFactory) {
        this.authorizationClient = authorizationClient;
        this.planToSqlConverter = planToSqlConverter;
        this.beanFactory = beanFactory;
        this.expressionEvaluator = new CerbosMethodExpressionEvaluator(beanFactory);
    }

    @Around("@annotation(io.cerboshelper.mybatis.CerbosDebugPlan)")
    public Object debugPlan(ProceedingJoinPoint joinPoint) {
        Method method = method(joinPoint);
        CerbosDebugPlan debugPlan = method.getAnnotation(CerbosDebugPlan.class);
        CerbosMethodExpressionEvaluator.Context context = expressionEvaluator.context(method, joinPoint.getArgs());
        Object principal = resolvePrincipal(debugPlan.principal(), context);
        String action = resolveAction(debugPlan.action(), context);
        JsonNode plan = authorizationClient.planResources(principal, debugPlan.resourceKind(), action);
        CerbosSqlFilter filter = planToSqlConverter.convertNamed(debugPlan.resourceKind(), plan);
        return new CerbosPlanDebugResult(principal, action, plan, filter.whereSql(), filter.namedParams(), filter.denied());
    }

    @Around("@annotation(io.cerboshelper.mybatis.CerbosRowTrace)")
    public Object rowTrace(ProceedingJoinPoint joinPoint) {
        Method method = method(joinPoint);
        CerbosRowTrace rowTrace = method.getAnnotation(CerbosRowTrace.class);
        CerbosMethodExpressionEvaluator.Context context = expressionEvaluator.context(method, joinPoint.getArgs());
        Object principal = resolvePrincipal(rowTrace.principal(), context);
        String action = resolveAction(rowTrace.action(), context);
        context.setVariable("principal", principal);
        context.setVariable("action", action);

        int pageNum = number(rowTrace.pageNum(), context);
        int pageSize = number(rowTrace.pageSize(), context);
        JsonNode plan = authorizationClient.planResources(principal, rowTrace.resourceKind(), action);
        CerbosSqlFilter filter = planToSqlConverter.convertNamed(rowTrace.resourceKind(), plan);

        Object candidateRows = pageInfo(pageNum, pageSize, () -> traceValue(rowTrace.candidates(), rowTrace.resourceKind(), "findAll", context));
        Object sqlMatchedRows = filter.denied()
                ? pageInfo(List.of())
                : CerbosScopeContext.with(principal, action, () -> pageInfo(pageNum, pageSize, () -> traceValue(rowTrace.scopedRows(), rowTrace.resourceKind(), "find" + capitalized(rowTrace.resourceKind()) + "s", context)));
        Set<Object> sqlMatchedIds = filter.denied()
                ? Set.of()
                : CerbosScopeContext.with(principal, action, () -> ids(traceValue(rowTrace.scopedIds(), rowTrace.resourceKind(), "find" + capitalized(rowTrace.resourceKind()) + "Ids", context)));
        List<?> candidateList = rows(candidateRows);
        Map<String, String> effects = authorizationClient.checkResources(principal, candidateList, action);
        List<CerbosRowTraceResult.RowDecision> rowDecisions = candidateList.stream()
                .map(row -> rowDecision(row, rowTrace, sqlMatchedIds, effects))
                .toList();

        log.info(
                "cerbos.row-trace principal={} action={} whereSql={} params={} candidateRows={} sqlMatchedRows={} denied={}",
                principal,
                action,
                filter.whereSql(),
                filter.namedParams(),
                size(candidateRows),
                size(sqlMatchedRows),
                filter.denied()
        );
        rowDecisions.forEach(decision -> log.info(
                "cerbos.row-trace.row principal={} action={} rowId={} planMatched={} checkEffect={} row={}",
                principal,
                action,
                decision.rowId(),
                decision.matchedByPlanSql(),
                decision.checkEffect(),
                decision.row()
        ));

        return new CerbosRowTraceResult(principal, action, plan, filter.whereSql(), filter.namedParams(), filter.denied(), candidateRows, sqlMatchedRows, rowDecisions);
    }

    private CerbosRowTraceResult.RowDecision rowDecision(Object row, CerbosRowTrace rowTrace, Set<Object> sqlMatchedIds, Map<String, String> effects) {
        Object rowId = CerbosReflection.value(row, rowTrace.rowId());
        Object title = CerbosReflection.value(row, rowTrace.title());
        return new CerbosRowTraceResult.RowDecision(
                rowId,
                title,
                sqlMatchedIds.contains(rowId),
                effects.getOrDefault(String.valueOf(rowId), "EFFECT_DENY"),
                row
        );
    }

    private Method method(ProceedingJoinPoint joinPoint) {
        return ((MethodSignature) joinPoint.getSignature()).getMethod();
    }

    private Object resolvePrincipal(String expression, CerbosMethodExpressionEvaluator.Context context) {
        if (!expression.isBlank()) {
            return expressionEvaluator.value(expression, context);
        }
        Object principal = context.variable("principal");
        if (principal != null) {
            return principal;
        }
        Object userContext = context.variable("userContext");
        if (userContext != null) {
            return userContext;
        }
        Object userId = context.variable("userId");
        if (userId != null && beanFactory.containsBean("userContextService")) {
            return invoke(beanFactory.getBean("userContextService"), "load", userId);
        }
        throw new IllegalArgumentException("Cannot resolve Cerbos principal. Provide principal expression or a userId parameter with userContextService.load(...).");
    }

    private String resolveAction(String expression, CerbosMethodExpressionEvaluator.Context context) {
        if (!expression.isBlank()) {
            return String.valueOf(expressionEvaluator.value(expression, context));
        }
        Object action = context.variable("action");
        return action == null ? "view" : String.valueOf(action);
    }

    private Object traceValue(String expression, String resourceKind, String defaultMethod, CerbosMethodExpressionEvaluator.Context context) {
        if (!expression.isBlank()) {
            return expressionEvaluator.value(expression, context);
        }
        Object mapper = beanFactory.getBean(resourceKind + "Mapper");
        return invokeNoArg(mapper, defaultMethod);
    }

    private int number(String expression, CerbosMethodExpressionEvaluator.Context context) {
        Object value = expressionEvaluator.value(expression, context);
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(String.valueOf(value));
    }

    private Set<Object> ids(Object value) {
        return rows(value).stream().collect(LinkedHashSet::new, LinkedHashSet::add, LinkedHashSet::addAll);
    }

    private Object pageInfo(int pageNum, int pageSize, SupplierWithResult supplier) {
        startPage(pageNum, pageSize);
        return pageInfo(supplier.get());
    }

    private void startPage(int pageNum, int pageSize) {
        try {
            Class<?> pageHelper = Class.forName("com.github.pagehelper.PageHelper");
            pageHelper.getMethod("startPage", int.class, int.class).invoke(null, pageNum, pageSize);
        } catch (ClassNotFoundException ignored) {
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot start PageHelper page", exception);
        }
    }

    private Object pageInfo(Object rows) {
        try {
            Class<?> pageInfo = Class.forName("com.github.pagehelper.PageInfo");
            return pageInfo.getConstructor(List.class).newInstance(rows(rows));
        } catch (ClassNotFoundException ignored) {
            return rows;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot create PageInfo", exception);
        }
    }

    private Object invoke(Object target, String methodName, Object arg) {
        for (Method method : target.getClass().getMethods()) {
            if (method.getName().equals(methodName) && method.getParameterCount() == 1) {
                try {
                    return method.invoke(target, arg);
                } catch (ReflectiveOperationException exception) {
                    throw new IllegalStateException("Cannot invoke " + target.getClass().getName() + "." + methodName + "(...)", exception);
                }
            }
        }
        throw new IllegalArgumentException("Cannot find " + target.getClass().getName() + "." + methodName + "(...)");
    }

    private Object invokeNoArg(Object target, String methodName) {
        try {
            return target.getClass().getMethod(methodName).invoke(target);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot invoke " + target.getClass().getName() + "." + methodName + "()", exception);
        }
    }

    private String capitalized(String value) {
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    @SuppressWarnings("unchecked")
    private List<?> rows(Object value) {
        if (value == null) {
            return List.of();
        }
        try {
            Method getList = value.getClass().getMethod("getList");
            Object list = getList.invoke(value);
            if (list instanceof List<?> rows) {
                return rows;
            }
        } catch (ReflectiveOperationException ignored) {
        }
        if (value instanceof List<?> rows) {
            return rows;
        }
        if (value instanceof Iterable<?> iterable) {
            return (List<?>) java.util.stream.StreamSupport.stream(iterable.spliterator(), false).toList();
        }
        return List.of(value);
    }

    private long size(Object value) {
        try {
            Method getTotal = value.getClass().getMethod("getTotal");
            Object total = getTotal.invoke(value);
            if (total instanceof Number number) {
                return number.longValue();
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return rows(value).size();
    }

    @FunctionalInterface
    private interface SupplierWithResult {
        Object get();
    }
}
