package io.cerboshelper.mybatis.sql;

import java.util.List;
import java.util.Map;

public record CerbosSqlFilter(boolean denied, String whereSql, Map<String, Object> namedParams, List<Object> positionalParams) {
    public static CerbosSqlFilter allowAll() {
        return new CerbosSqlFilter(false, "", Map.of(), List.of());
    }

    public static CerbosSqlFilter denyAll() {
        return new CerbosSqlFilter(true, "1 = 0", Map.of(), List.of());
    }
}
