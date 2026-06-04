package io.cerboshelper.mybatis.auth;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

public interface CerbosAuthorizationClient {
    JsonNode planResources(Object principal, String resourceKind, String action);

    boolean isAllowed(Object principal, Object resource, String action);

    Map<String, String> checkResources(Object principal, List<?> resources, String action);

    JsonNode checkResourcesRaw(Object principal, List<?> resources, String action);
}
