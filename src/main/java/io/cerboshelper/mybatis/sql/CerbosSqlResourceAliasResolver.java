package io.cerboshelper.mybatis.sql;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

public final class CerbosSqlResourceAliasResolver {
    private static final Set<String> SQL_CLAUSE_KEYWORDS = Set.of(
            "where", "join", "left", "right", "full", "inner", "outer", "cross", "on",
            "order", "group", "having", "limit", "offset", "fetch", "union", "except", "intersect"
    );

    public Optional<String> resolve(String sql, String resourceKind) {
        if (sql == null || sql.isBlank() || resourceKind == null || resourceKind.isBlank()) {
            return Optional.empty();
        }
        List<String> tableCandidates = List.of(resourceKind, camelToSnake(resourceKind), resourceKind + "s", camelToSnake(resourceKind) + "s");
        String lowerSql = sql.toLowerCase(Locale.ROOT);
        int depth = 0;
        boolean inSingleQuote = false;
        for (int index = 0; index < sql.length(); index++) {
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
            if (depth != 0) {
                continue;
            }
            if (!startsWithKeyword(lowerSql, index, "from") && !startsWithKeyword(lowerSql, index, "join")) {
                continue;
            }
            TableReference reference = readTableReference(sql, index + 4);
            if (reference != null && tableCandidates.stream().anyMatch(candidate -> candidate.equalsIgnoreCase(reference.tableName()))) {
                return Optional.of(reference.alias());
            }
        }
        return Optional.empty();
    }

    private TableReference readTableReference(String sql, int start) {
        int tableStart = skipWhitespace(sql, start);
        if (tableStart >= sql.length() || sql.charAt(tableStart) == '(') {
            return null;
        }
        Token table = readIdentifier(sql, tableStart);
        if (table == null) {
            return null;
        }
        int aliasStart = skipWhitespace(sql, table.end());
        Token firstAliasToken = readIdentifier(sql, aliasStart);
        if (firstAliasToken == null) {
            return new TableReference(simpleName(table.value()), simpleName(table.value()));
        }
        String alias = firstAliasToken.value();
        if ("as".equalsIgnoreCase(alias)) {
            Token explicitAlias = readIdentifier(sql, skipWhitespace(sql, firstAliasToken.end()));
            if (explicitAlias == null) {
                return new TableReference(simpleName(table.value()), simpleName(table.value()));
            }
            return new TableReference(simpleName(table.value()), unquoteIdentifier(explicitAlias.value()));
        }
        if (SQL_CLAUSE_KEYWORDS.contains(alias.toLowerCase(Locale.ROOT))) {
            return new TableReference(simpleName(table.value()), simpleName(table.value()));
        }
        return new TableReference(simpleName(table.value()), unquoteIdentifier(alias));
    }

    private int skipWhitespace(String value, int start) {
        int index = start;
        while (index < value.length() && Character.isWhitespace(value.charAt(index))) {
            index++;
        }
        return index;
    }

    private Token readIdentifier(String sql, int start) {
        int index = skipWhitespace(sql, start);
        if (index >= sql.length()) {
            return null;
        }
        char first = sql.charAt(index);
        if (first == '"' || first == '`') {
            char quote = first;
            int end = index + 1;
            while (end < sql.length() && sql.charAt(end) != quote) {
                end++;
            }
            if (end >= sql.length()) {
                return null;
            }
            return new Token(sql.substring(index, end + 1), end + 1);
        }
        if (!isIdentifierStart(first)) {
            return null;
        }
        int end = index + 1;
        while (end < sql.length()) {
            char current = sql.charAt(end);
            if (!isIdentifierPart(current) && current != '.') {
                break;
            }
            end++;
        }
        return new Token(sql.substring(index, end), end);
    }

    private String simpleName(String tableName) {
        String unquoted = unquoteIdentifier(tableName);
        int separator = unquoted.lastIndexOf('.');
        return separator >= 0 ? unquoted.substring(separator + 1) : unquoted;
    }

    private String unquoteIdentifier(String value) {
        if (value == null || value.length() < 2) {
            return value;
        }
        char first = value.charAt(0);
        char last = value.charAt(value.length() - 1);
        if ((first == '"' && last == '"') || (first == '`' && last == '`')) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private boolean startsWithKeyword(String lowerSql, int index, String keyword) {
        return lowerSql.startsWith(keyword, index) && isKeywordBoundary(lowerSql, index, keyword.length());
    }

    private boolean isKeywordBoundary(String sql, int start, int length) {
        int before = start - 1;
        int after = start + length;
        return (before < 0 || !isIdentifierPart(sql.charAt(before))) && (after >= sql.length() || !isIdentifierPart(sql.charAt(after)));
    }

    private boolean isIdentifierStart(char value) {
        return Character.isLetter(value) || value == '_' || value == '$';
    }

    private boolean isIdentifierPart(char value) {
        return Character.isLetterOrDigit(value) || value == '_';
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

    private record Token(String value, int end) {
    }

    private record TableReference(String tableName, String alias) {
    }
}
