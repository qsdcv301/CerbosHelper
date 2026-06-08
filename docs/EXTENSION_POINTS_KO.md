# CerbosHelper 확장 포인트

CerbosHelper의 기본 방향은 사내 표준 규칙 자동 적용이다. 보호 대상 DTO가 `CerbosCommonDto`를 상속하면 resource payload, SQL column allowlist, 목록 scope, 단건/쓰기 check가 자동으로 연결된다.

## 기본값

- `ownerBy`는 `users.id`를 담고 SQL column은 `owner_by`다.
- `ownerOrgBy`는 `organizations.id`를 담고 SQL column은 `owner_org_by`다.
- owner column 이름은 override 대상이 아니다. 신규/표준 프로젝트는 보호 대상 테이블에 이 두 컬럼을 둔다.
- `resourceKind`는 DTO 클래스명에서 `Dto` suffix를 제거한 뒤 lower camel로 만든다.
- 기본 SQL scope alias는 `__cerbos_scope`다. 원본 SQL에 같은 identifier가 있으면 suffix를 붙여 충돌을 피한다. custom injector가 alias를 제공하지 않는 경우에는 top-level `FROM` / `JOIN`에서 resource alias를 fallback으로 감지한다.
- `findAll*`, `debug*`, `trace*`, `admin*`은 자동 보호 대상에서 제외한다.
- auto-check 적용 대상은 `cerboshelper.check.auto.include-class-name-patterns` / `exclude-class-name-patterns`와 method pattern 설정으로 제한할 수 있다.
- `create*` check는 `ownerBy`가 비어 있으면 principal id를 기본값으로 채우고, `ownerOrgBy`가 비어 있으면 DTO의 `orgId` / `organizationId` getter를 기본값 후보로 사용한다.
- `update*`, `delete*`, `get*` check는 DTO 자체가 아니라 `getId()` / `id()` 또는 `{resource}Id`로 기존 row를 조회해 검사한다.

## 확장 Bean

| 확장 포인트 | 기본 구현 | 교체 이유 |
| --- | --- | --- |
| `CerbosPrincipalResolver` | Spring Security resolver | 현재 사용자 principal에 조직/테넌트 속성 보강 |
| `CerbosResourceResolver` | `DefaultCerbosResourceResolver` | `{resource}Mapper.findById(...)` convention이 맞지 않는 조회 연결 |
| `CerbosResourceColumnRegistry` | `CerbosResourceColumns` | derived table, CTE처럼 원본 resource row가 감춰지는 고급 조회 처리 |
| `CerbosSqlPredicateInjector` | `DefaultCerbosSqlPredicateInjector` | 기본 `SELECT __cerbos_scope.* FROM (...) __cerbos_scope WHERE (...)`와 다른 SQL shape 처리 |
| `CerbosMyBatisInterceptorOrderStrategy` | `DefaultCerbosMyBatisInterceptorOrderStrategy` | MyBatis plugin 순서 수동 관리 |
| `CerbosAccessDeniedHandler` | `SecurityException` handler | 프로젝트 표준 예외 변환 |

신규 도메인은 위 Bean을 만들지 않는다. 먼저 `CerbosCommonDto` 상속, `owner_by`/`owner_org_by` 컬럼, Mapper/Service naming convention을 맞춘다.
