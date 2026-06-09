package io.cerboshelper.mybatis.check;

public record CerbosDeniedDecision(CerbosFailureReason reason, String action, Object principal, Object resource, String principalDescription, String resourceDescription) {
    public CerbosDeniedDecision(String action, Object principal, Object resource, String principalDescription, String resourceDescription) {
        this(CerbosFailureReason.DENIED, action, principal, resource, principalDescription, resourceDescription);
    }

    public String message() {
        return "Cerbos denied reason=" + reason
                + ", action=" + action
                + ", principal=" + principalDescription
                + ", resource=" + resourceDescription;
    }
}
