package in.guardianservices.ai_log_analyzer.service;

import in.guardianservices.ai_log_analyzer.config.CacheConfig;
import in.guardianservices.ai_log_analyzer.config.InfisicalConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Service class for interacting with Infisical to retrieve secrets.
 * Implements an in-memory caching mechanism to optimize repeated secret fetches.
 */
@Service
public class InfisicalService {

    private static final Logger logger = LoggerFactory.getLogger(InfisicalService.class);

    /**
     * Retrieves a secret by its name. Checks the local cache first,
     * and if not found, queries Infisical.
     *
     * @param secretName the name of the secret to retrieve.
     * @param secretType the type or namespace of the secret in Infisical.
     * @return the value of the secret, or null if it cannot be found.
     */
    public String getSecret(String secretName, String secretType) {
        logger.debug("Requesting secret: '{}' of type: '{}'", secretName, secretType);
        try {
            if (CacheConfig.CACHE.containsKey(secretName)) {
                logger.debug("Secret '{}' found in cache", secretName);
                return String.valueOf(CacheConfig.CACHE.get(secretName));
            }

            logger.info("Secret '{}' not in cache. Fetching from Infisical...", secretName);
            Map<String, Object> configMap = InfisicalConfig.fetchConfig(secretType);

            if (configMap == null || configMap.isEmpty()) {
                logger.error("ConfigMap is missing or empty for type: '{}'", secretType);
                throw new RuntimeException("ConfigMap is missing or empty.");
            }

            // ✅ Store ALL entries in cache
            int count = 0;
            for (Map.Entry<String, Object> entry : configMap.entrySet()) {
                String key = entry.getKey();
                String value = String.valueOf(entry.getValue());

                CacheConfig.CACHE.put(key, value);
                count++;
            }

            logger.info("Cache populated with {} entries from Infisical", count);

            // Return requested secret
            Object requestedSecret = CacheConfig.CACHE.get(secretName);
            if (requestedSecret == null) {
                logger.warn("Secret '{}' still not found after fetching from Infisical", secretName);
            }
            return requestedSecret != null ? String.valueOf(requestedSecret) : null;

        } catch (Exception e) {
            logger.error("Failed to fetch secret '{}' from Infisical", secretName, e);
        }
        return null;
    }
}
