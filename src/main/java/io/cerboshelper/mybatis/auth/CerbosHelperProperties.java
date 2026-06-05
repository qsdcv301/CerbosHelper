package io.cerboshelper.mybatis.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;
import java.util.List;

@ConfigurationProperties(prefix = "cerboshelper")
public class CerbosHelperProperties {
    private String baseUrl = "http://localhost:3592";
    private String target = "";
    private boolean plaintext = true;
    private boolean insecure = false;
    private Duration timeout = Duration.ofSeconds(1);
    private String policyVersion = "default";
    private List<String> principalRoles = List.of("authenticated");
    private Check check = new Check();

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getTarget() {
        if (target != null && !target.isBlank()) {
            return target;
        }
        return targetFromBaseUrl();
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public boolean isPlaintext() {
        return plaintext;
    }

    public void setPlaintext(boolean plaintext) {
        this.plaintext = plaintext;
    }

    public boolean isInsecure() {
        return insecure;
    }

    public void setInsecure(boolean insecure) {
        this.insecure = insecure;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(Duration timeout) {
        this.timeout = timeout == null ? Duration.ofSeconds(1) : timeout;
    }

    public String getPolicyVersion() {
        return policyVersion;
    }

    public void setPolicyVersion(String policyVersion) {
        this.policyVersion = policyVersion;
    }

    public List<String> getPrincipalRoles() {
        return principalRoles;
    }

    public void setPrincipalRoles(List<String> principalRoles) {
        this.principalRoles = principalRoles == null || principalRoles.isEmpty()
                ? List.of("authenticated")
                : List.copyOf(principalRoles);
    }

    public Check getCheck() {
        return check;
    }

    public void setCheck(Check check) {
        this.check = check == null ? new Check() : check;
    }

    private String targetFromBaseUrl() {
        try {
            URI uri = URI.create(baseUrl);
            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                return baseUrl;
            }
            int port = uri.getPort();
            int grpcPort = port == 3592 || port < 0 ? 3593 : port;
            return host + ":" + grpcPort;
        } catch (IllegalArgumentException exception) {
            return baseUrl;
        }
    }

    public static final class Check {
        private Auto auto = new Auto();

        public Auto getAuto() {
            return auto;
        }

        public void setAuto(Auto auto) {
            this.auto = auto == null ? new Auto() : auto;
        }
    }

    public static final class Auto {
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
}
