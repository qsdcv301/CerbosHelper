package io.cerboshelper.mybatis;

import io.cerboshelper.mybatis.sql.CerbosSqlResourceAliasResolver;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CerbosSqlResourceAliasResolverTest {
    private final CerbosSqlResourceAliasResolver resolver = new CerbosSqlResourceAliasResolver();

    @Test
    void detectsPlainAlias() {
        assertEquals("d", resolver.resolve("SELECT d.* FROM document d WHERE d.deleted = false", "document").orElseThrow());
    }

    @Test
    void detectsExplicitAsAliasForSnakeCaseTable() {
        assertEquals("memo", resolver.resolve("SELECT memo.* FROM user_memo AS memo", "userMemo").orElseThrow());
    }

    @Test
    void usesTableNameWhenAliasIsAbsent() {
        assertEquals("document", resolver.resolve("SELECT * FROM document ORDER BY document.id", "document").orElseThrow());
    }
}
