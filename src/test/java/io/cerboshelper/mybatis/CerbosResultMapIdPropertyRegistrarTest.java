package io.cerboshelper.mybatis;

import io.cerboshelper.mybatis.auth.CerbosHelperProperties;
import io.cerboshelper.mybatis.auth.CerbosPayloadMapper;
import io.cerboshelper.mybatis.check.CerbosCheckSpec;
import io.cerboshelper.mybatis.convention.CerbosCheckConventionResolver;
import io.cerboshelper.mybatis.convention.CerbosCommonResourceRegistry;
import io.cerboshelper.mybatis.convention.CerbosResultMapIdPropertyRegistrar;
import io.cerboshelper.mybatis.model.CerbosCommonDto;
import io.cerboshelper.mybatis.support.CerbosMethodExpressionEvaluator;
import org.apache.ibatis.mapping.ResultFlag;
import org.apache.ibatis.mapping.ResultMap;
import org.apache.ibatis.mapping.ResultMapping;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.defaults.DefaultSqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CerbosResultMapIdPropertyRegistrarTest {
    @Test
    void registersExactSingleResultMapIdPropertyWithoutGuessingFromResourceName() {
        CerbosCommonResourceRegistry registry = new CerbosCommonResourceRegistry(List.of(UserMemoDto.class));
        Configuration configuration = configurationWithResultMap("userMemoDtoResultMap", UserMemoDto.class, "userMemoId");

        new CerbosResultMapIdPropertyRegistrar(registry).register(new DefaultSqlSessionFactory(configuration));

        assertEquals("userMemoId", registry.idPropertyForType(UserMemoDto.class).orElseThrow());
    }

    @Test
    void skipsMyBatisAmbiguousShortResultMapNames() {
        CerbosCommonResourceRegistry registry = new CerbosCommonResourceRegistry(List.of(UserMemoDto.class));
        Configuration configuration = new Configuration();
        configuration.addResultMap(resultMap(configuration, "memo.userMemoDtoResultMap", UserMemoDto.class,
                idMapping(configuration, "userMemoId")));
        configuration.addResultMap(resultMap(configuration, "archive.userMemoDtoResultMap", UserMemoDto.class,
                idMapping(configuration, "userMemoId")));

        new CerbosResultMapIdPropertyRegistrar(registry).register(new DefaultSqlSessionFactory(configuration));

        assertEquals("userMemoId", registry.idPropertyForType(UserMemoDto.class).orElseThrow());
    }

    @Test
    void resourcePayloadUsesRegisteredResultMapIdProperty() {
        CerbosCommonResourceRegistry registry = new CerbosCommonResourceRegistry(List.of(UserMemoDto.class));
        registry.registerIdProperty(UserMemoDto.class, "userMemoId");
        CerbosPayloadMapper payloadMapper = new CerbosPayloadMapper(new CerbosHelperProperties(), registry);

        Map<String, Object> payload = payloadMapper.resourcePayload(new UserMemoDto(12));

        assertEquals("12", payload.get("id"));
        assertEquals("userMemo", payload.get("kind"));
    }

    @Test
    void resourcePayloadUsesSyntheticNewIdForCreateWhenRegisteredIdPropertyIsNull() {
        CerbosCommonResourceRegistry registry = new CerbosCommonResourceRegistry(List.of(UserMemoDto.class));
        registry.registerIdProperty(UserMemoDto.class, "userMemoId");
        CerbosPayloadMapper payloadMapper = new CerbosPayloadMapper(new CerbosHelperProperties(), registry);

        Map<String, Object> payload = payloadMapper.resourcePayload(new UserMemoDto(null));

        assertEquals("new", payload.get("id"));
    }

    @Test
    void conventionUsesRegisteredIdPropertyForDtoOnlyExistingResourceChecks() throws Exception {
        CerbosCommonResourceRegistry registry = new CerbosCommonResourceRegistry(List.of(UserMemoDto.class));
        registry.registerIdProperty(UserMemoDto.class, "userMemoId");
        CerbosCheckConventionResolver resolver = new CerbosCheckConventionResolver(registry);
        CerbosMethodExpressionEvaluator evaluator =
                new CerbosMethodExpressionEvaluator(new DefaultListableBeanFactory());
        Method method = UserMemoService.class.getDeclaredMethod("updateUserMemo", UserMemoDto.class);

        CerbosCheckSpec check = resolver.resolve(method, evaluator.context(method, new Object[]{new UserMemoDto(20)}))
                .orElseThrow();

        assertEquals("#userMemoDto.userMemoId", check.id());
    }

    @Test
    void skipsCompositeResultMapIdsBecauseResourceIdWouldBeAmbiguous() {
        CerbosCommonResourceRegistry registry = new CerbosCommonResourceRegistry(List.of(UserMemoDto.class));
        Configuration configuration = new Configuration();
        configuration.addResultMap(resultMap(
                configuration,
                "compositeUserMemoDtoResultMap",
                UserMemoDto.class,
                idMapping(configuration, "tenantId"),
                idMapping(configuration, "userMemoId")
        ));

        new CerbosResultMapIdPropertyRegistrar(registry).register(new DefaultSqlSessionFactory(configuration));

        assertTrue(registry.idPropertyForType(UserMemoDto.class).isEmpty());
    }

    @Test
    void rejectsConflictingResultMapIdPropertiesForSameResourceType() {
        CerbosCommonResourceRegistry registry = new CerbosCommonResourceRegistry(List.of(UserMemoDto.class));
        Configuration configuration = new Configuration();
        configuration.addResultMap(resultMap(
                configuration,
                "userMemoDtoResultMap",
                UserMemoDto.class,
                idMapping(configuration, "memoId")
        ));
        configuration.addResultMap(resultMap(
                configuration,
                "alternateUserMemoDtoResultMap",
                UserMemoDto.class,
                idMapping(configuration, "userMemoId")
        ));

        CerbosResultMapIdPropertyRegistrar registrar = new CerbosResultMapIdPropertyRegistrar(registry);

        assertThrows(IllegalStateException.class, () -> registrar.register(new DefaultSqlSessionFactory(configuration)));
    }

    private Configuration configurationWithResultMap(String resultMapId, Class<?> type, String idProperty) {
        Configuration configuration = new Configuration();
        configuration.addResultMap(resultMap(configuration, resultMapId, type, idMapping(configuration, idProperty)));
        return configuration;
    }

    private ResultMap resultMap(Configuration configuration, String resultMapId, Class<?> type, ResultMapping... mappings) {
        return new ResultMap.Builder(configuration, resultMapId, type, List.of(mappings)).build();
    }

    private ResultMapping idMapping(Configuration configuration, String property) {
        return new ResultMapping.Builder(configuration, property, camelToSnake(property), Integer.class)
                .flags(List.of(ResultFlag.ID))
                .build();
    }

    private String camelToSnake(String value) {
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (Character.isUpperCase(current)) {
                builder.append('_');
                builder.append(Character.toLowerCase(current));
            } else {
                builder.append(current);
            }
        }
        return builder.toString();
    }

    static class UserMemoDto extends CerbosCommonDto {
        private final Integer userMemoId;

        UserMemoDto(Integer userMemoId) {
            this.userMemoId = userMemoId;
        }

        public Integer getUserMemoId() {
            return userMemoId;
        }
    }

    static class UserMemoService {
        @SuppressWarnings("unused")
        void updateUserMemo(UserMemoDto userMemoDto) {
        }
    }
}
