package io.cerboshelper.mybatis;

import io.cerboshelper.mybatis.sql.CerbosSqlInjectionResult;
import io.cerboshelper.mybatis.sql.DefaultCerbosSqlPredicateInjector;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CerbosSqlPredicateInjectorTest {
    private final DefaultCerbosSqlPredicateInjector injector = new DefaultCerbosSqlPredicateInjector();

    @Test
    void appendsPredicateBeforeTopLevelOrderBy() {
        CerbosSqlInjectionResult result = injector.inject(
                "SELECT * FROM documents document WHERE document.deleted = ? ORDER BY document.id",
                "document.company_id = ?"
        );

        assertEquals("SELECT * FROM documents document WHERE document.deleted = ? AND (document.company_id = ?) ORDER BY document.id", result.sql());
        assertEquals(1, result.parameterInsertionIndex());
    }

    @Test
    void addsWhereWhenQueryHasNoTopLevelWhere() {
        CerbosSqlInjectionResult result = injector.inject(
                "SELECT * FROM documents document ORDER BY document.id",
                "document.company_id = ?"
        );

        assertEquals("SELECT * FROM documents document WHERE (document.company_id = ?) ORDER BY document.id", result.sql());
        assertEquals(0, result.parameterInsertionIndex());
    }

}
