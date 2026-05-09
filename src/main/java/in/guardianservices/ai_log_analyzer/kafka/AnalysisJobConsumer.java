package in.guardianservices.ai_log_analyzer.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.guardianservices.ai_log_analyzer.model.AnalysisJob;
import in.guardianservices.ai_log_analyzer.model.AnalysisResult;
import in.guardianservices.ai_log_analyzer.model.LogEntry;
import in.guardianservices.ai_log_analyzer.repository.AnalysisJobRepository;
import in.guardianservices.ai_log_analyzer.repository.LogEntryRepository;
import in.guardianservices.ai_log_analyzer.service.AiAnalysisService;
import in.guardianservices.ai_log_analyzer.service.SseEmitterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class AnalysisJobConsumer {

    private final ObjectMapper objectMapper;
    private final AnalysisJobRepository analysisJobRepository;
    private final LogEntryRepository logEntryRepository;
    private final AiAnalysisService aiAnalysisService;
    private final SseEmitterService sseEmitterService;

    @KafkaListener(
            topics = "analysis-jobs",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeAnalysisJob(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        String payload = record.value();
        AnalysisJob job = null;
        
        try {
            job = objectMapper.readValue(payload, AnalysisJob.class);
            log.info("[{}] Received analysis job for logEntryId: {}", job.getCorrelationId(), job.getLogEntryId());
            
            // Mark job as in progress
            job.setStatus(AnalysisJob.Status.IN_PROGRESS);
            analysisJobRepository.save(job);
            
            // Fetch the LogEntry
            Optional<LogEntry> entryOpt = logEntryRepository.findById(job.getLogEntryId());
            if (entryOpt.isEmpty()) {
                throw new RuntimeException("LogEntry not found for ID: " + job.getLogEntryId());
            }
            
            LogEntry logEntry = entryOpt.get();
            
            // Perform analysis (This is the heavy lifting)
            AnalysisResult result = aiAnalysisService.analyzeLog(logEntry);
            
            // Mark job as completed
            job.setStatus(AnalysisJob.Status.COMPLETED);
            analysisJobRepository.save(job);
            
            // Emit SSE Event to frontend
            sseEmitterService.sendAnalysisCompleteEvent(job.getLogEntryId().toString(), result);
            
            log.info("[{}] Successfully completed analysis job", job.getCorrelationId());
            
        } catch (Exception e) {
            log.error("Failed to process analysis job", e);
            if (job != null) {
                job.setStatus(AnalysisJob.Status.FAILED);
                job.setFailReason(e.getMessage());
                analysisJobRepository.save(job);
            }
        } finally {
            if (acknowledgment != null) {
                acknowledgment.acknowledge();
            }
        }
    }
}
