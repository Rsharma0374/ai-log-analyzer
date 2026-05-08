package in.guardianservices.ai_log_analyzer.consumer;

import in.guardianservices.ai_log_analyzer.dto.LogAnalysisRequest;
import in.guardianservices.ai_log_analyzer.exception.LogProcessingException;
import in.guardianservices.ai_log_analyzer.model.AnalysisResult;
import in.guardianservices.ai_log_analyzer.model.LogEntry;
import in.guardianservices.ai_log_analyzer.service.LogAnalysisService;
import in.guardianservices.ai_log_analyzer.utils.Helper;
import org.apache.commons.lang3.StringUtils;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.concurrent.atomic.AtomicInteger;
/**
 * Kafka Message Consumer for Raw Error Logs.
 *
 * This component listens to the Kafka topic "logging.analytics.raw-errors.v1"
 * and processes error logs with intelligent analysis and enrichment capabilities.
 *
 * Features:
 * - Manual acknowledgment for guaranteed processing
 * - Comprehensive error handling and logging
 * - Async processing delegation to service layer
 * - Partition and offset tracking for monitoring
 *
 * @author Architecture Team
 * @version 1.0.0
 */
@Component
public class ErrorLogConsumer {

    private static final Logger log = LoggerFactory.getLogger(ErrorLogConsumer.class);
    private final LogAnalysisService logAnalysisService;
    private final AtomicInteger processedCount = new AtomicInteger(0);
    private final AtomicInteger failureCount = new AtomicInteger(0);
    public ErrorLogConsumer(LogAnalysisService logAnalysisService) {
        this.logAnalysisService = logAnalysisService;
    }
    /**
     * Listens to error log messages from Kafka topic.
     *
     * Processing strategy:
     * 1. Receives message with metadata (partition, offset)
     * 2. Delegates to analysis service for processing
     * 3. Manually acknowledges on successful processing
     * 4. Handles failures gracefully without acknowledgment
     *
     * @param record the deserialized error log message
     * @param acknowledgment manual acknowledgment handle
     */
    @KafkaListener(
            topics = "logging.analytics.raw-errors.v1",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory",
            concurrency = "3"
    )
    public void consumeErrorLog(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {

        LogAnalysisRequest logAnalysisRequest = null;
        try {
            String loggerName = record.key();
            String logAnalysisRequestString = record.value();
            logAnalysisRequest = Helper.stringToObject(logAnalysisRequestString, LogAnalysisRequest.class);

            logMessageReceived(loggerName, logAnalysisRequest);
            // Validate incoming message
            validateErrorLog(logAnalysisRequest);
            // Process the error log through analysis service
            LogEntry logEntry = logAnalysisService.parseAndSaveLog(logAnalysisRequest);
            AnalysisResult result = logAnalysisService.analyzeLog(logEntry);
            // Acknowledge successful processing
            acknowledgeMessage(acknowledgment, result);
            incrementProcessedCount();
        } catch (LogProcessingException processingException) {
            handleProcessingException(logAnalysisRequest, processingException);
        } catch (Exception unexpectedException) {
            handleUnexpectedException(unexpectedException);
        }
    }
    /**
     * Validates the error log for essential fields.
     *
     * @param errorLog the error log to validate
     * @throws LogProcessingException if validation fails
     */
    private void validateErrorLog(LogAnalysisRequest errorLog) throws LogProcessingException {
        if (errorLog == null) {
            throw new LogProcessingException("Received null error log message");
        }
        if (errorLog.getRawLog() == null || errorLog.getRawLog().isBlank()) {
            throw new LogProcessingException("Error log missing required field: rawLog");
        }
        if (StringUtils.isBlank(errorLog.getSource())) {
            throw new LogProcessingException("Error log missing required field: source");
        }
    }
    /**
     * Acknowledges the message, signaling successful processing to Kafka.
     */
    private void acknowledgeMessage(Acknowledgment acknowledgment, AnalysisResult result) {
        if (acknowledgment != null) {
            acknowledgment.acknowledge();
            log.debug("Message acknowledged - LogId: {}, Confidence score: {}, Analysis time: {}",
                    result.getLogEntry().getId(), result.getConfidenceScore(), result.getAnalysisTimeMs());
        }
    }
    /**
     * Handles processing-specific exceptions.
     * Logs details and increments failure counter without acknowledging.
     */
    private void handleProcessingException(LogAnalysisRequest errorLog,
                                           LogProcessingException exception) {
        incrementFailureCount();
        log.error("LogProcessingException occurred - ServiceName: {}, Environment: {}, Message: {}",
                 errorLog.getSource(), errorLog.getEnvironment(),
                exception.getMessage(), exception);
    }
    /**
     * Handles unexpected exceptions.
     * Logs details with full stack trace and increments failure counter.
     */
    private void handleUnexpectedException(Exception exception) {
        incrementFailureCount();
        log.error("Unexpected exception while processing error log with cause: ", exception);
    }
    /**
     * Logs message reception with metadata.
     */
    private void logMessageReceived(String loggerName, LogAnalysisRequest request) {
        if (log.isDebugEnabled()) {
            log.debug("Message received - ServiceName: {}, Environment: {}, rawLog: {}",
                    request.getSource(), request.getEnvironment(), request.getRawLog());
        }
    }
    private void incrementProcessedCount() {
        int count = processedCount.incrementAndGet();
        if (count % 100 == 0) {
            log.info("Processed {} error logs successfully", count);
        }
    }
    private void incrementFailureCount() {
        int count = failureCount.incrementAndGet();
        log.warn("Failed to process error log. Failure count: {}", count);
    }
    /**
     * Provides monitoring metrics for the consumer.
     */
    public int getProcessedCount() {
        return processedCount.get();
    }
    public int getFailureCount() {
        return failureCount.get();
    }
}
