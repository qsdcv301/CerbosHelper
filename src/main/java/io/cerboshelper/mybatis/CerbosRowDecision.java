package io.cerboshelper.mybatis;

public record CerbosRowDecision(
        Object rowId,
        Object title,
        boolean matchedByPlanSql,
        String checkEffect,
        Object row
) {
}
