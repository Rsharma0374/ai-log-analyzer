package in.guardianservices.ai_log_analyzer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.guardianservices.ai_log_analyzer.dto.AnalysisResponse;
import in.guardianservices.ai_log_analyzer.dto.LogAnalysisRequest;
import in.guardianservices.ai_log_analyzer.model.AnalysisJob;
import in.guardianservices.ai_log_analyzer.model.AnalysisResult;
import in.guardianservices.ai_log_analyzer.model.LogEntry;
import in.guardianservices.ai_log_analyzer.repository.AnalysisJobRepository;
import in.guardianservices.ai_log_analyzer.repository.AnalysisResultRepository;
import in.guardianservices.ai_log_analyzer.repository.LogEntryRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LogAnalysisService {
    private static final Logger log = LoggerFactory.getLogger(LogAnalysisService.class);
    private final LogEntryRepository logEntryRepository;
    private final AnalysisJobRepository jobRepository;
    private final LogParser logParser;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final AnalysisResultRepository analysisResultRepository;
    private final AiAnalysisService aiAnalysisService;

    @Transactional
    public LogEntry parseAndSaveLog(LogAnalysisRequest request) {
        log.debug("Parsing log from source: {}", request.getSource());
        
        String contextStr = null;
        if (request.getContext() != null && !request.getContext().isEmpty()) {
            try {
                contextStr = objectMapper.writeValueAsString(request.getContext());
            } catch (Exception e) {
                log.warn("Failed to serialize context map", e);
                contextStr = request.getContext().toString();
            }
        }
        
        LogEntry entry = logParser.parseLog(
                request.getRawLog(),
                request.getSource(),
                request.getSeverity(),
                contextStr,
                request.getEnvironment()
        );
        entry.setCreatedAt(Instant.now().atZone(java.time.ZoneId.systemDefault()).toLocalDateTime());
        entry.setStatus(LogEntry.ProcessingStatus.PENDING);
        LogEntry saved = logEntryRepository.save(entry);
        log.info("Parsed and saved log entry with ID: {}", saved.getId());
        return saved;
    }

    public UUID enqueueAnalysis(LogEntry entry, String correlationId) {
        try {
            AnalysisJob job = new AnalysisJob();
            job.setLogEntryId(entry.getId());
            job.setCorrelationId(correlationId);
            job.setStatus(AnalysisJob.Status.PENDING);
            job.setCreatedAt(Instant.now());
            AnalysisJob saved = jobRepository.save(job);
            
            kafkaTemplate.send("analysis-jobs", saved.getId().toString(), objectMapper.writeValueAsString(saved));
            log.info("Enqueued analysis job {} for log {}", saved.getId(), entry.getId());
            return saved.getId();
        } catch (Exception e) {
            log.error("Failed to enqueue analysis job for log {}", entry.getId(), e);
            throw new RuntimeException("Failed to enqueue analysis job", e);
        }
    }

    @Transactional
    public AnalysisResult analyzeLog(LogEntry logEntry) {
        log.info("Starting analysis for log entry ID: {}", logEntry.getId());
        
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

        AnalysisResult result = aiAnalysisService.analyzeLog(logEntry);
        result = analysisResultRepository.save(result);

        logEntry.setStatus(LogEntry.ProcessingStatus.ANALYZED);
        logEntry.setAnalysisResult(result);
        logEntryRepository.save(logEntry);

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

    public List<AnalysisResult> findSimilarLogs(String logId) {
        log.debug("Finding similar logs for log ID: {}", logId);
        try {
            UUID id = UUID.fromString(logId);
            LogEntry logEntry = logEntryRepository.findById(id).orElse(null);
            if (logEntry == null) {
                log.warn("Log entry not found for ID: {}", logId);
                return List.of();
            }

            List<AnalysisResult> similar = analysisResultRepository
                    .findSimilarAnalyses(logEntry.getErrorType(), logEntry.getSource(), 5);
            log.info("Found {} similar logs for log ID: {}", similar.size(), logId);
            return similar;
        } catch (IllegalArgumentException e) {
            log.warn("Invalid UUID format for logId: {}", logId);
            return List.of();
        }
    }
}
