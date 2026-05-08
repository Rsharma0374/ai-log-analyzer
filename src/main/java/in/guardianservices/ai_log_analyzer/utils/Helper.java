package in.guardianservices.ai_log_analyzer.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

@Slf4j
public class Helper {

    private static final ObjectMapper objectMapper = new ObjectMapper();


    public static Properties fetchProperties(String passManagerPropertiesPath) {
        Properties properties = new Properties();
        try {
            properties.load(new FileInputStream(passManagerPropertiesPath));
            return properties;
        } catch (IOException e) {
            log.error("Exception occurred while getting pass manager config with probable cause - ", e);
            return null;
        }
    }

    public static <T> T stringToObject(String json, Class<T> clazz) {
        try {
            objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            return objectMapper.readValue(json, clazz);
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert JSON to " + clazz.getSimpleName(), e);
        }
    }

    public static String createMerlinRequest(String prompt) throws JsonProcessingException {
        Map<String, Object> request = new HashMap<>();

        request.put("attachments", new ArrayList<>());
        request.put("chatId", "e2544616-d7e0-43f2-9f1f-1d103206c3b5");
        request.put("language", "AUTO");

        Map<String, Object> message = new HashMap<>();
        message.put("childId", "936ded20-311a-49fa-bd79-17d3978fa9e2");
        message.put("content", prompt);
        message.put("context", "");
        message.put("id", "efc4515e-952f-411f-b672-66deff2ba903");
        message.put("parentId", "c1eb6155-d43e-4155-9539-4f8820cbd43d");

        request.put("message", message);
        request.put("mode", "UNIFIED_CHAT");
        request.put("model", "claude-4.6-sonnet");

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("noTask", true);
        metadata.put("isWebpageChat", false);
        metadata.put("deepResearch", false);
        metadata.put("webAccess", true);
        metadata.put("proFinderMode", false);

        Map<String, Object> mcpConfig = new HashMap<>();
        mcpConfig.put("isEnabled", false);

        metadata.put("mcpConfig", mcpConfig);
        metadata.put("merlinMagic", false);

        request.put("metadata", metadata);

// Convert to JSON string
        return objectMapper.writeValueAsString(request);
    }

    public static JsonNode parseToNode(String responseBody) {
        try {

            return objectMapper.readTree(responseBody);
        } catch (Exception e) {
            log.error("Exception occurred while parsing to json node with probable cause - ", e);
            return null;
        }
    }

    public static String parseToString(Object object) {
        try {

            return objectMapper.writeValueAsString(object);

        } catch (Exception e) {
            log.error("Exception occurred while parsing to string with probable cause - ", e);
            return null;
        }
    }

}
