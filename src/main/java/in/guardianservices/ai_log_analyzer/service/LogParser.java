package in.guardianservices.ai_log_analyzer.service;

import in.guardianservices.ai_log_analyzer.model.LogEntry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class LogParser {

    private static final Pattern EXCEPTION_PATTERN =
            Pattern.compile("(\\w+(?:Exception|Error))[:\\s]([^\\n]+)");
    private static final Pattern TIMESTAMP_PATTERN =
            Pattern.compile("(\\d{4}-\\d{2}-\\d{2}\\s\\d{2}:\\d{2}:\\d{2})");
    private static final Pattern SEVERITY_PATTERN =
            Pattern.compile("\\[(ERROR|WARN|INFO|DEBUG|CRITICAL)\\]");

    public LogEntry parseLog(String rawLog, String source, String severity, String context, String environment) {
        log.debug("Parsing log from source '{}', environment '{}'", source, environment);
        
        LogEntry entry = new LogEntry();
        entry.setRawLog(rawLog);
        entry.setSource(source);
        entry.setContext(context);
        entry.setCreatedAt(LocalDateTime.now());
        entry.setStatus(LogEntry.ProcessingStatus.PENDING);
        entry.setEnvironment(environment);

        // Extract severity if not provided
        if (severity == null || severity.trim().isEmpty()) {
            log.trace("Severity not provided, attempting to extract from log content");
            severity = extractSeverity(rawLog);
        }
        
        String resolvedSeverity = (severity != null && !severity.trim().isEmpty()) ? severity : "UNKNOWN";
        entry.setSeverity(resolvedSeverity);
        log.debug("Resolved log severity: {}", resolvedSeverity);

        // Extract message (first line)
        String extractedMessage = extractMessage(rawLog);
        entry.setMessage(extractedMessage);
        log.trace("Extracted log message: {}", extractedMessage);

        // Extract exception type and stack trace
        var exceptionMatch = extractException(rawLog);
        entry.setErrorType(exceptionMatch[0]);
        entry.setStackTrace(exceptionMatch[1]);
        log.debug("Extracted error type: '{}'", exceptionMatch[0]);

        log.info("Successfully parsed log from source: {}", source);
        return entry;
    }

    private String extractSeverity(String logText) {
        if (logText == null || logText.isEmpty()) return null;
        
        Matcher m = SEVERITY_PATTERN.matcher(logText);
        return m.find() ? m.group(1) : null;
    }

    private String extractMessage(String logText) {
        if (logText == null || logText.isEmpty()) return "";
        
        String[] lines = logText.split("\\n");
        return lines.length > 0 ? lines[0].substring(0, Math.min(200, lines[0].length())) : "";
    }

    private String[] extractException(String logText) {
        if (logText == null || logText.isEmpty()) {
             return new String[]{"Unknown", ""};
        }
        
        Matcher m = EXCEPTION_PATTERN.matcher(logText);
        if (m.find()) {
            return new String[]{m.group(1), logText.substring(m.start())};
        }
        return new String[]{"Unknown", ""};
    }
}
