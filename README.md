# CerbosHelper

CerbosHelper is a Spring Boot + MyBatis helper library that applies Cerbos `PlanResources` results to annotated MyBatis `SELECT` statements.

It is designed to behave like a scope-filter companion to PageHelper:

- CerbosHelper injects the Cerbos SQL predicate first.
- PageHelper then calculates `count`, `limit`, and `offset` from the already-scoped SQL.
- If the MyBatis interceptor order is unsafe, CerbosHelper moves its interceptor after PageHelper in the MyBatis interceptor chain so Cerbos execution wraps PageHelper execution.
- Applications can annotate resource objects and let CerbosHelper build the Cerbos variable to SQL column registry automatically.

## Library Contract

Applications provide a Cerbos plan provider bean:

```java
@Component
class AppCerbosPlanProvider implements CerbosPlanProvider {
    @Override
    public JsonNode planResources(Object principal, String resourceKind, String action) {
        // Call Cerbos /api/plan/resources or an SDK equivalent.
    }
}
```

Then annotate resource objects:

```java
@CerbosResource(kind = "document", sqlAlias = "d")
record Document(
        long id,
        long companyId,
        long siteId,
        long organizationId,
        String ownerUserId
) {
}
```

CerbosHelper scans `@CerbosResource` classes from the Spring Boot application package and builds a safe allowlisted column registry. By default, Java property names are converted to snake_case SQL columns, so `ownerUserId` becomes `d.owner_user_id`.

Use `@CerbosAttribute` only when a field needs a different Cerbos attribute name, a different SQL column, or must be ignored.

Then mapper methods can be protected with:

```java
@CerbosScoped(resourceKind = "document", action = "view")
List<Document> findDocuments();
```

Service code passes the current principal and optional action around the mapper call:

```java
return CerbosScopeContext.with(principal, "view", () ->
        PageHelper.startPage(pageNum, pageSize).doSelectPageInfo(mapper::findDocuments));
```

## Current Support Level

This is a v0.1 helper module for Spring Boot 3, MyBatis 3.5, PostgreSQL-style SQL, and PageHelper. It supports safe column whitelisting, positional parameter binding, existing `WHERE`, and top-level `ORDER BY`.

It intentionally does not cover every SQL grammar shape yet. Complex joins, nested subqueries, vendor-specific syntax, and update/delete authorization should be validated before reuse in another production project.

## GitHub + JitPack Publish

JitPack does not require pushing to GitHub Packages. Publish the source to the public GitHub repository and create a release tag.

Local JitPack-equivalent check:

```bash
./gradlew publishToMavenLocal
```

Recommended GitHub flow:

```bash
git remote add origin https://github.com/qsdcv301/CerbosHelper.git
git branch -M main
git push -u origin main
git tag v0.1.0
git push origin v0.1.0
```

Consumers add JitPack at the end of their repositories and use the GitHub repo coordinates:

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
    implementation 'com.github.qsdcv301:CerbosHelper:v0.2.0'
}
```
