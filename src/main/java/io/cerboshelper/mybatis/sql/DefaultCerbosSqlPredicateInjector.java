package io.cerboshelper.mybatis.sql;

import java.util.Optional;

public class DefaultCerbosSqlPredicateInjector implements CerbosSqlPredicateInjector {
    private static final String CERBOS_ALIAS_BASE = "__cerbos_scope";

    @Override
    public Optional<String> predicateAlias(String sql, String resourceKind) {
        return Optional.of(aliasFor(sql));
    }

    @Override
    public CerbosSqlInjectionResult inject(String sql, String predicate) {
        if (predicate == null || predicate.isBlank()) {
            return new CerbosSqlInjectionResult(sql, countPlaceholders(sql));
        }

        String originalSql = stripTrailingSemicolon(sql.strip());
        String alias = aliasFor(originalSql);
        String scopedSql = "SELECT " + alias + ".* FROM (" + originalSql + ") " + alias
                + " WHERE (" + predicate + ")";
        return new CerbosSqlInjectionResult(scopedSql, countPlaceholders(originalSql));
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

    private String stripTrailingSemicolon(String sql) {
        return sql.endsWith(";") ? sql.substring(0, sql.length() - 1).stripTrailing() : sql;
    }

    private String aliasFor(String sql) {
        String candidate = CERBOS_ALIAS_BASE;
        int suffix = 0;
        while (containsIdentifier(sql, candidate)) {
            suffix++;
            candidate = CERBOS_ALIAS_BASE + "_" + suffix;
        }
        return candidate;
    }

    private boolean containsIdentifier(String sql, String identifier) {
        if (sql == null || sql.isBlank()) {
            return false;
        }
        boolean inSingleQuote = false;
        for (int index = 0; index <= sql.length() - identifier.length(); index++) {
            char current = sql.charAt(index);
            if (current == '\'' && (index == 0 || sql.charAt(index - 1) != '\\')) {
                inSingleQuote = !inSingleQuote;
            }
            if (!inSingleQuote && sql.startsWith(identifier, index) && isIdentifierBoundary(sql, index, identifier.length())) {
                return true;
            }
        }
        return false;
    }

    private boolean isIdentifierBoundary(String sql, int start, int length) {
        int before = start - 1;
        int after = start + length;
        return (before < 0 || !isIdentifierPart(sql.charAt(before)))
                && (after >= sql.length() || !isIdentifierPart(sql.charAt(after)));
    }

    private boolean isIdentifierPart(char value) {
        return Character.isLetterOrDigit(value) || value == '_';
    }
}
