# 고급 SQL 적용 가이드

CerbosHelper의 기본 `DefaultCerbosSqlPredicateInjector`는 원본 MyBatis SQL을 `cb` alias를 가진 derived table로 감싼다.

```sql
SELECT document.*
FROM documents document
WHERE document.deleted = false
ORDER BY document.id
```

기본 실행 형태는 다음과 같다.

```sql
SELECT cb.*
FROM (
    SELECT document.*
    FROM documents document
    WHERE document.deleted = false
    ORDER BY document.id
) cb
WHERE (cb.owner_by = ?)
```

`CerbosSqlPredicateInjector.predicateAlias(...)`의 기본값은 `cb`다. 그래서 `CerbosPlanToSqlConverter`는 Cerbos plan을 `cb.owner_by`, `cb.owner_org_by` 같은 outer alias 기준 predicate로 변환한다.

## 기본 원칙

- Cerbos predicate가 참조하는 column은 inner query projection에 있어야 한다.
- owner-column 정책이면 inner query가 `owner_by`, `owner_org_by`를 projection해야 한다.
- `request.resource.attr.*`와 SQL column 매핑은 `owner_by`, `owner_org_by` 고정 규칙을 우선 사용한다.
- aggregate 결과처럼 원본 resource row가 사라진 결과에는 직접 scope를 걸지 않는다.
- 기본 injector가 alias를 제공하지 않는 custom 구현으로 교체된 경우에는 top-level `FROM` / `JOIN`의 resource alias를 fallback으로 감지한다.

## WITH CTE

CTE 내부에서 원본 resource row가 유지된다면 CTE를 대상으로 predicate를 넣을 수 있다.

권장 방식은 원본 resource row 조회 mapper를 분리하고, `findVisibleDocumentIds`처럼 resource 이름을 포함한 사내 네이밍 규칙을 사용해 자동 scope 대상이 되게 하는 것이다.

한 SQL 안에서 처리해야 한다면 custom injector가 CTE의 resource row 단계에 predicate를 삽입해야 한다. outer aggregate나 outer projection에 predicate를 붙이면 권한 의미가 달라질 수 있다.

## UNION

같은 resource kind와 같은 column shape를 합치는 `UNION ALL`은 branch별로 같은 predicate를 삽입하는 custom injector로 구현할 수 있다.

하지만 branch마다 alias가 다르면 하나의 predicate 문자열을 그대로 재사용하기 어렵다. 이 경우 branch별 mapper를 분리하거나 custom injector 규칙을 명확히 둔다.

서로 다른 resource kind가 섞인 `UNION`은 하나의 `resourceKind/action` plan으로 표현하기 어렵다. resource kind별로 조회한 뒤 application layer에서 합치는 쪽이 안전하다.

## GROUP BY

`GROUP BY` 결과는 원본 resource row가 아니다. 따라서 aggregate 결과를 자동 scope 대상으로 만들지 않는다.

안전한 패턴은 다음과 같다.

```java
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
