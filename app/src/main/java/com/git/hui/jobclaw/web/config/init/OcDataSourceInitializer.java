package com.git.hui.jobclaw.web.config.init;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.core.env.Environment;

import java.net.URI;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * 数据库初始化：MySQL 环境下自动创建数据库（如果不存在）
 * <p>
 * 库表结构由 Liquibase 统一管理，本类只负责确保数据库本身存在，避免 JPA 启动失败。
 *
 * @date 2022/10/15
 */
@Slf4j
@DependsOn("environment")
@ConditionalOnClass(name = "liquibase.Liquibase")
@Configuration
public class OcDataSourceInitializer implements BeanDefinitionRegistryPostProcessor, PriorityOrdered, EnvironmentAware {
    @Value("${jobclaw.database.name}")
    private String database;

    private Environment environment;

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        log.info("开始初始化数据库...");
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
        autoInitDatabase();
    }

    /**
     * 数据库不存在时，尝试创建数据库
     */
    private void autoInitDatabase() {
        URI url = URI.create(getConfigOrElse("spring.datasource.url", "spring.dynamic.datasource.master.url").substring(5));
        String uname = getConfigOrElse("spring.datasource.username", "spring.dynamic.datasource.master.username");
        String pwd = getConfigOrElse("spring.datasource.password", "spring.dynamic.datasource.master.password");

        try (Connection connection = DriverManager.getConnection("jdbc:mysql://" + url.getHost() + ":" + url.getPort() +
                "?" + url.getRawQuery(), uname, pwd);
             Statement statement = connection.createStatement()) {
            String dbQuerySql = "select schema_name from information_schema.schemata where schema_name = '" + database + "'";
            ResultSet set = statement.executeQuery(dbQuerySql);
            if (!set.next()) {
                String createDb = "CREATE DATABASE IF NOT EXISTS `" + database + "`";
                connection.setAutoCommit(false);
                statement.execute(createDb);
                connection.commit();
                log.info("创建数据库（{}）成功", database);
            } else {
                log.info("数据库已存在，无需初始化");
            }
            set.close();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private String getConfigOrElse(String mainKey, String slaveKey) {
        String ans = environment.getProperty(mainKey);
        if (ans == null) {
            return environment.getProperty(slaveKey);
        }
        return ans;
    }
}
