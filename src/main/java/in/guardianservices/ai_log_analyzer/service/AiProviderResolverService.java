package in.guardianservices.ai_log_analyzer.service;

import in.guardianservices.ai_log_analyzer.config.CacheConfig;
import in.guardianservices.ai_log_analyzer.constants.AiProvider;
import in.guardianservices.ai_log_analyzer.model.AiProviderConfig;
import in.guardianservices.ai_log_analyzer.repository.AiProviderConfigRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@Slf4j
public class AiProviderResolverService {
    
    private final AiProviderConfigRepository aiConfigRepository;
    
    private static final String CONFIG_KEY = "active_ai_provider";
    private static final String CACHE_NAME = "aiProviderCache";
    private static final AiProvider DEFAULT_PROVIDER = AiProvider.DEEPSEEK;

    @Autowired
    public AiProviderResolverService(AiProviderConfigRepository aiConfigRepository) {
        this.aiConfigRepository = aiConfigRepository;
    }

    public AiProviderConfig getAiProvider() {
        log.debug("Resolving active AI Provider");
        
        if (CacheConfig.CACHE.containsKey(CONFIG_KEY)) {
            AiProviderConfig cachedConfig = (AiProviderConfig) CacheConfig.CACHE.get(CONFIG_KEY);
            log.debug("Found active AI Provider in cache: {}", cachedConfig.getConfigValue());
            return cachedConfig;
        } else {
            log.info("Active AI Provider not found in cache, fetching from repository with key: {}", CACHE_NAME);
            Optional<AiProviderConfig> config = aiConfigRepository.findByConfigKey(CACHE_NAME);
            
            if (config.isPresent()) {
                AiProviderConfig resolvedConfig = config.get();
                CacheConfig.CACHE.put(CONFIG_KEY, resolvedConfig); // Fix: use CONFIG_KEY to match cache check above
                log.info("Successfully fetched and cached active AI Provider: {}", resolvedConfig.getConfigValue());
                return resolvedConfig;
            } else {
                log.error("No active cache config is found in repository, returning default provider: {}", DEFAULT_PROVIDER);
                AiProviderConfig defaultConfig = new AiProviderConfig();
                defaultConfig.setConfigKey(CONFIG_KEY);
                defaultConfig.setConfigValue(DEFAULT_PROVIDER.name());
                defaultConfig.setModel("default");
                return defaultConfig;
            }
        }
    }
}
