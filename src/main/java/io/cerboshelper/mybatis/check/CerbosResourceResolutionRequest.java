package io.cerboshelper.mybatis.check;

import io.cerboshelper.mybatis.annotation.CerbosCheck;
import io.cerboshelper.mybatis.support.CerbosMethodExpressionEvaluator;

import java.lang.reflect.Method;

public record CerbosResourceResolutionRequest(
        CerbosCheck check,
        Method method,
        Object[] args,
        CerbosMethodExpressionEvaluator.Context context
) {
}
