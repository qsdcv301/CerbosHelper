package io.cerboshelper.mybatis.sql;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CerbosPlanToSqlConverter {
    private final CerbosResourceColumnRegistry columnRegistry;

    public CerbosPlanToSqlConverter(CerbosResourceColumnRegistry columnRegistry) {
        this.columnRegistry = columnRegistry;
    }

    public CerbosSqlFilter convertNamed(String resourceKind, JsonNode plan) {
        return convert(resourceKind, plan, PlaceholderMode.NAMED);
    }

    public CerbosSqlFilter convertPositional(String resourceKind, JsonNode plan) {
        return convert(resourceKind, plan, PlaceholderMode.POSITIONAL);
    }

    private CerbosSqlFilter convert(String resourceKind, JsonNode plan, PlaceholderMode placeholderMode) {
        JsonNode filter = plan.path("filter");
        String kind = filter.path("kind").asText("");

        if ("KIND_ALWAYS_ALLOWED".equals(kind) || "ALWAYS_ALLOWED".equals(kind)) {
            return CerbosSqlFilter.allowAll();
        }
        if ("KIND_ALWAYS_DENIED".equals(kind) || "ALWAYS_DENIED".equals(kind)) {
            return CerbosSqlFilter.denyAll();
        }
        if (!"KIND_CONDITIONAL".equals(kind) && !"CONDITIONAL".equals(kind)) {
            throw new IllegalArgumentException("Unsupported Cerbos plan kind: " + kind);
        }

        Context context = new Context(resourceKind, placeholderMode);
        String whereSql = expressionNode(filter.path("condition"), context);
        return new CerbosSqlFilter(false, whereSql, context.namedParams, context.positionalParams);
    }

    private String expressionNode(JsonNode node, Context context) {
        if (node.has("expression")) {
            return expressionNode(node.path("expression"), context);
        }
        if (node.has("variable") || node.has("value")) {
            return operand(node, context).sql;
        }

        String operator = node.path("operator").asText();
        JsonNode operands = node.path("operands");
        if (!operands.isArray()) {
            throw new IllegalArgumentException("Cerbos expression has no operands: " + node);
        }

        return switch (operator) {
            case "and" -> join("AND", operands, context);
            case "or" -> join("OR", operands, context);
            case "not" -> notExpression(operands, context);
            case "eq" -> binary("=", operands, context);
            case "ne", "neq" -> binary("!=", operands, context);
            case "lt" -> binary("<", operands, context);
            case "le", "lte" -> binary("<=", operands, context);
            case "gt" -> binary(">", operands, context);
            case "ge", "gte" -> binary(">=", operands, context);
            case "in" -> inExpression(operands, context);
            default -> throw new IllegalArgumentException("Unsupported Cerbos operator: " + operator);
        };
    }

    private String join(String sqlOperator, JsonNode operands, Context context) {
        if (operands.isEmpty()) {
            return "AND".equals(sqlOperator) ? "1 = 1" : "1 = 0";
        }
        List<String> parts = new ArrayList<>();
        operands.forEach(operand -> parts.add("(" + expressionNode(operand, context) + ")"));
        return String.join(" " + sqlOperator + " ", parts);
    }

    private String notExpression(JsonNode operands, Context context) {
        if (operands.size() != 1) {
            throw new IllegalArgumentException("NOT operator requires exactly one operand: " + operands);
        }
        JsonNode operandNode = operands.get(0);
        if (operandNode.has("variable") || operandNode.has("value")) {
            return "NOT (" + operand(operandNode, context).sql + ")";
        }
        return "NOT (" + expressionNode(operandNode, context) + ")";
    }

    private String binary(String sqlOperator, JsonNode operands, Context context) {
        if (operands.size() != 2) {
            throw new IllegalArgumentException("Binary operator requires exactly two operands: " + operands);
        }
        Operand left = operand(operands.get(0), context);
        Operand right = operand(operands.get(1), context);

        if (left.column == null && right.column == null) {
            throw new IllegalArgumentException("At least one operand must be a resource column: " + operands);
        }
        if (left.column != null && right.column != null) {
            throw new IllegalArgumentException("Column-to-column comparisons are not supported: " + operands);
        }

        if (isNullLiteral(right) && left.column != null) {
            return left.sql + nullSql(sqlOperator);
        }
        if (isNullLiteral(left) && right.column != null) {
            return right.sql + nullSql(sqlOperator);
        }

        if (left.column != null) {
            return left.sql + " " + sqlOperator + " " + right.sql;
        }
        return right.sql + " " + sqlOperator + " " + left.sql;
    }

    private boolean isNullLiteral(Operand operand) {
        return operand.column == null && operand.values != null && operand.values.size() == 1 && operand.values.get(0) == null;
    }

    private String nullSql(String sqlOperator) {
        if ("=".equals(sqlOperator)) {
            return " IS NULL";
        }
        if ("!=".equals(sqlOperator)) {
            return " IS NOT NULL";
        }
        throw new IllegalArgumentException("Only equality operators can compare resource columns with null");
    }

    private String inExpression(JsonNode operands, Context context) {
        if (operands.size() != 2) {
            throw new IllegalArgumentException("IN operator requires exactly two operands: " + operands);
        }

        Operand left = operand(operands.get(0), context);
        Operand right = operand(operands.get(1), context);

        if (left.column != null && right.values != null) {
            if (right.values.isEmpty()) {
                return "1 = 0";
            }
            return left.sql + " IN (" + String.join(", ", right.placeholders) + ")";
        }
        if (right.column != null && left.values != null) {
            if (left.values.isEmpty()) {
                return "1 = 0";
            }
            return right.sql + " IN (" + String.join(", ", left.placeholders) + ")";
        }

        throw new IllegalArgumentException("IN requires one resource column and one literal list: " + operands);
    }

    private Operand operand(JsonNode node, Context context) {
        if (node.has("expression")) {
            return new Operand("(" + expressionNode(node.path("expression"), context) + ")", null, null, null);
        }
        if (node.has("variable")) {
            String variable = node.path("variable").asText();
            String column = columnRegistry.columnFor(context.resourceKind, variable)
                    .orElseThrow(() -> new IllegalArgumentException("Unsupported or unsafe Cerbos variable: " + variable));
            return new Operand(column, column, null, null);
        }
        if (node.has("value")) {
            return literal(node.path("value"), context);
        }
        throw new IllegalArgumentException("Unsupported Cerbos operand: " + node);
    }

    private Operand literal(JsonNode valueNode, Context context) {
        if (valueNode.isArray()) {
            List<Object> values = new ArrayList<>();
            List<String> placeholders = new ArrayList<>();
            valueNode.forEach(item -> {
                Object value = jsonValue(item);
                String placeholder = context.addParam(value);
                values.add(value);
                placeholders.add(placeholder);
            });
            return new Operand(null, null, values, placeholders);
        }

        Object value = jsonValue(valueNode);
        if (value == null) {
            return new Operand("NULL", null, Collections.singletonList(null), List.of());
        }
        String placeholder = context.addParam(value);
        return new Operand(placeholder, null, Collections.singletonList(value), List.of(placeholder));
    }

    private Object jsonValue(JsonNode node) {
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isIntegralNumber()) {
            return node.asLong();
        }
        if (node.isFloatingPointNumber()) {
            return node.asDouble();
        }
        if (node.isBoolean()) {
            return node.asBoolean();
        }
        if (node.isNull()) {
            return null;
        }
        throw new IllegalArgumentException("Unsupported literal value in Cerbos plan: " + node);
    }

    private record Operand(String sql, String column, List<Object> values, List<String> placeholders) {
    }

    private enum PlaceholderMode {
        NAMED,
        POSITIONAL
    }

    private static final class Context {
        private final String resourceKind;
        private final PlaceholderMode placeholderMode;
        private final Map<String, Object> namedParams = new LinkedHashMap<>();
        private final List<Object> positionalParams = new ArrayList<>();
        private int nextParameterIndex = 0;

        private Context(String resourceKind, PlaceholderMode placeholderMode) {
            this.resourceKind = resourceKind;
            this.placeholderMode = placeholderMode;
        }

        private String addParam(Object value) {
            if (placeholderMode == PlaceholderMode.POSITIONAL) {
                positionalParams.add(value);
                return "?";
            }
            String name = "cp" + nextParameterIndex++;
            namedParams.put(name, value);
            return "#{params." + name + "}";
        }
    }
}
