package io.cerboshelper.mybatis;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

public record CerbosPlanDebugResult(
        Object principal,
        String action,
        JsonNode cerbosPlan,
        String whereSql,
        Map<String, Object> params,
        boolean denied
) {
}
