package io.cerboshelper.mybatis.config;

import java.util.ArrayList;
import java.util.List;

public final class CerbosMethodRuleOptions {
    private final List<ScopeRule> scopeRules = new ArrayList<>();
    private final List<CheckRule> checkRules = new ArrayList<>();
    private List<String> excludeMethodNamePrefixes = List.of();
    private List<String> excludeMethodNames = List.of();
    private List<String> excludeMethodNameContains = List.of();

    public CerbosMethodRuleOptions scope(String action, String... methodNamePrefixes) {
        scopeRules.add(new ScopeRule(requireAction(action), copyVarargs(methodNamePrefixes)));
        return this;
    }

    public CerbosMethodRuleOptions before(String action, String... methodNamePrefixes) {
        checkRules.add(new CheckRule(requireAction(action), copyVarargs(methodNamePrefixes)));
        return this;
    }

    public CerbosMethodRuleOptions excludePrefixes(String... methodNamePrefixes) {
        excludeMethodNamePrefixes = copyVarargs(methodNamePrefixes);
        return this;
    }

    public CerbosMethodRuleOptions excludeNames(String... methodNames) {
        excludeMethodNames = copyVarargs(methodNames);
        return this;
    }

    public CerbosMethodRuleOptions excludeContains(String... methodNameParts) {
        excludeMethodNameContains = copyVarargs(methodNameParts);
        return this;
    }

    public List<ScopeRule> scopeRules() {
        return List.copyOf(scopeRules);
    }

    public List<CheckRule> checkRules() {
        return List.copyOf(checkRules);
    }

    public List<String> excludeMethodNamePrefixes() {
        return excludeMethodNamePrefixes;
    }

    public List<String> excludeMethodNames() {
        return excludeMethodNames;
    }

    public List<String> excludeMethodNameContains() {
        return excludeMethodNameContains;
    }

    public boolean isExcluded(String methodName) {
        if (methodName == null || methodName.isBlank()) {
            return true;
        }
        if (excludeMethodNames.contains(methodName)) {
            return true;
        }
        for (String prefix : excludeMethodNamePrefixes) {
            if (methodName.startsWith(prefix)) {
                return true;
            }
        }
        for (String part : excludeMethodNameContains) {
            if (methodName.contains(part)) {
                return true;
            }
        }
        return false;
    }

    private String requireAction(String action) {
        if (action == null || action.isBlank()) {
            throw new IllegalArgumentException("action must not be blank");
        }
        return action;
    }

    private List<String> copyVarargs(String... values) {
        if (values == null || values.length == 0) {
            return List.of();
        }
        List<String> copy = new ArrayList<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                copy.add(value);
            }
        }
        return List.copyOf(copy);
    }

    public record ScopeRule(String action, List<String> methodNamePrefixes) {
    }

    public record CheckRule(String action, List<String> methodNamePrefixes) {
    }
}
