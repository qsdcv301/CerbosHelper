package io.cerboshelper.mybatis;

import io.cerboshelper.mybatis.sql.CerbosSqlInjectionResult;
import io.cerboshelper.mybatis.sql.DefaultCerbosSqlPredicateInjector;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CerbosSqlPredicateInjectorTest {
    private final DefaultCerbosSqlPredicateInjector injector = new DefaultCerbosSqlPredicateInjector();

    @Test
    void wrapsOriginalSqlWithCerbosAlias() {
        CerbosSqlInjectionResult result = injector.inject(
                "SELECT * FROM documents document WHERE document.deleted = ? ORDER BY document.id",
                "cb.owner_by = ?"
        );

        assertEquals("SELECT cb.* FROM (SELECT * FROM documents document WHERE document.deleted = ? ORDER BY document.id) cb WHERE (cb.owner_by = ?)", result.sql());
        assertEquals(1, result.parameterInsertionIndex());
    }

    @Test
    void wrapsSqlWithoutOriginalWhere() {
        CerbosSqlInjectionResult result = injector.inject(
                "SELECT * FROM documents document ORDER BY document.id",
                "cb.owner_by = ?"
        );

        assertEquals("SELECT cb.* FROM (SELECT * FROM documents document ORDER BY document.id) cb WHERE (cb.owner_by = ?)", result.sql());
        assertEquals(0, result.parameterInsertionIndex());
    }

    @Test
    void usesCbAsPredicateAlias() {
        assertEquals("cb", injector.predicateAlias("SELECT * FROM documents", "document").orElseThrow());
    }

    @Test
    void insertsCerbosParametersAfterOriginalSqlPlaceholders() {
        CerbosSqlInjectionResult result = injector.inject(
                "SELECT * FROM documents document WHERE document.title = '?' AND document.status = ?",
                "cb.owner_by = ?"
        );

        assertEquals(1, result.parameterInsertionIndex());
    }

}
