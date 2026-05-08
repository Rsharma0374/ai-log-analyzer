package in.guardianservices.ai_log_analyzer.controller;

import in.guardianservices.ai_log_analyzer.dto.AnalysisResponse;
import in.guardianservices.ai_log_analyzer.dto.LogAnalysisRequest;
import in.guardianservices.ai_log_analyzer.model.AnalysisResult;
import in.guardianservices.ai_log_analyzer.model.LogEntry;
import in.guardianservices.ai_log_analyzer.service.LogAnalysisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/logs")
@RequiredArgsConstructor
public class LogAnalysisController {

    private final LogAnalysisService logAnalysisService;

    /**
     * Analyze a single log entry
     */
    @PostMapping("/analyze")
    public ResponseEntity<AnalysisResponse> analyzeLog(
            @RequestBody LogAnalysisRequest request) {

        log.info("Received request to analyze log from source: {}", request.getSource());

        try {
            LogEntry logEntry = logAnalysisService.parseAndSaveLog(request);
            AnalysisResult result = logAnalysisService.analyzeLog(logEntry);

            AnalysisResponse response = AnalysisResponse.builder()
                    .logId(String.valueOf(logEntry.getId()))
                    .status("SUCCESS")
                    .rootCauseAnalysis(result.getRootCauseAnalysis())
                    .recommendedFix(result.getRecommendedFix())
                    .codeSnippet(result.getCodeSnippet())
                    .confidenceScore(result.getConfidenceScore())
                    .analysisTimeMs(result.getAnalysisTimeMs())
                    .build();

            log.info("Successfully analyzed log entry ID: {}", logEntry.getId());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error analyzing log from source: {}", request.getSource(), e);
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * Get analysis result by log ID
     */
    @GetMapping("/{logId}/analysis")
    public ResponseEntity<AnalysisResponse> getAnalysis(@PathVariable String logId) {
        log.info("Fetching analysis result for log ID: {}", logId);
        AnalysisResult result = logAnalysisService.getAnalysisResult(logId);

        if (result == null) {
            log.warn("Analysis result not found for log ID: {}", logId);
            return ResponseEntity.notFound().build();
        }

        AnalysisResponse response = AnalysisResponse.builder()
                .logId(logId)
                .status("SUCCESS")
                .rootCauseAnalysis(result.getRootCauseAnalysis())
                .recommendedFix(result.getRecommendedFix())
                .codeSnippet(result.getCodeSnippet())
                .confidenceScore(result.getConfidenceScore())
                .analysisTimeMs(result.getAnalysisTimeMs())
                .build();

        return ResponseEntity.ok(response);
    }

    /**
     * Batch analyze logs
     */
    @PostMapping("/batch-analyze")
    public ResponseEntity<List<AnalysisResponse>> batchAnalyze(
            @RequestBody List<LogAnalysisRequest> requests) {

        log.info("Received request to batch analyze {} logs", requests.size());
        
        try {
            List<AnalysisResponse> results = logAnalysisService.batchAnalyze(requests);
            log.info("Successfully completed batch analysis for {} logs", requests.size());
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            log.error("Error occurred during batch analysis", e);
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * Get similar logs for reference
     */
    @GetMapping("/{logId}/similar")
    public ResponseEntity<List<AnalysisResult>> getSimilarLogs(@PathVariable String logId) {
        log.info("Fetching similar logs for log ID: {}", logId);
        
        try {
            List<AnalysisResult> similarLogs = logAnalysisService.findSimilarLogs(logId);
            return ResponseEntity.ok(similarLogs);
        } catch (Exception e) {
            log.error("Error finding similar logs for log ID: {}", logId, e);
            return ResponseEntity.status(500).build();
        }
    }
}
