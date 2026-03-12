# HelioCore Server

Ktor-based service for estimating solar energy production, proxying PVWatts potential data, and providing Open-Meteo
forecasts with peak irradiance windows.

## Overview

HelioCore exposes REST endpoints to:

- Validate and compute solar system capacity and production.
- Retrieve PVWatts-based potential for a location.
- Forecast daily solar production (with peak irradiance time and sun window) using Open-Meteo.
- Provide a simple health check.

## Features

- Solar energy estimation with explicit units per field.
- PVWatts integration for irradiance/potential data.
- Open-Meteo integration for 7-day production forecasts and sun-window times.
- Input validation for coordinates, tilt, azimuth, and panel specs.
- Global rate limiting (100 req / 60s).
- JSON APIs with kotlinx.serialization.

## Architecture

- **Ktor server** (`Application.kt`): configures JSON, logging, CORS, status pages, rate limiting, HttpClient timeouts.
- **Config** (`AppConfig`): loaded from `application.conf`; fixed 30s HttpClient timeout.
- **Domain models** (`domain/`): split into requests/responses, measured values, forecast entries, errors.
- **Services** (`services/SolarProductionCalculator`): validation, capacity, and production calculations (constants in
  companion).
- **Integrations**
    - `integrations/pvwatts/PvWattsClient`: PVWatts API client, implements `SolarDataProvider`.
    - `integrations/forecast/OpenMeteoForecastClient`: Open-Meteo forecast client, implements `SolarForecastProvider`.
    - `integrations/common/Providers`: shared provider interfaces.
- **Routes** (`routes/`): health, solar estimate, potential, forecast. All under `/solar`, behind global rate limit.
- **Tests**: route tests, calculator tests, forecast client tests (including sun-window handling).

## Project Structure

```
src/main/kotlin/com/devtamuno/heliocore/
  Application.kt
  config/AppConfig.kt
  domain/
    SolarEstimateRequest.kt
    SolarPotentialRequest.kt
    SolarEstimateResponse.kt
    SolarPotentialResponse.kt
    SolarForecastModels.kt
    MeasuredValue.kt
    Errors.kt
  services/SolarProductionCalculator.kt
  integrations/
    common/Providers.kt
    pvwatts/PvWattsClient.kt
    forecast/OpenMeteoForecastClient.kt
  routes/
    Routing.kt
    HealthRoutes.kt
    SolarRoutes.kt
src/main/resources/application.conf
requests.http
```

## Installation

```bash
./gradlew clean build
```

## Environment Variables

Only PVWatts requires a key; server port is optional. Redis (with optional auth) is optional for caching.

Example `.env`:

```
PVWATTS_API_KEY=your-nrel-key
SERVER_PORT=8080
REDIS_URL=redis://localhost:6379  # optional
REDIS_USERNAME=default           # optional, matches Redis ACL user
REDIS_PASSWORD=redispass         # optional, required if Redis is secured
```

## Running the Server

```bash
./gradlew run   # uses SERVER_PORT or defaults to 8080
```

To start Redis locally (optional cache):
```bash
REDIS_PASSWORD=redispass docker-compose up -d redis
```

## API Endpoints

All bodies and responses are JSON with explicit units.

### Health

- `GET /health` → `{ "status": "ok" }`

### Solar Estimate

- `POST /solar/estimate`

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

**Response**

```json
{
  "system_capacity": {
    "value": 5.4,
    "unit": "kW"
  },
  "peak_sun_hours": {
    "value": 5.2,
    "unit": "hours"
  },
  "daily_energy": {
    "value": 24.4,
    "unit": "kWh"
  },
  "monthly_energy": {
    "value": 732.0,
    "unit": "kWh"
  },
  "annual_energy": {
    "value": 8906.0,
    "unit": "kWh"
  }
}
```

### Solar Potential (PVWatts)

- `POST /solar/potential`

```json
{
  "latitude": 6.5244,
  "longitude": 3.3792,
  "panel_wattage": 1000,
  "panel_count": 1,
  "panel_tilt": 20,
  "azimuth": 180
}
```

**Response**

```json
{
  "solrad_annual": {
    "value": 5.1,
    "unit": "kWh/m²/day"
  },
  "ac_monthly": [
    {
      "value": 400.0,
      "unit": "kWh"
    }
  ],
  "ac_annual": {
    "value": 4800.0,
    "unit": "kWh"
  },
  "panel_wattage": 1000.0,
  "panel_count": 1
}
```

### Solar Forecast (Open-Meteo, 7-day fixed window)

- `POST /solar/forecast`

```json
{
  "latitude": 6.5244,
  "longitude": 3.3792,
  "panel_wattage": 450,
  "panel_count": 12
}
```

**Response (per day)**

```json
{
  "forecasts": [
    {
      "date": "2026-03-12",
      "peak_sun_hours": {
        "value": 5.0,
        "unit": "hours"
      },
      "expected_energy": {
        "value": 10.0,
        "unit": "kWh"
      },
      "peak_irradiance_time": "2026-03-12T12:00",
      "peak_irradiance": {
        "value": 500.0,
        "unit": "Wh/m²"
      },
      "sun_window_start": "2026-03-12T09:00",
      "sun_window_end": "2026-03-12T15:00"
    }
  ],
  "panel_wattage": 450.0,
  "panel_count": 12
}
```

## Example Requests

See `requests.http` for ready-to-use samples (Lagos, Port Harcourt, Benin City, London, Benin forecast).

## Development

- Build: `./gradlew build`
- Tests: `./gradlew test`

## Future Improvements

- Add configurable forecast horizon and loss factors.
- Cache PVWatts/Open-Meteo responses to reduce external calls.
- Add authentication and per-key rate limits.
- Provide hourly forecast/production breakdowns.
- Surface validation errors with field-level details.
