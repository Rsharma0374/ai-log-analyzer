package in.guardianservices.ai_log_analyzer.service;

import in.guardianservices.ai_log_analyzer.utils.Helper;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Service for integrating Merlin AI API for code analysis
 * Requires: HttpClient (Java 11+), Jackson for JSON processing
 */
@Service
public class MerlinAICodeAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(MerlinAICodeAnalyzer.class);

    private final InfisicalService infisicalService;

    @Autowired
    public MerlinAICodeAnalyzer(InfisicalService infisicalService) {
        this.infisicalService = infisicalService;
    }

    /**
     * Analyze code using Merlin AI
     * @param prompt The prompt containing the log to analyze
     * @return Analysis result as String
     */
    public String analyzeCode(String prompt) {
        try {
            log.info("Initiating Merlin AI code analysis");
            return callMerlinChatApi(prompt);
        } catch (Exception e) {
            log.error("Merlin AI Code analysis failed", e);
            throw new RuntimeException("Failed to analyze code with Merlin AI", e);
        }
    }

    private String callMerlinChatApi(String prompt) {
        try {
            String apiUrl = infisicalService.getSecret("merlinApiUrl", "MerlinSecret");
            String apiKey = infisicalService.getSecret("merlinApikey", "MerlinSecret");

            if (apiUrl == null || apiKey == null) {
                log.error("Merlin API configuration is missing");
                throw new RuntimeException("Merlin API URL or API Key is missing");
            }

            URL url = new URL(apiUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            String token = "Bearer " + apiKey;

            conn.setRequestMethod("POST");
            conn.setDoOutput(true);

            conn.setRequestProperty("Accept", "text/event-stream");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", token);

            String requestBody = Helper.createMerlinRequest(prompt);
            conn.getOutputStream().write(requestBody.getBytes());

            log.debug("Sending streaming request to Merlin API");
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream())
            );

            StringBuilder finalText = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null) {
                // Only process SSE data lines
                if (!line.startsWith("data:")) continue;

                String jsonPart = line.substring(5).trim();

                // End of stream
                if (jsonPart.equals("[DONE]")) break;

                try {
                    JSONObject root = new JSONObject(jsonPart);

                    if (root.has("data")) {
                        JSONObject data = root.getJSONObject("data");

                        if (data.has("text")) {
                            finalText.append(data.getString("text"));
                        }
                    }

                } catch (Exception e) {
                    // Ignore malformed chunks (very common in streams)
                    log.trace("Ignored malformed SSE chunk: {}", jsonPart);
                }
            }

            reader.close();
            
            log.info("Merlin API streaming request completed successfully");
            log.debug("Final Combined Text from Merlin API: \n{}", finalText.toString());
            return finalText.toString();
        } catch (Exception e) {
            log.error("Error occurred while getting response from Merlin API", e);
            throw new RuntimeException("Failed to get response from Merlin API", e);
        }
    }
}
