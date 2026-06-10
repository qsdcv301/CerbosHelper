# CerbosHelper 프로젝트 설정 가이드

이 문서는 CerbosHelper를 프로젝트에 붙일 때 어디까지 설정하고 어디부터는 Helper convention을 그대로 둘지 정리한다.

## 1. 설정 원칙

CerbosHelper는 Spring Boot + MyBatis 기준의 공통 라이브러리다. 프로젝트는 Helper 내부 엔진을 바꾸기보다 다음 세 가지를 주로 조정한다.

1. 어떤 `@Service` class/method를 auto-check 대상으로 볼지
2. 현재 요청 principal을 어디에서 만들지
3. deny 결과를 프로젝트 표준 예외/ErrorCode로 어떻게 바꿀지

반대로 아래 항목은 프로젝트마다 바꾸는 기본 설정 대상이 아니다.

- `find*`, `get*`, `select*` -> `view`
- `update*`, `modify*` -> `update`
- `delete*`, `remove*` -> `delete`
- `owner_by`, `owner_org_by` owner column convention

이 convention이 모듈 하나에서 맞지 않으면 먼저 DTO/mapper/service shape을 정렬한다. update/delete check 대상 DTO에는 데이터 접근 시점에 신뢰 가능한 `ownerBy` / `ownerOrgBy`가 포함되어야 한다. 반복되는 구조적 예외일 때만 고급 extension을 사용한다.

## 2. 가장 일반적인 설정

Spring Security를 쓰는 Spring Boot 프로젝트라면 principal은 기본 resolver를 그대로 사용한다. 프로젝트는 보통 auto-check 범위와 예외 변환만 설정하면 된다.

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
                .autoCheck(auto -> {
                    auto.setIncludeClassNamePatterns(List.of(".*CommandService", ".*QueryService"));
                    auto.setExcludeClassNamePatterns(List.of(".*UtilService", ".*SchedulerService"));
                });
    }
}
```

이 설정의 의미:

- `CommandService`, `QueryService` 계열만 Service AOP auto-check 대상으로 본다.
- `UtilService`, scheduler/batch 계열은 자동 check에서 제외한다.
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
                .autoCheck(auto -> auto.setIncludeClassNamePatterns(List.of(".*CommandService", ".*QueryService")));
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
| `MISSING_OWNER` | 보호 resource에 `ownerBy`, `ownerOrgBy`가 모두 없음 |
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

프로젝트 ErrorCode가 아직 세분화되어 있지 않다면 우선 모두 `ACCESS_DENIED`로 매핑하고, 운영/디버깅 요구가 생겼을 때 reason별 코드를 분리한다.

## 5. 고급 extension은 언제 쓰나

아래 extension은 기본 설정으로 쓰지 않는다. convention으로 해결되지 않는 구조적 예외가 있을 때만 사용한다.

| extension | 사용할 때 |
| --- | --- |
| `CerbosResourceResolver` | service method 인자만으로 검사 대상 `CerbosCommonDto`를 만들 수 없을 때 |
| `CerbosResourceColumnRegistry` | 기본 owner column mapping 외에 SQL alias/column allowlist가 필요할 때 |
| `CerbosSqlPredicateInjector` | CTE, UNION, aggregate, 복잡 join 때문에 기본 derived table wrapper가 맞지 않을 때 |
| `CerbosMyBatisInterceptorOrderStrategy` | PageHelper 같은 다른 MyBatis plugin과 순서 충돌이 있을 때 |
| `CerbosAuthorizationClient` | Cerbos 호출 자체를 test double 또는 별도 transport로 바꿔야 할 때 |

고급 extension을 쓰기 전에 먼저 확인할 것:

1. DTO가 `CerbosCommonDto`를 상속하는가
2. SQL projection 또는 service assembly 결과에 `owner_by`, `owner_org_by`가 있는가
3. update/delete service method가 신뢰 가능한 owner DTO를 받는가
4. auto-check include/exclude가 너무 넓거나 좁지 않은가

## 6. read/update/delete 설정 체크

| 흐름 | 프로젝트 설정 | 모듈 convention |
| --- | --- | --- |
| 목록 read | MyBatis interceptor가 등록되어 있고 principal을 만들 수 있어야 한다. | mapper method가 `find/select/list/search`이고 반환 타입이 보호 DTO여야 한다. |
| 단건 read | auto-check 범위에 해당 service가 포함되어야 한다. | service method가 `find/get/select`이고 반환값이 `CerbosCommonDto` 또는 `Optional<CerbosCommonDto>`여야 한다. |
| update | auto-check 범위에 해당 service가 포함되어야 한다. | service method가 `update/modify`이고 신뢰 가능한 `ownerBy` / `ownerOrgBy`를 포함한 `CerbosCommonDto`를 받아야 한다. |
| delete | auto-check 범위에 해당 service가 포함되어야 한다. | service method가 `delete/remove`이고 신뢰 가능한 `ownerBy` / `ownerOrgBy`를 포함한 `CerbosCommonDto`를 받아야 한다. |
| create | 별도 설정하지 않는다. | `create/insert/save`는 auto-check 대상이 아니다. |

## 7. 비권장 예시

다음 방향은 피한다.

- 프로젝트마다 `select`, `find`, `update`, `delete` prefix 의미를 바꾸는 설정을 추가한다.
- 단위 모듈마다 `CerbosPrincipalResolver`를 만든다.
- owner column 이름을 업무별로 다르게 둔다.
- 복잡 SQL 하나 때문에 전체 SQL injector를 먼저 교체한다.

권장 방향은 Helper convention을 유지하고, 프로젝트 common/config에서 scan 범위와 인증/예외 연결만 얇게 조정하는 것이다.
