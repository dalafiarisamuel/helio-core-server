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

- JWT auth and refresh tokens (`/auth/register`, `/auth/login`, `/auth/refresh`, `/auth/me`)
- Solar production estimation with explicit units
- PVWatts potential + AC energy
- Open‑Meteo 7‑day forecast with peak irradiance window
- Global rate limiting (100 req / 60s)
- Default Redis caching for PVWatts and Open-Meteo (per-user)

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

### Health
- `GET /health` — Check service status.
  - **Response:** `{ "status": "ok" }`

### Auth
- `POST /auth/register` — Create a new user.
  - **Request:** `{"email":"user@example.com","password":"Passw0rd!"}`
  - **Response:** `{ "token": "...", "refresh_token": "...", "expires_in": 3600, "user": { "email": "user@example.com" } }`
- `POST /auth/login` — Authenticate and receive JWT.
  - **Request:** `{"email":"user@example.com","password":"Passw0rd!"}`
  - **Response:** `{ "token": "...", "refresh_token": "...", "expires_in": 3600, "user": { "email": "user@example.com" } }`
- `POST /auth/refresh` — Refresh expired JWT.
  - **Request:** `{"refresh_token": "..."}`
  - **Response:** Same as login.
- `GET /auth/me` — Get current user details (JWT required).
  - **Response:** `{ "email": "user@example.com" }`

### Solar (JWT Protected)
- `POST /solar/estimate` — General production estimate.
  - **Request:** `{"latitude":6.5244,"longitude":3.3792,"panel_wattage":450,"panel_count":12}`
  - **Response:**
    ```json
    {
      "system_capacity": { "value": 5.4, "unit": "kW" },
      "peak_sun_hours": { "value": 5.2, "unit": "hours" },
      "daily_energy": { "value": 24.4, "unit": "kWh" },
      "monthly_energy": { "value": 732.0, "unit": "kWh" },
      "annual_energy": { "value": 8906.0, "unit": "kWh" }
    }
    ```
- `POST /solar/potential` — Detailed PVWatts potential.
  - **Request:** `{"latitude":6.5244,"longitude":3.3792,"panel_wattage":1000,"panel_count":1}`
  - **Response:**
    ```json
    {
      "solrad_annual": { "value": 5.63, "unit": "kWh/m2/day" },
      "ac_monthly": {
        "january": { "data": { "value": 130.5, "unit": "kWh" } },
        "february": { "data": { "value": 125.2, "unit": "kWh" } },
        "...": "..."
      },
      "ac_annual": { "value": 1540.2, "unit": "kWh" },
      "panel_wattage": 1000.0,
      "panel_count": 1
    }
    ```
- `POST /solar/forecast` — 7-day weather-driven forecast.
  - **Request:** `{"latitude":6.5244,"longitude":3.3792,"panel_wattage":450,"panel_count":12}`
  - **Response:**
    ```json
    {
      "forecasts": [
        {
          "date": "2026-03-14",
          "peak_sun_hours": { "value": 5.1, "unit": "hours" },
          "expected_energy": { "value": 27.5, "unit": "kWh" },
          "peak_irradiance_time": "2026-03-14T12:00:00",
          "peak_irradiance": { "value": 950.0, "unit": "W/m2" },
          "sun_window_start": "07:00",
          "sun_window_end": "18:30",
          "weather_condition": "sunshine"
        }
      ],
      "panel_wattage": 450.0,
      "panel_count": 12
    }
    ```

## Curl Examples

### Health
```bash
curl http://localhost:8080/health
```

### Authentication
**Register:**
```bash
curl -X POST http://localhost:8080/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"user@example.com","password":"Passw0rd!"}'
```

**Login:**
```bash
TOKEN=$(curl -s -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"user@example.com","password":"Passw0rd!"}' | jq -r .token)
```

**Refresh Token:**
```bash
curl -X POST http://localhost:8080/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{"refresh_token":"$REFRESH_TOKEN"}'
```

### Solar Operations (JWT required)

**Estimate:**
```bash
curl -X POST http://localhost:8080/solar/estimate \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"latitude":6.5244,"longitude":3.3792,"panel_wattage":450,"panel_count":12}'
```

**Potential:**
```bash
curl -X POST http://localhost:8080/solar/potential \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"latitude":6.5244,"longitude":3.3792,"panel_wattage":1000,"panel_count":1,"panel_tilt":20,"azimuth":180}'
```

**Forecast:**
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

### Development Data Seeding
To quickly set up a test user with solar configurations in a development environment:
1. Ensure the server is running (it defaults to development mode when using `./gradlew run` without production flags).
2. Run the seeding script:
   ```bash
   ./seed-dev-data.sh
   ```
3. This will create:
   - **User:** `dev@example.com`
   - **Password:** `password123`
   - **Configs:** Two sample solar configurations (Los Angeles and New York).

## Future Improvements
- Configurable forecast horizon and loss factors.
- Hourly forecast/production breakdowns.
