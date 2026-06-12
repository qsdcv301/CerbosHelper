# CerbosHelper 확장 포인트

CerbosHelper의 표준 실행 경로는 MyBatis mapper interceptor 하나다. 보호 대상 DTO가 `CerbosCommonDto`를 상속하고 `CerbosHelperConfig.resources(...)`에 등록되어야 resource payload와 SQL column allowlist에 포함된다.

## 기본값

- `ownerBy`는 SQL column `owner_by`로 매핑한다.
- `ownerGroupBy`는 SQL column `owner_group_by`로 매핑한다.
- owner column 이름은 override 대상이 아니다.
- `resourceKind`와 DTO 타입은 `CerbosHelperConfig.resources(...)`에서 명시한다.
- read action과 mapper prefix는 `methodRules(...).scope(...)`로 명시한다.
- create/update/delete action과 mapper prefix는 `methodRules(...).before(...)`로 명시한다.
- 제외할 mapper method는 `excludePrefixes(...)`, `excludeNames(...)`, `excludeContains(...)`로 명시한다.
- action 이름은 helper core가 정하지 않는다.

## 확장 Bean

| 확장 포인트 | 기본 구현 | 교체 이유 |
| --- | --- | --- |
| `CerbosHelperConfig` | 없음 | 프로젝트 설정을 한 클래스에 모아 resource, method/action, principal, 예외 변환을 연결 |
| `CerbosPrincipalResolver` | Spring Security resolver | Spring Security를 쓰지 않거나 프로젝트 인증/session context에서 principal을 만들어야 할 때 |
| `CerbosAccessDeniedHandler` | `SecurityException` handler | `CerbosDeniedDecision.reason()` 기반 프로젝트 표준 예외 변환 |
| `CerbosResourceColumnRegistry` | `CerbosResourceColumns` | derived table, CTE처럼 원본 resource row가 감춰지는 고급 조회 처리 |
| `CerbosSqlPredicateInjector` | `DefaultCerbosSqlPredicateInjector` | 기본 `SELECT __cerbos_scope.* FROM (...) __cerbos_scope WHERE (...)`와 다른 SQL shape 처리 |
| `CerbosMyBatisInterceptorOrderStrategy` | `DefaultCerbosMyBatisInterceptorOrderStrategy` | PageHelper 같은 MyBatis plugin 순서 수동 관리 |
| `CerbosAuthorizationClient` | Cerbos SDK client | 테스트 더블 또는 별도 transport가 필요할 때 |

신규 도메인은 고급 Bean을 먼저 만들지 않는다. 먼저 `CerbosCommonDto` 상속, `owner_by`/`owner_group_by` 컬럼, `resources(...)`, `methodRules(...)`, mapper method shape을 맞춘다.

## 설정 예시

```java
@Configuration
class ProjectCerbosConfig extends CerbosHelperConfig {
    @Override
    public void configure(CerbosHelperConfigurer configurer) {
        configurer
                .principalResolver(new ProjectPrincipalResolver())
                .accessDeniedHandler(decision -> new ProjectAccessDeniedException(decision.reason()))
                .resources(resources -> resources.resource("document", DocumentDto.class))
                .methodRules(methods -> {
                    methods.excludePrefixes("findAll", "debug", "trace", "admin");
                    methods.scope("document:view", "find", "list");
                    methods.before("document:create", "create", "insert");
                    methods.before("document:update", "update");
                    methods.before("document:delete", "delete", "remove");
                });
    }
}
```

권장 우선순위는 `resources(...)`, `methodRules(...)`, Spring Security 미사용 시 `principalResolver`, 프로젝트 표준 예외로 바꾸는 `accessDeniedHandler`다. SQL shape이나 MyBatis plugin 순서 문제가 반복될 때만 `resourceColumnRegistry`, `sqlPredicateInjector`, `interceptorOrderStrategy`를 교체한다.

## 라이브러리 / 프로젝트 / 모듈 경계

| 상황 | 우선 위치 | 이유 |
| --- | --- | --- |
| 보호 DTO/resourceKind를 등록해야 한다. | 프로젝트 common/config | `CerbosHelperConfig.resources(...)`에 명시한다. |
| method prefix와 Cerbos action을 연결해야 한다. | 프로젝트 common/config | `CerbosHelperConfig.methodRules(...)`에 명시한다. |
| deny 응답을 프로젝트 표준 `ErrorCode`로 바꿔야 한다. | 프로젝트 common/config | `CerbosAccessDeniedHandler`를 교체한다. |
| Spring Security principal에 이미 필요한 attr이 있다. | 라이브러리 기본값 | 기본 resolver가 getter/record/Map 기반 attr을 Cerbos principal attr로 펼친다. |
| Spring Security를 쓰지 않는다. | 프로젝트 common/config | custom `CerbosPrincipalResolver`가 맞다. |
| update/delete mapper가 id만 받는다. | 모듈 수정 | mapper command는 신뢰 가능한 `ownerBy` / `ownerGroupBy`를 가진 등록 DTO를 받아야 한다. |
| 특정 mapper SQL이 join/CTE/aggregate 때문에 owner row projection을 잃는다. | 단위 모듈 우선, 필요 시 프로젝트 common/config | 먼저 query split이나 projection 정렬을 검토하고, 반복되는 패턴이면 SQL 확장 Bean을 교체한다. |
| owner column 이름을 업무별로 바꾸고 싶다. | 설계 재검토 | 현재 Helper 표준은 `owner_by`, `owner_group_by` 고정이다. |
| 새로운 업무 action이나 scope 모델이 필요하다. | 정책/모듈 설계 | Helper는 action/resource 전달 경로를 제공하고, 정책 의미는 Cerbos policy와 업무 모듈이 결정한다. |
| owner 값이 비어 있는 원인을 ErrorCode로 구분해야 한다. | 프로젝트 common/config | `CerbosAccessDeniedHandler`에서 `MISSING_OWNER` reason을 프로젝트 ErrorCode로 매핑한다. |

## Runtime 개입 지점

| 개입 지점 | 파일 | 역할 |
| --- | --- | --- |
| Mapper read/write | `CerbosMyBatisInterceptor` | `Executor.query(...)`에는 plan query SQL predicate를, `Executor.update(...)`에는 command check를 적용 |
| Check 실행 | `CerbosResourceCheckExecutor` | mapper parameter DTO를 그대로 Cerbos에 전달하고 owner 누락을 fail-closed 처리 |
| Resource payload | `CerbosPayloadMapper` | Cerbos SDK 요청/응답 매칭용 synthetic id를 만든다. 정책 판단용 기본키가 아니다. |

정리하면, 라이브러리는 MyBatis interception과 config 기반 rule 엔진을 제공하고, 프로젝트 common/config는 resource/method/action/예외 연결을 담당하며, 단위 모듈은 Helper가 읽을 수 있는 DTO/mapper shape을 맞춘다.
