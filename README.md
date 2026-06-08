# CerbosHelper

Spring Boot + MyBatis 프로젝트에서 Cerbos `PlanResources` / `CheckResources`를 사내 표준 DTO 규칙으로 자동 연결하는 라이브러리다.

이 라이브러리는 완전 범용 라이브러리가 아니라 사내 공용 라이브러리다. 모든 보호 대상 테이블에 `owner_by`, `owner_org_by`가 있고, 각각 `users.id`, `organizations.id`를 참조한다는 전제를 고정 규칙으로 사용한다.

## 1. 기본 모델

보호 대상 DTO는 `CerbosCommonDto`를 상속한다.

```java
public class Document extends CerbosCommonDto {
    private long id;
    private long tenantId;
    private String title;

    public Document(long id, long tenantId, String title, String ownerBy, Long ownerOrgBy) {
        this.id = id;
        this.tenantId = tenantId;
        this.title = title;
        setOwnerBy(ownerBy);
        setOwnerOrgBy(ownerOrgBy);
    }
}
```

상속만 하면 다음 매핑이 자동 적용된다.

| Java attr | Cerbos attr | SQL column |
| --- | --- | --- |
| `ownerBy` | `request.resource.attr.ownerBy` | `{resourceAlias}.owner_by` |
| `ownerOrgBy` | `request.resource.attr.ownerOrgBy` | `{resourceAlias}.owner_org_by` |

`resourceKind`는 DTO 클래스명에서 `Dto` suffix를 제거한 뒤 lower camel로 만든다. 예를 들어 `DocumentDto`는 `document`가 된다.

resource id는 기본적으로 `id` / `getId()` / `id()`에서 읽는다. `1.0.11`부터는 애플리케이션의 MyBatis `resultMap`에 단일 `<id property="...">`가 있으면 그 property를 해당 DTO의 resource id로 자동 등록한다.

```xml
<resultMap id="userMemoDtoResultMap" type="com.example.UserMemoDto">
    <id property="userMemoId" column="user_memo_id"/>
    <result property="ownerBy" column="owner_by"/>
    <result property="ownerOrgBy" column="owner_org_by"/>
</resultMap>
```

이 경우 `UserMemoDto`가 `getId()`를 만들지 않아도 `userMemoId`가 Cerbos resource id로 사용된다. Helper는 `*Id` 이름을 추측하지 않는다. 단일 `<id>`만 자동 등록하며, 복합 id는 자동 등록하지 않고, 같은 DTO에 서로 다른 `<id property>`가 선언되면 시작 시 실패시켜 잘못된 권한 판단을 막는다.

## 2. 자동 적용 규칙

Spring Security가 classpath에 있으면 `SecurityContextHolder`의 현재 `Authentication`을 기본 principal로 사용한다. 기본값은 `Authentication.getName()`을 Cerbos principal id로, authorities를 Cerbos roles로 보낸다. `Authentication.getPrincipal()`이 record, Map, getter 기반 객체이면 읽을 수 있는 단순 값과 단순 list/map 값을 Cerbos attr로 펼쳐 보낸다.

정책에서 `organizationIds`, `organizationTreeIds`, `tenantIds` 같은 사내 확장 속성이 필요하면 애플리케이션이 Spring Security principal에 이미 계산된 값을 넣는 방식을 권장한다. Spring Security가 아닌 인증 구조라면 `CerbosPrincipalResolver` Bean을 직접 제공할 수 있고, 이 경우 Helper 기본 resolver는 대체된다.

목록 조회는 MyBatis Mapper 메서드명과 반환 타입으로 자동 판단한다.

- `find*`, `select*`, `list*`, `search*`는 기본 action `view`로 처리한다.
- 반환 타입이 `CerbosCommonDto` 또는 `List<CerbosCommonDto>`이면 resourceKind를 자동 추론한다.
- `find{Resource}Ids` 같은 ID 목록 메서드는 메서드명에서 resourceKind를 추론한다.
- `findAll*`, `selectAll*`, `listAll*`, `debug*`, `trace*`, `admin*`, `findById` 계열은 자동 scope에서 제외한다.

목록 조회 SQL은 기본적으로 Helper 내부 alias를 가진 derived table로 감싼 뒤 outer query에서 Cerbos predicate를 적용한다.

```sql
SELECT d.*
FROM document d
ORDER BY d.id
```

위 SQL은 다음 형태로 실행된다.

```sql
SELECT __cerbos_scope.*
FROM (
    SELECT d.*
    FROM document d
    ORDER BY d.id
) __cerbos_scope
WHERE (__cerbos_scope.owner_by = ?)
```

기본 alias는 `__cerbos_scope`다. 원본 SQL에 같은 identifier가 있으면 `__cerbos_scope_1`, `__cerbos_scope_2`처럼 suffix를 붙여 충돌을 피한다. Cerbos predicate가 비어 있는 allow-all plan이면 원본 SQL을 감싸지 않는다.

따라서 보호 대상 목록 SQL은 `owner_by`, `owner_org_by`를 projection해야 한다. 기본 injector가 alias를 제공하지 않는 custom 구현으로 교체된 경우에는 top-level `FROM` / `JOIN`의 table alias를 fallback으로 감지한다.

단건/쓰기 서비스 메서드는 `@Service` 메서드명으로 자동 판단한다.

- `find*`, `get*`, `select*` -> `view`
- `create*`, `insert*`, `save*` -> `create`
- `update*`, `modify*` -> `update`
- `delete*`, `remove*` -> `delete`
- `create*`는 `CerbosCommonDto` 인자에 `ownerBy`가 없으면 현재 principal id를 기본값으로 채운다. `ownerOrgBy`가 없고 DTO가 `getOrgId()` 또는 `getOrganizationId()`를 제공하면 그 값을 기본값으로 채운다.
- `update*`, `delete*`, `get*`는 `{resource}Id` 인자, `id` / `getId()` / `id()`, 또는 단일 MyBatis `resultMap <id property="...">`로 등록된 DTO id property를 사용해 `{resource}Mapper.findById(...)`로 기존 row를 조회한 뒤 그 row를 검사한다.
- 따라서 Cerbos로 보내는 resource payload에는 `ownerBy` / `ownerOrgBy`가 포함되어야 한다. HTTP 요청 DTO가 아니라 Helper가 검사에 사용하는 resource DTO 기준이다.
- `findAll*`, `debug*`, `trace*`, `admin*`은 자동 check에서 제외한다.

## 3. 설치

```groovy
dependencies {
    implementation 'com.github.qsdcv301:CerbosHelper:1.0.13'
}
```

```yaml
cerboshelper:
  target: ${CERBOS_TARGET:cerbos:3593}
  plaintext: true
  timeout: 1s
  check:
    auto:
      include-class-name-patterns:
        - ".*CommandService.*"
        - ".*CerbosGuardService.*"
      exclude-class-name-patterns:
        - ".*UtilService.*"
```

파라미터 이름 기반 convention을 쓰므로 Java 컴파일에 `-parameters`를 켠다.

```groovy
tasks.withType(JavaCompile).configureEach {
    options.compilerArgs.add('-parameters')
}
```

## 4. Policy 예시

```yaml
apiVersion: api.cerbos.dev/v1
resourcePolicy:
  version: default
  resource: document
  rules:
    - actions: ["view"]
      effect: EFFECT_ALLOW
      roles: ["authenticated"]
      condition:
        match:
          expr: "request.resource.attr.ownerBy == request.principal.attr.userId || request.resource.attr.ownerOrgBy in request.principal.attr.organizationTreeIds"
```

## 5. 예외 처리

신규 도메인은 별도 설정 Bean을 만들지 않는다. 기본 사내 규칙에서 벗어난 복잡 SQL이나 예외 lookup을 연결해야 하는 경우에만 Bean을 교체한다.

- `CerbosPrincipalResolver`: 현재 사용자 principal 연결
- `CerbosResourceResolver`: 단건/쓰기 resource lookup 규칙 교체
- `CerbosResourceColumnRegistry`: derived table, CTE 같은 고급 SQL column allowlist 구성
- `CerbosSqlPredicateInjector`: 복잡 SQL에 대한 predicate 삽입 규칙 교체
- `CerbosAccessDeniedHandler`: deny 예외 변환

owner column은 `owner_by`, `owner_org_by` 고정이다. 신규 도메인의 기본 방식은 어노테이션이나 별도 설정이 아니라 `CerbosCommonDto` 상속이다.
