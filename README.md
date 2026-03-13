# HelioCore Server

Ktor-based solar production service with JWT-secured APIs, Postgres-backed users, PVWatts potential, and Open-Meteo forecasts.

## Overview

HelioCore exposes REST endpoints to:

- Register/login users and issue JWTs.
- Validate and compute solar system capacity and production.
- Retrieve PVWatts-based potential for a location.
- Forecast daily solar production (with peak irradiance time and sun window) using Open-Meteo.
- Provide a simple health check.

## Features

- JWT auth with register/login and `/auth/me`.
- Solar energy estimation with explicit units per field.
- PVWatts integration for irradiance/potential data.
- Open-Meteo integration for 7-day production forecasts and sun-window times.
- Input validation for coordinates, tilt, azimuth, and panel specs.
- Global rate limiting (100 req / 60s).
- JSON APIs with kotlinx.serialization.

## Architecture

- **Ktor server** (`Application.kt`): JSON, logging, CORS, status pages, rate limit, JWT auth, HttpClient timeouts.
- **Config** (`config/`): env-backed `AppConfig`, `DbConfig`, `JwtConfig`, `RedisConfig`.
- **Domain models** (`domain/`): requests/responses, measured values, errors, auth DTOs.
- **Services**: `SolarProductionCalculator`; `AuthService` for register/login + JWT issuance.
- **Integrations**
  - `integrations/pvwatts/PvWattsClient`: PVWatts API client (`SolarDataProvider`).
  - `integrations/forecast/OpenMeteoForecastClient`: Open-Meteo forecast client (`SolarForecastProvider`).
  - `integrations/common/Providers`: shared provider interfaces and optional Redis caching.
- **Persistence**: Postgres via Exposed + Hikari (`auth` tables/repository).
- **Routes** (`routes/`): `/auth`, `/health`, `/solar/*` (estimate, potential, forecast) behind rate limit; `/solar` requires JWT.
- **Tests**: route tests, calculator tests, forecast client tests (including sun-window handling).

## Project Structure

```
src/main/kotlin/com/devtamuno/heliocore/
  Application.kt
  config/
    AppConfig.kt
    DbConfig.kt
    JwtConfig.kt
    RedisConfig.kt
  domain/
    AuthModels.kt
    SolarEstimateRequest.kt
    SolarPotentialRequest.kt
    SolarEstimateResponse.kt
    SolarPotentialResponse.kt
    SolarForecastModels.kt
    MeasuredValue.kt
    Errors.kt
  services/SolarProductionCalculator.kt
  auth/
    AuthService.kt
    UserRepository.kt
    model/UserRecord.kt
    tables/Users.kt
  integrations/
    common/Providers.kt
    pvwatts/PvWattsClient.kt
    forecast/OpenMeteoForecastClient.kt
  routes/
    Routing.kt
    HealthRoutes.kt
    SolarRoutes.kt
    AuthRoutes.kt
src/main/resources/application.conf
requests.http
```

## Installation

```bash
./gradlew clean build
```

## Environment Variables

```
PVWATTS_API_KEY=your-nrel-key
SERVER_PORT=8080

# Redis (optional cache)
REDIS_URL=redis://localhost:6379
REDIS_USERNAME=
REDIS_PASSWORD=aGVsaW8tcmVkaXMtZGV2

# Postgres
DB_URL=jdbc:postgresql://localhost:5432/heliocore
DB_USER=heliocore
DB_PASSWORD=aGVsaW8tcmVkaXMtZGV2

# JWT
JWT_SECRET=your-secret
JWT_ISSUER=helio-core
JWT_AUDIENCE=helio-core-users
JWT_REALM=helio-core
JWT_EXPIRY_MINUTES=60
```

## Running the Server

Start infra (Redis optional, Postgres + pgAdmin included):
```bash
docker-compose up -d
```

Run the service:
```bash
./gradlew run   # binds 0.0.0.0:${SERVER_PORT:-8080}
```

## API Endpoints

All bodies and responses are JSON with explicit units.

### Auth

- `POST /auth/register` → `{ token, expires_in, user }`
- `POST /auth/login` → `{ token, expires_in, user }`
- `GET /auth/me` (JWT) → `{ user_id, email }`

### Health

- `GET /health` → `{ "status": "ok" }`

### Solar (JWT required)

- `POST /solar/estimate`
- `POST /solar/potential`
- `POST /solar/forecast`

See `requests.http` for ready-to-use bodies and Authorization headers.

## Development

- Build: `./gradlew build`
- Tests: `./gradlew test`

## Future Improvements

- Add configurable forecast horizon and loss factors.
- Cache PVWatts/Open-Meteo responses to reduce external calls.
- Add authentication and per-key rate limits.
- Provide hourly forecast/production breakdowns.
- Surface validation errors with field-level details.
