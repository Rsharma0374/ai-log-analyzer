package in.guardianservices.ai_log_analyzer.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "analysis_results", indexes = {
        @Index(name = "idx_log_id", columnList = "log_entry_id")
})
public class AnalysisResult {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @ToString.Exclude
    @JsonIgnore
    @OneToOne
    @JoinColumn(name = "log_entry_id")
    private LogEntry logEntry;

    @Column(columnDefinition = "TEXT")
    private String rootCauseAnalysis;

    @Column(columnDefinition = "TEXT")
    private String recommendedFix;

    @Column(columnDefinition = "TEXT")
    private String codeSnippet; // Java code fix if applicable

    private Integer confidenceScore; // 0-100

    @Column(columnDefinition = "TEXT")
    private String relatedLogs; // JSON array of similar log IDs

    private String aiModel; // Claude model version used

    private LocalDateTime analyzedAt;
    private Long analysisTimeMs;
}
