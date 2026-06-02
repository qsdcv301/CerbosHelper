# CerbosHelper

Spring Boot + MyBatis 프로젝트에서 Cerbos 권한 범위를 annotation 중심으로 적용하는 라이브러리다.

일반 사용 흐름에서는 `CerbosAuthorizationClient`, `CerbosPlanToSqlConverter`, Cerbos 요청 JSON, column registry를 직접 작성하지 않는다.

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
    implementation 'com.github.qsdcv301:CerbosHelper:v0.8.0'
}
```

Cerbos 서버 주소를 설정한다.

```yaml
cerboshelper:
  base-url: ${CERBOS_BASE_URL:http://localhost:3592}
```

## 2. 리소스 선언

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

## 3. 목록 조회

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

서비스 조회 메서드에 `@CerbosScope`를 붙인다. 기본 규약은 메서드 파라미터 `principal`을 현재 사용자 객체로 사용하는 것이다.

```java
@CerbosScope(action = "view")
public PageInfo<Document> findVisibleDocuments(CurrentUser principal, int pageNum, int pageSize) {
    return PageHelper.startPage(pageNum, pageSize)
            .doSelectPageInfo(documentMapper::findDocuments);
}
```

PageHelper와 같이 쓰면 Cerbos scope 조건이 먼저 SQL에 반영되고, PageHelper count/page SQL은 권한 범위가 적용된 SQL을 기준으로 생성된다.

정상 기동 시 다음 로그가 나온다.

```text
cerboshelper.mybatis.interceptor-order [PageInterceptor, CerbosMyBatisScopeInterceptor]
```

## 4. 단건 권한 체크

생성처럼 요청 객체 자체를 검사하면 action만 적는다.

```java
@CerbosCheck(action = "create")
public Document createDocument(CurrentUser principal, Document document) {
    documentMapper.insert(document);
    return documentMapper.findById(document.id()).orElseThrow();
}
```

기존 row를 읽어서 검사하면 `resourceKind`와 id 파라미터 이름을 적는다.

```java
@CerbosCheck(action = "view", resourceKind = "document", id = "documentId")
public Document findVisibleDocument(CurrentUser principal, long documentId) {
    return documentMapper.findById(documentId).orElseThrow();
}
```

이 경우 기본 convention은 다음과 같다.

```text
resourceKind = "document"
id = "documentId"
=> documentMapper.findById(documentId)
```

수정처럼 기존 row와 변경 후 row를 모두 검사해야 하면 annotation을 두 개 붙인다.

```java
@CerbosCheck(action = "update", resourceKind = "document", id = "documentId")
@CerbosCheck(action = "update", resource = "document", id = "documentId")
public Document updateDocument(CurrentUser principal, long documentId, Document document) {
    Document after = document.withId(documentId);
    documentMapper.update(after);
    return documentMapper.findById(documentId).orElseThrow();
}
```

`resource = "document"`는 메서드 파라미터 `Document document`를 뜻한다. `id = "documentId"`가 함께 있고 `withId(...)` 메서드가 있으면 검사 전에 자동으로 id를 적용한다.

삭제는 기존 row를 읽어 검사하는 형태를 권장한다.

```java
@CerbosCheck(action = "delete", resourceKind = "document", id = "documentId")
public void deleteDocument(CurrentUser principal, long documentId) {
    documentMapper.delete(documentId);
}
```

## 5. Principal Convention

기본 convention은 메서드 파라미터 `principal` 하나만 찾는다. `userId`, `accountId`, `memberNo` 같은 애플리케이션별 식별자를 현재 사용자 객체로 바꾸는 일은 각 애플리케이션의 controller, argument resolver, security context resolver 같은 경계에서 처리한다.

예를 들어 다음 메서드는 별도 principal 설정이 필요 없다.

```java
@CerbosCheck(action = "create")
public Document createDocument(CurrentUser principal, Document document) {
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

## 6. 디버그 Endpoint

Plan 원문, SQL 변환 결과, parameter, denied 여부를 보고 싶으면 `@CerbosDebugPlan`을 쓴다.

```java
@CerbosDebugPlan(resourceKind = "document")
public CerbosPlanDebugResult debugPlan(CurrentUser principal, String action) {
    throw new UnsupportedOperationException("@CerbosDebugPlan should handle this method");
}
```

후보 row, SQL scope 적용 row, row별 `checkResources` 결과를 같이 보고 싶으면 `@CerbosRowTrace`를 쓴다.

```java
@CerbosRowTrace(resourceKind = "document")
public CerbosRowTraceResult traceRows(CurrentUser principal, String action, int pageNum, int pageSize) {
    throw new UnsupportedOperationException("@CerbosRowTrace should handle this method");
}
```

기본 convention은 다음 mapper 메서드를 찾는다.

```text
documentMapper.findAll()
documentMapper.findDocuments()
documentMapper.findDocumentIds()
```

메서드명이 다르면 필요한 항목만 override한다.

```java
@CerbosRowTrace(
        resourceKind = "document",
        candidates = "@documentMapper.findAllDocuments()",
        scopedRows = "@documentMapper.findVisibleDocuments()",
        scopedIds = "@documentMapper.findVisibleDocumentIds()"
)
```

## 7. Override 문법

대부분은 convention으로 처리한다. 그래도 직접 지정이 필요하면 SpEL을 사용할 수 있다.

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

이 방식은 복잡한 예외 케이스용이다. 일반 CRUD에서는 파라미터 이름을 `principal`로 두는 convention 방식을 권장한다.

## 8. 지원하는 Plan 표현

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

SQL 병합은 기존 `WHERE`와 top-level `ORDER BY`를 기준으로 처리한다. 복잡한 nested subquery, vendor-specific SQL, 컬럼 대 컬럼 비교는 적용 전 별도 검증이 필요하다.

## 9. 기본 사용에서 직접 다루지 않는 것

일반 사용 경로에서는 다음을 직접 작성하지 않는다.

- `CerbosAuthorizationClient`
- `CerbosPlanToSqlConverter`
- Cerbos 요청 JSON
- principal/resource payload mapper
- resource column registry
- MyBatis interceptor 순서 조정

## 10. 릴리스

GitHub/JitPack 릴리스는 태그 기준이다.

```bash
git tag v0.8.0
git push origin v0.8.0
```

새 기능을 의존성으로 쓰려면 사용하는 프로젝트의 버전을 새 태그로 올린다.
