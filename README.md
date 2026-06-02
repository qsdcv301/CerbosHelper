# CerbosHelper

Spring Boot + MyBatis 프로젝트에서 Cerbos 권한 범위를 annotation 중심으로 적용하는 라이브러리다.

일반 사용 흐름에서는 `CerbosAuthorizationClient`, `CerbosPlanToSqlConverter`, Cerbos 요청 JSON, column registry를 직접 작성하지 않는다.

`v1.0.1`부터 Cerbos PDP 통신은 공식 Cerbos Java SDK를 사용한다. 이 라이브러리의 목적은 SDK를 숨기는 것이 아니라, SDK가 제공하는 `CheckResources` / `PlanResources`를 Spring Boot + MyBatis + PostgreSQL + PageHelper 흐름에 자연스럽게 연결하는 것이다.

핵심 사용 방식은 다음 네 가지다.

1. 현재 사용자 principal을 `CerbosPrincipalResolver` Bean으로 제공한다.
2. 정책 대상 DTO/record/class에 `@CerbosResource`를 붙인다.
3. 목록 Mapper에 `@CerbosScoped`를 붙인다.
4. 단건/쓰기 서비스 메서드에 `@CerbosCheck`를 붙인다.

## 0. 동작 구조

CerbosHelper는 다음 책임을 나눠 가진다.

| 영역 | 담당 |
|------|------|
| Cerbos PDP 통신 | 공식 `dev.cerbos:cerbos-sdk-java` |
| 현재 사용자 해석 | 애플리케이션의 `CerbosPrincipalResolver` Bean |
| principal/resource payload 생성 | `CerbosPayloadMapper` |
| 복잡한 principal envelope | `CerbosPrincipalEnvelope` |
| 목록 권한 범위 조회 | `@CerbosScoped` + MyBatis interceptor |
| Cerbos Plan -> SQL WHERE | `CerbosPlanToSqlConverter` |
| 단건/쓰기 권한 검사 | `@CerbosCheck` + AOP |
| deny 예외 변환 | `CerbosAccessDeniedHandler` |
| PageHelper 호환 | interceptor order verifier |

목록 조회 흐름은 다음과 같다.

```text
Service calls mapper
-> MyBatis Executor query intercepted
-> CerbosPrincipalResolver resolves current principal
-> official Cerbos SDK calls PlanResources over gRPC
-> Cerbos Plan AST is converted to SQL WHERE
-> MyBatis query continues
-> PageHelper count/page SQL runs against the scoped SQL
```

단건/쓰기 흐름은 다음과 같다.

```text
Service method annotated with @CerbosCheck
-> existing resource is inferred from documentId-style parameter when needed
-> official Cerbos SDK calls CheckResources over gRPC
-> denied requests fail before service mutation proceeds
```

개발자는 일반적으로 공식 `CerbosBlockingClient`, `CerbosAuthorizationClient`, `CerbosPlanToSqlConverter`를 직접 주입하지 않는다.

## 1. 설치

`settings.gradle`에 JitPack 저장소를 추가한다.

```groovy
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }
}
```

`build.gradle`에 의존성을 추가한다.

```groovy
dependencies {
    implementation 'com.github.qsdcv301:CerbosHelper:v1.0.3'
}
```

CerbosHelper `v1.0.3`는 내부적으로 다음 Cerbos SDK 계열 의존성을 사용한다.

```groovy
implementation 'dev.cerbos:cerbos-sdk-java:0.18.0'
implementation 'io.grpc:grpc-core:1.79.0'
implementation 'com.google.protobuf:protobuf-java-util:4.33.5'
```

일반 애플리케이션은 위 의존성을 직접 추가하지 않아도 된다. 다만 이미 gRPC/protobuf를 직접 쓰는 프로젝트라면 effective dependency version이 충돌하지 않는지 `./gradlew dependencies`로 확인하는 것을 권장한다.

Cerbos PDP gRPC target을 설정한다. 공식 Java SDK는 gRPC 포트인 `3593`을 사용한다.

```yaml
cerboshelper:
  target: ${CERBOS_TARGET:localhost:3593}
```

기존 REST 설정과의 호환을 위해 `base-url`만 있으면 host를 읽어 `3593` target으로 변환한다. 신규 프로젝트에서는 `target`을 직접 쓰는 것을 권장한다.

설정 키는 다음과 같다.

| key | 기본값 | 설명 |
|-----|--------|------|
| `cerboshelper.target` | 비어 있음 | 공식 SDK가 연결할 gRPC target. 예: `localhost:3593`, `cerbos:3593` |
| `cerboshelper.base-url` | `http://localhost:3592` | 기존 REST 설정 호환용. `target`이 없을 때 host를 읽어 `3593`으로 변환 |
| `cerboshelper.plaintext` | `true` | 로컬/내부망 PDP에 plaintext gRPC 연결 |
| `cerboshelper.insecure` | `false` | TLS 사용 시 insecure trust 설정 |
| `cerboshelper.timeout` | `1s` | SDK blocking call timeout |
| `cerboshelper.policy-version` | `default` | principal/resource policy version |
| `cerboshelper.principal-roles` | `authenticated` | Cerbos principal top-level roles |

Docker Compose에서 Cerbos를 함께 띄우는 경우 예시는 다음과 같다.

```yaml
services:
  app:
    environment:
      CERBOS_TARGET: cerbos:3593

  cerbos:
    image: ghcr.io/cerbos/cerbos:0.53.0
    ports:
      - "3592:3592"
      - "3593:3593"
```

파라미터 이름 기반 convention을 쓰려면 Java 컴파일에 `-parameters`를 켠다.

```groovy
tasks.withType(JavaCompile).configureEach {
    options.compilerArgs.add('-parameters')
}
```

## 2. 5분 Quick Start

가장 작은 구성은 다음 순서로 만든다.

1. `settings.gradle`에 JitPack을 추가한다.
2. `build.gradle`에 `CerbosHelper`를 추가한다.
3. `application.yml`에 `cerboshelper.target`을 설정한다.
4. 현재 사용자를 반환하는 `CerbosPrincipalResolver` Bean을 만든다.
5. 정책 대상 DTO에 `@CerbosResource`를 붙인다.
6. 목록 Mapper에 `@CerbosScoped(action = "view")`를 붙인다.
7. 단건/쓰기 Service 메서드에 `@CerbosCheck(action = "...")`를 붙인다.
8. Cerbos policy에서 `request.principal.attr.*`, `request.resource.attr.*` 이름을 Java 필드명과 맞춘다.

최소 설정 예시는 다음과 같다.

```yaml
cerboshelper:
  target: ${CERBOS_TARGET:cerbos:3593}
  plaintext: true
  timeout: 1s
```

```java
@Component
public class SecurityCerbosPrincipalResolver implements CerbosPrincipalResolver {
    @Override
    public Optional<Object> currentPrincipal() {
        CurrentUser user = CurrentUserContext.require();
        return Optional.of(new CerbosPrincipalEnvelope(
                user.id(),
                user.roles(),
                Map.of(
                        "tenantId", user.tenantId(),
                        "companyIds", user.companyIds(),
                        "organizationIds", user.organizationIds()
                ),
                "default"
        ));
    }
}
```

```java
@CerbosResource(kind = "document")
public record Document(
        long id,
        long tenantId,
        long companyId,
        long organizationId,
        String ownerUserId,
        String visibility,
        String status
) {
    public Document withId(long id) {
        return new Document(id, tenantId, companyId, organizationId, ownerUserId, visibility, status);
    }
}
```

```java
@Mapper
public interface DocumentMapper {
    Optional<Document> findById(long documentId);

    @CerbosScoped(action = "view")
    List<Document> findDocuments();

    int update(Document document);
}
```

```xml
<select id="findDocuments" resultType="com.example.Document">
    SELECT
        document.id,
        document.tenant_id,
        document.company_id,
        document.organization_id,
        document.owner_user_id,
        document.visibility,
        document.status
    FROM documents document
    WHERE document.deleted = false
    ORDER BY document.id
</select>
```

```java
@Service
public class DocumentService {
    private final DocumentMapper documentMapper;

    public DocumentService(DocumentMapper documentMapper) {
        this.documentMapper = documentMapper;
    }

    public PageInfo<Document> findDocuments(int pageNum, int pageSize) {
        return PageHelper.startPage(pageNum, pageSize)
                .doSelectPageInfo(documentMapper::findDocuments);
    }

    @CerbosCheck(action = "view")
    public Document findDocument(long documentId) {
        return documentMapper.findById(documentId).orElseThrow();
    }

    @CerbosCheck(action = "update")
    public Document updateDocument(long documentId, Document document) {
        Document updated = document.withId(documentId);
        documentMapper.update(updated);
        return documentMapper.findById(documentId).orElseThrow();
    }
}
```

이 구성에서 목록 조회는 `PlanResources`로 SQL WHERE를 자동 추가하고, 단건/수정은 `CheckResources`로 서비스 본문 실행 전에 차단한다.

## 3. 최소 Cerbos Policy 예제

다음 policy는 위 `Document` 예제와 맞는 최소 형태다.

```yaml
apiVersion: api.cerbos.dev/v1
resourcePolicy:
  version: default
  resource: document
  rules:
    - actions: ["view"]
      effect: EFFECT_ALLOW
      condition:
        match:
          any:
            of:
              - expr: request.resource.attr.visibility == "PUBLIC"
              - expr: request.resource.attr.ownerUserId == request.principal.id
              - expr: request.resource.attr.companyId in request.principal.attr.companyIds

    - actions: ["update", "delete"]
      effect: EFFECT_ALLOW
      condition:
        match:
          all:
            of:
              - expr: request.resource.attr.ownerUserId == request.principal.id
              - expr: request.resource.attr.status != "DELETED"

    - actions: ["create"]
      effect: EFFECT_ALLOW
      condition:
        match:
          expr: request.resource.attr.companyId in request.principal.attr.companyIds
```

정책 작성 시 이름을 다음처럼 맞춘다.

| Java field | Cerbos policy 변수 | 기본 SQL column |
|------------|--------------------|-----------------|
| `companyId` | `request.resource.attr.companyId` | `document.company_id` |
| `organizationId` | `request.resource.attr.organizationId` | `document.organization_id` |
| `ownerUserId` | `request.resource.attr.ownerUserId` | `document.owner_user_id` |
| principal `id` | `request.principal.id` | SQL 변환 대상 아님 |
| principal attr `companyIds` | `request.principal.attr.companyIds` | SQL 변환 시 값으로 사용 |

CerbosHelper는 `request.resource.attr.*`를 SQL column으로 바꾼다. `request.principal.*`는 SQL column이 아니라 plan 생성 시 값으로 접힌 조건으로 내려온다.

## 4. 리소스 선언

권한 정책 대상 객체에 `@CerbosResource`를 붙인다.

```java
@CerbosResource(kind = "document")
public record Document(
        long id,
        long companyId,
        long siteId,
        long organizationId,
        String ownerUserId,
        String visibility,
        String status
) {
}
```

기본 변환 규칙은 다음과 같다.

```text
resource kind: document
Java field: ownerUserId
Cerbos attr: request.resource.attr.ownerUserId
SQL column: document.owner_user_id
```

SQL alias가 다를 때만 `sqlAlias`를 쓴다.

```java
@CerbosResource(kind = "document", sqlAlias = "d")
```

필드명과 Cerbos attribute명 또는 DB 컬럼명이 다를 때만 `@CerbosAttribute`를 쓴다.

```java
@CerbosAttribute(value = "ownerUserId", column = "document.owner_user_id")
String ownerId
```

SQL 변환 대상에서 제외할 필드는 `ignore = true`를 쓴다.

```java
@CerbosAttribute(ignore = true)
String displayOnlyText
```

principal과 resource는 같은 attribute mapper를 사용한다. 즉 Java 객체의 필드, record component, getter, `@CerbosAttribute`가 Cerbos `attr`로 전달된다.

| Java 값 | Cerbos attr 처리 |
|---------|------------------|
| `String`, `char` | string |
| `Number` | number |
| `boolean` | bool |
| `List` / `Iterable` | list |
| `Map` | map |
| 기타 객체 | `toString()` |
| `null` | SDK builder 제약상 attr에서 제외 |

정책에서 null 자체를 중요한 조건으로 다뤄야 한다면, 애플리케이션 DTO에서 `hasXxx`, `xxxPresent`, `xxxStatus` 같은 명시적 필드를 두는 방식을 권장한다.

## 5. 목록 조회

Mapper 조회 메서드에 `@CerbosScoped`를 붙인다.

```java
@Mapper
public interface DocumentMapper {
    @CerbosScoped(action = "view")
    List<Document> findDocuments();
}
```

반환 타입이 `@CerbosResource(kind = "document")`이면 `resourceKind`는 자동으로 추론된다. 반환 타입만으로 추론할 수 없는 메서드에서는 직접 적는다.

```java
@CerbosScoped(resourceKind = "document", action = "view")
List<Long> findDocumentIds();
```

Mapper SQL은 가능하면 resource kind와 같은 alias를 쓴다.

```xml
<select id="findDocuments" resultType="com.example.Document">
    SELECT
        document.id,
        document.company_id,
        document.organization_id,
        document.owner_user_id
    FROM documents document
    ORDER BY document.id
</select>
```

서비스에서는 일반 PageHelper 사용처럼 조회만 호출한다. `CerbosPrincipalResolver` Bean이 있으면 현재 principal은 자동으로 해석된다.

```java
public PageInfo<Document> findVisibleDocuments(int pageNum, int pageSize) {
    return PageHelper.startPage(pageNum, pageSize)
            .doSelectPageInfo(documentMapper::findDocuments);
}
```

같은 Mapper 메서드를 여러 action으로 재사용해야 하는 예외 케이스에서는 서비스 메서드에 `@CerbosScope(action = "...")`를 붙여 action/principal을 명시적으로 덮어쓸 수 있다.

PageHelper와 같이 쓰면 Cerbos scope 조건이 먼저 SQL에 반영되고, PageHelper count/page SQL은 권한 범위가 적용된 SQL을 기준으로 생성된다.

정상 기동 시 다음 로그가 나온다.

```text
cerboshelper.mybatis.interceptor-order [PageInterceptor, CerbosMyBatisScopeInterceptor]
```

이 로그에서 `CerbosMyBatisScopeInterceptor`가 마지막에 있어야 한다. MyBatis plugin chain 특성상 마지막에 등록된 interceptor가 query 진입 시 먼저 실행되므로, Cerbos WHERE가 먼저 합쳐지고 PageHelper가 그 결과를 기준으로 count/page SQL을 만든다.

## 6. 단건 권한 체크

`@CerbosCheck`는 서비스 메서드에서 사용한다. 일반적으로 action만 적고, 나머지는 convention으로 맞춘다.

생성처럼 요청 객체 자체를 검사하면 action만 적는다.

```java
@CerbosCheck(action = "create")
public Document createDocument(Document document) {
    documentMapper.insert(document);
    return documentMapper.findById(document.id()).orElseThrow();
}
```

기존 row를 읽어서 검사하는 단건 조회도 action만 적는다.

```java
@CerbosCheck(action = "view")
public Document findVisibleDocument(long documentId) {
    return documentMapper.findById(documentId).orElseThrow();
}
```

이 경우 기본 convention은 다음과 같다.

```text
resourceKind = "document"
id = "documentId"
=> documentMapper.findById(documentId)
```

프로젝트의 Mapper 이름이나 조회 메서드명이 다르면 필요한 값만 덮어쓴다.

```java
@CerbosCheck(action = "view", resourceKind = "document", id = "documentId", mapper = "documentQueryMapper", finder = "selectById")
public Document findVisibleDocument(long documentId) {
    return documentQueryMapper.selectById(documentId).orElseThrow();
}
```

수정처럼 기존 row와 변경 후 row를 모두 검사해야 하는 경우에도 action만 적는다. `documentId`와 `Document document`가 같이 있으면 기존 row와 변경 후 row를 모두 검사한다.

```java
@CerbosCheck(action = "update")
public Document updateDocument(long documentId, Document document) {
    Document after = document.withId(documentId);
    documentMapper.update(after);
    return documentMapper.findById(documentId).orElseThrow();
}
```

`withId(...)` 메서드가 있으면 변경 후 객체 검사 전에 id를 자동으로 적용한다.

삭제는 기존 row를 읽어 검사하는 형태를 권장한다.

```java
@CerbosCheck(action = "delete")
public void deleteDocument(long documentId) {
    documentMapper.delete(documentId);
}
```

자주 쓰는 패턴은 다음과 같다.

| 패턴 | 예시 | 동작 |
|------|------|------|
| 생성 요청 객체 검사 | `@CerbosCheck(action = "create") create(Document document)` | `@CerbosResource`가 붙은 인자를 resource로 검사 |
| 단건 조회 | `@CerbosCheck(action = "view") find(long documentId)` | `documentMapper.findById(documentId)`로 기존 row 검사 |
| 수정 | `@CerbosCheck(action = "update") update(long documentId, Document document)` | 기존 row와 변경 후 객체를 모두 검사 |
| 삭제 | `@CerbosCheck(action = "delete") delete(long documentId)` | 기존 row를 먼저 검사 |
| Mapper 이름 예외 | `@CerbosCheck(action = "view", resourceKind = "document", id = "documentId", mapper = "documentQueryMapper")` | 기본 `documentMapper` convention 대신 지정한 Bean 사용 |
| 조회 메서드명 예외 | `@CerbosCheck(action = "view", resourceKind = "document", id = "documentId", finder = "selectById")` | 기본 `findById` 대신 지정한 메서드 사용 |
| 복잡한 예외 | `@CerbosCheck(action = "view", resource = "@documentMapper.findById(#documentId).orElseThrow()")` | SpEL로 직접 resource 해석 |

일반 CRUD에서는 `resource` SpEL을 먼저 쓰지 말고, `documentId` / `documentMapper.findById(...)` convention을 우선 사용한다.

## 7. Principal Convention

권장 방식은 `CerbosPrincipalResolver` Bean을 하나 제공하는 것이다. 각 애플리케이션은 Spring Security, request context, argument resolver 등 자기 인증 구조에 맞게 현재 사용자 객체를 반환하면 된다.

`CerbosPrincipalResolver`는 상속해서 확장하는 클래스가 아니라 인터페이스다. 애플리케이션에서 구현 Bean을 하나 등록하면 CerbosHelper의 기본 empty resolver가 자동으로 대체된다.

```java
@Component
public class SecurityCerbosPrincipalResolver implements CerbosPrincipalResolver {
    @Override
    public Optional<Object> currentPrincipal() {
        return Optional.of(SecurityContextHolder.getContext().getAuthentication().getPrincipal());
    }
}
```

resolver가 없거나 현재 principal을 반환하지 못하면 메서드 파라미터 `principal`을 찾고, 그래도 없으면 첫 번째 인자를 현재 사용자 객체로 사용한다. 하지만 공용 라이브러리 사용에서는 `CerbosPrincipalResolver` Bean 등록을 권장한다.

`userId`, `accountId`, `memberNo` 같은 애플리케이션별 식별자를 현재 사용자 객체로 바꾸는 일은 각 애플리케이션의 controller, argument resolver, security context resolver 같은 경계에서 처리한다. CerbosHelper는 특정 사용자 모델이나 식별자 이름에 종속되지 않는다.

resolver를 제공하면 다음처럼 principal 파라미터 없이 사용할 수 있다.

```java
@CerbosCheck(action = "create")
public Document createDocument(Document document) {
    ...
}
```

principal 객체는 `id` 필드를 principal id로 사용한다. 필드명이 다르면 `@CerbosAttribute("id")`로 매핑한다. 나머지 필드는 `request.principal.attr.*`로 전달된다.

```java
public record CurrentUser(
        String id,
        List<String> permissions,
        List<Long> companyIds,
        List<Long> organizationIds
) {
}
```

```java
public record CurrentUser(
        @CerbosAttribute("id")
        String accountNo,
        List<String> permissions
) {
}
```

top-level Cerbos role은 기본적으로 `authenticated`를 사용한다. 필요하면 설정으로 바꾼다.

```yaml
cerboshelper:
  principal-roles:
    - authenticated
```

### 복잡한 Principal

프로젝트에 따라 principal은 단순한 `id + roles`가 아닐 수 있다. 예를 들어 tenant, company, site, organization, admin scope, delegated scope, system role 같은 데이터를 이미 인증 모듈에서 하나의 권한 snapshot으로 만들고 있을 수 있다.

이 경우 `CerbosPrincipalEnvelope`를 반환하면 custom `CerbosPayloadMapper` 없이도 `id`, `roles`, `attr`, `policyVersion`을 그대로 Cerbos에 전달할 수 있다.

```java
@Component
public class SecurityCerbosPrincipalResolver implements CerbosPrincipalResolver {
    private final RequestContextResolver requestContextResolver;

    public SecurityCerbosPrincipalResolver(RequestContextResolver requestContextResolver) {
        this.requestContextResolver = requestContextResolver;
    }

    @Override
    public Optional<Object> currentPrincipal() {
        AuthenticatedUser user = requestContextResolver.requireUser();
        return Optional.of(new CerbosPrincipalEnvelope(
                String.valueOf(user.userId()),
                user.cerbosRoles(),
                Map.of(
                        "tenantId", user.tenantId(),
                        "tenantIds", user.tenantIds(),
                        "tenantAdmin", user.tenantAdmin(),
                        "companyIds", user.companyIds(),
                        "siteIds", user.siteIds(),
                        "organizationIds", user.organizationIds(),
                        "organizationAdminIds", user.organizationAdminIds(),
                        "organizationRepIds", user.organizationRepIds()
                ),
                "default"
        ));
    }
}
```

`CerbosPrincipalEnvelope`는 다음 값을 가진다.

| field | 설명 |
|-------|------|
| `id` | Cerbos principal id. 필수 |
| `roles` | Cerbos top-level roles. 예: `authenticated`, `SYSTEM_ADMIN`, tenant role code |
| `attr` | `request.principal.attr.*`로 전달할 map |
| `policyVersion` | principal policy version |

`attr`에 null 값이 포함될 수는 있지만, 공식 SDK builder로 전송할 때 null attr는 제외된다. 정책에서 null 자체를 판단해야 하면 `hasXxx`, `xxxPresent`, `xxxStatus` 같은 명시적 attr를 추가하는 방식을 권장한다.

## 8. Override 문법

대부분은 convention으로 처리한다. 그래도 직접 지정이 필요하면 `mapper` / `finder` 또는 SpEL을 사용할 수 있다.

```text
@beanName     Spring bean 참조
#paramName    메서드 파라미터 참조
```

예시는 다음과 같다.

```java
@CerbosCheck(
        action = "view",
        principal = "#currentUser",
        resource = "@documentMapper.findById(#documentId).orElseThrow()"
)
```

이 방식은 복잡한 예외 케이스용이다. 일반 CRUD에서는 `CerbosPrincipalResolver` Bean을 등록하고 짧은 annotation만 쓰는 방식을 권장한다.

우선순위는 다음과 같다.

| 대상 | 우선순위 |
|------|----------|
| principal | annotation expression -> `principal` 파라미터 -> `CerbosPrincipalResolver` -> 첫 번째 인자 |
| resource | annotation `resource` -> `resourceKind/id/mapper/finder` -> `documentId` 같은 `resourceId` 파라미터 convention -> `@CerbosResource` 인자 |
| list resourceKind | `@CerbosScoped(resourceKind = "...")` -> Mapper 반환 타입의 `@CerbosResource(kind = "...")` |

복잡한 예외를 처리할 수는 있지만, 기본 권장 형태는 짧은 annotation이다.

```java
@CerbosScoped(action = "view")
List<Document> findDocuments();

@CerbosCheck(action = "update")
public Document updateDocument(long documentId, Document document) {
    ...
}
```

## 9. 지원하는 Plan 표현

현재 SQL 변환이 지원하는 Cerbos Plan 표현은 다음과 같다.

- `and`
- `or`
- `not`
- `eq`
- `ne` / `neq`
- `lt`
- `le` / `lte`
- `gt`
- `ge` / `gte`
- `in`
- `null` equality 비교
- `KIND_ALWAYS_ALLOWED`
- `KIND_ALWAYS_DENIED`
- `KIND_CONDITIONAL`

SQL 병합은 기존 `WHERE`와 top-level `ORDER BY`를 기준으로 처리한다. 현재 권장 SQL 형태는 PostgreSQL의 일반적인 단일 `SELECT ... FROM ... WHERE ... ORDER BY ...` 조회다.

다음 SQL은 적용 전에 별도 검증하거나 Mapper에서 명시적으로 분리하는 것을 권장한다.

- `WITH`
- `UNION`
- 복잡한 nested subquery
- vendor-specific syntax
- 컬럼 대 컬럼 비교
- resource row가 아닌 aggregate/grouped 결과

지원하지 않는 Cerbos 변수나 operator가 나오면 전체 조회로 fallback하지 않고 예외를 발생시킨다. 이때 리소스 객체의 `@CerbosResource`, `@CerbosAttribute`, SQL alias, 정책의 `request.resource.attr.*` 이름이 서로 맞는지 먼저 확인한다.

CerbosHelper는 안전하지 않은 plan을 넓은 조회로 바꾸지 않는다. 변환할 수 없는 plan은 실패시키는 것이 기본 철학이다. 권한 필터가 실패했는데 전체 데이터를 반환하는 동작은 허용하지 않는다.

## 10. 기본 사용에서 직접 다루지 않는 것

일반 사용 경로에서는 다음을 직접 작성하지 않는다.

- `CerbosAuthorizationClient`
- 공식 `CerbosBlockingClient`
- `CerbosPlanToSqlConverter`
- Cerbos 요청 JSON
- principal/resource payload mapper
- resource column registry
- MyBatis interceptor 순서 조정

CerbosHelper 내부 통신은 공식 Cerbos Java SDK를 사용한다. 개발자는 SDK client를 직접 주입하지 않아도 되고, 필요한 경우 `CerbosAuthorizationClient` Bean을 직접 등록해 기본 SDK 구현을 대체할 수 있다.

기본 구현은 `CerbosSdkAuthorizationClient`다. 직접 대체가 필요한 예시는 다음과 같다.

- 조직 표준 gRPC channel 설정을 반드시 써야 하는 경우
- mTLS 인증서 로딩을 별도 보안 모듈에서 관리하는 경우
- Cerbos Hub / custom gateway / sidecar 정책에 맞춘 header나 interceptor가 필요한 경우
- 테스트에서 PDP 없이 결정 결과를 고정하고 싶은 경우

이 경우 애플리케이션에서 `CerbosAuthorizationClient` Bean을 직접 등록하면 auto configuration의 기본 SDK client는 생성되지 않는다.

## 11. Deny 예외 커스터마이징

기본적으로 `@CerbosCheck`에서 deny가 발생하면 `SecurityException`을 던진다.

프로젝트의 API 오류 계약이 다르면 `CerbosAccessDeniedHandler` Bean을 등록한다. 예를 들어 Spring Security의 `AccessDeniedException`을 쓰고 싶으면 다음처럼 등록한다.

```java
@Bean
CerbosAccessDeniedHandler cerbosAccessDeniedHandler() {
    return decision -> new AccessDeniedException(decision.message());
}
```

업무 예외 타입을 써야 하는 프로젝트도 같은 방식으로 연결한다.

```java
@Bean
CerbosAccessDeniedHandler cerbosAccessDeniedHandler() {
    return decision -> new BusinessException(ErrorCode.ACCESS_DENIED, decision.message());
}
```

handler는 `CerbosDeniedDecision`을 받는다.

| field | 설명 |
|-------|------|
| `action` | deny된 action |
| `principal` | 실제 principal 객체 |
| `resource` | 실제 resource 객체 |
| `principalDescription` | 로그/메시지용 principal 요약 |
| `resourceDescription` | 로그/메시지용 resource 요약 |

이 hook은 `@CerbosScoped` 목록 조회가 아니라 `@CerbosCheck` 단건/쓰기 guard에 적용된다. 목록 조회는 Cerbos Plan을 SQL로 변환해 허용된 row만 반환하므로 개별 row deny 예외를 던지지 않는다.

## 12. 다른 프로젝트에 붙일 때 필요한 것

필수 작업은 다음이다.

1. JitPack repository와 `CerbosHelper` dependency를 추가한다.
2. `cerboshelper.target`을 설정한다.
3. Java compiler option `-parameters`를 켠다.
4. 현재 사용자 객체를 반환하는 `CerbosPrincipalResolver` Bean을 등록한다.
5. Cerbos 정책 대상 객체에 `@CerbosResource`를 붙인다.
6. 목록 조회 Mapper에 `@CerbosScoped`를 붙인다.
7. 단건/쓰기 서비스 메서드에 `@CerbosCheck`를 붙인다.
8. Cerbos 정책의 `request.resource.attr.*` 이름과 Java field 이름이 맞는지 확인한다.
9. Mapper SQL alias가 기본값과 다르면 `@CerbosResource(sqlAlias = "...")` 또는 `@CerbosAttribute(column = "...")`로 맞춘다.

선택 작업은 다음이다.

- Mapper Bean 이름이 `documentMapper` convention과 다르면 `mapper = "..."`를 지정한다.
- 단건 조회 메서드가 `findById`가 아니면 `finder = "..."`를 지정한다.
- 반환 타입만으로 resource kind를 알 수 없는 Mapper는 `@CerbosScoped(resourceKind = "...")`를 지정한다.
- top-level Cerbos role이 `authenticated`가 아니면 `cerboshelper.principal-roles`를 설정한다.
- 복잡한 principal envelope를 그대로 보내야 하면 `CerbosPrincipalEnvelope`를 반환한다.
- deny 예외를 프로젝트 표준으로 바꾸려면 `CerbosAccessDeniedHandler` Bean을 등록한다.

문제가 생겼을 때는 실패 메시지에서 다음 항목을 먼저 확인한다.

- principal이 해석되었는지
- `@CerbosResource`가 붙어 있는지
- `resourceKind`, `id`, `mapper`, `finder` convention이 맞는지
- Java compiler `-parameters`가 켜져 있는지
- Cerbos plan의 attr 이름이 `@CerbosResource` / `@CerbosAttribute` 매핑과 맞는지
- PageHelper와 함께 쓸 때 기동 로그에 `CerbosMyBatisScopeInterceptor`가 마지막에 있는지

## 13. 1.0.0에서 1.0.1로 올릴 때

`1.0.1`은 외부 annotation API를 유지하면서 내부 Cerbos 통신을 직접 REST 호출에서 공식 Java SDK/gRPC로 바꾼 패치 릴리스다.

애플리케이션 코드에서 그대로 유지되는 것:

- `@CerbosScoped`
- `@CerbosCheck`
- `@CerbosScope`
- `@CerbosResource`
- `@CerbosAttribute`
- `CerbosPrincipalResolver`
- `CerbosAuthorizationClient` 확장 지점

확인하거나 바꿔야 하는 것:

- Cerbos PDP의 gRPC 포트 `3593`이 애플리케이션에서 접근 가능한지 확인한다.
- 신규 설정은 `cerboshelper.target`을 사용한다.
- 기존에 `cerboshelper.base-url=http://cerbos:3592`만 쓰던 프로젝트는 자동으로 `cerbos:3593` target을 유추하지만, 명시적으로 `target`을 추가하는 것을 권장한다.
- 이미 gRPC/protobuf를 쓰는 프로젝트는 dependency tree에서 effective version 충돌이 없는지 확인한다.

권장 마이그레이션 예시는 다음과 같다.

```yaml
cerboshelper:
  target: ${CERBOS_TARGET:cerbos:3593}
```

검증 순서는 다음을 권장한다.

1. 애플리케이션 기동 로그에서 interceptor order를 확인한다.
2. `@CerbosScoped`가 붙은 목록 API를 호출한다.
3. PageHelper total/list가 권한 필터 후 결과인지 확인한다.
4. `@CerbosCheck`가 붙은 단건/수정/삭제 API를 호출한다.
5. 가능하면 Cerbos decision log 또는 demo row trace에서 `PlanResources`와 `CheckResources` 결과가 같은 정책 의미를 갖는지 확인한다.

## 14. 1.0.1에서 1.0.2로 올릴 때

`1.0.2`는 복잡한 엔터프라이즈 principal과 프로젝트별 예외 계약을 더 쉽게 붙이기 위한 패치 릴리스다.

추가된 것:

- `CerbosPrincipalEnvelope`
- `CerbosAccessDeniedHandler`
- `CerbosDeniedDecision`

기존 코드에서 그대로 유지되는 것:

- `@CerbosScoped`
- `@CerbosCheck`
- `CerbosPrincipalResolver`
- `@CerbosResource`
- `@CerbosAttribute`
- `cerboshelper.target`

일반 프로젝트는 반드시 수정할 필요가 없다. 기존 방식처럼 현재 사용자 객체를 resolver에서 반환해도 된다.

복잡한 principal을 가진 프로젝트는 resolver 반환값만 다음처럼 바꿀 수 있다.

```java
return Optional.of(new CerbosPrincipalEnvelope(id, roles, attr, policyVersion));
```

프로젝트 표준 예외를 써야 하면 다음 Bean만 추가한다.

```java
@Bean
CerbosAccessDeniedHandler cerbosAccessDeniedHandler() {
    return decision -> new AccessDeniedException(decision.message());
}
```

## 15. Annotation/API Reference

### `@CerbosResource`

정책 대상 resource 타입에 붙인다.

| field | 필수 | 기본값 | 설명 |
|-------|------|--------|------|
| `kind` | 예 | 없음 | Cerbos resource kind. 예: `document` |
| `sqlAlias` | 아니오 | `""` | SQL alias가 resource kind와 다를 때만 지정 |

권장 형태는 SQL alias를 resource kind와 같게 두는 것이다.

```java
@CerbosResource(kind = "document")
public record Document(...) {
}
```

SQL이 `FROM documents d`처럼 다른 alias를 쓰면 다음처럼 지정한다.

```java
@CerbosResource(kind = "document", sqlAlias = "d")
```

### `@CerbosAttribute`

필드, getter, record component에 붙인다.

| field | 기본값 | 설명 |
|-------|--------|------|
| `value` | `""` | Cerbos attr 이름. 비어 있으면 Java property 이름 사용 |
| `column` | `""` | SQL column 이름. 비어 있으면 `sqlAlias.camel_to_snake` 사용 |
| `ignore` | `false` | Cerbos attr/SQL column 매핑에서 제외 |

예시는 다음과 같다.

```java
public record Document(
        @CerbosAttribute(value = "ownerUserId", column = "document.owner_user_id")
        String ownerId,

        @CerbosAttribute(ignore = true)
        String displayLabel
) {
}
```

### `@CerbosScoped`

MyBatis Mapper 목록 조회 메서드에 붙인다. Cerbos `PlanResources` 결과가 SQL WHERE로 병합된다.

| field | 기본값 | 설명 |
|-------|--------|------|
| `resourceKind` | `""` | 비어 있으면 반환 타입의 `@CerbosResource(kind = "...")`에서 추론 |
| `action` | `""` | Cerbos action. 비어 있으면 `@CerbosScope` context 또는 호출부 설정을 사용 |

```java
@CerbosScoped(action = "view")
List<Document> findDocuments();
```

반환 타입이 DTO가 아니거나 추론이 불가능하면 `resourceKind`를 명시한다.

```java
@CerbosScoped(resourceKind = "document", action = "view")
List<Long> findDocumentIds();
```

### `@CerbosScope`

서비스 메서드에서 Mapper 목록 조회의 action/principal context를 덮어쓴다. 일반 CRUD에서는 거의 필요하지 않다.

| field | 기본값 | 설명 |
|-------|--------|------|
| `action` | `"view"` | 이 메서드 안에서 실행되는 `@CerbosScoped` Mapper의 action |
| `principal` | `""` | SpEL principal override |

```java
@CerbosScope(action = "export")
public List<Document> exportDocuments() {
    return documentMapper.findDocuments();
}
```

### `@CerbosCheck`

서비스 단건/쓰기 메서드에 붙인다. Cerbos `CheckResources`가 deny하면 서비스 본문은 실행되지 않는다.

| field | 필수 | 기본값 | 설명 |
|-------|------|--------|------|
| `action` | 예 | 없음 | Cerbos action. 예: `view`, `create`, `update`, `delete` |
| `principal` | 아니오 | `""` | SpEL principal override |
| `resource` | 아니오 | `""` | SpEL resource override |
| `resourceKind` | 아니오 | `""` | convention 추론 실패 시 resource kind 지정 |
| `id` | 아니오 | `""` | resource id 파라미터명. 예: `documentId` |
| `mapper` | 아니오 | `""` | finder를 호출할 Mapper Bean 이름 |
| `finder` | 아니오 | `"findById"` | 기존 row 조회 메서드명 |

권장 사용은 action만 쓰는 것이다.

```java
@CerbosCheck(action = "update")
public Document updateDocument(long documentId, Document document) {
    ...
}
```

convention이 맞지 않을 때만 필요한 값만 덮어쓴다.

```java
@CerbosCheck(action = "view", resourceKind = "document", id = "documentId", mapper = "documentQueryMapper", finder = "selectById")
public Document findDocument(long documentId) {
    ...
}
```

### `@CerbosChecks`

`@CerbosCheck`를 같은 메서드에 여러 개 적용할 때 사용한다. `@CerbosCheck`는 repeatable annotation이므로 보통 컨테이너 annotation을 직접 쓰지 않는다.

```java
@CerbosCheck(action = "approve")
@CerbosCheck(action = "update")
public Document approveDocument(long documentId) {
    ...
}
```

### `CerbosPrincipalResolver`

현재 principal을 제공하는 애플리케이션 Bean이다.

```java
public interface CerbosPrincipalResolver {
    Optional<Object> currentPrincipal();
}
```

프로젝트마다 인증 구조가 다르므로 CerbosHelper는 특정 `userId`, `tenantId`, Spring Security principal 타입을 강제하지 않는다.

### `CerbosPrincipalEnvelope`

복잡한 principal payload를 명시적으로 전달하는 record다.

| field | 필수 | 기본값 | 설명 |
|-------|------|--------|------|
| `id` | 예 | 없음 | Cerbos principal id |
| `roles` | 아니오 | `authenticated` | Cerbos top-level roles |
| `attr` | 아니오 | 빈 map | `request.principal.attr.*` |
| `policyVersion` | 아니오 | `default` | principal policy version |

```java
return Optional.of(CerbosPrincipalEnvelope.of(
        "user-1",
        List.of("TENANT_ADMIN"),
        Map.of("tenantId", 1L, "companyIds", List.of(10L))
));
```

### `CerbosAccessDeniedHandler`

`@CerbosCheck` deny를 프로젝트 표준 예외로 바꾼다.

```java
@Bean
CerbosAccessDeniedHandler cerbosAccessDeniedHandler() {
    return decision -> new AccessDeniedException(decision.message());
}
```

### `CerbosAuthorizationClient`

공식 SDK 기반 기본 client를 대체해야 할 때만 직접 구현한다.

```java
public interface CerbosAuthorizationClient {
    JsonNode planResources(Object principal, String resourceKind, String action);

    boolean isAllowed(Object principal, Object resource, String action);

    Map<String, String> checkResources(Object principal, List<?> resources, String action);

    JsonNode checkResourcesRaw(Object principal, List<?> resources, String action);
}
```

일반 프로젝트에서는 직접 구현하지 않는다.

### `CerbosResourceColumnRegistry`

기본 annotation 기반 column 매핑으로 처리할 수 없는 경우에만 사용한다.

```java
@Bean
CerbosResourceColumnRegistry cerbosResourceColumnRegistry() {
    return CerbosResourceColumnRegistry.builder()
            .resource(Document.class)
            .column("document", "request.resource.attr.customOwner", "document.owner_user_id")
            .build();
}
```

## 16. Troubleshooting

| 증상 | 확인할 것 | 해결 |
|------|-----------|------|
| `principal`을 찾을 수 없음 | `CerbosPrincipalResolver` Bean 등록 여부 | 인증 context에서 현재 사용자 snapshot을 반환하는 Bean 추가 |
| `documentId` convention이 동작하지 않음 | `-parameters` 적용 여부 | Gradle `JavaCompile`에 `options.compilerArgs.add('-parameters')` 추가 |
| Mapper Bean을 찾지 못함 | 기본 Bean 이름이 `documentMapper`인지 | `@CerbosCheck(mapper = "...")`로 실제 Bean 이름 지정 |
| `findById`를 찾지 못함 | Mapper 조회 메서드명 | `@CerbosCheck(finder = "selectById")` 지정 |
| `resourceKind` 추론 실패 | 반환 타입에 `@CerbosResource`가 있는지 | `@CerbosScoped(resourceKind = "...")` 명시 |
| SQL column 매핑 실패 | policy attr 이름과 Java 필드명 불일치 | `@CerbosAttribute(value = "...", column = "...")`로 맞춤 |
| 전체 조회로 fallback될까 걱정됨 | 변환 실패 시 동작 | CerbosHelper는 변환 실패 시 예외를 던지고 전체 조회로 열지 않음 |
| PageHelper total이 이상함 | interceptor order 로그 | `CerbosMyBatisScopeInterceptor`가 로그 마지막에 있는지 확인 |
| gRPC 연결 실패 | PDP 포트 | `cerboshelper.target`이 `cerbos:3593` 또는 `localhost:3593`인지 확인 |
| REST 포트 `3592`만 열려 있음 | 공식 SDK는 gRPC 사용 | Docker Compose에서 `3593` 포트도 노출 |
| 정책이 기대보다 넓거나 좁음 | Cerbos policy attr 이름 | `request.resource.attr.*`와 Java field 이름을 대조 |
| null 조건이 정책에 안 맞음 | SDK attr builder에서 null 제외 | `hasXxx`, `xxxPresent`, `xxxStatus` 같은 명시 필드 사용 |

문제 분석 순서는 다음을 권장한다.

1. 애플리케이션이 PDP gRPC target에 연결되는지 확인한다.
2. `CerbosPrincipalResolver`가 현재 principal을 반환하는지 확인한다.
3. 목록 조회면 `@CerbosScoped`와 resource kind 추론을 확인한다.
4. 단건/쓰기면 `@CerbosCheck`의 id/mapper/finder convention을 확인한다.
5. 정책의 `request.resource.attr.*` 이름과 Java field/`@CerbosAttribute` 이름을 비교한다.
6. PageHelper 사용 시 interceptor order 로그를 확인한다.

## 17. 검증 체크리스트

새 프로젝트에 붙인 뒤 다음을 확인한다.

| 구분 | 검증 |
|------|------|
| 의존성 | `./gradlew dependencies`에서 `CerbosHelper`와 Cerbos SDK가 충돌 없이 잡히는지 |
| 컴파일 | `./gradlew compileJava` 성공 |
| 기동 | `cerboshelper.mybatis.interceptor-order [...]` 로그 출력 |
| PDP 연결 | `cerboshelper.target`의 gRPC 포트 접근 가능 |
| 목록 API | 허용되지 않은 row가 PageHelper `total`과 `list`에 포함되지 않는지 |
| 단건 API | 허용되지 않은 resource 접근이 서비스 본문 실행 전에 차단되는지 |
| 쓰기 API | update/delete가 기존 row 기준으로 먼저 차단되는지 |
| 정책 의미 | 같은 principal/resource/action에서 list plan과 single check 결과가 일치하는지 |
| 실패 계약 | deny 예외가 프로젝트 표준 API 오류로 변환되는지 |

PageHelper와 함께 쓸 때는 다음 로그가 핵심이다.

```text
cerboshelper.mybatis.interceptor-order [PageInterceptor, CerbosMyBatisScopeInterceptor]
```

로그에서 `CerbosMyBatisScopeInterceptor`가 마지막이면 query 진입 시 Cerbos가 먼저 실행되고, PageHelper가 권한 필터가 반영된 SQL을 기준으로 count/page를 만든다.

## 18. 복잡한 SQL 처리 가이드

CerbosHelper는 일반적인 단일 resource 목록 조회를 가장 안정적으로 지원한다.

권장 SQL은 다음 형태다.

```sql
SELECT document.*
FROM documents document
WHERE document.deleted = false
ORDER BY document.id
```

복잡한 조회가 필요하면 권한 필터가 적용되는 resource row 조회를 먼저 분리하고, aggregate나 join projection은 그 이후 단계로 나누는 방식을 권장한다.

권장 분리 예시는 다음과 같다.

```java
@CerbosScoped(action = "view")
List<Document> findVisibleDocuments(DocumentSearchCondition condition);

List<DocumentSummary> summarizeDocuments(List<Long> visibleDocumentIds);
```

다음 패턴은 바로 붙이기 전에 별도 검증이 필요하다.

- `WITH` CTE 안에서 resource row가 여러 단계로 변형되는 SQL
- `UNION`으로 여러 resource kind가 섞이는 SQL
- `GROUP BY`로 row 단위 resource가 사라진 결과
- subquery alias와 outer query alias가 같은 attr 이름을 다르게 의미하는 SQL

이런 경우에는 `@CerbosScoped`를 가장 원본 resource row를 반환하는 Mapper에 붙이고, 이후 가공은 별도 Mapper나 service 단계에서 처리하는 것이 안전하다.

## 19. 1.0.2에서 1.0.3으로 올릴 때

`1.0.3`은 코드 기능 변경이 아니라 사용성 문서 보강 릴리스다.

변경된 문서 내용:

- 5분 Quick Start
- 최소 Cerbos policy YAML 예제
- annotation/API reference
- troubleshooting 표
- PageHelper 검증 체크리스트
- 복잡한 SQL 처리 가이드

라이브러리 API는 `1.0.2`와 동일하게 유지된다. 기존 애플리케이션 코드는 dependency version만 올리면 된다.

```groovy
implementation 'com.github.qsdcv301:CerbosHelper:v1.0.3'
```

## 20. 릴리스

GitHub/JitPack 릴리스는 태그 기준이다.

```bash
git tag v1.0.3
git push origin v1.0.3
```

새 기능을 의존성으로 쓰려면 사용하는 프로젝트의 버전을 새 태그로 올린다.
