package in.guardianservices.ai_log_analyzer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnalysisResponse {
    private String logId;
    private String status;
    private String rootCauseAnalysis;
    private String recommendedFix;
    private String codeSnippet;
    private Integer confidenceScore;
    private Long analysisTimeMs;
}
