package io.cerboshelper.mybatis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cerboshelper.mybatis.annotation.CerbosResource;
import io.cerboshelper.mybatis.sql.CerbosPlanToSqlConverter;
import io.cerboshelper.mybatis.sql.CerbosResourceColumns;
import io.cerboshelper.mybatis.sql.CerbosSqlFilter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CerbosPlanToSqlConverterTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final CerbosPlanToSqlConverter converter = new CerbosPlanToSqlConverter(
            CerbosResourceColumns.builder()
                    .resource(DocumentResource.class)
                    .build()
    );

    @Test
    void convertsConditionalPlanToNamedSqlFilter() throws Exception {
        CerbosSqlFilter filter = converter.convertNamed("document", plan("""
                {
                  "filter": {
                    "kind": "KIND_CONDITIONAL",
                    "condition": {
                      "expression": {
                        "operator": "and",
                        "operands": [
                          {
                            "expression": {
                              "operator": "eq",
                              "operands": [
                                {"variable": "request.resource.attr.status"},
                                {"value": "ACTIVE"}
                              ]
                            }
                          },
                          {
                            "expression": {
                              "operator": "le",
                              "operands": [
                                {"variable": "request.resource.attr.sensitivityLevel"},
                                {"value": 2}
                              ]
                            }
                          }
                        ]
                      }
                    }
                  }
                }
                """));

        assertEquals("(document.status = #{params.cp0}) AND (document.sensitivity_level <= #{params.cp1})", filter.whereSql());
        assertEquals("ACTIVE", filter.namedParams().get("cp0"));
        assertEquals(2L, filter.namedParams().get("cp1"));
    }

    @Test
    void convertsInAndNullComparisons() throws Exception {
        CerbosSqlFilter filter = converter.convertPositional("document", plan("""
                {
                  "filter": {
                    "kind": "KIND_CONDITIONAL",
                    "condition": {
                      "expression": {
                        "operator": "or",
                        "operands": [
                          {
                            "expression": {
                              "operator": "in",
                              "operands": [
                                {"variable": "request.resource.attr.region"},
                                {"value": ["KR", "US"]}
                              ]
                            }
                          },
                          {
                            "expression": {
                              "operator": "eq",
                              "operands": [
                                {"variable": "request.resource.attr.ownerUserId"},
                                {"value": null}
                              ]
                            }
                          }
                        ]
                      }
                    }
                  }
                }
                """));

        assertEquals("(document.region IN (?, ?)) OR (document.owner_user_id IS NULL)", filter.whereSql());
        assertEquals(2, filter.positionalParams().size());
        assertEquals("KR", filter.positionalParams().get(0));
        assertEquals("US", filter.positionalParams().get(1));
    }

    @Test
    void deniesAlwaysDeniedPlan() throws Exception {
        CerbosSqlFilter filter = converter.convertNamed("document", plan("""
                {"filter": {"kind": "KIND_ALWAYS_DENIED"}}
                """));

        assertTrue(filter.denied());
    }

    @Test
    void rejectsVariablesOutsideTheResourceColumnAllowlist() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> converter.convertNamed("document", plan("""
                {
                  "filter": {
                    "kind": "KIND_CONDITIONAL",
                    "condition": {
                      "expression": {
                        "operator": "eq",
                        "operands": [
                          {"variable": "request.principal.attr.role"},
                          {"value": "ADMIN"}
                        ]
                      }
                    }
                  }
                }
                """)));

        assertTrue(exception.getMessage().contains("Unsupported or unsafe Cerbos variable"));
    }

    private JsonNode plan(String json) throws Exception {
        return OBJECT_MAPPER.readTree(json);
    }

    @CerbosResource(kind = "document")
    private record DocumentResource(
            String status,
            int sensitivityLevel,
            String region,
            String ownerUserId
    ) {
    }
}
