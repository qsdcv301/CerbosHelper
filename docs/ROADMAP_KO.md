# CerbosHelper 보완 로드맵

이 문서는 PageHelper급 공용 라이브러리 사용감과 추상화 수준을 목표로 할 때 남은 보완 후보를 정리한다.

## 완료된 추상화

| 영역 | 상태 |
| --- | --- |
| Cerbos PDP 호출 | `CerbosAuthorizationClient`로 분리 |
| 현재 principal 해석 | `CerbosPrincipalResolver`로 분리 |
| payload 변환 | `CerbosPayloadMapper`로 분리 |
| 단건/쓰기 resource assembly | `CerbosResourceResolver`로 분리 |
| SQL predicate 생성 | `CerbosPlanToSqlConverter`로 분리 |
| SQL predicate 삽입 | `CerbosSqlPredicateInjector`로 분리 |
| MyBatis interceptor order | `CerbosMyBatisInterceptorOrderStrategy`로 분리 |
| deny 예외 변환 | `CerbosAccessDeniedHandler`로 분리 |
| auto-check 대상 제한 | `CerbosHelperConfig.autoCheck(...)`로 분리 |
| 프로젝트 설정 진입점 | `CerbosHelperConfig`, `CerbosHelperConfigurer`로 분리 |
| DTO id 명시 | 제거. Helper는 DTO 기본키를 요구하지 않고 owner attribute만 검사 |
| read check 최적화 | 단건 read는 service 반환 DTO 기준으로 검사 |
| write check resource 보강 | update/delete service DTO owner 검사 지원 |
| create check 제외 | `create*`, `insert*`, `save*` auto-check 제외 |

## 우선순위 높음

| 항목 | 이유 | 방향 |
| --- | --- | --- |
| 고급 SQL injector 구현 | `WITH`, `UNION`, aggregate는 실제 수요가 높다. | `CteTarget`, `UnionBranch` injector를 별도 구현과 테스트로 제공 |
| resolver 전용 테스트 | `DefaultCerbosResourceResolver`가 기본 CRUD 사용감의 핵심이다. | DTO 인자/resource expression/custom resolver 케이스 테스트 |
| interceptor order 전략 테스트 | PageHelper 호환의 핵심이다. | default strategy와 no-op strategy를 분리 검증 |

## 우선순위 중간

| 항목 | 이유 | 방향 |
| --- | --- | --- |
| null attr 정책 | Cerbos SDK attr builder는 null을 보내지 않는다. | null 포함/제외 property 또는 payload strategy 제공 |
| parameter name strategy | `cp0`는 합리적 기본값이지만 프로젝트별 prefix 요구가 있을 수 있다. | `CerbosSqlParameterNameStrategy` 추가 |
| SQL dialect 명시 | 문자열 기반 injector는 dialect별 차이에 취약하다. | dialect별 injector 또는 parser 기반 구현 제공 |
| observability hook | 운영 장애 분석에는 plan/predicate/decision 관찰이 필요하다. | 마스킹 가능한 listener interface 추가 |
| batch check 최적화 | 여러 resource check를 개별 호출하면 비용이 커질 수 있다. | `CerbosResourceCheckExecutor`에서 batch check 옵션 제공 |

## 우선순위 낮음

| 항목 | 이유 | 방향 |
| --- | --- | --- |
| check payload 전략 분리 | synthetic resource id나 attr 포함 정책을 바꿔야 할 수 있다. | payload strategy interface 추가 |
| Spring Security adapter | core는 framework 중립을 유지해야 한다. | 별도 예제 또는 optional adapter 문서 |
| 문서 예제 프로젝트 분리 | README가 길어질 수 있다. | advanced docs와 sample apps를 분리 유지 |

## 유지 원칙

- 하드코딩을 core runtime에 추가하지 않는다.
- convention은 `Default...` 구현 안에만 둔다.
- 복잡한 요구는 strategy Bean으로 푼다.
- 실패 시 전체 조회 fallback은 제공하지 않는다.
- 테스트 fixture 없이 SQL shape 지원을 늘리지 않는다.
