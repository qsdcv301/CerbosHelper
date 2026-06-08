package io.cerboshelper.mybatis.convention;

import org.apache.ibatis.mapping.ResultMap;
import org.apache.ibatis.mapping.ResultMapping;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

public final class CerbosResultMapIdPropertyRegistrar {
    private static final Logger log = LoggerFactory.getLogger(CerbosResultMapIdPropertyRegistrar.class);

    private final CerbosCommonResourceRegistry registry;

    public CerbosResultMapIdPropertyRegistrar(CerbosCommonResourceRegistry registry) {
        this.registry = registry;
    }

    public void register(List<SqlSessionFactory> sqlSessionFactories) {
        if (sqlSessionFactories == null || sqlSessionFactories.isEmpty()) {
            return;
        }
        sqlSessionFactories.forEach(this::register);
    }

    public void register(SqlSessionFactory sqlSessionFactory) {
        if (sqlSessionFactory == null || sqlSessionFactory.getConfiguration() == null) {
            return;
        }
        sqlSessionFactory.getConfiguration().getResultMaps().forEach(this::register);
    }

    private void register(ResultMap resultMap) {
        Class<?> resourceType = resultMap.getType();
        Optional<String> resourceKind = registry.resourceKindForType(resourceType);
        if (resourceKind.isEmpty()) {
            return;
        }
        Optional<String> idProperty = singleIdProperty(resultMap);
        if (idProperty.isEmpty()) {
            return;
        }
        registry.registerIdProperty(resourceKind.get(), idProperty.get());
        log.debug(
                "cerboshelper.result-map-id resourceKind={} resourceType={} resultMap={} idProperty={}",
                resourceKind.get(),
                resourceType.getName(),
                resultMap.getId(),
                idProperty.get()
        );
    }

    private Optional<String> singleIdProperty(ResultMap resultMap) {
        List<ResultMapping> idMappings = resultMap.getIdResultMappings();
        if (idMappings == null || idMappings.isEmpty()) {
            return Optional.empty();
        }
        if (idMappings.size() > 1) {
            log.warn(
                    "cerboshelper.result-map-id skipped composite id resultMap={} type={} idCount={}",
                    resultMap.getId(),
                    resultMap.getType().getName(),
                    idMappings.size()
            );
            return Optional.empty();
        }
        String property = idMappings.get(0).getProperty();
        if (property == null || property.isBlank()) {
            log.warn(
                    "cerboshelper.result-map-id skipped blank id property resultMap={} type={}",
                    resultMap.getId(),
                    resultMap.getType().getName()
            );
            return Optional.empty();
        }
        return Optional.of(property);
    }
}
