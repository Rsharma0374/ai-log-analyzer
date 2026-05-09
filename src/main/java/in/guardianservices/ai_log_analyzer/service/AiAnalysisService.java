package in.guardianservices.ai_log_analyzer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.guardianservices.ai_log_analyzer.model.AiProviderConfig;
import in.guardianservices.ai_log_analyzer.model.AnalysisResult;
import in.guardianservices.ai_log_analyzer.model.LogEntry;
import in.guardianservices.ai_log_analyzer.repository.AnalysisResultRepository;
import in.guardianservices.ai_log_analyzer.repository.LogEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiAnalysisService {

    private final MerlinAICodeAnalyzer merlinAICodeAnalyzer;
    private final GeminiService geminiService;
    private final DeepseekService deepseekService;
    private final AiProviderResolverService aiProviderResolverService;
    private final ObjectMapper objectMapper;
    private final AnalysisResultRepository analysisResultRepository;
    private final LogEntryRepository logEntryRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final AIResponseParser aiResponseParser; // parser to extract rca/fix/code/confidence

    @Autowired
    public AiAnalysisService(MerlinAICodeAnalyzer merlinAICodeAnalyzer,
                             GeminiService geminiService,
                             DeepseekService deepseekService,
                             AiProviderResolverService aiProviderResolverService,
                             AnalysisResultRepository analysisResultRepository,
                             LogEntryRepository logEntryRepository,
                             RedisTemplate<String, Object> redisTemplate,
                             AIResponseParser aiResponseParser) {
        this.merlinAICodeAnalyzer = merlinAICodeAnalyzer;
        this.geminiService = geminiService;
        this.deepseekService = deepseekService;
        this.aiProviderResolverService = aiProviderResolverService;
        this.analysisResultRepository = analysisResultRepository;
        this.logEntryRepository = logEntryRepository;
        this.redisTemplate = redisTemplate;
        this.aiResponseParser = aiResponseParser;
        this.objectMapper = new ObjectMapper();
    }

    public AnalysisResult analyzeLog(LogEntry logEntry) {
        log.info("Starting analysis for log entry ID: {}", logEntry.getId());
        String cacheKey = "analysis:log:" + logEntry.getId();
        
        // 1. Check Redis Cache
        try {
            Object cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                log.info("Cache hit for log: {}", logEntry.getId());
                if (cached instanceof AnalysisResult) {
                    return (AnalysisResult) cached;
                }
                return objectMapper.convertValue(cached, AnalysisResult.class);
            }
        } catch (Exception e) {
            log.warn("Failed to retrieve from Redis cache for key: {}", cacheKey, e);
        }

        // 2. Idempotency Check: DB check to prevent duplicate key constraint violations
        List<AnalysisResult> existingResults = analysisResultRepository.findByLogEntryId(logEntry.getId());
        if (!existingResults.isEmpty()) {
            log.info("Analysis already exists in database for log entry ID: {}", logEntry.getId());
            AnalysisResult existing = existingResults.get(0);
            
            // Re-populate cache since it missed but DB hit
            try {
                redisTemplate.opsForValue().set(cacheKey, existing);
                redisTemplate.expire(cacheKey, Duration.ofHours(24));
            } catch (Exception e) {
                // Ignore cache update failure
            }
            return existing;
        }

        // 3. Perform AI Analysis
        String prompt = buildAnalysisPrompt(logEntry);
        String aiResponse = deepseekService.callDeepSeekAPI(prompt); // may throw
        String[] parsed = aiResponseParser.parseAIResponse(aiResponse); // returns {rca, fix, code}
        
        AnalysisResult result = new AnalysisResult();
        result.setLogEntry(logEntry);
        result.setRootCauseAnalysis(parsed[0]);
        result.setRecommendedFix(parsed[1]);
        result.setCodeSnippet(parsed[2]);
        result.setConfidenceScore(aiResponseParser.extractConfidenceScore(aiResponse));
        result.setAiModel("deepseek"); // set properly with info from DeepseekService if available
        result.setAnalyzedAt(LocalDateTime.now());
        
        long startTimeMs = logEntry.getCreatedAt() != null ? 
                           logEntry.getCreatedAt().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() : 
                           System.currentTimeMillis();
        result.setAnalysisTimeMs(System.currentTimeMillis() - startTimeMs);

        // 4. Persist in a new transaction
        AnalysisResult saved = persistAnalysisResult(result);
        
        // 5. Update log entry status
        logEntry.setStatus(LogEntry.ProcessingStatus.ANALYZED);
        logEntry.setAnalysisResult(saved);
        logEntryRepository.save(logEntry);
        
        // 6. Update Cache
        try {
            redisTemplate.opsForValue().set(cacheKey, saved);
            redisTemplate.expire(cacheKey, Duration.ofHours(24));
            log.debug("Cached analysis result for log: {}", logEntry.getId());
        } catch (Exception e) {
            log.warn("Failed to save to Redis cache for key: {}", cacheKey, e);
        }

        log.info("Log analyzed successfully: {}", logEntry.getId());
        return saved;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected AnalysisResult persistAnalysisResult(AnalysisResult result) {
        return analysisResultRepository.save(result);
    }

    private String buildAnalysisPrompt(LogEntry logEntry) {
        return """
            Analyze the following application log and provide:
            1. Root Cause Analysis (RCA): Why this error occurred
            2. Recommended Fix: How to fix this issue
            3. Code Solution: Java code snippet to prevent this in future
            
            Log Details:
            - Source: %s
            - Severity: %s
            - Error Type: %s
            - Message: %s
            
            Log Content:
            %s
            
            Additional Context:
            %s
            
            Provide your response in the following JSON format:
            {
              "rca": "detailed root cause analysis",
              "fix": "recommended fix steps",
              "code": "java code snippet",
              "confidence": 85
            }
            """.formatted(
                logEntry.getSource(),
                logEntry.getSeverity(),
                logEntry.getErrorType(),
                logEntry.getMessage(),
                logEntry.getRawLog(),
                logEntry.getContext() != null ? logEntry.getContext() : "N/A"
        );
    }

    private String callClaudeAPI(String prompt) {
        try {
            log.debug("Initiating Claude API call");
            return invokeClaudeModel(prompt);
        } catch (Exception e) {
            log.error("Claude API call failed", e);
            throw new RuntimeException("Failed to call Claude API", e);
        }
    }

    private String invokeClaudeModel(String prompt) {
        log.warn("Claude API integration is currently a stub");
        return "{}";
    }

    private String[] parseAIResponse(String jsonResponse) {
        try {
            var json = objectMapper.readTree(cleanJson(jsonResponse));
            return new String[]{
                    json.has("rca") ? json.get("rca").asText() : "",
                    json.has("fix") ? json.get("fix").asText() : "",
                    json.has("code") ? json.get("code").asText() : ""
            };
        } catch (Exception e) {
            log.error("Failed to parse AI response", e);
            return new String[]{"", "", ""};
        }
    }

    private String cleanJson(String response) {
        if (response == null) return null;

        return response
                .replaceAll("^```json", "")
                .replaceAll("^```", "")
                .replaceAll("```$", "")
                .trim();
    }

    private Integer extractConfidenceScore(String response) {
        try {
            var json = objectMapper.readTree(cleanJson(response));
            return json.has("confidence") ? json.get("confidence").asInt() : 50;
        } catch (Exception e) {
            log.warn("Failed to extract confidence score, defaulting to 50");
            return 50;
        }
    }
}
