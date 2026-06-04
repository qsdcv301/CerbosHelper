package io.cerboshelper.mybatis.sql;

import java.util.Locale;

public class DefaultCerbosSqlPredicateInjector implements CerbosSqlPredicateInjector {
    @Override
    public CerbosSqlInjectionResult inject(String sql, String predicate) {
        if (predicate == null || predicate.isBlank()) {
            return new CerbosSqlInjectionResult(sql, countPlaceholders(sql));
        }

        int orderByIndex = findTopLevelOrderBy(sql);
        String selectPart = orderByIndex >= 0 ? sql.substring(0, orderByIndex) : sql;
        String suffix = orderByIndex >= 0 ? sql.substring(orderByIndex) : "";
        String trimmedSelect = selectPart.stripTrailing();
        int insertionIndex = countPlaceholders(trimmedSelect);

        if (hasTopLevelWhere(trimmedSelect)) {
            return new CerbosSqlInjectionResult(trimmedSelect + " AND (" + predicate + ") " + suffix.stripLeading(), insertionIndex);
        }
        return new CerbosSqlInjectionResult(trimmedSelect + " WHERE (" + predicate + ") " + suffix.stripLeading(), insertionIndex);
    }

    private int countPlaceholders(String sql) {
        int count = 0;
        boolean inSingleQuote = false;
        for (int index = 0; index < sql.length(); index++) {
            char current = sql.charAt(index);
            if (current == '\'' && (index == 0 || sql.charAt(index - 1) != '\\')) {
                inSingleQuote = !inSingleQuote;
            }
            if (!inSingleQuote && current == '?') {
                count++;
            }
        }
        return count;
    }

    private boolean hasTopLevelWhere(String sql) {
        return findTopLevelKeyword(sql, "where", 0) >= 0;
    }

    private int findTopLevelOrderBy(String sql) {
        int index = 0;
        while (index >= 0 && index < sql.length()) {
            int orderIndex = findTopLevelKeyword(sql, "order", index);
            if (orderIndex < 0) {
                return -1;
            }
            int afterOrder = orderIndex + "order".length();
            int byIndex = findTopLevelKeyword(sql, "by", afterOrder);
            if (byIndex >= 0 && sql.substring(afterOrder, byIndex).isBlank()) {
                return orderIndex;
            }
            index = afterOrder;
        }
        return -1;
    }

    private int findTopLevelKeyword(String sql, String keyword, int start) {
        String lowerSql = sql.toLowerCase(Locale.ROOT);
        int depth = 0;
        boolean inSingleQuote = false;
        for (int index = start; index <= sql.length() - keyword.length(); index++) {
            char current = sql.charAt(index);
            if (current == '\'' && (index == 0 || sql.charAt(index - 1) != '\\')) {
                inSingleQuote = !inSingleQuote;
            }
            if (inSingleQuote) {
                continue;
            }
            if (current == '(') {
                depth++;
                continue;
            }
            if (current == ')') {
                depth = Math.max(0, depth - 1);
                continue;
            }
            if (depth == 0 && lowerSql.startsWith(keyword, index) && isKeywordBoundary(sql, index, keyword.length())) {
                return index;
            }
        }
        return -1;
    }

    private boolean isKeywordBoundary(String sql, int start, int length) {
        int before = start - 1;
        int after = start + length;
        return (before < 0 || !isIdentifierPart(sql.charAt(before))) && (after >= sql.length() || !isIdentifierPart(sql.charAt(after)));
    }

    private boolean isIdentifierPart(char value) {
        return Character.isLetterOrDigit(value) || value == '_';
    }
}
