package io.cerboshelper.mybatis;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "cerboshelper")
public class CerbosHelperProperties {
    private String baseUrl = "http://localhost:3592";
    private String policyVersion = "default";
    private List<String> principalRoles = List.of("authenticated");

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
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
}
