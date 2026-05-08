package in.guardianservices.ai_log_analyzer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AiLogAnalyzerApplication {
    private static final Logger log = LoggerFactory.getLogger(AiLogAnalyzerApplication.class);

    public static void main(String[] args) {
        try {
            SpringApplication.run(AiLogAnalyzerApplication.class, args);
            log.info("========================================");
            log.info("AI Log Analyzer Service Started Successfully");
            log.info("Consumer Topic: logging.analytics.raw-errors.v1");
            log.info("========================================");
        } catch (Exception exception) {
            log.error("Failed to start AI Log Analyzer Service", exception);
            System.exit(1);
        }
    }

}
