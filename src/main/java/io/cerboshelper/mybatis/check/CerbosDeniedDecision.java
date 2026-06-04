package io.cerboshelper.mybatis.check;

public record CerbosDeniedDecision(String action, Object principal, Object resource, String principalDescription, String resourceDescription) {
    public String message() {
        return "Cerbos denied action=" + action
                + ", principal=" + principalDescription
                + ", resource=" + resourceDescription;
    }
}
