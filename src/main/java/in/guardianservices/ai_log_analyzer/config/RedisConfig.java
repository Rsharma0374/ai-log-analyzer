package in.guardianservices.ai_log_analyzer.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import in.guardianservices.ai_log_analyzer.service.InfisicalService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

    @Autowired
    private InfisicalService infisicalService;

    @Bean
    public JedisConnectionFactory jedisConnectionFactory() {
        String host = infisicalService.getSecret("host", "RedisSecret");
        String port = infisicalService.getSecret("port", "RedisSecret");
        String password = infisicalService.getSecret("password", "RedisSecret");
        RedisStandaloneConfiguration redisConfig = new RedisStandaloneConfiguration();
        redisConfig.setHostName(StringUtils.isBlank(host) ? "localhost" : host);
        redisConfig.setPort(StringUtils.isBlank(port) ? 6379 : Integer.parseInt(port));
        if (StringUtils.isNotBlank(password)) {
            redisConfig.setPassword(password);
        }
        return new JedisConnectionFactory(redisConfig);
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate() {
        RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(jedisConnectionFactory());

        ObjectMapper customMapper = new ObjectMapper();
        // Required to serialize java.time.LocalDateTime correctly
        customMapper.registerModule(new JavaTimeModule());
        customMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer(customMapper);

        redisTemplate.setKeySerializer(new StringRedisSerializer());
        redisTemplate.setValueSerializer(jsonSerializer);
        redisTemplate.setHashKeySerializer(new StringRedisSerializer());
        redisTemplate.setHashValueSerializer(jsonSerializer);

        redisTemplate.afterPropertiesSet();
        return redisTemplate;
    }
}
