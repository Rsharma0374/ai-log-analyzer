package in.guardianservices.ai_log_analyzer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisResultDTO {
    private UUID id;
    private UUID logEntryId;
    private String rootCauseAnalysis;
    private String recommendedFix;
    private String codeSnippet;
    private Integer confidenceScore;
    private String aiModel;
    private LocalDateTime analyzedAt;
    private Long analysisTimeMs;
}
