# CerbosHelper 프로젝트 설정 가이드

이 문서는 CerbosHelper를 프로젝트에 붙일 때 어떤 항목을 명시 설정하고 어떤 항목이 Helper 엔진 책임인지 정리한다.

## 1. 설정 원칙

CerbosHelper는 Spring Boot + MyBatis 기준의 공통 라이브러리다. 프로젝트는 Helper 내부 엔진을 바꾸기보다 다음 항목을 `CerbosHelperConfig` 한 곳에서 명시한다.

1. 어떤 DTO/resourceKind를 보호 resource로 볼지
2. 어떤 mapper method prefix를 어떤 Cerbos action으로 보낼지
3. 현재 요청 principal을 어디에서 만들지
4. deny 결과를 프로젝트 표준 예외/ErrorCode로 어떻게 바꿀지
5. 고급 SQL 또는 MyBatis plugin 순서 예외가 있는지

Helper 코어에 고정된 기본 계약은 `owner_by`, `owner_group_by` owner column convention으로 제한한다. method prefix와 action 이름은 기본값이 비어 있다. 프로젝트가 `methodRules(...)`에서 명시하지 않으면 mapper read/write scope가 적용되지 않는다.

update/delete/create check 대상 mapper parameter에는 데이터 접근 시점에 신뢰 가능한 `ownerBy` / `ownerGroupBy`가 포함되어야 한다. Helper는 id로 재조회하지 않는다.

## 2. 가장 일반적인 설정

Spring Security를 쓰는 Spring Boot 프로젝트라면 principal은 기본 resolver를 그대로 사용할 수 있다. 프로젝트는 보호 resource, method rule, 예외 변환을 명시한다.

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
                .resources(resources -> {
                    resources.resource("document", DocumentDto.class);
                    resources.resource("calendarEvent", CalendarEventDto.class);
                })
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

이 설정의 의미:

- MyBatis mapper interceptor가 read/write를 보호한다.
- `DocumentDto`, `CalendarEventDto`만 보호 resource로 등록한다.
- `view/create/update/delete` action과 method prefix를 프로젝트 config에서 명시한다.
- PDP 접속값도 `CerbosHelperConfig.client(...)`에서 명시한다.
- Cerbos deny는 프로젝트 예외로 변환한다.
- Spring Security principal은 Helper 기본값을 사용한다.

## 3. Spring Security를 쓰지 않는 프로젝트

Spring Security가 없으면 Helper 기본 principal resolver는 principal을 만들 수 없다. 이 경우 프로젝트 인증/session/request context에서 principal을 만드는 resolver를 제공한다.

```java
@Configuration
public class ProjectCerbosConfig extends CerbosHelperConfig {
    private final CurrentUserContext currentUserContext;

    public ProjectCerbosConfig(CurrentUserContext currentUserContext) {
        this.currentUserContext = currentUserContext;
    }

    @Override
    public void configure(CerbosHelperConfigurer configurer) {
        configurer
                .principalResolver(this::currentPrincipal)
                .accessDeniedHandler(decision -> new ProjectAccessDeniedException(decision.reason()))
                .resources(resources -> resources.resource("document", DocumentDto.class))
                .methodRules(methods -> {
                    methods.scope("document:view", "find", "list");
                    methods.before("document:create", "create", "insert");
                    methods.before("document:update", "update");
                    methods.before("document:delete", "delete");
                });
    }

    private Optional<Object> currentPrincipal() {
        CurrentUser user = currentUserContext.currentUser().orElse(null);
        if (user == null) {
            return Optional.empty();
        }
        Map<String, Object> attr = new LinkedHashMap<>();
        attr.put("userId", String.valueOf(user.userId()));
        attr.put("organizationIds", user.organizationIds());
        return Optional.of(new CerbosPrincipalEnvelope(
                String.valueOf(user.userId()),
                List.of("authenticated"),
                attr,
                "default"
        ));
    }
}
```

이 방식은 Spring Security를 쓰지 않는 JSP/legacy/session 기반 프로젝트를 위한 확장이다. Helper bean config 자체를 바꾸는 것이 아니라 principal 생성 지점만 프로젝트에 맞게 연결한다.

## 4. ErrorCode 매핑 예제

`CerbosDeniedDecision.reason()`은 실패 원인을 구분한다.

| reason | 의미 |
| --- | --- |
| `MISSING_PRINCIPAL` | 현재 요청 principal을 만들 수 없음 |
| `MISSING_OWNER` | 보호 resource에 `ownerBy` 또는 `ownerGroupBy`가 없음 |
| `DENIED` | Cerbos가 명시적으로 deny |

프로젝트는 이 reason을 표준 ErrorCode로 바꿀 수 있다.

```java
@Configuration
public class ProjectCerbosConfig extends CerbosHelperConfig {
    @Override
    public void configure(CerbosHelperConfigurer configurer) {
        configurer.accessDeniedHandler(decision -> switch (decision.reason()) {
            case MISSING_PRINCIPAL -> new BusinessException(ErrorCode.AUTHENTICATION_REQUIRED);
            case MISSING_OWNER -> new BusinessException(ErrorCode.CERBOS_RESOURCE_OWNER_MISSING);
            case DENIED -> new BusinessException(ErrorCode.ACCESS_DENIED);
        });
    }
}
```

프로젝트 ErrorCode가 아직 세분화되어 있지 않다면 우선 모두 `ACCESS_DENIED`로 매핑할 수 있다. 다만 `MISSING_OWNER`는 모듈 계약 위반이므로 운영/디버깅을 위해 별도 ErrorCode로 분리하는 것을 권장한다.

## 5. 고급 extension은 언제 쓰나

아래 extension은 기본 설정으로 쓰지 않는다. `resources(...)`와 `methodRules(...)`로 해결되지 않는 구조적 예외가 있을 때만 사용한다.

| extension | 사용할 때 |
| --- | --- |
| `CerbosResourceColumnRegistry` | 기본 owner column mapping 외에 SQL alias/column allowlist가 필요할 때 |
| `CerbosSqlPredicateInjector` | CTE, UNION, aggregate, 복잡 join 때문에 기본 derived table wrapper가 맞지 않을 때 |
| `CerbosMyBatisInterceptorOrderStrategy` | PageHelper 같은 다른 MyBatis plugin과 순서 충돌이 있을 때 |
| `CerbosAuthorizationClient` | Cerbos 호출 자체를 test double 또는 별도 transport로 바꿔야 할 때 |

고급 extension을 쓰기 전에 먼저 확인할 것:

1. DTO가 `CerbosCommonDto`를 상속하는가
2. `resources(...)`에 resourceKind와 DTO가 등록되어 있는가
3. `methodRules(...)`에 scope/check action과 prefix가 등록되어 있는가
4. SELECT SQL projection에 `owner_by`, `owner_group_by`가 있는가
5. create/update/delete mapper method가 신뢰 가능한 owner DTO를 받는가

## 6. read/update/delete 설정 체크

| 흐름 | 프로젝트 설정 | 모듈 요구사항 |
| --- | --- | --- |
| 목록 read | `resources(...)`, `methodRules.scope(...)`, principal이 필요하다. | mapper 반환 타입이 등록된 보호 DTO이거나 `find{Resource}Ids`처럼 등록 prefix 뒤 resource token을 포함해야 한다. |
| 단건 read | `resources(...)`, `methodRules.scope(...)`, principal이 필요하다. | mapper SELECT가 등록된 prefix에 맞고, SQL projection에 owner column이 포함되어야 한다. |
| update | `resources(...)`, `methodRules.before(...)`, principal이 필요하다. | mapper method가 신뢰 가능한 `ownerBy` / `ownerGroupBy`를 포함한 등록 DTO를 받아야 한다. |
| delete | `resources(...)`, `methodRules.before(...)`, principal이 필요하다. | mapper method가 id만 받으면 안 되고, 신뢰 가능한 owner DTO를 받아야 한다. |
| create | 필요할 때 `methodRules.before(...)`로 명시한다. | 등록하면 mapper method가 신뢰 가능한 owner DTO를 받아야 한다. |

## 7. 비권장 예시

다음 방향은 피한다.

- config 없이 DTO 상속이나 method 이름만으로 보호된다고 가정한다.
- 단위 모듈마다 `CerbosPrincipalResolver`를 만든다.
- owner column 이름을 업무별로 다르게 둔다.
- update/delete mapper가 id만 받고 Helper가 id로 다시 조회해 줄 것이라고 기대한다.
- 복잡 SQL 하나 때문에 전체 SQL injector를 먼저 교체한다.

권장 방향은 Helper 엔진을 그대로 두고, 프로젝트 common/config에서 resource, method rule, 인증/예외 연결을 명시하는 것이다.
