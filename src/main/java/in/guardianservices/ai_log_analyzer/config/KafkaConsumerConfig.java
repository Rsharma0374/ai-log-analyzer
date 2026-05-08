package in.guardianservices.ai_log_analyzer.config;

import in.guardianservices.ai_log_analyzer.dto.LogAnalysisRequest;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.listener.ContainerProperties;

import java.util.HashMap;
import java.util.Map;
/**
 * Kafka Consumer Configuration for AI Log Analyzer.
 *
 * Configures the Kafka consumer with:
 * - Proper serialization/deserialization
 * - Error handling mechanisms
 * - Performance tuning parameters
 * - Acknowledgment strategies
 *
 * @author Architecture Team
 * @version 1.0.0
 */
@Configuration
@EnableKafka
public class KafkaConsumerConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaConsumerConfig.class);

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;
    @Value("${spring.kafka.consumer.group-id}")
    private String groupId;
    @Value("${spring.kafka.consumer.max-poll-records:500}")
    private Integer maxPollRecords;
    @Value("${spring.kafka.consumer.max-poll-interval-ms:300000}")
    private Integer maxPollIntervalMs;
    @Value("${spring.kafka.consumer.session-timeout-ms:10000}")
    private Integer sessionTimeoutMs;
    @Value("${spring.kafka.consumer.heartbeat-interval-ms:3000}")
    private Integer heartbeatIntervalMs;
    @Value("${spring.kafka.consumer.fetch-min-bytes:1024}")
    private Integer fetchMinBytes;
    @Value("${spring.kafka.consumer.fetch-max-wait-ms:500}")
    private Integer fetchMaxWaitMs;
    /**
     * Creates a ConsumerFactory for RawErrorLog deserialization.
     * Uses error handling deserializer to gracefully handle malformed messages.
     */

    @Bean
    public ConsumerFactory<String, String> consumerFactory() {
        log.debug("Initializing Kafka ConsumerFactory with bootstrap servers: {}", bootstrapServers);

        Map<String, Object> props = new HashMap<>();

        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);

        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, maxPollRecords);
        props.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, maxPollIntervalMs);
        props.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, sessionTimeoutMs);
        props.put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, heartbeatIntervalMs);
        props.put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, fetchMinBytes);
        props.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, fetchMaxWaitMs);

        log.info("ConsumerFactory configured with group-id: {}", groupId);

        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory() {

        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(consumerFactory());
        factory.setConcurrency(3);

        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        factory.getContainerProperties().setPollTimeout(3000);

        log.info("KafkaListenerContainerFactory configured with manual acknowledgment");

        return factory;
    }
}
