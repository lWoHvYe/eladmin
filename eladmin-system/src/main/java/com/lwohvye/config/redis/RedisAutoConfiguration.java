/*
 *  Copyright 2020-2022 lWoHvYe
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.lwohvye.config.redis;

import com.alibaba.fastjson.parser.ParserConfig;
import com.lwohvye.utils.serializer.FastJsonRedisSerializer;
import com.lwohvye.utils.serializer.StringRedisSerializer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;

/**
 * @author Hongyan Wang
 * @description Redis配置类
 * @date 2021/3/17 0:11
 */
@Configuration
@EnableConfigurationProperties(
        {
                RedisAutoConfiguration.MainRedisProperties.class,
                RedisAutoConfiguration.SlaveRedisProperties.class,
                RedisAutoConfiguration.AuthRedisProperties.class,
                RedisAutoConfiguration.AuthSlaveRedisProperties.class
        }
)
public class RedisAutoConfiguration {
    /**
     * @Primary 注解告诉Spring遇到多个bean的时候，它为默认
     * prefix  告诉spring读取前缀为spring.main-redis的配置信息
     */
    @Primary
    @ConfigurationProperties(prefix = "spring.main-redis")
    public static class MainRedisProperties extends RedisProperties {
    }

    @ConfigurationProperties(prefix = "spring.slave-redis")
    public static class SlaveRedisProperties extends RedisProperties {
    }

    @ConfigurationProperties(prefix = "spring.auth-redis")
    public static class AuthRedisProperties extends RedisProperties {
    }

    @ConfigurationProperties(prefix = "spring.auth-slave-redis")
    public static class AuthSlaveRedisProperties extends RedisProperties {
    }

    /**
     * 通过配置获取RedisConnectionFactory
     *
     * @param mainRedisProperties mainRedis的配置信息
     * @return
     */
    @Primary
    @Bean("mainRedisConnectionFactory")
    public static RedisConnectionFactory getMainRedisConnectionFactory(MainRedisProperties mainRedisProperties) {
        var factory = new RedisConnectionConfiguration(mainRedisProperties);
        return factory.redisConnectionFactory();
    }

    /**
     * 获取mainRedis的RedisTemplate实例
     *
     * @param redisConnectionFactory 使用@Qulifier指定需要的factory
     * @return template
     */
    @Primary
    @Bean("mainRedisTemplate")
    public static RedisTemplate<Object, Object> getMainRedisTemplate(@Qualifier(value = "mainRedisConnectionFactory") RedisConnectionFactory redisConnectionFactory) {
        return createRedisTemplate(redisConnectionFactory);
    }

    @Bean("slaveRedisConnectionFactory")
    public static RedisConnectionFactory getSlaveRedisConnectionFactory(SlaveRedisProperties slaveRedisProperties) {
        var factory = new RedisConnectionConfiguration(slaveRedisProperties);
        return factory.redisConnectionFactory();
    }

    @Bean("slaveRedisTemplate")
    public static RedisTemplate<Object, Object> getSlaveRedisTemplate(@Qualifier(value = "slaveRedisConnectionFactory") RedisConnectionFactory redisConnectionFactory) {
        return createRedisTemplate(redisConnectionFactory);
    }

    @Bean("authRedisConnectionFactory")
    public static RedisConnectionFactory getAuthRedisConnectionFactory(AuthRedisProperties authRedisProperties) {
        var factory = new RedisConnectionConfiguration(authRedisProperties);
        return factory.redisConnectionFactory();
    }

    @Bean("authRedisTemplate")
    public static RedisTemplate<Object, Object> getAuthRedisTemplate(@Qualifier(value = "authRedisConnectionFactory") RedisConnectionFactory redisConnectionFactory) {
        return createRedisTemplate(redisConnectionFactory);
    }

    @Bean("authSlaveRedisConnectionFactory")
    public static RedisConnectionFactory getAuthSlaveRedisConnectionFactory(AuthSlaveRedisProperties authSlaveRedisProperties) {
        var factory = new RedisConnectionConfiguration(authSlaveRedisProperties);
        return factory.redisConnectionFactory();
    }

    @Bean("authSlaveRedisTemplate")
    public static RedisTemplate<Object, Object> getAuthSlaveRedisTemplate(@Qualifier(value = "authSlaveRedisConnectionFactory") RedisConnectionFactory redisConnectionFactory) {
        return createRedisTemplate(redisConnectionFactory);
    }

    private static RedisTemplate<Object, Object> createRedisTemplate(RedisConnectionFactory redisConnectionFactory) {
        var template = new RedisTemplate<>();
        //序列化
        var fastJsonRedisSerializer = new FastJsonRedisSerializer<>(Object.class);
        // value值的序列化采用fastJsonRedisSerializer
        template.setValueSerializer(fastJsonRedisSerializer);
        template.setHashValueSerializer(fastJsonRedisSerializer);
        // 全局开启AutoType，这里方便开发，使用全局的方式
        ParserConfig.getGlobalInstance().setAutoTypeSupport(true);
        // 建议使用这种方式，小范围指定白名单
        // ParserConfig.getGlobalInstance().addAccept("com.lwohvye.domain");
        // key的序列化采用StringRedisSerializer
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setConnectionFactory(redisConnectionFactory);
        template.afterPropertiesSet();
        return template;
    }
}
