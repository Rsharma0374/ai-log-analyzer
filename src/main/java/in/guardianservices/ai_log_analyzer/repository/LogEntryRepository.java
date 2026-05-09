package in.guardianservices.ai_log_analyzer.repository;

import in.guardianservices.ai_log_analyzer.model.LogEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface LogEntryRepository extends JpaRepository<LogEntry, UUID> {

    List<LogEntry> findBySource(String source);

    List<LogEntry> findBySeverity(String severity);

    List<LogEntry> findByErrorType(String errorType);

    @Query("SELECT l FROM LogEntry l WHERE l.createdAt BETWEEN ?1 AND ?2 ORDER BY l.createdAt DESC")
    List<LogEntry> findByDateRange(LocalDateTime start, LocalDateTime end);

    @Query("SELECT l FROM LogEntry l WHERE l.status = 'PENDING' ORDER BY l.createdAt ASC LIMIT ?1")
    List<LogEntry> findPendingLogs(int limit);
}
