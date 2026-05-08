package in.guardianservices.ai_log_analyzer.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LogAnalysisRequest {
    private String rawLog;
    private String source;
    private String severity;
    private String context;
    private String logger;
    private String environment;
}
