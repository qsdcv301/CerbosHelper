package io.cerboshelper.mybatis;

import io.cerboshelper.mybatis.annotation.CerbosAttribute;
import io.cerboshelper.mybatis.annotation.CerbosResource;
import io.cerboshelper.mybatis.sql.CerbosResourceColumnRegistry;
import io.cerboshelper.mybatis.sql.CerbosResourceColumns;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CerbosResourceColumnsTest {
    @Test
    void mapsRecordAttributesToQualifiedSnakeCaseColumns() {
        CerbosResourceColumnRegistry registry = CerbosResourceColumns.builder()
                .resource(DocumentResource.class)
                .build();

        assertEquals(
                "document.owner_user_id",
                registry.columnFor("document", "request.resource.attr.ownerUserId").orElseThrow()
        );
        assertEquals(
                "document.sensitivity_level",
                registry.columnFor("document", "request.resource.attr.sensitivityLevel").orElseThrow()
        );
        assertTrue(registry.columnFor("document", "request.resource.attr.displayOnly").isEmpty());
    }

    @Test
    void usesExplicitAliasAndColumnOverridesOnlyWhenProvided() {
        CerbosResourceColumnRegistry registry = CerbosResourceColumns.builder()
                .resource("document", AliasedDocumentResource.class, "d")
                .build();

        assertEquals(
                "d.owner_user_id",
                registry.columnFor("document", "request.resource.attr.ownerUserId").orElseThrow()
        );
        assertEquals(
                "documents.status_code",
                registry.columnFor("document", "request.resource.attr.status").orElseThrow()
        );
    }

    @CerbosResource(kind = "document")
    private record DocumentResource(
            long id,
            String ownerUserId,
            int sensitivityLevel,
            @CerbosAttribute(ignore = true)
            String displayOnly
    ) {
    }

    private record AliasedDocumentResource(
            String ownerUserId,
            @CerbosAttribute(value = "status", column = "documents.status_code")
            String state
    ) {
    }
}
