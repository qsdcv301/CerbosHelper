package io.cerboshelper.mybatis.auth;

import java.util.Optional;

public interface CerbosPrincipalResolver {
    Optional<Object> currentPrincipal();
}
