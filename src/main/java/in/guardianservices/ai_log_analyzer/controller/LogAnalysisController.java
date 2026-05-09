package in.guardianservices.ai_log_analyzer.controller;

import in.guardianservices.ai_log_analyzer.dto.AnalysisResponse;
import in.guardianservices.ai_log_analyzer.dto.LogAnalysisRequest;
import in.guardianservices.ai_log_analyzer.model.AnalysisResult;
import in.guardianservices.ai_log_analyzer.model.LogEntry;
import in.guardianservices.ai_log_analyzer.repository.AnalysisResultRepository;
import in.guardianservices.ai_log_analyzer.service.LogAnalysisService;
import in.guardianservices.ai_log_analyzer.service.SseEmitterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/logs")
@RequiredArgsConstructor
public class LogAnalysisController {

    private final LogAnalysisService logAnalysisService;
    private final AnalysisResultRepository analysisResultRepository;
    private final SseEmitterService sseEmitterService;

    /**
     * Ingest a single log entry. Saves it and enqueues it for async analysis.
     */
    @PostMapping
    public ResponseEntity<?> ingestLog(@RequestBody LogAnalysisRequest request) {
        log.info("Received request to ingest log from source: {}", request.getSource());
        try {
            LogEntry logEntry = logAnalysisService.parseAndSaveLog(request);
            String correlationId = UUID.randomUUID().toString();
            UUID jobId = logAnalysisService.enqueueAnalysis(logEntry, correlationId);
            
            return ResponseEntity.accepted().body(java.util.Map.of(
                    "status", "ACCEPTED",
                    "logEntryId", logEntry.getId(),
                    "jobId", jobId,
                    "message", "Log accepted and queued for analysis."
            ));
        } catch (Exception e) {
            log.error("Error ingesting log from source: {}", request.getSource(), e);
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * Synchronously analyze a single log entry (legacy support)
     */
    @PostMapping("/analyze")
    public ResponseEntity<AnalysisResponse> analyzeLogSync(@RequestBody LogAnalysisRequest request) {
        log.info("Received request to synchronously analyze log from source: {}", request.getSource());
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

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error synchronously analyzing log", e);
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * Get analysis result by log ID
     */
    @GetMapping("/{logId}/analysis")
    public ResponseEntity<AnalysisResponse> getAnalysis(@PathVariable String logId) {
        log.info("Fetching analysis result for log ID: {}", logId);
        
        try {
            UUID id = UUID.fromString(logId);
            List<AnalysisResult> results = analysisResultRepository.findByLogEntryId(id);
            
            if (results.isEmpty()) {
                log.warn("Analysis result not found for log ID: {}", logId);
                return ResponseEntity.notFound().build();
            }

            AnalysisResult result = results.get(0);

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
        } catch (IllegalArgumentException e) {
            log.warn("Invalid UUID format for logId: {}", logId);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Batch analyze logs synchronously (legacy)
     */
    @PostMapping("/batch-analyze")
    public ResponseEntity<List<AnalysisResponse>> batchAnalyze(
            @RequestBody List<LogAnalysisRequest> requests) {
        log.info("Received request to batch analyze {} logs", requests.size());
        try {
            List<AnalysisResponse> results = logAnalysisService.batchAnalyze(requests);
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

    /**
     * Subscribe to real-time events via Server-Sent Events (SSE)
     */
    @GetMapping(value = "/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribeToEvents(@RequestParam(required = false) String clientId) {
        log.info("New SSE subscription from client: {}", clientId);
        return sseEmitterService.createEmitter(clientId != null ? clientId : UUID.randomUUID().toString());
    }
}
