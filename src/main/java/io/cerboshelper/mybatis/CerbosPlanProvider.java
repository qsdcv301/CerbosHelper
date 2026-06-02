package io.cerboshelper.mybatis;

import com.fasterxml.jackson.databind.JsonNode;

public interface CerbosPlanProvider {
    JsonNode planResources(Object principal, String resourceKind, String action);
}
