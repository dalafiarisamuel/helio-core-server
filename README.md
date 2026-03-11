# heliocore-server

Production-ready Ktor service that estimates solar energy production and proxies PVWatts potential data.

## Architecture

- Kotlin + Ktor 3, Kotlinx Serialization
- Clean package slices: `config`, `domain`, `services`, `integrations`, `routes`
- Netty engine, JSON APIs, CORS, call logging, status pages

## Configuration

Environment variables:

- `PVWATTS_API_KEY` (required)
- `SERVER_PORT` (defaults to 8080)

Example `.env`:

```
PVWATTS_API_KEY=your-nrel-key
SERVER_PORT=8080
```

## Running locally

```
./gradlew clean build
./gradlew run            # reads SERVER_PORT from env, default 8080
```

Server will bind on `0.0.0.0:PORT`.

## REST endpoints

- `GET /health` → `{ "status": "ok" }`
- `GET /solar/potential?lat=6.52&lon=3.37[&tilt=20&azimuth=180]` → PVWatts-derived potential
- `POST /solar/estimate`

```json
{
  "latitude": 6.5244,
  "longitude": 3.3792,
  "panel_wattage": 450,
  "panel_count": 12
}
```

Sample response:

```json
{
  "system_capacity_kw": 5.4,
  "peak_sun_hours": 5.2,
  "daily_energy_kwh": 24.4,
  "monthly_energy_kwh": 732.0,
  "annual_energy_kwh": 8906.0
}
```

## cURL examples

```bash
# health
curl -s http://localhost:8080/health

# potential (PVWatts)
curl -s "http://localhost:8080/solar/potential?lat=6.5244&lon=3.3792&tilt=20&azimuth=180"

# estimate (uses PVWatts internally)
curl -s -X POST http://localhost:8080/solar/estimate \
  -H "Content-Type: application/json" \
  -d '{"latitude":6.5244,"longitude":3.3792,"panel_wattage":450,"panel_count":12}'
```

## Notes

- Validation guards against bad coordinates/inputs; PVWatts failures return 502 with error payload.
- Energy calculations use default system losses of 14% and peak sun hours from PVWatts solrad_annual.
- Tilt & azimuth guidance for requests:
    - `tilt` (degrees from horizontal): start with your site latitude for all-year production; use latitude−10° if you
      favor summer output, latitude+10° for winter. Near the equator, 10–15° works well to shed rain.
    - `azimuth` (degrees clockwise from true north): 180° faces south (best for northern hemisphere), 0° faces north (
      best for southern hemisphere). If roof limits direction, use the roof’s bearing (phone compass or map) and pass
      that value.
