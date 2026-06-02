package io.cerboshelper.mybatis;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CerbosHttpAuthorizationClient implements CerbosAuthorizationClient {
    private final RestClient restClient;
    private final CerbosPayloadMapper payloadMapper;
    private final CerbosHelperProperties properties;

    public CerbosHttpAuthorizationClient(CerbosHelperProperties properties, CerbosPayloadMapper payloadMapper) {
        this.properties = properties;
        this.payloadMapper = payloadMapper;
        this.restClient = RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .build();
    }

    @Override
    public JsonNode planResources(Object principal, String resourceKind, String action) {
        Map<String, Object> body = Map.of(
                "requestId", "plan-" + principalId(principal) + "-" + action,
                "action", action,
                "resource", Map.of(
                        "kind", resourceKind,
                        "policyVersion", properties.getPolicyVersion()
                ),
                "principal", payloadMapper.principalPayload(principal)
        );

        return restClient.post()
                .uri("/api/plan/resources")
                .body(body)
                .retrieve()
                .body(JsonNode.class);
    }

    @Override
    public boolean isAllowed(Object principal, Object resource, String action) {
        return "EFFECT_ALLOW".equals(checkResources(principal, List.of(resource), action).get(payloadMapper.resourceId(resource)));
    }

    @Override
    public Map<String, String> checkResources(Object principal, List<?> resources, String action) {
        if (resources.isEmpty()) {
            return Map.of();
        }

        JsonNode response = checkResourcesRaw(principal, resources, action);
        Map<String, String> results = new LinkedHashMap<>();
        for (int index = 0; index < resources.size(); index++) {
            String resourceId = payloadMapper.resourceId(resources.get(index));
            JsonNode effect = response.path("results").path(index).path("actions").path(action);
            results.put(resourceId, effect.isTextual() ? effect.asText() : "EFFECT_DENY");
        }
        return results;
    }

    @Override
    public JsonNode checkResourcesRaw(Object principal, List<?> resources, String action) {
        List<Map<String, Object>> resourceRequests = resources.stream()
                .map(resource -> Map.of(
                        "resource", payloadMapper.resourcePayload(resource),
                        "actions", List.of(action)
                ))
                .toList();

        Map<String, Object> body = Map.of(
                "requestId", "check-" + principalId(principal) + "-" + action,
                "principal", payloadMapper.principalPayload(principal),
                "resources", resourceRequests
        );

        return restClient.post()
                .uri("/api/check/resources")
                .body(body)
                .retrieve()
                .body(JsonNode.class);
    }

    private String principalId(Object principal) {
        return String.valueOf(payloadMapper.principalPayload(principal).get("id"));
    }
}
