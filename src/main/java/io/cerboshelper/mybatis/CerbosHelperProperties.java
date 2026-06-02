package io.cerboshelper.mybatis;

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
}
