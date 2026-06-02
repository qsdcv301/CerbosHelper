package io.cerboshelper.mybatis;

import java.util.Optional;

public interface CerbosPrincipalResolver {
    Optional<Object> currentPrincipal();
}
