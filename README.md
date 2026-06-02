# CerbosHelper

`CerbosHelper`는 Spring Boot + MyBatis 프로젝트에서 Cerbos 권한 범위를 PageHelper처럼 자연스럽게 적용하기 위한 라이브러리다.

애플리케이션 개발자는 Cerbos 요청 JSON, Plan SQL 변환, MyBatis 인터셉터 순서, resource column registry를 직접 조립하지 않는다. 기본 사용 흐름은 설정, 리소스 annotation, Mapper annotation, 서비스 annotation이다.

## 사용 예시

의존성을 추가한다.

```groovy
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }
}
```

```groovy
dependencies {
    implementation 'com.github.qsdcv301:CerbosHelper:v0.6.0'
}
```

Cerbos 서버 주소만 설정한다.

```yaml
cerboshelper:
  base-url: ${CERBOS_BASE_URL:http://localhost:3592}
```

리소스 객체에 `@CerbosResource`를 붙인다.

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

Mapper 조회 메서드에 `@CerbosScoped`를 붙인다.

```java
@Mapper
public interface DocumentMapper {
    @CerbosScoped(resourceKind = "document", action = "view")
    List<Document> findDocuments();
}
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

서비스에서는 조회 구간만 `CerbosScopeContext.with(...)`로 감싼다.

```java
return CerbosScopeContext.with(principal, "view", () ->
        PageHelper.startPage(pageNum, pageSize)
                .doSelectPageInfo(documentMapper::findDocuments));
```

생성, 수정, 삭제처럼 단건 resource decision이 필요한 경우에는 `@CerbosCheck`를 붙인다. 메서드에 `userId` 파라미터가 있으면 `userContextService.load(userId)`를 자동으로 사용한다.

```java
@CerbosCheck(action = "create")
public Document createDocument(String userId, Document document) {
    documentMapper.insert(document);
    return documentMapper.findById(document.id()).orElseThrow();
}
```

기존 row를 읽어서 검사해야 하면 `resourceKind`와 id 파라미터 이름만 적는다. 예를 들어 `resourceKind = "document"`이면 기본적으로 `documentMapper.findById(documentId)`를 호출한다.

```java
@CerbosCheck(action = "view", resourceKind = "document", id = "documentId")
public Document findVisibleDocument(String userId, long documentId) {
    return documentMapper.findById(documentId).orElseThrow();
}
```

수정처럼 기존 리소스와 변경 후 리소스를 모두 검사해야 하는 경우에는 annotation을 여러 개 붙인다. `resource = "document"`는 메서드 파라미터 이름이고, `id = "documentId"`가 있으면 `withId(documentId)` 메서드가 있을 때 자동 적용한다.

```java
@CerbosCheck(action = "update", resourceKind = "document", id = "documentId")
@CerbosCheck(action = "update", resource = "document", id = "documentId")
public Document updateDocument(String userId, long documentId, Document document) {
    Document after = document.withId(documentId);
    documentMapper.update(after);
    return documentMapper.findById(documentId).orElseThrow();
}
```

Plan과 row trace를 화면이나 로그에서 확인해야 하면 디버그 전용 annotation을 붙인다. 서비스는 `CerbosAuthorizationClient`나 `CerbosPlanToSqlConverter`를 직접 주입하지 않는다.

```java
@CerbosDebugPlan(resourceKind = "document")
public CerbosPlanDebugResult debugPlan(String userId, String action) {
    throw new UnsupportedOperationException("@CerbosDebugPlan should handle this method");
}
```

row trace는 후보 row, Cerbos scope 적용 row, scope 적용 id 조회식을 넘긴다. `PageHelper`가 있으면 `pageNum`, `pageSize` 파라미터를 사용해 `PageInfo` 형태로 응답한다.

```java
@CerbosRowTrace(resourceKind = "document")
public CerbosRowTraceResult traceRows(String userId, String action, int pageNum, int pageSize) {
    throw new UnsupportedOperationException("@CerbosRowTrace should handle this method");
}
```

`@`와 `#`는 필요한 경우에만 쓰는 SpEL 문법이다. `@beanName`은 Spring bean, `#paramName`은 메서드 파라미터를 뜻한다. 기본 convention으로 해결되는 경우에는 쓰지 않는다.

## 자동 처리되는 일

`CerbosHelper`가 자동으로 처리하는 항목은 다음과 같다.

- `@CerbosResource` 객체 스캔
- Java 필드명 또는 record component 이름을 Cerbos resource attribute로 사용
- camelCase 필드명을 snake_case SQL 컬럼으로 변환
- `resourceKind`를 기본 SQL qualifier로 사용
- Cerbos `PlanResources` 호출
- Cerbos `CheckResources` 호출
- principal/resource 객체를 Cerbos 요청 payload로 변환
- `@CerbosCheck` 메서드 권한 검사
- `@CerbosDebugPlan` Plan 디버그 응답 생성
- `@CerbosRowTrace` row trace 응답 및 row별 로그 생성
- Cerbos Plan을 SQL `WHERE` 조건으로 변환
- MyBatis `SELECT` SQL에 조건 주입
- PageHelper와 충돌하지 않도록 MyBatis interceptor 순서 정리

예를 들어 `@CerbosResource(kind = "document")`가 붙은 객체의 `ownerUserId` 필드는 다음처럼 변환된다.

```text
request.resource.attr.ownerUserId -> document.owner_user_id
```

특수 SQL에서 alias가 반드시 다르면 `sqlAlias`를 옵션으로 사용한다.

```java
@CerbosResource(kind = "document", sqlAlias = "d")
```

필드명과 Cerbos attribute명 또는 DB 컬럼명이 맞지 않는 경우에만 `@CerbosAttribute`를 사용한다.

```java
@CerbosAttribute(value = "ownerUserId", column = "document.owner_user_id")
String ownerId
```

Cerbos SQL 변환 대상에서 제외할 필드는 `ignore = true`를 사용한다.

```java
@CerbosAttribute(ignore = true)
String displayOnlyText
```

## Principal 규칙

기본 client는 principal 객체도 reflection으로 읽는다.

```java
public record UserContext(
        String userId,
        List<String> permissions,
        List<Long> companyIds,
        List<Long> organizationIds
) {
}
```

principal id는 `id` 또는 `userId` 필드를 사용한다. 나머지 필드는 `request.principal.attr.*`로 전달된다.

예를 들어 위 객체는 다음처럼 Cerbos 정책에서 사용할 수 있다.

```yaml
condition:
  match:
    expr: "'document:view:org' in request.principal.attr.permissions"
```

top-level Cerbos role은 기본적으로 `authenticated`를 사용한다. 필요하면 설정으로 바꾼다.

```yaml
cerboshelper:
  principal-roles:
    - authenticated
```

## PageHelper와의 관계

PageHelper와 같이 사용할 때 권장 형태는 다음과 같다.

```java
return CerbosScopeContext.with(principal, "view", () ->
        PageHelper.startPage(pageNum, pageSize)
                .doSelectPageInfo(documentMapper::findDocuments));
```

정상 기동 시 다음 로그가 나온다.

```text
cerboshelper.mybatis.interceptor-order [PageInterceptor, CerbosMyBatisScopeInterceptor]
```

이 순서에서는 Cerbos scope 조건이 먼저 반영되고, PageHelper의 count/page SQL은 이미 권한 범위가 적용된 SQL을 기준으로 생성된다.

## 현재 지원 범위

현재 지원하는 Cerbos Plan 표현은 다음과 같다.

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

SQL 병합은 기존 `WHERE`와 top-level `ORDER BY`를 기준으로 처리한다. 복잡한 nested subquery, vendor-specific SQL, 컬럼 대 컬럼 비교는 적용 전 별도 검증이 필요하다.

## 개발자가 준비할 것

기본 경로에서 준비할 항목은 다음 정도다.

- Cerbos 서버 주소 설정
- principal 객체가 정책에 필요한 attribute를 갖도록 설계
- resource 객체에 `@CerbosResource` 부여
- 보호할 Mapper `SELECT` 메서드에 `@CerbosScoped` 부여
- 조회 호출을 `CerbosScopeContext.with(...)`로 감싸기
- create/update/delete에서 `@CerbosCheck` 부여
- 디버그 endpoint가 필요하면 `@CerbosDebugPlan` 또는 `@CerbosRowTrace` 부여

즉 수동 `CerbosClient`, 수동 `CerbosAuthorizationClient`, 수동 `CerbosPlanToSqlConverter`, 수동 `resourcePayload`, 수동 `principalPayload`, 수동 column registry는 기본 경로에서 필요하지 않다.

## 릴리스

현재 GitHub/JitPack 사용 방식은 다음과 같다.

```bash
git remote add origin https://github.com/qsdcv301/CerbosHelper.git
git branch -M main
git push -u origin main
git tag v0.6.0
git push origin v0.6.0
```

새 기능을 JitPack 의존성으로 쓰려면 기능 커밋 후 새 태그를 발행하고, 사용하는 프로젝트의 의존성 버전을 그 태그로 올려야 한다.
