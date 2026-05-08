package in.guardianservices.ai_log_analyzer.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "log_entries", indexes = {
        @Index(name = "idx_source", columnList = "source"),
        @Index(name = "idx_severity", columnList = "severity"),
        @Index(name = "idx_created_at", columnList = "created_at")
})
public class LogEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(columnDefinition = "TEXT")
    private String rawLog;

    private String source; // app name, service, etc.
    private String severity; // ERROR, WARN, CRITICAL
    private String message;
    private String errorType; // e.g., NullPointerException, TimeoutException
    private String stackTrace;

    @Column(columnDefinition = "TEXT")
    private String context; // additional metadata

    private LocalDateTime timestamp;
    private LocalDateTime createdAt;

    @Enumerated(EnumType.STRING)
    private ProcessingStatus status; // PENDING, ANALYZED, FAILED

    private String environment;

    @OneToOne(mappedBy = "logEntry", cascade = CascadeType.ALL)
    private AnalysisResult analysisResult;

    public enum ProcessingStatus {
        PENDING, ANALYZED, FAILED
    }
}
