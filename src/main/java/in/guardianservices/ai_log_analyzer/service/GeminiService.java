package in.guardianservices.ai_log_analyzer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class GeminiService {

    private static final Logger log = LoggerFactory.getLogger(GeminiService.class);
    private static final String GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent";
    
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final InfisicalService infisicalService;

    @Autowired
    public GeminiService(RestTemplate restTemplate, ObjectMapper objectMapper, InfisicalService infisicalService) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.infisicalService = infisicalService;
    }

    public String callGeminiAPI(String prompt) {
        try {
            log.info("Initiating call to Gemini API");
            return invokeGeminiModel(prompt);
        } catch (Exception e) {
            log.error("Gemini API call failed", e);
            throw new RuntimeException("Failed to call Gemini API", e);
        }
    }

    private String invokeGeminiModel(String prompt) throws Exception {
        String apiKey = infisicalService.getSecret("apiKey", "GeminiSecret");
        if (apiKey == null || apiKey.isEmpty()) {
            throw new RuntimeException("Gemini API key is not configured.");
        }
        
        String urlWithKey = GEMINI_API_URL + "?key=" + apiKey;
        
        Map<String, Object> requestBody = buildRequestBody(prompt);
        String jsonBody = objectMapper.writeValueAsString(requestBody);
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(jsonBody, headers);
        
        log.debug("Sending request to Gemini API");
        ResponseEntity<String> response = restTemplate.exchange(
                urlWithKey,
                HttpMethod.POST,
                entity,
                String.class
        );
        
        if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
            log.debug("Received successful response from Gemini API");
            return extractTextFromResponse(response.getBody());
        }
        
        log.error("Failed response from Gemini API: HTTP {}", response.getStatusCode());
        throw new RuntimeException("Empty or unsuccessful response from Gemini API");
    }

    private Map<String, Object> buildRequestBody(String prompt) {
        Map<String, Object> part = new HashMap<>();
        part.put("text", prompt);
        
        Map<String, Object> content = new HashMap<>();
        content.put("parts", List.of(part));
        
        Map<String, Object> generationConfig = new HashMap<>();
        generationConfig.put("temperature", 0.7);
        generationConfig.put("maxOutputTokens", 1024);
        generationConfig.put("topP", 0.9);
        
        Map<String, Object> body = new HashMap<>();
        body.put("contents", List.of(content));
        body.put("generationConfig", generationConfig);
        
        return body;
    }

    private String extractTextFromResponse(String jsonResponse) throws Exception {
        Map<?, ?> responseMap = objectMapper.readValue(jsonResponse, Map.class);
        List<?> candidates = (List<?>) responseMap.get("candidates");
        if (candidates == null || candidates.isEmpty()) {
            log.error("No candidates found in Gemini response: {}", jsonResponse);
            throw new RuntimeException("No candidates in Gemini response");
        }
        
        Map<?, ?> firstCandidate = (Map<?, ?>) candidates.get(0);
        Map<?, ?> contentMap = (Map<?, ?>) firstCandidate.get("content");
        List<?> parts = (List<?>) contentMap.get("parts");
        
        if (parts == null || parts.isEmpty()) {
             log.error("No parts found in Gemini response content: {}", jsonResponse);
             throw new RuntimeException("No parts in Gemini response content");
        }
        
        Map<?, ?> firstPart = (Map<?, ?>) parts.get(0);
        return (String) firstPart.get("text");
    }
}
