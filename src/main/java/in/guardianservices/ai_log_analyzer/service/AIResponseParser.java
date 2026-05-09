package in.guardianservices.ai_log_analyzer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AIResponseParser {

    private final ObjectMapper objectMapper;

    /**
     * Parses the AI response string (assumed to be a JSON block) and returns an array:
     * [0] = rca (Root Cause Analysis)
     * [1] = fix (Recommended Fix)
     * [2] = code (Code Snippet)
     */
    public String[] parseAIResponse(String jsonResponse) {
        try {
            var json = objectMapper.readTree(cleanJson(jsonResponse));
            return new String[]{
                    json.has("rca") ? json.get("rca").asText() : "",
                    json.has("fix") ? json.get("fix").asText() : "",
                    json.has("code") ? json.get("code").asText() : ""
            };
        } catch (Exception e) {
            log.error("Failed to parse AI response. Raw string: {}", jsonResponse, e);
            return new String[]{"Failed to parse RCA", "Failed to parse fix", ""};
        }
    }

    /**
     * Extracts the confidence score from the JSON. Default to 50 if missing or error.
     */
    public Integer extractConfidenceScore(String response) {
        try {
            var json = objectMapper.readTree(cleanJson(response));
            return json.has("confidence") ? json.get("confidence").asInt() : 50;
        } catch (Exception e) {
            log.warn("Failed to extract confidence score, defaulting to 50");
            return 50;
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
}
