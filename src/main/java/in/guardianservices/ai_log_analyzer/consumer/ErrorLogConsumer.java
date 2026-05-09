package in.guardianservices.ai_log_analyzer.consumer;

import in.guardianservices.ai_log_analyzer.dto.LogAnalysisRequest;
import in.guardianservices.ai_log_analyzer.exception.LogProcessingException;
import in.guardianservices.ai_log_analyzer.model.LogEntry;
import in.guardianservices.ai_log_analyzer.service.LogAnalysisService;
import in.guardianservices.ai_log_analyzer.utils.Helper;
import org.apache.commons.lang3.StringUtils;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Kafka Message Consumer for Raw Error Logs.
 *
 * This component listens to the Kafka topic "logging.analytics.raw-errors.v1"
 * and processes error logs asynchronously. It saves the log and enqueues it 
 * for AI analysis to avoid blocking the consumer thread.
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

    @KafkaListener(
            topics = "logging.analytics.raw-errors.v1",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory",
            concurrency = "3"
    )
    public void consumeErrorLog(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        LogAnalysisRequest request = null;
        String correlationId = UUID.randomUUID().toString();
        
        try {
            String loggerName = record.key();
            String payload = record.value();
            request = Helper.stringToObject(payload, LogAnalysisRequest.class);

            logMessageReceived(correlationId, loggerName, request);
            
            // Validate incoming message
            validateErrorLog(request);
            
            // Fast Processing: Parse, Save, Enqueue, Ack
            LogEntry logEntry = logAnalysisService.parseAndSaveLog(request);
            logAnalysisService.enqueueAnalysis(logEntry, correlationId);
            
            // Acknowledge successfully ingested log
            acknowledgeMessage(acknowledgment, logEntry.getId().toString());
            incrementProcessedCount();
            
        } catch (LogProcessingException e) {
            handleProcessingException(correlationId, request, e);
            sendToDeadLetterQueue(record, correlationId, e.getMessage());
            if (acknowledgment != null) acknowledgment.acknowledge();
        } catch (Exception e) {
            handleUnexpectedException(correlationId, e);
            sendToDeadLetterQueue(record, correlationId, e.getMessage());
            if (acknowledgment != null) acknowledgment.acknowledge();
        }
    }

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

    private void acknowledgeMessage(Acknowledgment acknowledgment, String logId) {
        if (acknowledgment != null) {
            acknowledgment.acknowledge();
            log.debug("Message acknowledged and queued for analysis - LogId: {}", logId);
        }
    }

    private void handleProcessingException(String correlationId, LogAnalysisRequest request, LogProcessingException exception) {
        incrementFailureCount();
        String source = request != null ? request.getSource() : "UNKNOWN";
        String env = request != null ? request.getEnvironment() : "UNKNOWN";
        log.error("[{}] LogProcessingException - Source: {}, Env: {}, Error: {}", 
                  correlationId, source, env, exception.getMessage());
    }

    private void handleUnexpectedException(String correlationId, Exception exception) {
        incrementFailureCount();
        log.error("[{}] Unexpected exception processing log: ", correlationId, exception);
    }
    
    private void sendToDeadLetterQueue(ConsumerRecord<String, String> record, String correlationId, String reason) {
        // Implementation for sending to a dead-letter topic (DLQ)
        log.warn("[{}] Sending message to DLQ due to: {}", correlationId, reason);
        // kafkaTemplate.send("logging.analytics.raw-errors.dlq", record.key(), record.value());
    }

    private void logMessageReceived(String correlationId, String loggerName, LogAnalysisRequest request) {
        if (log.isDebugEnabled()) {
            log.debug("[{}] Received message - Source: {}, Env: {}", 
                      correlationId, request.getSource(), request.getEnvironment());
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

    public int getProcessedCount() { return processedCount.get(); }
    public int getFailureCount() { return failureCount.get(); }
}