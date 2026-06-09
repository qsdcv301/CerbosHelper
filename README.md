# CerbosHelper

Spring Boot + MyBatis 프로젝트에서 Cerbos `PlanResources` / `CheckResources`를 사내 표준 DTO 규칙으로 자동 연결하는 라이브러리다.

이 라이브러리는 완전 범용 라이브러리가 아니라 사내 공용 라이브러리다. 모든 보호 대상 테이블에 `owner_by`, `owner_org_by`가 있고, 각각 `users.id`, `organizations.id`를 참조한다는 전제를 고정 규칙으로 사용한다.

## 1. 기본 모델

보호 대상 DTO는 `CerbosCommonDto`를 상속한다.

```java
public class Document extends CerbosCommonDto {
    @CerbosId
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

resource id는 DTO 필드, getter, record component에 붙은 `@CerbosId`로만 읽는다. 기본 `id` / `getId()` / `id()` 이름 추론은 사용하지 않는다.

```java
public class UserMemoDto extends CerbosCommonDto {
    @CerbosId
    private Integer userMemoId;
}
```

이 경우 `userMemoId`가 Cerbos resource id로 사용된다. Helper는 MyBatis resultMap이나 필드/메서드 이름을 resource id 판단에 사용하지 않는다.

## 2. 자동 적용 규칙

Spring Security가 classpath에 있으면 `SecurityContextHolder`의 현재 `Authentication`을 기본 principal로 사용한다. 기본값은 `Authentication.getName()`을 Cerbos principal id로, authorities를 Cerbos roles로 보낸다. `Authentication.getPrincipal()`이 record, Map, getter 기반 객체이면 읽을 수 있는 단순 값과 단순 list/map 값을 Cerbos attr로 펼쳐 보낸다.

정책에서 `organizationIds`, `organizationTreeIds`, `tenantIds` 같은 사내 확장 속성이 필요하면 애플리케이션이 Spring Security principal에 이미 계산된 값을 넣는 방식을 권장한다. Spring Security가 아닌 인증 구조라면 `CerbosHelperConfig`에서 `CerbosPrincipalResolver`를 제공한다.

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

단건 read/update/delete 서비스 메서드는 `@Service` 메서드명으로 자동 판단한다.

- `find*`, `get*`, `select*` -> `view`
- `update*`, `modify*` -> `update`
- `delete*`, `remove*` -> `delete`
- `create*`, `insert*`, `save*`는 CerbosHelper auto-check 대상이 아니다.
- `find*`, `get*`, `select*`는 service method를 먼저 실행하고, 반환된 `CerbosCommonDto` 또는 `Optional<CerbosCommonDto>`의 `ownerBy` / `ownerOrgBy`로 `view` check를 수행한다.
- `update*`, `delete*`는 `{resource}Id` 인자 또는 `@CerbosId`로 표시된 DTO id를 사용해 `{resource}Mapper.findById(...)`로 기존 row를 조회한 뒤 그 row를 검사한다.
- 따라서 Cerbos로 보내는 resource payload에는 `ownerBy` / `ownerOrgBy`가 포함되어야 한다. HTTP 요청 DTO가 아니라 Helper가 검사에 사용하는 resource DTO 기준이다.
- `findAll*`, `debug*`, `trace*`, `admin*`은 자동 check에서 제외한다.

## 3. 설치

```groovy
dependencies {
    implementation 'com.github.qsdcv301:CerbosHelper:1.0.15'
}
```

```yaml
cerboshelper:
  target: ${CERBOS_TARGET:cerbos:3593}
  plaintext: true
  timeout: 1s
```

auto-check 적용 범위와 프로젝트 예외 변환은 yml보다 `CerbosHelperConfig`에 모으는 방식을 권장한다.

```java
@Configuration
public class ProjectCerbosConfig extends CerbosHelperConfig {
    @Override
    public void configure(CerbosHelperConfigurer configurer) {
        configurer
                .accessDeniedHandler(decision -> new ProjectAccessDeniedException(decision.reason()))
                .autoCheck(auto -> {
                    auto.setIncludeClassNamePatterns(List.of(".*CommandService", ".*QueryService"));
                    auto.setExcludeClassNamePatterns(List.of(".*UtilService"));
                });
    }
}
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

신규 도메인은 도메인마다 별도 설정 Bean을 만들지 않는다. 기본 사내 규칙에서 벗어난 복잡 SQL이나 예외 lookup을 연결해야 하는 경우에만 프로젝트 common/config에서 조정한다.

- `CerbosPrincipalResolver`: 현재 사용자 principal 연결
- `CerbosResourceResolver`: 단건/쓰기 resource lookup 규칙 교체
- `CerbosResourceColumnRegistry`: derived table, CTE 같은 고급 SQL column allowlist 구성
- `CerbosSqlPredicateInjector`: 복잡 SQL에 대한 predicate 삽입 규칙 교체
- `CerbosMyBatisInterceptorOrderStrategy`: MyBatis plugin 순서 조정
- `CerbosAccessDeniedHandler`: deny 예외 변환

owner column은 `owner_by`, `owner_org_by` 고정이다. 신규 도메인의 기본 방식은 어노테이션이나 별도 설정이 아니라 `CerbosCommonDto` 상속이다.

`CerbosDeniedDecision.reason()`은 프로젝트 표준 ErrorCode 매핑에 사용할 수 있다. 기본 reason은 `DENIED`이고, Helper가 Cerbos 호출 전에 owner 정보를 만들 수 없으면 `MISSING_OWNER`로 실패한다.

프로젝트별 설정은 `CerbosHelperConfig` 한 곳에 모을 수 있다. 설정하지 않으면 Helper 기본값이 사용되고, 설정한 항목만 override된다.

```java
@Configuration
public class ProjectCerbosConfig extends CerbosHelperConfig {
    @Override
    public void configure(CerbosHelperConfigurer configurer) {
        configurer
                .principalResolver(new SessionCerbosPrincipalResolver())
                .accessDeniedHandler(decision -> new ProjectAccessDeniedException(decision.reason()))
                .autoCheck(auto -> {
                    auto.setIncludeClassNamePatterns(List.of(".*CommandService"));
                    auto.setExcludeClassNamePatterns(List.of(".*UtilService"));
                });
    }
}
```

권장 override는 적용 범위, principal, deny 예외 변환이다. `select`, `find`, `update`, `delete`, `findById` 같은 내부 convention prefix를 프로젝트마다 갈아끼우는 방식은 권장하지 않는다. prefix convention은 Helper 기본 규칙으로 유지하고, 프로젝트는 어떤 service class/method를 스캔할지와 어떤 인증/예외 체계를 쓸지만 조정한다.

`CerbosHelperConfig`에서 설정할 수 있는 항목은 `CerbosAuthorizationClient`, `CerbosPrincipalResolver`, `CerbosAccessDeniedHandler`, `CerbosResourceResolver`, `CerbosResourceColumnRegistry`, `CerbosSqlPredicateInjector`, `CerbosMyBatisInterceptorOrderStrategy`, auto-check include/exclude pattern이다. 다만 `CerbosResourceResolver`, `CerbosResourceColumnRegistry`, `CerbosSqlPredicateInjector`, `CerbosMyBatisInterceptorOrderStrategy`는 복잡 SQL, 비표준 mapper lookup, MyBatis plugin 순서 충돌 같은 고급 예외에서만 사용한다.

자세한 프로젝트 설정 예제는 `docs/PROJECT_CONFIGURATION_KO.md`를 본다.

## 6. 프로젝트 적용 경계

CerbosHelper는 업무 모듈을 대신 구현하는 엔진이 아니라 Spring/MyBatis 실행 지점에 Cerbos 판단을 연결하는 공통 라이브러리다. 실제 프로젝트에서는 다음 경계를 유지한다.

| 영역 | 책임 |
| --- | --- |
| Helper library | `CerbosCommonDto`, `@CerbosId`, resource registry, Service AOP, MyBatis interceptor, Cerbos SDK client, plan-to-SQL 변환, 기본 resource resolver, extension interface 제공 |
| Project common/config | Helper 적용 범위 설정, MyBatis plugin 등록/순서 확인, deny 예외 변환, Spring Security principal 구성, Spring Security 미사용 시 principal resolver 제공, 필요한 경우 고급 extension 연결 |
| Domain module | DTO 상속, `@CerbosId`, `owner_by` / `owner_org_by` 컬럼과 projection, mapper method naming, `{resource}Mapper.findById(...)`, Service `id + dto` signature 정렬 |

예를 들어 RuneHS는 `RunehsCerbosHelperConfig extends CerbosHelperConfig`에서 `CommandService` / `QueryService` 계열만 auto-check 대상으로 묶고 `UtilService`를 제외한다. deny decision도 같은 config에서 표준 `BusinessException`으로 변환한다. 이런 적용 범위와 예외 변환은 Helper 코어를 수정하지 않고 프로젝트 설정으로 조정하는 영역이다.

## 7. Runtime 개입 파일

| 파일 | Runtime 위치 | 역할 |
| --- | --- | --- |
| `autoconfigure/CerbosHelperAutoConfiguration.java` | Spring Boot 시작 | Helper Bean 등록, CommonDto scan, interceptor order verifier 연결 |
| `check/CerbosAutoCheckAspect.java` | Spring AOP | `@Service` 메서드를 가로채 auto-check 설정과 method convention을 평가 |
| `check/CerbosResourceCheckExecutor.java` | Check 실행기 | resource/principal을 준비하고 `CheckResources` allow/deny를 확인 |
| `scope/CerbosMyBatisScopeInterceptor.java` | MyBatis plugin | `Executor.query(...)`의 SELECT `BoundSql`에 `PlanResources` 기반 SQL predicate 적용 |
| `sql/DefaultCerbosSqlPredicateInjector.java` | SQL scope 적용 | 원본 SQL을 Helper alias derived table로 감싸고 Cerbos predicate를 주입 |

실제 Spring AOP Aspect는 `CerbosAutoCheckAspect`다. `CerbosResourceCheckExecutor`는 AOP advice가 아니라 check 실행기이며, 목록 조회 scope는 MyBatis interceptor가 담당한다.
