# 고급 SQL 적용 가이드

CerbosHelper의 기본 `DefaultCerbosSqlPredicateInjector`는 단일 resource row 목록 조회에 최적화되어 있다.

```sql
SELECT document.*
FROM documents document
WHERE document.deleted = false
ORDER BY document.id
```

복잡한 SQL은 default injector에 하드코딩하지 않고 `CerbosSqlPredicateInjector` 구현으로 분리한다.

## 기본 원칙

- Cerbos predicate가 참조하는 column은 필터 적용 위치에 반드시 존재해야 한다.
- `request.resource.attr.*`와 SQL column 매핑은 `CerbosResourceColumnRegistry`로 명확히 고정한다.
- aggregate 결과처럼 원본 resource row가 사라진 결과에는 직접 scope를 걸지 않는다.
- 구현이 애매하면 먼저 visible resource id 목록을 구하고, 그 id로 후속 집계를 수행한다.

## Derived Table Wrapping

원본 SQL을 derived table로 감싸고 outer query에서 predicate를 붙일 수 있다.

```java
final class WrappingCerbosSqlPredicateInjector implements CerbosSqlPredicateInjector {
    @Override
    public CerbosSqlInjectionResult inject(String sql, String predicate) {
        return new CerbosSqlInjectionResult(
                "SELECT scoped.* FROM (" + sql + ") scoped WHERE (" + predicate + ")",
                countOriginalPlaceholders(sql)
        );
    }
}
```

이 방식은 inner query가 Cerbos predicate에 필요한 column을 projection해야 한다. 예를 들어 outer alias가 `scoped`면 registry는 다음처럼 맞춘다.

```java
@Bean
CerbosResourceColumnRegistry cerbosResourceColumnRegistry() {
    return CerbosResourceColumnRegistry.builder()
            .column("document", "request.resource.attr.companyId", "scoped.company_id")
            .column("document", "request.resource.attr.ownerUserId", "scoped.owner_user_id")
            .build();
}
```

## WITH CTE

CTE 내부에서 원본 resource row가 유지된다면 CTE를 대상으로 predicate를 넣을 수 있다.

권장 방식은 원본 resource row 조회 mapper를 분리해 그 mapper에 `@CerbosScoped`를 붙이는 것이다.

```java
@CerbosScoped(resourceKind = "document", action = "view")
List<Long> findVisibleDocumentIds(DocumentSearchCondition condition);
```

한 SQL 안에서 처리해야 한다면 custom injector가 CTE의 resource row 단계에 predicate를 삽입해야 한다. outer aggregate나 outer projection에 predicate를 붙이면 권한 의미가 달라질 수 있다.

## UNION

같은 resource kind와 같은 column shape를 합치는 `UNION ALL`은 branch별로 같은 predicate를 삽입하는 custom injector로 구현할 수 있다.

하지만 branch마다 alias가 다르면 하나의 predicate 문자열을 그대로 재사용하기 어렵다. 이 경우 branch별 mapper를 분리하거나 branch별 registry/injector 규칙을 명확히 둔다.

서로 다른 resource kind가 섞인 `UNION`은 하나의 `resourceKind/action` plan으로 표현하기 어렵다. resource kind별로 조회한 뒤 application layer에서 합치는 쪽이 안전하다.

## GROUP BY

`GROUP BY` 결과는 원본 resource row가 아니다. 따라서 aggregate 결과에 직접 `@CerbosScoped`를 붙이지 않는다.

안전한 패턴은 다음과 같다.

```java
@CerbosScoped(resourceKind = "document", action = "view")
List<Long> findVisibleDocumentIds(DocumentSearchCondition condition);

List<DocumentSummary> summarizeByVisibleDocumentIds(List<Long> visibleDocumentIds);
```

SQL 한 번으로 끝내야 한다면 CTE에서 visible rows를 먼저 만들고 그 CTE를 aggregate한다.

## 테스트 권장사항

고급 injector를 추가하면 다음 fixture를 테스트한다.

- 기존 SQL에 `?` placeholder가 있는 경우
- string literal 안에 `?`가 있는 경우
- 기존 `WHERE`가 있는 경우
- `ORDER BY`가 있는 경우
- CTE 또는 derived table 내부에 `ORDER BY`가 있는 경우
- PageHelper count/page SQL과 함께 실행되는 경우
