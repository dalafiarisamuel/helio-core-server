# HelioCore Server

Ktor service that authenticates users with JWT, stores them in Postgres, and delivers solar production estimates, PVWatts potential, and Open‑Meteo forecasts.

## Overview

HelioCore exposes a small REST API to:
- Register/login users and issue JWTs.
- Validate solar system specs and compute production.
- Fetch PVWatts potential for a location.
- Forecast 7‑day solar energy using irradiance-driven weather data.
- Report service health.

## Features

- JWT auth (`/auth/register`, `/auth/login`, `/auth/me`)
- Solar production estimation with explicit units
- PVWatts potential + AC energy
- Open‑Meteo 7‑day forecast with peak irradiance window
- Global rate limiting (100 req / 60s)
- Optional Redis caching in front of integrations

## Architecture

- **Ktor server**: JSON, logging, CORS, status pages, rate limit, JWT auth, DI wiring.
- **Domain/Services**: typed DTOs and `SolarProductionCalculator`.
- **Auth**: `AuthService` + Exposed `UserRepository` on Postgres (Hikari pool).
- **Integrations**: PVWatts client, Open‑Meteo client, optional Redis cache decorators.
- **Routes**: `/auth`, `/health`, `/solar/*` (JWT-protected).

## Project Structure
```
helio-core
├─ src/main/kotlin/com/devtamuno/heliocore/
│  ├─ Application.kt
│  ├─ config/ (AppConfig, DbConfig, JwtConfig, RedisConfig)
│  ├─ domain/ (AuthModels, Solar DTOs, Errors, MeasuredValue)
│  ├─ services/ SolarProductionCalculator.kt
│  ├─ auth/ (AuthService, UserRepository, model, tables)
│  ├─ integrations/
│  │   ├─ pvwatts/ PvWattsClient.kt
│  │   ├─ forecast/ OpenMeteoForecastClient.kt
│  │   └─ common/ Providers, RedisCache
│  └─ routes/ (Routing, AuthRoutes, HealthRoutes, SolarRoutes)
├─ src/main/resources/ (application.conf, application-prod.conf)
├─ docker-compose.yml
└─ requests.http
```

## Installation
```bash
./gradlew clean build
```

## Environment Variables (.env)
```env
PVWATTS_API_KEY=your-nrel-key
SERVER_PORT=8080

# Redis (optional)
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
Start infra (Postgres + pgAdmin + optional Redis):
```bash
docker-compose up -d
```
Run the app:
```bash
./gradlew run   # binds 0.0.0.0:${SERVER_PORT:-8080}
```

## API Endpoints

Auth:
- `POST /auth/register` — create user, returns `{ token, expires_in, user }`
- `POST /auth/login` — returns `{ token, expires_in, user }`
- `GET /auth/me` — JWT required, returns `{ user_id, email }`

Health:
- `GET /health` — `{ "status": "ok" }`

Solar (JWT required):
- `POST /solar/estimate` — production estimate
- `POST /solar/potential` — PVWatts potential and AC energy
- `POST /solar/forecast` — 7‑day forecast with peak irradiance window

## Curl Examples

Register:
```bash
curl -X POST http://localhost:8080/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"user@example.com","password":"Passw0rd!"}'
```

Login (capture token):
```bash
TOKEN=$(curl -s -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"user@example.com","password":"Passw0rd!"}' | jq -r .token)
```

Health:
```bash
curl http://localhost:8080/health
```

Estimate:
```bash
curl -X POST http://localhost:8080/solar/estimate \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"latitude":6.5244,"longitude":3.3792,"panel_wattage":450,"panel_count":12,"panel_tilt":20,"azimuth":180}'
```

Potential:
```bash
curl -X POST http://localhost:8080/solar/potential \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"latitude":6.5244,"longitude":3.3792,"panel_wattage":1000,"panel_count":1,"panel_tilt":20,"azimuth":180}'
```

Forecast:
```bash
curl -X POST http://localhost:8080/solar/forecast \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"latitude":6.5244,"longitude":3.3792,"panel_wattage":450,"panel_count":12}'
```

## Example Request / Response

`POST /solar/estimate`
```json
{
  "latitude": 6.5244,
  "longitude": 3.3792,
  "panel_wattage": 450,
  "panel_count": 12,
  "panel_tilt": 20,
  "azimuth": 180
}
```
```json
{
  "system_capacity": { "value": 5.4, "unit": "kW" },
  "peak_sun_hours": { "value": 5.2, "unit": "hours" },
  "daily_energy": { "value": 24.4, "unit": "kWh" },
  "monthly_energy": { "value": 732.0, "unit": "kWh" },
  "annual_energy": { "value": 8906.0, "unit": "kWh" }
}
```

## Development
- Build: `./gradlew build`
- Test: `./gradlew test`

## Future Improvements
- Configurable forecast horizon and loss factors.
- Default caching for PVWatts/Open‑Meteo responses.
- Per-user/API-key rate limits and refresh tokens.
- Hourly forecast/production breakdowns.
