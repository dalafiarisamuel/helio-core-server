# Project Overview
HelioCore is a Ktor-based Kotlin service that estimates and forecasts solar energy production. It validates system specs, calls the NREL PVWatts API for potential data, uses Open-Meteo for irradiance-driven forecasts, and returns JSON responses with explicit units.

# Architecture
Layered, service-first Ktor application. Routes are thin HTTP adapters; services handle validation and computation; integrations wrap external APIs; domain models carry typed request/response data with explicit units. Optional Redis decorators add transparent caching in front of integrations.

# Tech Stack
- Kotlin (JVM)
- Ktor server & CIO HttpClient
- kotlinx.serialization for JSON
- Gradle Kotlin DSL
- PVWatts API, Open-Meteo API via HTTP clients
- Redis (optional) for caching
- SLF4J + Logback for logging

# Project Structure
```
src/main/kotlin/com/devtamuno/heliocore/
  Application.kt          # Ktor setup: JSON, logging, CORS, status pages, rate limit, HttpClient, DI wiring
  config/AppConfig.kt     # Config loader (env-backed)
  domain/                 # Request/response DTOs, errors, measured values, forecast models
  services/               # Business logic (SolarProductionCalculator)
  integrations/           # External clients and caching
    common/Providers.kt   # Interfaces
    common/RedisCache.kt  # Cache decorators + Redis factory
    pvwatts/PvWattsClient.kt
    forecast/OpenMeteoForecastClient.kt
  routes/                 # Route registration and handlers
src/main/resources/       # application.conf, profiles, logback.xml
requests.http             # Sample HTTP requests
```

# Coding Guidelines
- Keep route handlers thin; delegate business logic to services.
- Validate inputs in services/calculators; throw `ValidationException` on bad data.
- Prefer small, focused services and domain models that include units (`MeasuredValue`).
- Preserve snake_case JSON via `@SerialName`; avoid breaking API field names.
- Use coroutines idiomatically; mark all I/O functions `suspend`.
- Log outbound calls and key results with SLF4J; avoid `println`.
- Follow existing timeout/logging config unless there is a clear need to change it.

# API Development Rules
- Define request/response models under `domain/`.
- Validate inputs before processing; throw `ValidationException`.
- Delegate computation or external calls to services/integrations.
- Return structured JSON with units using existing `MeasuredValue`.
- Register routes in `routes/Routing.kt`; keep endpoints under `/solar` unless justified.
- Respect current paths: `/health`, `/solar/estimate`, `/solar/potential`, `/solar/forecast`.

# Integration Guidelines
- Wrap external APIs in clients under `integrations/<provider>`.
- Handle non-200 responses and provider errors; throw `ExternalServiceException`.
- Convert provider payloads into domain models; do not leak raw schemas.
- For cacheable calls, add Redis decorators via `CachingSolarDataProvider` or `CachingSolarForecastProvider`.
- Keep cache keys stable; TTLs default to 1800s (data) and 3600s (forecast) unless requirements change.

# Error Handling
- Use Ktor `StatusPages` (see `Application.kt`) to map:
  - `ValidationException` → 400 with `ErrorResponse`
  - `ExternalServiceException` → 502 with `ErrorResponse`
  - Unhandled exceptions → 500 with generic message
- Prefer throwing the above exceptions from services/integrations.
- Leave Ktor features (CallLogging, CORS, RateLimit, ContentNegotiation) enabled unless explicitly requested otherwise.

# Configuration
- Primary config in `src/main/resources/application.conf`; `application-prod.conf` overrides.
- Environment variables (read via HOCON):
  - `PVWATTS_API_KEY` (required)
  - `SERVER_PORT` (optional, default 8080)
  - `REDIS_URL` (optional, enables caching)
  - `REDIS_USERNAME` (optional)
  - `REDIS_PASSWORD` (optional)
- HttpClient timeouts default to 30s (`httpClientTimeoutMillis`).
- When adding settings, update `AppConfig` and the relevant `application*.conf` entries together.

# Running the Project
- Local run: `./gradlew run`
- Build: `./gradlew build`
- Tests: `./gradlew test`
- Server binds to `0.0.0.0:${SERVER_PORT ?: 8080}`.
- Dependency management: Gradle Kotlin DSL with Kotlin 2.3, Ktor 3.4, JVM toolchain 21.

# Testing Strategy
- Write unit tests for services (e.g., `SolarProductionCalculator`).
- Use Ktor test utilities for route tests with in-memory engine.
- Mock external clients or use canned responses for integration tests.
- Include validation edge cases and error paths.
- Assert JSON field names/units to prevent API drift.

# Safe Modification Rules for AI Agents
- Avoid changing domain request/response contracts unless required; document any changes.
- Keep modules loosely coupled; depend on interfaces (`SolarDataProvider`, `SolarForecastProvider`).
- Do not remove validation or status-page mappings.
- Ensure Gradle build passes after changes.
- Preserve logging and caching behaviors when modifying integrations.
- Maintain measured units and snake_case serialization; do not rename fields silently.
- If enabling Redis, keep connection handling in `RedisFactory`; close resources on shutdown as `Application.kt` does.

# When Adding New Features
1. Add/extend domain models for requests and responses with units.
2. Implement service logic; keep computations outside routes.
3. Create integration clients if external calls are needed; wrap with caching if appropriate.
4. Expose routes in `routes/` and register in `Routing.kt`.
5. Update configuration/env docs and README.
6. Add tests for services, integrations (mocked), and routes.
