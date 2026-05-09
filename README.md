# AI Log Analyzer

## Overview
AI Log Analyzer is a Spring Boot application designed to automatically parse, analyze, and provide insights for application logs. It integrates with multiple AI providers (DeepSeek, Gemini, Merlin) to deliver Root Cause Analysis (RCA), recommended fixes, and code solutions for errors found in logs. 

The architecture is heavily decoupled using **Kafka** to handle high-throughput log ingestion without blocking, and **Server-Sent Events (SSE)** to push asynchronous analysis results to the frontend.

## Features
- **Asynchronous Log Processing**: Logs are ingested via Kafka, parsed, stored, and then passed to an asynchronous job queue (`analysis-jobs` topic) for AI processing to avoid blocking API and consumer threads.
- **Log Parsing**: Automatically extracts severity, error types, and messages from raw application logs.
- **Multi-AI Integration**: Supports DeepSeek, Gemini, and Merlin for log analysis with a fallback mechanism. Uses Spring WebClient for non-blocking HTTP requests with timeouts.
- **Caching**: Uses Redis (with specialized JSON serializers) for caching analysis results to reduce API calls and improve performance.
- **Real-Time Frontend Updates**: Features an `SseEmitterService` that pushes `ANALYSIS_COMPLETE` events directly to the frontend.
- **Secure Configuration**: Integrates with Infisical for secure secret management.
- **Idempotency**: Prevents duplicate analysis runs for the same log entry.

## Architecture & Tech Stack
- **Framework**: Spring Boot (Java 17+)
- **Build Tool**: Maven
- **Database**: PostgreSQL (JPA/Hibernate)
- **Messaging**: Apache Kafka
- **Cache**: Redis
- **AI Providers**: DeepSeek, Gemini, Merlin AI
- **Secret Management**: Infisical

## Project Structure
```text
ai-log-analyzer/
├── src/
│   ├── main/
│   │   ├── java/in/guardianservices/ai_log_analyzer/
│   │   │   ├── config/
│   │   │   │   ├── CacheConfig.java
│   │   │   │   ├── CorsConfig.java          # Configures cross-origin requests for the frontend
│   │   │   │   ├── InfisicalConfig.java
│   │   │   │   ├── KafkaConsumerConfig.java
│   │   │   │   ├── RedisConfig.java         # Sets up RedisTemplate with Jackson JavaTimeModule
│   │   │   │   └── WebClientConfig.java     # Configures non-blocking HTTP clients
│   │   │   ├── consumer/
│   │   │   │   └── ErrorLogConsumer.java    # Fast consumer for raw logs (validates, saves, enqueues)
│   │   │   ├── controller/
│   │   │   │   └── LogAnalysisController.java # REST API endpoints and SSE subscription
│   │   │   ├── dto/
│   │   │   │   ├── AnalysisResponse.java
│   │   │   │   ├── AnalysisResultDTO.java
│   │   │   │   └── LogAnalysisRequest.java
│   │   │   ├── kafka/
│   │   │   │   └── AnalysisJobConsumer.java # Worker that processes the async AI jobs
│   │   │   ├── model/
│   │   │   │   ├── AiProviderConfig.java
│   │   │   │   ├── AnalysisJob.java         # Tracks the state of async AI tasks
│   │   │   │   ├── AnalysisResult.java
│   │   │   │   └── LogEntry.java
│   │   │   ├── repository/
│   │   │   ├── service/
│   │   │   │   ├── AIResponseParser.java    # Parses and cleans JSON from AI providers
│   │   │   │   ├── AiAnalysisService.java   # Core AI orchestration (Idempotent)
│   │   │   │   ├── AiProviderResolverService.java
│   │   │   │   ├── DeepseekService.java     # WebClient integration for DeepSeek
│   │   │   │   ├── GeminiService.java
│   │   │   │   ├── InfisicalService.java
│   │   │   │   ├── LogAnalysisService.java  # Business logic for parsing and queueing
│   │   │   │   ├── LogParser.java
│   │   │   │   ├── MerlinAICodeAnalyzer.java
│   │   │   │   └── SseEmitterService.java   # Pushes real-time events to connected clients
│   │   │   └── utils/
```

## Setup and Configuration

### Prerequisites
- Java 17 or higher
- Redis instance running
- Apache Kafka instance running (Topics: `logging.analytics.raw-errors.v1` and `analysis-jobs`)
- PostgreSQL Database
- Infisical configured with necessary secrets

### Required Secrets in Infisical
Ensure the following secrets are configured in Infisical:
- **DeepSeekSecret**: `apiUrl`, `apiKey`, `model`, `temperature`, `maxToken`
- **GeminiSecret**: `apiKey`
- **MerlinSecret**: `merlinApiUrl`, `merlinApikey`
- **RedisSecret**: `host`, `port`, `password`

### Installation

1. Clone the repository:
   ```bash
   git clone <repository-url>
   cd ai-log-analyzer
   ```

2. Build the project:
   ```bash
   ./mvnw clean install -DskipTests
   ```

3. Run the application:
   ```bash
   ./mvnw spring-boot:run
   ```

## Key Components

### 1. `ErrorLogConsumer`
A fast Kafka consumer that listens to inbound error logs. It quickly validates the log, persists a `PENDING` `LogEntry` to PostgreSQL, enqueues an `AnalysisJob` to a secondary Kafka topic, and immediately acknowledges the message to prevent blocking.

### 2. `AnalysisJobConsumer`
A worker consumer that listens to the `analysis-jobs` topic. It executes the heavy AI analysis by calling `AiAnalysisService`, persists the results, and triggers the `SseEmitterService`.

### 3. `AiAnalysisService`
Handles caching, idempotency checks (to prevent duplicate key constraints), constructs the prompts, routes the request to the configured AI provider, and isolates database saves inside `REQUIRES_NEW` transactions.

### 4. `SseEmitterService`
Manages active `SseEmitter` connections for frontends. Broadcasts an `ANALYSIS_COMPLETE` event whenever the background job finishes analyzing a log.

## REST APIs
- `POST /api/v1/logs` - Ingest a log manually and enqueue it for background analysis.
- `GET /api/v1/logs/{id}/analysis` - Retrieve an analysis for a specific log entry ID.
- `GET /api/v1/logs/{id}/similar` - Find similar analyzed logs based on error types and sources.
- `GET /api/v1/logs/sse` - Subscribe to the Server-Sent Events stream for real-time updates.

## Logging
The application uses SLF4J with Logback. It provides comprehensive correlation IDs and debug traces for Kafka ingestion, AI API execution times, cache hits/misses, and failure handling.

## License
[Your License Here]