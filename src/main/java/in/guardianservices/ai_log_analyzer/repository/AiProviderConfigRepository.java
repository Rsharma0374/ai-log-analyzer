package in.guardianservices.ai_log_analyzer.repository;

import in.guardianservices.ai_log_analyzer.model.AiProviderConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AiProviderConfigRepository extends JpaRepository<AiProviderConfig, String> {
    Optional<AiProviderConfig> findByConfigKey(String configKey);
}
