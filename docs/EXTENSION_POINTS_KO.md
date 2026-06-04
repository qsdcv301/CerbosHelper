# CerbosHelper 확장 포인트

CerbosHelper의 기본 철학은 PageHelper처럼 “기본값은 바로 동작하고, 프로젝트 차이는 strategy Bean으로 교체한다”이다. annotation option을 계속 늘리는 대신, 하드코딩이 될 수 있는 부분은 아래 확장 포인트로 분리한다.

## 기본 사용과 고급 사용

기본 CRUD 프로젝트는 다음만 준비하면 된다.

- `CerbosPrincipalResolver`
- `@CerbosResource`
- `@CerbosScoped`
- `@CerbosCheck`
- `cerboshelper.target`

프로젝트 구조가 기본 convention과 다르면 아래 Bean을 등록한다. 모두 `@ConditionalOnMissingBean` 대상이므로 애플리케이션 Bean이 있으면 기본 구현은 생성되지 않는다.

| 확장 포인트 | 기본 구현 | 교체하는 이유 |
| --- | --- | --- |
| `CerbosAuthorizationClient` | `CerbosSdkAuthorizationClient` | Cerbos SDK channel, mTLS, sidecar, gateway, 테스트 fake client |
| `CerbosPrincipalResolver` | empty resolver | 현재 사용자 principal 연결 |
| `CerbosPayloadMapper` | annotation/reflection mapper | roles, attr, null 처리, policyVersion 정책 변경 |
| `CerbosResourceResolver` | `DefaultCerbosResourceResolver` | 단건/쓰기 resource lookup, before/after 구성 변경 |
| `CerbosResourceColumnRegistry` | `CerbosResourceColumns` | SQL column allowlist 수동 구성 |
| `CerbosSqlPredicateInjector` | `DefaultCerbosSqlPredicateInjector` | `WITH`, `UNION`, aggregate, vendor SQL predicate 삽입 |
| `CerbosMyBatisInterceptorOrderStrategy` | `DefaultCerbosMyBatisInterceptorOrderStrategy` | MyBatis plugin order를 수동 관리하거나 reflection을 쓰지 않으려는 경우 |
| `CerbosAccessDeniedHandler` | `SecurityException` handler | 프로젝트 표준 deny 예외 변환 |

## Resource Resolver

`CerbosResourceResolver`는 `@CerbosCheck`가 검사할 resource 목록을 만든다.

기본 구현은 다음 순서로 resource를 찾는다.

1. `@CerbosCheck(resource = "...")` SpEL
2. `resourceKind/id/mapper/finder`
3. `documentId` 같은 id parameter convention
4. `@CerbosResource`가 붙은 method argument
5. method parameter 이름 `resource`

이 순서가 프로젝트에 맞지 않으면 resolver를 직접 구현한다.

```java
@Bean
CerbosResourceResolver cerbosResourceResolver(DocumentLookupService lookupService) {
    return request -> {
        Object documentId = request.context().variable("documentId");
        if (documentId == null) {
            return List.of();
        }
        return List.of(lookupService.findCerbosSnapshot(documentId));
    };
}
```

## SQL Predicate Injector

`CerbosSqlPredicateInjector`는 원본 MyBatis SQL과 Cerbos predicate를 받아 새 SQL과 parameter 삽입 위치를 반환한다.

```java
public interface CerbosSqlPredicateInjector {
    CerbosSqlInjectionResult inject(String sql, String predicate);
}
```

`parameterInsertionIndex`는 Cerbos positional parameter가 기존 MyBatis parameter mapping 중 어느 위치에 들어가야 하는지를 뜻한다. SQL을 wrapping하거나 predicate 위치를 바꾸는 구현은 이 값을 반드시 정확히 계산해야 한다.

## Interceptor Order Strategy

`CerbosMyBatisInterceptorOrderStrategy`는 PageHelper 같은 MyBatis plugin과 함께 쓸 때 Cerbos interceptor가 기대 순서로 동작하도록 조정한다.

기본 구현은 MyBatis 내부 interceptor chain을 reflection으로 열어 `CerbosMyBatisScopeInterceptor`를 마지막 등록 interceptor로 이동한다. MyBatis plugin chain 특성상 마지막 등록 interceptor가 query 진입 시 먼저 실행되므로, Cerbos predicate가 먼저 합쳐지고 PageHelper가 그 결과로 count/page SQL을 만든다.

프로젝트가 interceptor 등록 순서를 직접 보장한다면 no-op 전략을 등록할 수 있다.

```java
@Bean
CerbosMyBatisInterceptorOrderStrategy cerbosMyBatisInterceptorOrderStrategy() {
    return sqlSessionFactories -> {
        // Application configuration already registers CerbosMyBatisScopeInterceptor last.
    };
}
```

## 설계 원칙

- 전체 조회 fallback은 두지 않는다. 변환 실패는 예외로 처리한다.
- SQL column은 allowlist registry를 통해서만 생성한다.
- project-specific naming convention은 core aspect/interceptor에 넣지 않는다.
- 기본 구현은 작은 CRUD 프로젝트를 위한 convenience layer로 유지한다.
- 복잡한 프로젝트는 strategy Bean을 명시적으로 제공한다.
