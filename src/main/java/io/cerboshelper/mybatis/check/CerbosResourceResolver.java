package io.cerboshelper.mybatis.check;

import java.util.List;

@FunctionalInterface
public interface CerbosResourceResolver {
    List<Object> resolve(CerbosResourceResolutionRequest request);
}
