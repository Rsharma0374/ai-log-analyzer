package in.guardianservices.ai_log_analyzer.repository;

import in.guardianservices.ai_log_analyzer.model.AnalysisResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface AnalysisResultRepository extends JpaRepository<AnalysisResult, UUID> {

    @Query("""
        SELECT ar FROM AnalysisResult ar
        WHERE ar.logEntry.errorType = ?1 
        AND ar.logEntry.source = ?2
        ORDER BY ar.analysisTimeMs ASC
        LIMIT ?3
    """)
    List<AnalysisResult> findSimilarAnalyses(String errorType, String source, int limit);

    @Query("SELECT ar FROM AnalysisResult ar WHERE ar.confidenceScore >= ?1 ORDER BY ar.analyzedAt DESC")
    List<AnalysisResult> findHighConfidenceAnalyses(Integer minConfidence);

    @Query("SELECT ar FROM AnalysisResult ar WHERE ar.logEntry.id = ?1")
    List<AnalysisResult> findByLogEntryId(UUID logEntryId);
}
