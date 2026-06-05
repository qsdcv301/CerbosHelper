package io.cerboshelper.mybatis;

import io.cerboshelper.mybatis.convention.CerbosCommonResourceRegistry;
import io.cerboshelper.mybatis.model.CerbosCommonDto;
import io.cerboshelper.mybatis.sql.CerbosResourceColumnRegistry;
import io.cerboshelper.mybatis.sql.CerbosResourceColumns;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CerbosResourceColumnsTest {
    @Test
    void mapsCommonDtoAttributesToQualifiedSnakeCaseColumns() {
        CerbosResourceColumnRegistry registry = CerbosResourceColumns.builder()
                .resource(new CerbosCommonResourceRegistry(List.of(Document.class)).resources().get(0))
                .build();

        assertEquals(
                "document.owner_by",
                registry.columnFor("document", "request.resource.attr.ownerBy").orElseThrow()
        );
        assertEquals(
                "document.owner_org_by",
                registry.columnFor("document", "request.resource.attr.ownerOrgBy").orElseThrow()
        );
        assertEquals(
                "document.sensitivity_level",
                registry.columnFor("document", "request.resource.attr.sensitivityLevel").orElseThrow()
        );
    }

    @Test
    void usesExplicitAliasWhenProvided() {
        CerbosResourceColumnRegistry registry = CerbosResourceColumns.builder()
                .resource("document", AliasedDocumentResource.class, "d")
                .build();

        assertEquals(
                "d.owner_by",
                registry.columnFor("document", "request.resource.attr.ownerBy").orElseThrow()
        );
        assertEquals(
                "d.status",
                registry.columnFor("document", "request.resource.attr.status").orElseThrow()
        );
    }

    private static class Document extends CerbosCommonDto {
        private long id;
        private int sensitivityLevel;
    }

    private static class AliasedDocumentResource extends CerbosCommonDto {
        private String status;
    }

    @Test
    void mapsCommonDtoOwnerColumnsWithFixedDefaults() {
        CerbosResourceColumnRegistry registry = CerbosResourceColumns.builder()
                .resource("memo", Memo.class)
                .build();

        assertEquals("memo.owner_by", registry.columnFor("memo", "request.resource.attr.ownerBy").orElseThrow());
        assertEquals("memo.owner_org_by", registry.columnFor("memo", "request.resource.attr.ownerOrgBy").orElseThrow());
    }

    private static class Memo extends CerbosCommonDto {
        private long id;
    }

    @Test
    void stripsDtoSuffixFromDefaultResourceKindAndAlias() {
        CerbosResourceColumnRegistry registry = CerbosResourceColumns.builder()
                .resource(new CerbosCommonResourceRegistry(List.of(DocumentDto.class)).resources().get(0))
                .build();

        assertEquals("document.owner_by", registry.columnFor("document", "request.resource.attr.ownerBy").orElseThrow());
    }

    private static class DocumentDto extends CerbosCommonDto {
        private long id;
    }
}
