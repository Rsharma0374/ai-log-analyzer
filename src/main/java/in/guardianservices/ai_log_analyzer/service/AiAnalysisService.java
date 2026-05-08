package in.guardianservices.ai_log_analyzer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.guardianservices.ai_log_analyzer.model.AiProviderConfig;
import in.guardianservices.ai_log_analyzer.model.AnalysisResult;
import in.guardianservices.ai_log_analyzer.model.LogEntry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
public class AiAnalysisService {

    private final MerlinAICodeAnalyzer merlinAICodeAnalyzer;
    private final GeminiService geminiService;
    private final DeepseekService deepseekService;
    private final AiProviderResolverService aiProviderResolverService;
    private final ObjectMapper objectMapper;

    @Autowired
    public AiAnalysisService(MerlinAICodeAnalyzer merlinAICodeAnalyzer,
                             GeminiService geminiService,
                             DeepseekService deepseekService,
                             AiProviderResolverService aiProviderResolverService) {
        this.merlinAICodeAnalyzer = merlinAICodeAnalyzer;
        this.geminiService = geminiService;
        this.deepseekService = deepseekService;
        this.aiProviderResolverService = aiProviderResolverService;
        this.objectMapper = new ObjectMapper();
    }

    public AnalysisResult analyzeLog(LogEntry logEntry) {
        try {
            long startTime = System.currentTimeMillis();
            log.info("Starting AI analysis for log entry ID: {}", logEntry.getId());

            String prompt = buildAnalysisPrompt(logEntry);
            AiProviderConfig aiProviderConfig = aiProviderResolverService.getAiProvider();
            
            log.info("Using AI Provider: {}", aiProviderConfig.getConfigValue());

            String aiResponse = switch (aiProviderConfig.getConfigValue().toUpperCase()) {
                case "CLAUDE"   -> callClaudeAPI(prompt);
                case "MERLIN"   -> merlinAICodeAnalyzer.analyzeCode(prompt);
                case "DEEPSEEK" -> deepseekService.callDeepSeekAPI(prompt);
                case "GEMINI"   -> geminiService.callGeminiAPI(prompt);
                default -> {
                    log.warn("Unknown AI provider '{}', falling back to DeepSeek", aiProviderConfig.getConfigValue());
                    yield deepseekService.callDeepSeekAPI(prompt);
                }
            };
            
            log.debug("Received AI response successfully");
            String[] parsed = parseAIResponse(aiResponse);

            AnalysisResult result = new AnalysisResult();
            result.setLogEntry(logEntry);
            result.setRootCauseAnalysis(parsed[0]);
            result.setRecommendedFix(parsed[1]);
            result.setCodeSnippet(parsed[2]);
            result.setConfidenceScore(extractConfidenceScore(aiResponse));
            result.setAiModel(aiProviderConfig.getModel());
            result.setAnalyzedAt(LocalDateTime.now());
            result.setAnalysisTimeMs(System.currentTimeMillis() - startTime);

            log.info("AI analysis completed successfully for log entry ID: {}", logEntry.getId());
            return result;
        } catch (Exception e) {
            log.error("Failed to analyze log entry ID: {}", logEntry.getId(), e);
            throw new RuntimeException("AI analysis failed", e);
        }
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
