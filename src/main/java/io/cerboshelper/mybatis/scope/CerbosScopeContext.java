package io.cerboshelper.mybatis.scope;

import java.util.Objects;
import java.util.function.Supplier;

public final class CerbosScopeContext {
    private static final ThreadLocal<Request> CURRENT = new ThreadLocal<>();

    private CerbosScopeContext() {
    }

    public static <T> T with(Object principal, String action, Supplier<T> supplier) {
        Request previous = CURRENT.get();
        CURRENT.set(new Request(Objects.requireNonNull(principal), action));
        try {
            return supplier.get();
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }

    public static Request current() {
        return CURRENT.get();
    }

    public record Request(Object principal, String action) {
    }
}
