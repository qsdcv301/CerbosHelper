# CerbosHelper

Spring Boot + MyBatis 프로젝트에서 Cerbos `PlanResources` / `CheckResources`를 사내 표준 DTO 규칙과 프로젝트 명시 설정으로 연결하는 라이브러리다.

이 라이브러리는 완전 범용 라이브러리가 아니라 사내 공용 라이브러리다. 모든 보호 대상 테이블에 `owner_by`, `owner_group_by`가 있고, 각각 `users.id`, `organizations.id`를 참조한다는 전제를 고정 규칙으로 사용한다.

## 1. 기본 모델

보호 대상 DTO는 `CerbosCommonDto`를 상속한다.

```java
public class Document extends CerbosCommonDto {
    private long id;
    private long tenantId;
    private String title;

    public Document(long id, long tenantId, String title, String ownerBy, Long ownerGroupBy) {
        this.id = id;
        this.tenantId = tenantId;
        this.title = title;
        setOwnerBy(ownerBy);
        setOwnerGroupBy(ownerGroupBy);
    }
}
```

`CerbosHelperConfig`에서 보호 resource로 등록하면 다음 매핑이 적용된다.

| Java attr | Cerbos attr | SQL column |
| --- | --- | --- |
| `ownerBy` | `request.resource.attr.ownerBy` | `{resourceAlias}.owner_by` |
| `ownerGroupBy` | `request.resource.attr.ownerGroupBy` | `{resourceAlias}.owner_group_by` |

`resourceKind`는 더 이상 패키지 스캔으로 자동 등록하지 않는다. 프로젝트 설정에서 `resources.resource("document", DocumentDto.class)`처럼 명시한다.

Cerbos `CheckResources` 요청에는 resource id 문자열이 필요하지만, Helper는 DTO 기본키를 요구하지 않는다. 이 id는 Cerbos 요청/응답 매칭용 correlation key일 뿐이며 정책 판단은 `ownerBy` / `ownerGroupBy` attribute로 수행한다.

## 2. 명시 적용 규칙

Spring Security가 classpath에 있으면 `SecurityContextHolder`의 현재 `Authentication`을 기본 principal로 사용한다. 기본값은 `Authentication.getName()`을 Cerbos principal id로, authorities를 Cerbos roles로 보낸다. `Authentication.getPrincipal()`이 record, Map, getter 기반 객체이면 읽을 수 있는 단순 값과 단순 list/map 값을 Cerbos attr로 펼쳐 보낸다.

정책에서 `organizationIds`, `organizationTreeIds`, `tenantIds` 같은 사내 확장 속성이 필요하면 애플리케이션이 Spring Security principal에 이미 계산된 값을 넣는 방식을 권장한다. Spring Security가 아닌 인증 구조라면 `CerbosHelperConfig`에서 `CerbosPrincipalResolver`를 제공한다.

목록 조회는 `CerbosHelperConfig.methodRules(...)`에 등록된 scope rule과 MyBatis Mapper 반환 타입으로 판단한다. 기본 scope rule은 비어 있다.

- `scope("action", "prefix1", "prefix2")`로 목록/검색용 mapper prefix와 Cerbos action을 연결한다.
- 반환 타입이 config에 등록된 `CerbosCommonDto` 또는 `List<CerbosCommonDto>`이면 resourceKind를 추론한다.
- `find{Resource}Ids` 같은 ID 목록 메서드는 등록된 prefix 뒤의 resource token으로 resourceKind를 추론한다.
- 제외할 메서드는 `excludePrefixes(...)`, `excludeNames(...)`, `excludeContains(...)`로 명시한다.

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

따라서 보호 대상 목록 SQL은 `owner_by`, `owner_group_by`를 projection해야 한다. 기본 injector가 alias를 제공하지 않는 custom 구현으로 교체된 경우에는 top-level `FROM` / `JOIN`의 table alias를 fallback으로 감지한다.

단건 read는 mapper SELECT에 `PlanResources` predicate를 주입하는 방식으로 처리한다. create/update/delete mapper command는 `CerbosHelperConfig.methodRules(...)`에 등록된 `before(...)` rule과 mapper parameter DTO로 판단한다. 기본 check rule은 비어 있다.

- `scope("action", "prefix")`는 목록/단건 read mapper SELECT에 `PlanResources` SQL predicate를 적용한다.
- `before("action", "prefix")`는 mapper command parameter로 전달된 `CerbosCommonDto`를 검사한 뒤 SQL을 실행한다.
- `create*`, `insert*`, `save*`도 필요하면 `before(...)`로 명시 등록한다. 등록하지 않으면 command check 대상이 아니다.
- Helper는 `{resource}Mapper.findById(...)` 재조회를 하지 않는다.
- 따라서 command mapper parameter에는 `ownerBy` / `ownerGroupBy`가 포함되어야 한다. Helper는 이 값을 데이터 접근 시점에 이미 신뢰 가능한 값으로 본다.
- 제외할 메서드는 `excludePrefixes(...)`, `excludeNames(...)`, `excludeContains(...)`로 명시한다.

## 3. 설치

```groovy
dependencies {
    implementation 'com.github.qsdcv301:CerbosHelper:v1.0.18'
}
```

PDP 연결, mapper method/action rule, 프로젝트 예외 변환은 yml property binding이 아니라 `CerbosHelperConfig`에 모은다.

```java
@Configuration
public class ProjectCerbosConfig extends CerbosHelperConfig {
    @Override
    public void configure(CerbosHelperConfigurer configurer) {
        configurer
                .client(client -> {
                    client.setTarget(System.getenv().getOrDefault("CERBOS_TARGET", "cerbos:3593"));
                    client.setPlaintext(true);
                })
                .accessDeniedHandler(decision -> new ProjectAccessDeniedException(decision.reason()))
                .resources(resources -> resources.resource("document", DocumentDto.class))
                .methodRules(methods -> {
                    methods.excludePrefixes("findAll", "selectAll", "listAll", "debug", "trace", "admin");
                    methods.scope("view", "find", "select", "list", "search");
                    methods.before("create", "create", "insert");
                    methods.before("update", "update", "modify");
                    methods.before("delete", "delete", "remove");
                });
    }
}
```

## 4. Policy 예시

```yaml
apiVersion: api.cerbos.dev/v1
resourcePolicy:
  version: default
  resource: document
  rules:
    - actions: ["*"]
      effect: EFFECT_ALLOW
      roles: ["authenticated"]
      condition:
        match:
          expr: "request.resource.attr.ownerBy == request.principal.attr.userId || request.resource.attr.ownerGroupBy in request.principal.attr.organizationTreeIds"
```

action 이름은 Helper 코어가 정하지 않는다. 정책 파일에서 `actions: ["*"]`를 쓰면 프로젝트 config의 `methodRules(...)`가 실제 action 이름을 정한다.

## 5. 예외 처리

신규 도메인은 도메인마다 별도 설정 Bean을 만들지 않는다. 기본 사내 규칙에서 벗어난 복잡 SQL이나 예외 lookup을 연결해야 하는 경우에만 프로젝트 common/config에서 조정한다.

- `CerbosPrincipalResolver`: 현재 사용자 principal 연결
- `CerbosResourceColumnRegistry`: derived table, CTE 같은 고급 SQL column allowlist 구성
- `CerbosSqlPredicateInjector`: 복잡 SQL에 대한 predicate 삽입 규칙 교체
- `CerbosMyBatisInterceptorOrderStrategy`: MyBatis plugin 순서 조정
- `CerbosAccessDeniedHandler`: deny 예외 변환

owner column은 `owner_by`, `owner_group_by` 고정이다. 신규 도메인의 기본 방식은 어노테이션이 아니라 `CerbosCommonDto` 상속과 `CerbosHelperConfig.resources(...)` 명시 등록이다.

`CerbosDeniedDecision.reason()`은 프로젝트 표준 ErrorCode 매핑에 사용할 수 있다. 기본 reason은 `DENIED`이고, Helper가 Cerbos 호출 전에 `ownerBy` 또는 `ownerGroupBy`를 만들 수 없으면 `MISSING_OWNER`로 실패한다.

프로젝트별 설정은 `CerbosHelperConfig` 한 곳에 모은다. 설정하지 않은 resource와 method rule은 적용되지 않는다.

```java
@Configuration
public class ProjectCerbosConfig extends CerbosHelperConfig {
    @Override
    public void configure(CerbosHelperConfigurer configurer) {
        configurer
                .principalResolver(new SessionCerbosPrincipalResolver())
                .accessDeniedHandler(decision -> new ProjectAccessDeniedException(decision.reason()))
                .resources(resources -> resources.resource("document", DocumentDto.class))
                .methodRules(methods -> {
                    methods.scope("document:view", "find", "list");
                    methods.before("document:update", "update");
                    methods.before("document:delete", "delete");
                });
    }
}
```

권장 설정은 resource 등록, mapper method rule, principal, deny 예외 변환이다. `select`, `find`, `insert`, `update`, `delete` 같은 prefix와 Cerbos action은 Helper 코어에 고정하지 않고 프로젝트 config에서 명시한다.

`CerbosHelperConfig`에서 설정할 수 있는 항목은 `resources`, `methodRules`, `CerbosAuthorizationClient`, `CerbosPrincipalResolver`, `CerbosAccessDeniedHandler`, `CerbosResourceColumnRegistry`, `CerbosSqlPredicateInjector`, `CerbosMyBatisInterceptorOrderStrategy`다. 다만 `CerbosResourceColumnRegistry`, `CerbosSqlPredicateInjector`, `CerbosMyBatisInterceptorOrderStrategy`는 복잡 SQL, MyBatis plugin 순서 충돌 같은 고급 예외에서만 사용한다.

자세한 프로젝트 설정 예제는 `docs/PROJECT_CONFIGURATION_KO.md`를 본다.

## 6. 프로젝트 적용 경계

CerbosHelper는 업무 모듈을 대신 구현하는 엔진이 아니라 Spring/MyBatis 실행 지점에 Cerbos 판단을 연결하는 공통 라이브러리다. 실제 프로젝트에서는 다음 경계를 유지한다.

| 영역 | 책임 |
| --- | --- |
| Helper library | `CerbosCommonDto`, 설정 기반 resource/method rule registry, MyBatis interceptor, Cerbos SDK client, plan-to-SQL 변환, extension interface 제공 |
| Project common/config | resource 등록, mapper method rule, MyBatis plugin 등록/순서 확인, deny 예외 변환, Spring Security principal 구성, Spring Security 미사용 시 principal resolver 제공, 필요한 경우 고급 extension 연결 |
| Domain module | DTO 상속, `owner_by` / `owner_group_by` 컬럼과 projection, config에 맞는 mapper method shape, command mapper에 신뢰 가능한 owner DTO 전달 |



## 7. Runtime 개입 파일

| 파일 | Runtime 위치 | 역할 |
| --- | --- | --- |
| `autoconfigure/CerbosHelperAutoConfiguration.java` | Spring Boot 시작 | Helper Bean 등록, config 기반 resource/method rule registry 구성, interceptor order verifier 연결 |
| `check/CerbosResourceCheckExecutor.java` | Check 실행기 | mapper parameter DTO와 principal을 `CheckResources`로 확인 |
| `scope/CerbosMyBatisInterceptor.java` | MyBatis plugin | `Executor.query(...)` SELECT에는 `PlanResources` SQL predicate를 적용하고, `Executor.update(...)` command에는 `CheckResources`를 적용 |
| `sql/DefaultCerbosSqlPredicateInjector.java` | SQL scope 적용 | 원본 SQL을 Helper alias derived table로 감싸고 Cerbos predicate를 주입 |

실행 경로는 MyBatis interceptor 하나다. read는 `Executor.query(...)`에서 `PlanResources` SQL predicate를 주입하고, create/update/delete는 `Executor.update(...)`에서 mapper parameter DTO를 `CheckResources`로 검사한다.
