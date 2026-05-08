package in.guardianservices.ai_log_analyzer.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.infisical.sdk.InfisicalSdk;
import com.infisical.sdk.config.SdkConfig;
import in.guardianservices.ai_log_analyzer.utils.Helper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Properties;


public class InfisicalConfig {

    private static final String INFISICAL_PATH = "/opt/configs/infisical.properties";

    private static final Logger logger = LoggerFactory.getLogger(InfisicalConfig.class);
    public static final String GUARDIAN_SERVICES = "guardian-services";

    /**
     * Fetches configuration values from Infisical for a given secret name.
     *
     * @param secretName The name of the secret to retrieve.
     * @return A map containing the configuration values, or null if an error occurs.
     * @throws Exception if an error occurs during property fetching or Infisical SDK initialization.
     */
    public static Map fetchConfig(String secretName) throws Exception {
        logger.info("Attempting to fetch config for secret: {}", secretName);
        Properties properties = Helper.fetchProperties(INFISICAL_PATH);

        try {
            if (null != properties) {
                String infisicalUrl = properties.getProperty("url");
                String infisicalToken = properties.getProperty("token");
                String env = properties.getProperty("env");

                logger.debug("Infisical URL: {}, Environment: {}", infisicalUrl, env);

                var sdk = new InfisicalSdk(
                        new SdkConfig.Builder()
                                .withSiteUrl(infisicalUrl)
                                .build()
                );

                sdk.Auth().SetAccessToken(infisicalToken);

                var secret = sdk.Secrets().GetSecret(
                        secretName,
                        GUARDIAN_SERVICES,
                        env,
                        "/",
                        null, // Expand Secret References (boolean, optional)
                        null, // Include Imports (boolean, optional)
                        null  // Secret Type (shared/personal, defaults to shared, optional)
                );

                if (secret != null) {
                    logger.debug("Successfully fetched secret: {}", secretName);
                    ObjectMapper objectMapper = new ObjectMapper();
                    return objectMapper.readValue(secret.getSecretValue(), Map.class);
                } else {
                    logger.warn("Secret '{}' not found or returned null", secretName);
                }

            } else {
                logger.error("Failed to load properties from path: {}", INFISICAL_PATH);
            }
        } catch (Exception e) {
            logger.error("Exception occurred while fetching config for secret: {}", secretName, e);
            return null;
        }

        return null;
    }
}
