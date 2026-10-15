package com.example.myapp.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 模板配置。
 *
 * <p><b>为什么需要这个类</b>：Spring Boot 的自动配置只提供
 * {@code RedisTemplate<Object, Object>}，默认用 JDK 序列化 ——
 * 存进去的是二进制乱码，用 redis-cli 看一眼完全不知道存了什么，排查缓存问题会非常痛苦。
 * 这里改成「key 用 String、value 用 JSON」，缓存内容直接可读。
 *
 * <p>两个容易踩的点：
 * <ol>
 *   <li><b>必须单独建 ObjectMapper，不能注入 Spring 那个</b>。这里用的是
 *       {@code RedisTemplate<String, Object>}，声明类型是 Object，Jackson 光看类型
 *       不知道要还原成什么；而且往共享 mapper 上注册模块会影响 Web 层的序列化。
 *       所以 new 一个专用的，只装我们需要的配置。</li>
 *   <li><b>必须注册 JavaTimeModule</b>。缓存的 {@code UserVO} 带
 *       {@code LocalDateTime} 字段，new 出来的 ObjectMapper 没有默认注册 JSR-310 模块，
 *       不注册就会在写缓存时直接抛
 *       {@code InvalidDefinitionException: Java 8 date/time type not supported}。</li>
 * </ol>
 */
@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // key / hashKey 用纯字符串，方便在 redis-cli 里按 key 查找
        StringRedisSerializer keySerializer = new StringRedisSerializer();
        template.setKeySerializer(keySerializer);
        template.setHashKeySerializer(keySerializer);

        ObjectMapper mapper = new ObjectMapper();
        // 用 registeredModule 而不是 findAndRegisterModule：后者依赖模块已被 SPI 自动发现，
        // 在这个独立 mapper 上不保证成立，写缓存时会直接失败
        mapper.registerModule(new JavaTimeModule());

        // 时间写成 ISO-8601 字符串（"2026-10-15T16:59:39"）而不是时间戳数组
        // （[2026,10,15,16,59,39]）。两者都能正确往返，但前者人一眼能读懂 ——
        // 而这个配置类的全部意义就是让缓存内容可读。读写两侧都用同一个 mapper，
        // 格式一致，不会出现写得进读不出的问题。
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // 关键一步：**必须开启默认类型信息**。
        // 不开会怎样：写缓存时 JSON 里只有 {"id":1,"username":"alice",...}，没有类型信息；
        // 读回来时 Jackson 不知道该还原成什么，只能给 LinkedHashMap。
        // 于是 UserCache.get() 里的 `cached instanceof UserVO` 永远为 false ——
        // 缓存**看起来写成功了、key 也在、TTL 也正常，但永远命中不了**，
        // 命中率一直 0%。这类故障很隐蔽，因为没有任何报错。
        // 开启后 JSON 里会多一个 "@class" 字段，由它决定还原成哪个类。
        mapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY);

        GenericJackson2JsonRedisSerializer valueSerializer = new GenericJackson2JsonRedisSerializer(mapper);
        template.setValueSerializer(valueSerializer);
        template.setHashValueSerializer(valueSerializer);

        template.afterPropertiesSet();
        return template;
    }
}
