package io.cerboshelper.mybatis;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

final class CerbosReflection {
    private CerbosReflection() {
    }

    static Object value(Object target, String name) {
        if (target == null || name == null || name.isBlank()) {
            return null;
        }
        try {
            Method accessor = target.getClass().getMethod(name);
            return accessor.invoke(target);
        } catch (ReflectiveOperationException ignored) {
        }
        String getter = "get" + Character.toUpperCase(name.charAt(0)) + name.substring(1);
        try {
            Method accessor = target.getClass().getMethod(getter);
            return accessor.invoke(target);
        } catch (ReflectiveOperationException ignored) {
        }
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(target);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalArgumentException("Cannot read property '" + name + "' from " + target.getClass().getName(), exception);
        }
    }
}
