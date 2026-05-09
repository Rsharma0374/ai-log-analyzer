package in.guardianservices.ai_log_analyzer.service;

import in.guardianservices.ai_log_analyzer.dto.AnalysisResultDTO;
import in.guardianservices.ai_log_analyzer.model.AnalysisResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Service
public class SseEmitterService {

    private final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private final Map<String, SseEmitter> clientEmitters = new ConcurrentHashMap<>();

    public SseEmitter createEmitter(String clientId) {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        
        emitter.onCompletion(() -> {
            emitters.remove(emitter);
            if (clientId != null) clientEmitters.remove(clientId);
            log.debug("SSE Emitter completed for client: {}", clientId);
        });
        
        emitter.onTimeout(() -> {
            emitters.remove(emitter);
            if (clientId != null) clientEmitters.remove(clientId);
            log.debug("SSE Emitter timed out for client: {}", clientId);
        });
        
        emitter.onError(e -> {
            emitters.remove(emitter);
            if (clientId != null) clientEmitters.remove(clientId);
            log.error("SSE Emitter error for client: {}", clientId, e);
        });

        emitters.add(emitter);
        if (clientId != null) {
            clientEmitters.put(clientId, emitter);
        }
        
        try {
            // Send initial connection event
            emitter.send(SseEmitter.event().name("INIT").data("Connected successfully"));
        } catch (IOException e) {
            log.warn("Could not send initial SSE event to client {}", clientId);
        }
        
        return emitter;
    }

    public void sendAnalysisCompleteEvent(String logEntryId, AnalysisResult result) {
        log.info("Broadcasting AnalysisComplete event for log: {}", logEntryId);
        
        AnalysisResultDTO dto = AnalysisResultDTO.builder()
                .id(result.getId())
                .logEntryId(result.getLogEntry().getId())
                .rootCauseAnalysis(result.getRootCauseAnalysis())
                .recommendedFix(result.getRecommendedFix())
                .codeSnippet(result.getCodeSnippet())
                .confidenceScore(result.getConfidenceScore())
                .aiModel(result.getAiModel())
                .analyzedAt(result.getAnalyzedAt())
                .analysisTimeMs(result.getAnalysisTimeMs())
                .build();
                
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("ANALYSIS_COMPLETE")
                        .id(logEntryId)
                        .data(dto));
            } catch (IOException e) {
                log.debug("Failed to send event to emitter, removing it", e);
                emitters.remove(emitter);
            }
        }
    }
}
