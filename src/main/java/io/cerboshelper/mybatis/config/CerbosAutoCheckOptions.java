package io.cerboshelper.mybatis.config;

import java.util.List;

public final class CerbosAutoCheckOptions {
    private boolean enabled = true;
    private List<String> includeClassNamePatterns = List.of();
    private List<String> excludeClassNamePatterns = List.of();
    private List<String> includeMethodNamePatterns = List.of();
    private List<String> excludeMethodNamePatterns = List.of();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<String> getIncludeClassNamePatterns() {
        return includeClassNamePatterns;
    }

    public void setIncludeClassNamePatterns(List<String> includeClassNamePatterns) {
        this.includeClassNamePatterns = copyOrEmpty(includeClassNamePatterns);
    }

    public List<String> getExcludeClassNamePatterns() {
        return excludeClassNamePatterns;
    }

    public void setExcludeClassNamePatterns(List<String> excludeClassNamePatterns) {
        this.excludeClassNamePatterns = copyOrEmpty(excludeClassNamePatterns);
    }

    public List<String> getIncludeMethodNamePatterns() {
        return includeMethodNamePatterns;
    }

    public void setIncludeMethodNamePatterns(List<String> includeMethodNamePatterns) {
        this.includeMethodNamePatterns = copyOrEmpty(includeMethodNamePatterns);
    }

    public List<String> getExcludeMethodNamePatterns() {
        return excludeMethodNamePatterns;
    }

    public void setExcludeMethodNamePatterns(List<String> excludeMethodNamePatterns) {
        this.excludeMethodNamePatterns = copyOrEmpty(excludeMethodNamePatterns);
    }

    private static List<String> copyOrEmpty(List<String> values) {
        return values == null || values.isEmpty() ? List.of() : List.copyOf(values);
    }
}
