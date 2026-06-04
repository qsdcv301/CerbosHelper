package io.cerboshelper.mybatis.auth;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import dev.cerbos.sdk.CerbosBlockingClient;
import dev.cerbos.sdk.CerbosClientBuilder;
import dev.cerbos.sdk.CheckResourcesResult;
import dev.cerbos.sdk.PlanResourcesResult;
import dev.cerbos.sdk.builders.AttributeValue;
import dev.cerbos.sdk.builders.Principal;
import dev.cerbos.sdk.builders.Resource;
import dev.cerbos.sdk.builders.ResourceAction;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CerbosSdkAuthorizationClient implements CerbosAuthorizationClient {
    private final CerbosBlockingClient client;
    private final CerbosPayloadMapper payloadMapper;
    private final CerbosHelperProperties properties;
    private final ObjectMapper objectMapper;

    public CerbosSdkAuthorizationClient(CerbosHelperProperties properties, CerbosPayloadMapper payloadMapper) {
        this(properties, payloadMapper, new ObjectMapper());
    }

    CerbosSdkAuthorizationClient(CerbosHelperProperties properties, CerbosPayloadMapper payloadMapper, ObjectMapper objectMapper) {
        this.properties = properties;
        this.payloadMapper = payloadMapper;
        this.objectMapper = objectMapper;
        this.client = buildClient(properties);
    }

    @Override
    public JsonNode planResources(Object principal, String resourceKind, String action) {
        PlanResourcesResult result = client.plan(
                principal(principal),
                Resource.newInstance(resourceKind).withPolicyVersion(properties.getPolicyVersion()),
                action
        );
        return json(result);
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

        CheckResourcesResult result = batch(principal, resources, action);
        Map<String, String> decisions = new LinkedHashMap<>();
        for (Object resource : resources) {
            String resourceId = payloadMapper.resourceId(resource);
            boolean allowed = result.find(resourceId).map(check -> check.isAllowed(action)).orElse(false);
            decisions.put(resourceId, allowed ? "EFFECT_ALLOW" : "EFFECT_DENY");
        }
        return decisions;
    }

    @Override
    public JsonNode checkResourcesRaw(Object principal, List<?> resources, String action) {
        return json(batch(principal, resources, action));
    }

    private CheckResourcesResult batch(Object principal, List<?> resources, String action) {
        ResourceAction[] resourceActions = resources.stream()
                .map(resource -> resourceAction(resource, action))
                .toArray(ResourceAction[]::new);
        return client.batch(principal(principal))
                .addResources(resourceActions)
                .check();
    }

    private Principal principal(Object source) {
        Map<String, Object> payload = payloadMapper.principalPayload(source);
        String id = String.valueOf(payload.get("id"));
        @SuppressWarnings("unchecked")
        List<String> roles = (List<String>) payload.get("roles");
        @SuppressWarnings("unchecked")
        Map<String, Object> attr = (Map<String, Object>) payload.get("attr");
        return Principal.newInstance(id, roles.toArray(String[]::new))
                .withPolicyVersion(String.valueOf(payload.get("policyVersion")))
                .withAttributes(attributes(attr));
    }

    private ResourceAction resourceAction(Object source, String action) {
        Map<String, Object> payload = payloadMapper.resourcePayload(source);
        @SuppressWarnings("unchecked")
        Map<String, Object> attr = (Map<String, Object>) payload.get("attr");
        return ResourceAction.newInstance(String.valueOf(payload.get("kind")), String.valueOf(payload.get("id")))
                .withPolicyVersion(String.valueOf(payload.get("policyVersion")))
                .withAttributes(attributes(attr))
                .withActions(action);
    }

    private Map<String, AttributeValue> attributes(Map<String, Object> values) {
        Map<String, AttributeValue> attributes = new LinkedHashMap<>();
        values.forEach((key, value) -> {
            if (value != null) {
                attributes.put(key, attribute(value));
            }
        });
        return attributes;
    }

    private AttributeValue attribute(Object value) {
        if (value instanceof AttributeValue attributeValue) {
            return attributeValue;
        }
        if (value instanceof String stringValue) {
            return AttributeValue.stringValue(stringValue);
        }
        if (value instanceof Character characterValue) {
            return AttributeValue.stringValue(characterValue.toString());
        }
        if (value instanceof Number numberValue) {
            return AttributeValue.doubleValue(numberValue.doubleValue());
        }
        if (value instanceof Boolean booleanValue) {
            return AttributeValue.boolValue(booleanValue);
        }
        if (value instanceof Map<?, ?> mapValue) {
            Map<String, AttributeValue> nested = new LinkedHashMap<>();
            mapValue.forEach((key, nestedValue) -> {
                if (nestedValue != null) {
                    nested.put(String.valueOf(key), attribute(nestedValue));
                }
            });
            return AttributeValue.mapValue(nested);
        }
        if (value instanceof Iterable<?> iterableValue) {
            List<AttributeValue> nested = new java.util.ArrayList<>();
            iterableValue.forEach(item -> {
                if (item != null) {
                    nested.add(attribute(item));
                }
            });
            return AttributeValue.listValue(nested);
        }
        return AttributeValue.stringValue(value.toString());
    }

    private JsonNode json(PlanResourcesResult result) {
        try {
            return objectMapper.readTree(JsonFormat.printer().print(result.getRaw()));
        } catch (InvalidProtocolBufferException | JsonProcessingException exception) {
            throw new IllegalStateException("Cannot convert Cerbos SDK plan response to JSON", exception);
        }
    }

    private JsonNode json(CheckResourcesResult result) {
        try {
            return objectMapper.readTree(JsonFormat.printer().print(result.getRaw()));
        } catch (InvalidProtocolBufferException | JsonProcessingException exception) {
            throw new IllegalStateException("Cannot convert Cerbos SDK check response to JSON", exception);
        }
    }

    private CerbosBlockingClient buildClient(CerbosHelperProperties properties) {
        try {
            CerbosClientBuilder builder = new CerbosClientBuilder(properties.getTarget())
                    .withTimeout(properties.getTimeout());
            if (properties.isPlaintext()) {
                builder.withPlaintext();
            } else if (properties.isInsecure()) {
                builder.withInsecure();
            }
            return builder.buildBlockingClient();
        } catch (CerbosClientBuilder.InvalidClientConfigurationException exception) {
            throw new IllegalStateException("Cannot create Cerbos SDK client for target=" + properties.getTarget(), exception);
        }
    }
}
