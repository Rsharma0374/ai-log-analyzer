package in.guardianservices.ai_log_analyzer.service;

import com.fasterxml.jackson.databind.JsonNode;
import in.guardianservices.ai_log_analyzer.utils.Helper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class DeepseekService {

    private static final Logger log = LoggerFactory.getLogger(DeepseekService.class);

    private final RestTemplate restTemplate;
    private final InfisicalService infisicalService;

    @Autowired
    public DeepseekService(RestTemplate restTemplate, InfisicalService infisicalService) {
        this.restTemplate = restTemplate;
        this.infisicalService = infisicalService;
    }

    public String callDeepSeekAPI(String prompt) {
        try {
            log.info("Initiating call to DeepSeek API");
            return invokeDeepSeekModel(prompt);
        } catch (Exception e) {
            log.error("DeepSeek API call failed", e);
            throw new RuntimeException("Failed to call DeepSeek API", e);
        }
    }

    private String invokeDeepSeekModel(String prompt) throws Exception {
        String apiUrl = infisicalService.getSecret("apiUrl", "DeepSeekSecret");
        String apiKey = infisicalService.getSecret("apiKey", "DeepSeekSecret");
        String model = infisicalService.getSecret("model", "DeepSeekSecret");
        
        String tempStr = infisicalService.getSecret("temperature", "DeepSeekSecret");
        double temperature = (tempStr != null) ? Double.parseDouble(tempStr) : 0.7;
        
        String maxTokenStr = infisicalService.getSecret("maxToken", "DeepSeekSecret");
        int maxToken = (maxTokenStr != null) ? Integer.parseInt(maxTokenStr) : 2048;

        if (apiKey == null || apiUrl == null) {
            throw new RuntimeException("DeepSeek API configuration is missing.");
        }

        Map<String, Object> requestBody = buildDeepSeekRequestBody(prompt, model, temperature, maxToken);
        String jsonBody = Helper.parseToString(requestBody);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        HttpEntity<String> entity = new HttpEntity<>(jsonBody, headers);

        log.debug("Sending request to DeepSeek API");
        ResponseEntity<String> response = restTemplate.exchange(
                apiUrl,
                HttpMethod.POST,
                entity,
                String.class
        );

        if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
            log.debug("Received successful response from DeepSeek API");
            return extractTextFromDeepSeekResponse(response.getBody());
        }
        
        log.error("Failed response from DeepSeek API: HTTP {}", response.getStatusCode());
        throw new RuntimeException("Empty or unsuccessful response from DeepSeek API");
    }

    private Map<String, Object> buildDeepSeekRequestBody(String prompt, String model, double temperature, int maxTokens) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model != null ? model : "deepseek-coder");

        List<Map<String, String>> messages = new ArrayList<>();
        Map<String, String> userMessage = new HashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", prompt);
        messages.add(userMessage);

        requestBody.put("messages", messages);
        requestBody.put("stream", false);
        requestBody.put("temperature", temperature);
        requestBody.put("max_tokens", maxTokens);

        return requestBody;
    }

    private String extractTextFromDeepSeekResponse(String responseBody) throws Exception {
        JsonNode root = Helper.parseToNode(responseBody);
        if (null == root) {
            log.error("Failed to parse DeepSeek response body to JSON");
            return null;
        }

        JsonNode choices = root.path("choices");
        if (choices.isArray() && !choices.isEmpty()) {
            JsonNode message = choices.get(0).path("message");
            String content = message.path("content").asText();
            if (content != null && !content.isEmpty()) {
                return content;
            }
        }

        log.error("Unable to extract text from DeepSeek response: {}", responseBody);
        throw new RuntimeException("Unable to extract text from DeepSeek response");
    }
}
