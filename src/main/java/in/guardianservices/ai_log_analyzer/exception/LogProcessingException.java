package in.guardianservices.ai_log_analyzer.exception;

/**
 * Base exception for log processing failures.
 *
 * Used throughout the application to indicate errors during:
 * - Message deserialization
 * - Log analysis and enrichment
 * - Storage operations
 * - Service processing
 *
 * @author Architecture Team
 * @version 1.0.0
 */
public class LogProcessingException extends Exception {

    private static final long serialVersionUID = 1L;
    private final String errorCode;
    private final String correlationId;
    public LogProcessingException(String message) {
        super(message);
        this.errorCode = "UNKNOWN_ERROR";
        this.correlationId = null;
    }
    public LogProcessingException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = "UNKNOWN_ERROR";
        this.correlationId = null;
    }
    public LogProcessingException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.correlationId = null;
    }
    public LogProcessingException(String errorCode, String message,
                                  String correlationId, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.correlationId = correlationId;
    }
    public String getErrorCode() {
        return errorCode;
    }
    public String getCorrelationId() {
        return correlationId;
    }
    @Override
    public String toString() {
        return "LogProcessingException{" +
                "errorCode='" + errorCode + '\'' +
                ", correlationId='" + correlationId + '\'' +
                ", message='" + getMessage() + '\'' +
                '}';
    }
}
/**
 * Exception thrown when Kafka message deserialization fails.
 */
class KafkaDeserializationException extends LogProcessingException {
    public KafkaDeserializationException(String message, Throwable cause) {
        super("DESERIALIZATION_ERROR", message, cause);
    }
}
/**
 * Exception thrown when log storage/persistence fails.
 */
class LogStorageException extends LogProcessingException {
    public LogStorageException(String message, Throwable cause) {
        super("STORAGE_ERROR", message, cause);
    }
}
/**
 * Exception thrown when log enrichment fails.
 */
class LogEnrichmentException extends LogProcessingException {
    public LogEnrichmentException(String message, Throwable cause) {
        super("ENRICHMENT_ERROR", message, cause);
    }
}
