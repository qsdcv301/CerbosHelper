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
| 단건/쓰기 resource 해석 | `CerbosResourceResolver` |
| Mapper SQL predicate 삽입 | `CerbosSqlPredicateInjector` |
| MyBatis interceptor 순서 조정 | `CerbosMyBatisInterceptorOrderStrategy` |
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
    implementation 'com.github.qsdcv301:CerbosHelper:v1.0.6'
}
```

CerbosHelper `v1.0.6`는 내부적으로 다음 Cerbos SDK 계열 의존성을 사용한다.

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

기본 SQL predicate 삽입은 `DefaultCerbosSqlPredicateInjector`가 담당한다. 이 기본 구현은 기존 `WHERE`와 top-level `ORDER BY`를 기준으로 처리한다. 현재 기본 구현의 권장 SQL 형태는 PostgreSQL의 일반적인 단일 `SELECT ... FROM ... WHERE ... ORDER BY ...` 조회다.

다음 SQL은 기본 injector에 바로 맡기기보다 Mapper를 분리하거나 custom `CerbosSqlPredicateInjector` Bean으로 처리하는 것을 권장한다.

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
- `CerbosResourceResolver`
- `CerbosSqlPredicateInjector`
- `CerbosMyBatisInterceptorOrderStrategy`
- Cerbos 요청 JSON
- principal/resource payload mapper
- resource column registry
- MyBatis interceptor 순서 조정

CerbosHelper 내부 통신은 공식 Cerbos Java SDK를 사용한다. 개발자는 SDK client를 직접 주입하지 않아도 된다. 특정 프로젝트의 인증, resource lookup, SQL 병합 방식이 다르면 각 전략 Bean을 직접 등록해 기본 구현을 대체한다.

기본 구현은 `CerbosSdkAuthorizationClient`다. 직접 대체가 필요한 예시는 다음과 같다.

- 조직 표준 gRPC channel 설정을 반드시 써야 하는 경우
- mTLS 인증서 로딩을 별도 보안 모듈에서 관리하는 경우
- Cerbos Hub / custom gateway / sidecar 정책에 맞춘 header나 interceptor가 필요한 경우
- 테스트에서 PDP 없이 결정 결과를 고정하고 싶은 경우

이 경우 애플리케이션에서 `CerbosAuthorizationClient` Bean을 직접 등록하면 auto configuration의 기본 SDK client는 생성되지 않는다.

`@CerbosCheck`의 resource 해석 규칙을 완전히 바꾸려면 `CerbosResourceResolver` Bean을 등록한다. 기본 구현인 `DefaultCerbosResourceResolver`는 `@CerbosResource` 인자, `resource` SpEL, id parameter, mapper/finder, `withId(...)` convention을 지원한다. 이 convention을 쓰지 않는 프로젝트는 resolver를 직접 구현하면 된다.

```java
@Bean
CerbosResourceResolver cerbosResourceResolver(DocumentLookupService lookupService) {
    return request -> {
        Object id = request.context().variable("documentId");
        if (id == null) {
            return List.of();
        }
        return List.of(lookupService.findResourceSnapshot(id));
    };
}
```

원본 SQL에 Cerbos predicate를 삽입하는 방식을 바꾸려면 `CerbosSqlPredicateInjector` Bean을 등록한다. 기본 구현은 단일 `SELECT`의 top-level `WHERE`/`ORDER BY`에 삽입한다. `WITH`, `UNION`, aggregate query처럼 SQL shape가 다른 경우에는 이 전략을 프로젝트 SQL 규칙에 맞게 구현한다.

```java
@Bean
CerbosSqlPredicateInjector cerbosSqlPredicateInjector() {
    return new WrappingCerbosSqlPredicateInjector();
}

final class WrappingCerbosSqlPredicateInjector implements CerbosSqlPredicateInjector {
    @Override
    public CerbosSqlInjectionResult inject(String sql, String predicate) {
        return new CerbosSqlInjectionResult(
                "SELECT scoped.* FROM (" + sql + ") scoped WHERE (" + predicate + ")",
                countOriginalPlaceholders(sql)
        );
    }

    private int countOriginalPlaceholders(String sql) {
        int count = 0;
        for (int index = 0; index < sql.length(); index++) {
            if (sql.charAt(index) == '?') {
                count++;
            }
        }
        return count;
    }
}
```

위 derived-table 방식은 outer predicate가 참조할 column alias가 반드시 outer query에 노출되어야 한다. 예를 들어 `@CerbosResource(sqlAlias = "scoped")`를 쓰면 inner select가 `company_id`, `owner_user_id` 같은 resource attr column을 projection해야 한다.

상세한 extension point 설계는 `docs/EXTENSION_POINTS_KO.md`, 고급 SQL 구현 방식은 `docs/ADVANCED_SQL_KO.md`에 따로 정리되어 있다.

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

## 13. 확장 포인트 선택 기준

기본 CRUD convention으로 충분하면 annotation만 사용한다. 프로젝트 구조가 다르면 annotation option을 계속 늘리기보다 전략 Bean을 교체한다.

| 상황 | 우선 선택 | 이유 |
|------|-----------|------|
| 현재 사용자 principal 구조가 단순함 | `CerbosPrincipalResolver` | 애플리케이션 인증 context만 연결하면 된다. |
| principal roles/attr/policyVersion을 명시해야 함 | `CerbosPrincipalEnvelope` | custom payload mapper 없이 Cerbos principal payload를 고정할 수 있다. |
| principal/resource payload 규칙 자체가 다름 | `CerbosPayloadMapper` Bean | attr 추출, null 처리, roles 정책을 프로젝트 기준으로 바꾼다. |
| 단건/쓰기 resource lookup이 mapper convention과 다름 | `CerbosResourceResolver` Bean | `findById`, mapper 이름, `withId(...)` convention을 쓰지 않아도 된다. |
| SQL alias나 column allowlist를 직접 통제해야 함 | `CerbosResourceColumnRegistry` Bean | 정책 attr과 SQL column 매핑을 명시적으로 고정한다. |
| `WITH`, `UNION`, aggregate SQL에 predicate를 넣어야 함 | `CerbosSqlPredicateInjector` Bean | 원본 SQL shape별로 predicate 삽입 위치와 parameter index를 직접 계산한다. |
| deny 예외가 프로젝트 표준과 다름 | `CerbosAccessDeniedHandler` Bean | `SecurityException` 대신 API 표준 예외로 변환한다. |
| Cerbos SDK channel/gateway/mTLS를 직접 제어해야 함 | `CerbosAuthorizationClient` Bean | 기본 SDK client를 조직 표준 client로 대체한다. |

## 14. 보완 가능 목록

현재 파일들은 모두 실제 runtime 경로에 연결되어 있고 즉시 제거할 대상은 없다. 다만 더 범용적인 라이브러리로 키우려면 아래 개선을 순서대로 고려할 수 있다.

### 우선순위 높음

| 보완 항목 | 현재 상태 | 개선 방향 |
|-----------|-----------|-----------|
| MyBatis interceptor order 조정 추상화 | auto-configuration이 MyBatis 내부 field reflection으로 interceptor 순서를 재배치한다. | `CerbosMyBatisInterceptorOrderStrategy` 같은 전략 Bean으로 분리하고, reflection 기본 구현과 no-op/수동 등록 구현을 선택하게 한다. |
| `CerbosSqlPredicateInjector` 고급 구현 | 기본 구현은 단일 `SELECT`의 top-level `WHERE`/`ORDER BY` 중심이다. | derived-table wrapping injector, CTE-target injector, union-branch injector 샘플 구현과 테스트를 추가한다. |
| resource resolver 테스트 확대 | 현재 resolver는 기본 convention 구현이지만 전용 테스트가 부족하다. | `@CerbosResource` 인자, id parameter, mapper/finder, `withId(...)`, custom resolver 우선순위 테스트를 추가한다. |
| SQL injection result 검증 강화 | predicate 삽입 위치와 parameter index가 SQL shape에 민감하다. | `WHERE`, `ORDER BY`, nested subquery, string literal `?`, CTE, union fixture를 별도 테스트로 축적한다. |

### 우선순위 중간

| 보완 항목 | 현재 상태 | 개선 방향 |
|-----------|-----------|-----------|
| null attr 정책 명시화 | SDK attribute builder는 null 값을 보내지 않는다. | `CerbosPayloadMapper`에서 null 포함/제외 전략을 Bean 또는 property로 분리한다. |
| parameter naming policy | named mode는 `cp0`, `cp1`을 사용한다. | `CerbosSqlParameterNameStrategy`로 prefix를 바꿀 수 있게 한다. 기본값은 `cp`로 유지한다. |
| SQL dialect 명시 | 기본 injector는 ANSI/PostgreSQL에 가까운 SQL 문자열 처리를 한다. | `CerbosSqlDialect` 또는 injector 구현 이름으로 지원 SQL 범위를 명확히 나눈다. |
| observability hook | scope 적용 로그는 debug/info 수준에 제한된다. | plan, predicate, decision을 마스킹해서 관찰하는 listener hook을 추가한다. |
| batch check 최적화 | `CerbosCheckAspect`는 resolved resource마다 `isAllowed`를 호출한다. | 같은 action/principal의 다중 resource는 `checkResources` batch 호출로 묶는 옵션을 추가한다. |

### 우선순위 낮음

| 보완 항목 | 현재 상태 | 개선 방향 |
|-----------|-----------|-----------|
| Kotlin/record 외 immutable copy 지원 | 기본 resolver는 optional `withId(...)` convention을 사용한다. | `CerbosResourceIdentityApplier` 전략으로 id 적용 방식을 분리한다. |
| Spring Security starter 성격의 보조 모듈 | core는 Spring Security principal 타입을 모른다. | 별도 adapter 모듈에서 `Authentication` 기반 resolver 예제를 제공한다. |
| 문서 예제 분리 | README가 설치, API, 고급 SQL, 릴리스를 모두 담는다. | `docs/advanced-sql.md`, `docs/extension-points.md`, `docs/release.md`로 분리할 수 있다. |

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
| `resourceKind` | 아니오 | `""` | 기본 `CerbosResourceResolver`가 id lookup을 할 때 사용할 resource kind |
| `id` | 아니오 | `""` | resource id 파라미터명. 예: `documentId` |
| `mapper` | 아니오 | `""` | 기본 `CerbosResourceResolver`에서 finder를 호출할 Mapper Bean 이름 |
| `finder` | 아니오 | `"findById"` | 기본 `CerbosResourceResolver`의 기존 row 조회 메서드명 |

권장 사용은 action만 쓰는 것이다.

```java
@CerbosCheck(action = "update")
public Document updateDocument(long documentId, Document document) {
    ...
}
```

기본 resolver convention이 맞지 않을 때만 필요한 값만 덮어쓴다. convention 자체를 쓰지 않는 프로젝트는 `CerbosResourceResolver` Bean을 등록한다.

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

### `CerbosResourceResolver`

`@CerbosCheck`가 검사할 resource 목록을 반환하는 전략이다. 기본 구현은 CRUD convention을 제공하지만, 라이브러리 core는 특정 Mapper 이름이나 id 조회 방식에 고정되지 않는다.

```java
public interface CerbosResourceResolver {
    List<Object> resolve(CerbosResourceResolutionRequest request);
}
```

custom resolver는 다음 경우에 사용한다.

- repository/service 계층으로 resource snapshot을 읽어야 하는 경우
- mapper 이름이 resource kind와 전혀 맞지 않는 경우
- immutable copy 방식이 `withId(...)`가 아닌 경우
- update에서 before/after resource 구성이 프로젝트별로 다른 경우

### `CerbosSqlPredicateInjector`

원본 MyBatis SQL에 Cerbos predicate를 삽입하는 전략이다. 기본 구현은 단일 `SELECT`를 대상으로 하지만, custom Bean으로 SQL shape별 처리를 분리할 수 있다.

```java
public interface CerbosSqlPredicateInjector {
    CerbosSqlInjectionResult inject(String sql, String predicate);
}
```

`CerbosSqlInjectionResult.parameterInsertionIndex`는 새 positional parameter를 기존 MyBatis parameter mapping의 어느 위치에 넣을지 나타낸다. SQL을 wrapping하거나 predicate 위치를 옮기는 구현에서는 이 index를 반드시 함께 계산해야 한다.

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
| id convention이 동작하지 않음 | `-parameters` 적용 여부 또는 custom resolver 필요 여부 | `-parameters`를 켜거나 `CerbosResourceResolver` Bean 등록 |
| Mapper Bean을 찾지 못함 | 기본 resolver convention 사용 여부 | `@CerbosCheck(mapper = "...")` 지정 또는 `CerbosResourceResolver` Bean 등록 |
| `findById`를 찾지 못함 | 기본 resolver convention 사용 여부 | `@CerbosCheck(finder = "selectById")` 지정 또는 `CerbosResourceResolver` Bean 등록 |
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
4. 단건/쓰기면 `@CerbosCheck`의 id/mapper/finder convention 또는 custom `CerbosResourceResolver`를 확인한다.
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

CerbosHelper의 기본 `DefaultCerbosSqlPredicateInjector`는 일반적인 단일 resource 목록 조회를 가장 안정적으로 지원한다.

권장 SQL은 다음 형태다.

```sql
SELECT document.*
FROM documents document
WHERE document.deleted = false
ORDER BY document.id
```

복잡한 조회가 필요하면 권한 필터가 적용되는 resource row 조회를 먼저 분리하고, aggregate나 join projection은 그 이후 단계로 나누는 방식이 가장 단순하다.

권장 분리 예시는 다음과 같다.

```java
@CerbosScoped(action = "view")
List<Document> findVisibleDocuments(DocumentSearchCondition condition);

List<DocumentSummary> summarizeDocuments(List<Long> visibleDocumentIds);
```

### WITH CTE

`WITH`는 resource row가 유지되는 CTE라면 구현 가능하다. 핵심은 Cerbos predicate가 참조하는 모든 resource attr column이 필터 적용 지점에 존재해야 한다는 점이다.

권장 형태는 CTE 내부의 원본 resource 조회에 `@CerbosScoped`를 붙이는 것이다.

```sql
WITH visible_document AS (
    SELECT
        document.id,
        document.company_id,
        document.owner_user_id,
        document.status
    FROM documents document
    WHERE document.deleted = false
)
SELECT *
FROM visible_document
ORDER BY id
```

이 형태를 outer wrapping으로 처리하려면 custom `CerbosSqlPredicateInjector`가 outer alias를 기준으로 predicate를 만들 수 있어야 한다. 그 경우 `@CerbosResource(sqlAlias = "scoped")` 또는 별도 `CerbosResourceColumnRegistry`로 `request.resource.attr.companyId -> scoped.company_id`처럼 매핑한다.

### UNION

`UNION`은 두 종류로 나뉜다.

같은 resource kind의 동일 schema를 합치는 `UNION`은 각 branch에 같은 Cerbos predicate를 넣는 custom injector로 구현할 수 있다.

```sql
SELECT id, company_id, owner_user_id, status FROM draft_documents draft_document
UNION ALL
SELECT id, company_id, owner_user_id, status FROM published_documents published_document
```

이 경우에는 branch마다 alias가 다르므로 `CerbosSqlPredicateInjector`만으로는 부족할 수 있다. 더 안정적인 방식은 branch별 mapper를 분리해 각각 `@CerbosScoped`를 적용한 뒤 service에서 합치는 것이다.

서로 다른 resource kind가 섞이는 `UNION`은 하나의 `resourceKind/action` plan으로 안전하게 표현하기 어렵다. 이 경우 branch별 resource kind로 별도 조회하고 애플리케이션에서 합치는 구조가 맞다.

### GROUP BY와 aggregate

`GROUP BY` 결과는 원본 resource row가 사라진다. 따라서 aggregate 결과에 직접 `@CerbosScoped`를 붙이면 `request.resource.attr.*`가 의미하는 row가 불명확해질 수 있다.

구현 가능한 패턴은 먼저 visible resource id 또는 visible resource row를 구하고, 그 결과를 기준으로 aggregate를 수행하는 것이다.

```java
@CerbosScoped(resourceKind = "document", action = "view")
List<Long> findVisibleDocumentIds(DocumentSearchCondition condition);

List<DocumentSummary> summarizeByVisibleDocumentIds(List<Long> visibleDocumentIds);
```

SQL로 한 번에 처리해야 한다면 custom injector가 aggregate 이전 단계의 CTE에 predicate를 삽입해야 한다. outer aggregate 결과에 predicate를 붙이는 방식은 권한 의미가 깨질 가능성이 높다.

### 별도 검증이 필요한 패턴

다음 패턴은 기본 injector에 바로 붙이지 않는다.

- `WITH` CTE 안에서 resource row가 여러 단계로 변형되는 SQL
- `UNION`으로 여러 resource kind가 섞이는 SQL
- `GROUP BY`로 row 단위 resource가 사라진 결과
- subquery alias와 outer query alias가 같은 attr 이름을 다르게 의미하는 SQL

이런 경우에는 `@CerbosScoped`를 가장 원본 resource row를 반환하는 Mapper에 붙이고, 이후 가공은 별도 Mapper나 service 단계에서 처리하는 것이 안전하다. 한 SQL 안에서 반드시 처리해야 하면 `CerbosSqlPredicateInjector`와 `CerbosResourceColumnRegistry`를 함께 custom Bean으로 제공한다.

## 19. 추상화 확장 포인트 요약

CerbosHelper는 기본 CRUD convention을 제공하지만, core runtime은 다음 extension point로 분리되어 있다.

| 확장 포인트 | 기본 구현 | 교체 목적 |
|-------------|-----------|-----------|
| `CerbosAuthorizationClient` | `CerbosSdkAuthorizationClient` | Cerbos SDK channel, mTLS, gateway, 테스트 fake client |
| `CerbosPrincipalResolver` | empty resolver | 애플리케이션 인증 context 연결 |
| `CerbosPayloadMapper` | annotation/reflection 기반 mapper | principal/resource payload 정책 변경 |
| `CerbosResourceResolver` | `DefaultCerbosResourceResolver` | `@CerbosCheck` resource lookup, before/after resource 구성 변경 |
| `CerbosResourceColumnRegistry` | `CerbosResourceColumns` | SQL column allowlist 직접 구성 |
| `CerbosSqlPredicateInjector` | `DefaultCerbosSqlPredicateInjector` | `WITH`, `UNION`, aggregate 등 SQL shape별 predicate 삽입 |
| `CerbosMyBatisInterceptorOrderStrategy` | `DefaultCerbosMyBatisInterceptorOrderStrategy` | MyBatis plugin order 수동 관리 또는 reflection 회피 |
| `CerbosAccessDeniedHandler` | `SecurityException` handler | 프로젝트 표준 deny 예외 변환 |

하드코딩을 줄이는 방향은 새 annotation option을 계속 늘리는 것보다 위 전략 Bean을 프로젝트별로 교체하는 것이다. 기본 구현은 작은 CRUD 프로젝트를 빠르게 붙이기 위한 convention이고, 복잡한 프로젝트는 resolver/injector/registry를 명시 구현하는 쪽이 더 안전하다.

보완 로드맵은 `docs/ROADMAP_KO.md`에 유지한다.

새 extension point를 외부 프로젝트에서 사용하려면 해당 변경이 포함된 태그를 의존성 버전으로 지정한다.

```groovy
implementation 'com.github.qsdcv301:CerbosHelper:<tag>'
```

## 20. 릴리스

GitHub/JitPack 릴리스는 태그 기준이다.

```bash
git tag <tag>
git push origin <tag>
```

새 기능을 의존성으로 쓰려면 사용하는 프로젝트의 버전을 새 태그로 올린다.
