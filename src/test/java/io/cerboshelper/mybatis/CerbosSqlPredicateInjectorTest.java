package io.cerboshelper.mybatis;

import io.cerboshelper.mybatis.sql.CerbosSqlInjectionResult;
import io.cerboshelper.mybatis.sql.DefaultCerbosSqlPredicateInjector;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CerbosSqlPredicateInjectorTest {
    private final DefaultCerbosSqlPredicateInjector injector = new DefaultCerbosSqlPredicateInjector();

    @Test
    void wrapsOriginalSqlWithCerbosAlias() {
        String alias = injector.predicateAlias(
                "SELECT * FROM documents document WHERE document.deleted = ? ORDER BY document.id",
                "document"
        ).orElseThrow();
        CerbosSqlInjectionResult result = injector.inject(
                "SELECT * FROM documents document WHERE document.deleted = ? ORDER BY document.id",
                alias + ".owner_by = ?"
        );

        assertEquals("SELECT __cerbos_scope.* FROM (SELECT * FROM documents document WHERE document.deleted = ? ORDER BY document.id) __cerbos_scope WHERE (__cerbos_scope.owner_by = ?)", result.sql());
        assertEquals(1, result.parameterInsertionIndex());
    }

    @Test
    void wrapsSqlWithoutOriginalWhere() {
        String alias = injector.predicateAlias("SELECT * FROM documents document ORDER BY document.id", "document").orElseThrow();
        CerbosSqlInjectionResult result = injector.inject(
                "SELECT * FROM documents document ORDER BY document.id",
                alias + ".owner_by = ?"
        );

        assertEquals("SELECT __cerbos_scope.* FROM (SELECT * FROM documents document ORDER BY document.id) __cerbos_scope WHERE (__cerbos_scope.owner_by = ?)", result.sql());
        assertEquals(0, result.parameterInsertionIndex());
    }

    @Test
    void usesInternalCerbosScopeAlias() {
        assertEquals("__cerbos_scope", injector.predicateAlias("SELECT * FROM documents", "document").orElseThrow());
    }

    @Test
    void avoidsAliasAlreadyUsedInOriginalSql() {
        String sql = "SELECT __cerbos_scope.* FROM documents __cerbos_scope";
        String alias = injector.predicateAlias(sql, "document").orElseThrow();
        CerbosSqlInjectionResult result = injector.inject(sql, alias + ".owner_by = ?");

        assertEquals("__cerbos_scope_1", alias);
        assertEquals("SELECT __cerbos_scope_1.* FROM (SELECT __cerbos_scope.* FROM documents __cerbos_scope) __cerbos_scope_1 WHERE (__cerbos_scope_1.owner_by = ?)", result.sql());
    }

    @Test
    void doesNotWrapWhenPredicateIsBlank() {
        CerbosSqlInjectionResult result = injector.inject("SELECT * FROM documents", "");

        assertEquals("SELECT * FROM documents", result.sql());
        assertEquals(0, result.parameterInsertionIndex());
    }

    @Test
    void insertsCerbosParametersAfterOriginalSqlPlaceholders() {
        String alias = injector.predicateAlias(
                "SELECT * FROM documents document WHERE document.title = '?' AND document.status = ?",
                "document"
        ).orElseThrow();
        CerbosSqlInjectionResult result = injector.inject(
                "SELECT * FROM documents document WHERE document.title = '?' AND document.status = ?",
                alias + ".owner_by = ?"
        );

        assertEquals(1, result.parameterInsertionIndex());
    }

}
