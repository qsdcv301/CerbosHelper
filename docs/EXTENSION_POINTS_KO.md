# CerbosHelper 확장 포인트

CerbosHelper의 기본 방향은 사내 표준 규칙 자동 적용이다. 보호 대상 DTO가 `CerbosCommonDto`를 상속하면 resource payload, SQL column allowlist, 목록 scope, 단건/쓰기 check가 자동으로 연결된다.

## 기본값

- `ownerBy`는 `users.id`를 담고 SQL column은 `owner_by`다.
- `ownerOrgBy`는 `organizations.id`를 담고 SQL column은 `owner_org_by`다.
- owner column 이름은 override 대상이 아니다. 신규/표준 프로젝트는 보호 대상 테이블에 이 두 컬럼을 둔다.
- `resourceKind`는 DTO 클래스명에서 `Dto` suffix를 제거한 뒤 lower camel로 만든다.
- 기본 SQL scope alias는 `__cerbos_scope`다. 원본 SQL에 같은 identifier가 있으면 suffix를 붙여 충돌을 피한다. custom injector가 alias를 제공하지 않는 경우에는 top-level `FROM` / `JOIN`에서 resource alias를 fallback으로 감지한다.
- `findAll*`, `debug*`, `trace*`, `admin*`은 자동 보호 대상에서 제외한다.
- auto-check 적용 대상은 `CerbosHelperConfig.autoCheck(...)`로 제한한다. 프로젝트 표준은 Java config에 모으는 방식이다.
- `create*`, `insert*`, `save*`는 auto-check 대상이 아니다.
- `find*`, `get*`, `select*` check는 service 반환 DTO를 검사한다.
- `update*`, `delete*` check는 `{resource}Id` 인자 또는 `@CerbosId` DTO id로 기존 row를 조회해 검사한다.

## 확장 Bean

프로젝트 설정의 기본 목적은 Helper 내부 convention을 바꾸는 것이 아니라 프로젝트별 적용 범위와 환경을 맞추는 것이다. 구체 예제는 `PROJECT_CONFIGURATION_KO.md`를 본다.

| 확장 포인트 | 기본 구현 | 교체 이유 |
| --- | --- | --- |
| `CerbosHelperConfig` | 없음 | 프로젝트 설정을 한 클래스에 모아 auto-check 범위, principal, 예외 변환을 연결 |
| `CerbosPrincipalResolver` | Spring Security resolver | Spring Security를 쓰지 않거나 프로젝트 인증/session context에서 principal을 만들어야 할 때 |
| `CerbosAccessDeniedHandler` | `SecurityException` handler | `CerbosDeniedDecision.reason()` 기반 프로젝트 표준 예외 변환 |
| `CerbosResourceResolver` | `DefaultCerbosResourceResolver` | `{resource}Mapper.findById(...)` convention이 맞지 않는 고급 예외 lookup 연결 |
| `CerbosResourceColumnRegistry` | `CerbosResourceColumns` | derived table, CTE처럼 원본 resource row가 감춰지는 고급 조회 처리 |
| `CerbosSqlPredicateInjector` | `DefaultCerbosSqlPredicateInjector` | 기본 `SELECT __cerbos_scope.* FROM (...) __cerbos_scope WHERE (...)`와 다른 SQL shape 처리 |
| `CerbosMyBatisInterceptorOrderStrategy` | `DefaultCerbosMyBatisInterceptorOrderStrategy` | MyBatis plugin 순서 수동 관리 |

신규 도메인은 위 Bean을 만들지 않는다. 먼저 `CerbosCommonDto` 상속, `owner_by`/`owner_org_by` 컬럼, Mapper/Service naming convention을 맞춘다.

프로젝트별 설정을 한 곳에 모으려면 `CerbosHelperConfig`를 상속한다. 설정하지 않은 항목은 Helper 기본값을 그대로 사용한다.

```java
@Configuration
class ProjectCerbosConfig extends CerbosHelperConfig {
    @Override
    public void configure(CerbosHelperConfigurer configurer) {
        configurer
                .principalResolver(new ProjectPrincipalResolver())
                .accessDeniedHandler(decision -> new ProjectAccessDeniedException(decision.reason()))
                .autoCheck(auto -> auto.setIncludeClassNamePatterns(List.of(".*CommandService")));
    }
}
```

권장 우선순위는 `autoCheck(...)` scan 범위 제한, Spring Security 미사용 시 `principalResolver`, 프로젝트 표준 예외로 바꾸는 `accessDeniedHandler`다. `resourceResolver`, `resourceColumnRegistry`, `sqlPredicateInjector`, `interceptorOrderStrategy`는 convention으로 해결되지 않는 고급 예외에서만 사용한다.

`find`, `select`, `update`, `delete`, `findById` prefix 자체를 프로젝트마다 override하는 방향은 피한다. 이 문구들은 Helper convention으로 유지하고, 프로젝트는 보호할 class/method 범위를 include/exclude로 조정한다.

## 라이브러리 / 프로젝트 / 모듈 경계

| 상황 | 우선 위치 | 이유 |
| --- | --- | --- |
| `@Service` 중 일부 class만 auto-check 해야 한다. | 프로젝트 설정 | `CerbosHelperConfig.autoCheck(...)`에서 include/exclude pattern으로 제한한다. |
| deny 응답을 프로젝트 표준 `ErrorCode`로 바꿔야 한다. | 프로젝트 common/config | `CerbosAccessDeniedHandler` Bean만 교체하면 된다. |
| Spring Security principal에 이미 필요한 attr이 있다. | 라이브러리 기본값 | 기본 resolver가 getter/record/Map 기반 attr을 Cerbos principal attr로 펼친다. |
| Spring Security를 쓰지 않는다. | 프로젝트 common/config | custom `CerbosPrincipalResolver`가 맞다. Helper 코어에 프로젝트 인증 지식을 넣지 않는다. |
| principal attr 계산 자체가 프로젝트 인증 구조에 묶여 있다. | 프로젝트 common/config | custom `CerbosPrincipalResolver`가 맞다. Helper 코어에 프로젝트 인증 지식을 넣지 않는다. |
| update/delete 대상 row를 찾는 mapper convention이 다르다. | 프로젝트 common/config | custom `CerbosResourceResolver`로 예외 lookup만 연결한다. |
| 특정 mapper SQL이 join/CTE/aggregate 때문에 owner row projection을 잃는다. | 단위 모듈 우선, 필요 시 프로젝트 common/config | 먼저 query split이나 projection 정렬을 검토하고, 반복되는 패턴이면 `CerbosSqlPredicateInjector` / `CerbosResourceColumnRegistry`를 교체한다. |
| owner column 이름을 업무별로 바꾸고 싶다. | 설계 재검토 | 현재 Helper 표준은 `owner_by`, `owner_org_by` 고정이다. 신규 표준 프로젝트는 컬럼을 맞춘다. |
| 새로운 업무 action이나 scope 모델이 필요하다. | 정책/모듈 설계 | Helper는 action/resource 전달 경로를 제공하고, 정책 의미는 Cerbos policy와 업무 모듈이 결정한다. |
| owner 값이 비어 있는 원인을 ErrorCode로 구분해야 한다. | 프로젝트 common/config | `CerbosAccessDeniedHandler`에서 `MISSING_OWNER` reason을 프로젝트 ErrorCode로 매핑한다. |

## Runtime 개입 지점

| 개입 지점 | 파일 | 확장 판단 |
| --- | --- | --- |
| Service AOP | `CerbosAutoCheckAspect` | 적용 범위 문제는 설정으로 풀고, method convention 자체가 반복적으로 부족할 때만 Helper 변경을 검토한다. |
| Check 실행 | `CerbosResourceCheckExecutor` | deny 변환은 handler, resource lookup은 resolver로 분리한다. |
| Mapper SELECT | `CerbosMyBatisScopeInterceptor` | SQL shape 문제는 injector/column registry 또는 query split로 분리한다. |
| DTO id | `@CerbosId` | 기본키 필드명이 `id`가 아니면 DTO 필드/메서드에 명시한다. MyBatis resultMap은 id 판단에 사용하지 않는다. |

정리하면, 라이브러리는 interception과 convention 엔진을 제공하고, 프로젝트 common/config는 적용 범위와 예외 연결을 담당하며, 단위 모듈은 Helper가 읽을 수 있는 DTO/mapper/service shape을 맞춘다.
