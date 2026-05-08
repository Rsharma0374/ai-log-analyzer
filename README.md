# AI Log Analyzer

## Overview
AI Log Analyzer is a Spring Boot application designed to automatically parse, analyze, and provide insights for application logs. It integrates with multiple AI providers (DeepSeek, Gemini, Merlin) to deliver Root Cause Analysis (RCA), recommended fixes, and code solutions for errors found in logs.

## Features
- **Log Parsing**: Automatically extracts severity, error types, and messages from raw application logs.
- **Multi-AI Integration**: Supports DeepSeek, Gemini, and Merlin for log analysis with a fallback mechanism.
- **Caching**: Uses Redis for caching analysis results to reduce API calls and improve performance.
- **Secure Configuration**: Integrates with Infisical for secure secret management.
- **Batch Analysis**: Process multiple logs efficiently in a single batch operation.

## Architecture & Tech Stack
- **Framework**: Spring Boot (Java 17+)
- **Build Tool**: Maven
- **Database**: PostgreSQL / MongoDB (Depending on JPA configuration)
- **Cache**: Redis
- **AI Providers**: DeepSeek, Gemini, Merlin AI
- **Secret Management**: Infisical

## Setup and Configuration

### Prerequisites
- Java 17 or higher
- Redis instance running
- Infisical configured with necessary secrets

### Required Secrets in Infisical
Ensure the following secrets are configured in Infisical:
- **DeepSeekSecret**: `apiUrl`, `apiKey`, `model`, `temperature`, `maxToken`
- **GeminiSecret**: `apiKey`
- **MerlinSecret**: `merlinApiUrl`, `merlinApikey`

### Installation

1. Clone the repository:
   ```bash
   git clone <repository-url>
   cd ai-log-analyzer
   ```

2. Build the project:
   ```bash
   ./mvnw clean install
   ```

3. Run the application:
   ```bash
   ./mvnw spring-boot:run
   ```

## Key Components

### 1. `LogAnalysisService`
Handles the orchestration of parsing logs, triggering AI analysis, caching results, and saving to the database. Supports both single log analysis and batch processing.

### 2. `AiAnalysisService`
Constructs the prompts and routes the analysis request to the configured AI provider based on `AiProviderResolverService`.

### 3. AI Providers
- `DeepseekService`: Connects to DeepSeek API.
- `GeminiService`: Connects to Google's Gemini API.
- `MerlinAICodeAnalyzer`: Connects to Merlin API using Server-Sent Events (SSE).

### 4. `InfisicalService`
Fetches and caches API keys and configurations securely.

## Logging
The application uses SLF4J with Logback for logging. Important events like cache hits/misses, AI provider selection, and analysis success/failures are logged appropriately at `INFO`, `DEBUG`, or `WARN`/`ERROR` levels.

## Contributing
Contributions are welcome. Please ensure that new code includes appropriate logging and follows the existing code style.

## License
[Your License Here]