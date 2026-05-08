package in.guardianservices.ai_log_analyzer.service;

import in.guardianservices.ai_log_analyzer.dto.AnalysisResponse;
import in.guardianservices.ai_log_analyzer.dto.LogAnalysisRequest;
import in.guardianservices.ai_log_analyzer.model.AnalysisResult;
import in.guardianservices.ai_log_analyzer.model.LogEntry;
import in.guardianservices.ai_log_analyzer.repository.AnalysisResultRepository;
import in.guardianservices.ai_log_analyzer.repository.LogEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LogAnalysisService {

    private final LogParser logParser;
    private final AiAnalysisService aiAnalysisService;
    private final LogEntryRepository logEntryRepository;
    private final AnalysisResultRepository analysisResultRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    @Transactional
    public LogEntry parseAndSaveLog(LogAnalysisRequest request) {
        log.debug("Parsing log from source: {}", request.getSource());
        LogEntry logEntry = logParser.parseLog(
                request.getRawLog(),
                request.getSource(),
                request.getSeverity(),
                request.getContext(),
                request.getEnvironment()
        );

        LogEntry savedEntry = logEntryRepository.save(logEntry);
        log.info("Parsed and saved log entry with ID: {}", savedEntry.getId());
        return savedEntry;
    }

    @Transactional
    public AnalysisResult analyzeLog(LogEntry logEntry) {
        log.info("Starting analysis for log entry ID: {}", logEntry.getId());
        
        // Check cache first
        String cacheKey = "analysis:" + logEntry.getId();
        try {
            Object cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                log.info("Cache hit for log: {}", logEntry.getId());
                return (AnalysisResult) cached;
            }
        } catch (Exception e) {
            log.warn("Failed to retrieve from Redis cache for key: {}", cacheKey, e);
        }

        // Analyze with AI
        AnalysisResult result = aiAnalysisService.analyzeLog(logEntry);
        result = analysisResultRepository.save(result);

        // Update log entry status
        logEntry.setStatus(LogEntry.ProcessingStatus.ANALYZED);
        logEntry.setAnalysisResult(result);
        logEntryRepository.save(logEntry);

        // Cache result for 24 hours
        try {
            redisTemplate.opsForValue().set(cacheKey, result);
            redisTemplate.expire(cacheKey, Duration.ofHours(24));
            log.debug("Cached analysis result for log: {}", logEntry.getId());
        } catch (Exception e) {
            log.warn("Failed to save to Redis cache for key: {}", cacheKey, e);
        }

        log.info("Log analyzed successfully: {}", logEntry.getId());
        return result;
    }

    @Transactional
    public List<AnalysisResponse> batchAnalyze(List<LogAnalysisRequest> requests) {
        log.info("Starting batch analysis for {} requests", requests.size());
        return requests.stream()
                .map(req -> {
                    try {
                        LogEntry logEntry = parseAndSaveLog(req);
                        AnalysisResult result = analyzeLog(logEntry);

                        return AnalysisResponse.builder()
                                .logId(String.valueOf(logEntry.getId()))
                                .status("SUCCESS")
                                .rootCauseAnalysis(result.getRootCauseAnalysis())
                                .recommendedFix(result.getRecommendedFix())
                                .codeSnippet(result.getCodeSnippet())
                                .confidenceScore(result.getConfidenceScore())
                                .analysisTimeMs(result.getAnalysisTimeMs())
                                .build();
                    } catch (Exception e) {
                        log.error("Batch analysis failed for log from source: {}", req.getSource(), e);
                        return AnalysisResponse.builder()
                                .status("FAILED")
                                .build();
                    }
                })
                .collect(Collectors.toList());
    }

    public AnalysisResult getAnalysisResult(String logId) {
        log.debug("Fetching analysis result for log ID: {}", logId);
        LogEntry logEntry = logEntryRepository.findById(logId).orElse(null);
        return logEntry != null ? logEntry.getAnalysisResult() : null;
    }

    public List<AnalysisResult> findSimilarLogs(String logId) {
        log.debug("Finding similar logs for log ID: {}", logId);
        LogEntry logEntry = logEntryRepository.findById(logId).orElse(null);
        if (logEntry == null) {
            log.warn("Log entry not found for ID: {}", logId);
            return List.of();
        }

        // Find logs with same error type or source
        List<AnalysisResult> similar = analysisResultRepository
                .findSimilarAnalyses(logEntry.getErrorType(), logEntry.getSource(), 5);
        log.info("Found {} similar logs for log ID: {}", similar.size(), logId);
        return similar;
    }
}
