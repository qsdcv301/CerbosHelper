package io.cerboshelper.mybatis;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

public record CerbosRowTraceResult(
        Object principal,
        String action,
        JsonNode cerbosPlan,
        String whereSql,
        Map<String, Object> params,
        boolean denied,
        Object candidateRows,
        Object sqlMatchedRows,
        List<CerbosRowDecision> rowDecisions
) {
}
