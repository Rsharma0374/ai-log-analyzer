package in.guardianservices.ai_log_analyzer.repository;

import in.guardianservices.ai_log_analyzer.model.AnalysisResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface AnalysisResultRepository extends JpaRepository<AnalysisResult, String> {

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
}
