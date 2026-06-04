package io.cerboshelper.mybatis.scope;

import org.apache.ibatis.session.SqlSessionFactory;

import java.util.List;

@FunctionalInterface
public interface CerbosMyBatisInterceptorOrderStrategy {
    void apply(List<SqlSessionFactory> sqlSessionFactories);
}
