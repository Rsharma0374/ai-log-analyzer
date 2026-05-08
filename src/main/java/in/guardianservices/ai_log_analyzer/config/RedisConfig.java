package in.guardianservices.ai_log_analyzer.config;

import in.guardianservices.ai_log_analyzer.service.InfisicalService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;

import java.util.Map;

@Slf4j
@Configuration
public class RedisConfig {

    private final InfisicalService infisicalService;




    public RedisConfig(InfisicalService infisicalService) {
        this.infisicalService = infisicalService;
    }

    @Bean
    public JedisConnectionFactory jedisConnectionFactory() throws Exception {
        String host = infisicalService.getSecret("host", "RedisSecret");
        String port = infisicalService.getSecret("port", "RedisSecret");
        String password = infisicalService.getSecret("password", "RedisSecret");
        RedisStandaloneConfiguration redisConfig = new RedisStandaloneConfiguration();
        redisConfig.setHostName(StringUtils.isBlank(host) ? "localhost" : host);
        redisConfig.setPort(StringUtils.isBlank(port) ? 6379 : Integer.parseInt(port));
        redisConfig.setPassword(StringUtils.isBlank(password) ? null : password);
        return new JedisConnectionFactory(redisConfig);
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate() throws Exception {
        RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(jedisConnectionFactory());
        redisTemplate.setKeySerializer(RedisSerializer.string());
        redisTemplate.setValueSerializer(RedisSerializer.json());
        return redisTemplate;
    }
}
