# WeatherApp-Android-Kotlin-MVVM-Retrofit-Room-GPS
Real-time weather forecasting Android application consuming the OpenWeatherMap REST API, with offline caching via Room and GPS-based location detection.
# 🌤️ WeatherApp — Android Kotlin · MVVM · Retrofit · Room · GPS

> Real-time weather forecasting Android application consuming the OpenWeatherMap REST API, with offline caching via Room and GPS-based location detection.

---

## 📋 Table of Contents

- [Overview](#overview)
- [Architecture](#architecture)
- [Tech Stack](#tech-stack)
- [Project Structure](#project-structure)
- [Key Design Decisions](#key-design-decisions)
- [Known Issues & Fixes Applied](#known-issues--fixes-applied)
- [Security Warning](#security-warning)
- [Setup & Configuration](#setup--configuration)
- [API Reference](#api-reference)
- [Improvements Roadmap](#improvements-roadmap)

---

## Overview

WeatherApp is an Android application written in **Java** (MVVM pattern) that fetches real-time weather conditions and a 5-day / 3-hour forecast from the [OpenWeatherMap API v2.5](https://openweathermap.org/api). It uses the device's GPS coordinates (via FusedLocationProviderClient) to automatically center data on the user's position, with a graceful fallback to a default city when GPS is unavailable.

Weather data is cached locally with **Room** (SQLite) for offline-first access. The cache is invalidated after 30 minutes, triggering a fresh network request.

---

## Architecture

The project follows **MVVM (Model–View–ViewModel)** as recommended by the Android Jetpack guidelines.

```
┌─────────────────────────────────────────────────────────┐
│                        UI Layer                         │
│   MainActivity  ◄──LiveData──  WeatherViewModel         │
│   ForecastAdapter                                       │
└───────────────────────┬─────────────────────────────────┘
                        │ calls
┌───────────────────────▼─────────────────────────────────┐
│                   Domain / Data Layer                   │
│                  WeatherRepository                      │
│          ┌──────────────┴──────────────┐                │
│   Room (local cache)         Retrofit (remote API)      │
│   WeatherDao / WeatherEntity WeatherApiService          │
└─────────────────────────────────────────────────────────┘
```

**Data flow:**
1. `MainActivity` requests location → calls `viewModel.setLocation(lat, lon)`
2. `WeatherViewModel` triggers `WeatherRepository` via `Transformations.switchMap`
3. `WeatherRepository` checks Room for a fresh entry (< 30 min old)
   - **Cache hit** → posts cached `WeatherEntity` immediately
   - **Cache miss / stale** → calls Retrofit API, saves result to Room, posts response
4. `LiveData` propagates `Resource<T>` (LOADING / SUCCESS / ERROR) back to the UI

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 17 (Android) |
| UI | XML Layouts · Material Design 3 · RecyclerView |
| Architecture | MVVM · AndroidViewModel · LiveData · Transformations |
| Networking | Retrofit 2 · OkHttp 3 · Gson Converter |
| Local Cache | Room (SQLite) · DAO pattern |
| Location | FusedLocationProviderClient (Google Play Services) |
| Image Loading | Glide |
| Build | Gradle (Kotlin DSL) · Android Gradle Plugin |
| Min SDK | Android 5.0+ (API 21) |

---

## Project Structure

```
app/src/main/java/com/abdelhamid/examentpv2/
│
├── data/
│   ├── local/
│   │   ├── AppDatabase.java          # Room database singleton (double-checked locking)
│   │   ├── WeatherDao.java           # DAO: insert, query, delete operations
│   │   └── WeatherEntity.java        # Room entity — cached weather record
│   │
│   ├── remote/
│   │   ├── RetrofitClient.java       # Retrofit singleton with OkHttp logging interceptor
│   │   ├── WeatherApiService.java    # API interface: /weather and /forecast endpoints
│   │   └── model/
│   │       ├── WeatherResponse.java  # Root DTO for current weather
│   │       ├── ForecastResponse.java # Root DTO for 5-day forecast
│   │       ├── ForecastItem.java     # Single forecast time slot
│   │       ├── Main.java             # Temperature, humidity, pressure fields
│   │       ├── Weather.java          # Weather condition (id, main, description, icon)
│   │       └── Wind.java             # Wind speed and direction
│   │
│   └── repository/
│       └── WeatherRepository.java    # Single source of truth — cache-first strategy
│
├── ui/
│   ├── MainActivity.java             # Single-activity UI: permissions, observers, display
│   ├── WeatherViewModel.java         # Exposes LiveData, delegates to Repository
│   └── ForecastAdapter.java          # ListAdapter + DiffUtil for forecast RecyclerView
│
└── utils/
    ├── LocationHelper.java           # FusedLocationProviderClient wrapper
    └── Resource.java                 # Generic sealed-class-style state wrapper (LOADING/SUCCESS/ERROR)
```

---

## Key Design Decisions

### Cache-first with staleness check
`WeatherRepository.getWeather()` always queries Room first on a background thread (`databaseWriteExecutor`). If the cached record is older than **30 minutes** (`STALE_MS = 1_800_000`), a network call is triggered. This avoids redundant API calls and provides offline support.

### Resource wrapper
`Resource<T>` is a generic state container (LOADING / SUCCESS / ERROR) inspired by the official Google Architecture Components guide. It keeps the UI decoupled from the data layer — the UI only reacts to status transitions.

### Transformations.switchMap
`WeatherViewModel` uses `switchMap` on a `MutableLiveData<LocationParams>` trigger. Each time `setLocation()` is called, the previous repository LiveData is automatically unsubscribed and replaced, preventing stale data from leaking between location changes.

### DiffUtil in ForecastAdapter
`ForecastAdapter` extends `ListAdapter<ForecastItem, ...>` with a custom `DiffUtil.ItemCallback`. Items are compared by their `dt` (Unix timestamp) field, ensuring the RecyclerView only redraws cells that actually changed.

---

## Known Issues & Fixes Applied

### BUG 1 — Room query on main thread
**Problem:** Querying Room synchronously on the main thread causes `IllegalStateException` and ANR risk.  
**Fix:** `getLatestWeatherSync()` is now called inside `AppDatabase.databaseWriteExecutor.execute(...)`, keeping the main thread free and results posted via `result.postValue(...)`.

### BUG 2 — Forecast LiveData not observed
**Problem:** The forecast `LiveData` returned by `repository.getForecast()` was never observed, so forecast data never reached the UI.  
**Fix:** In `WeatherViewModel.setLocation()`, the repository's forecast `LiveData` is now observed with `observeForever`, and results are forwarded to the public `forecastResource` `MutableLiveData`.

> ⚠️ **Note:** Using `observeForever` without a corresponding `removeObserver` call can cause memory leaks if the ViewModel is cleared while an active network call is in flight. The recommended fix is to migrate to Kotlin Coroutines + `viewModelScope` (see [Improvements Roadmap](#improvements-roadmap)).

---

## Security Warning

> **⚠️ CRITICAL — API key exposed in source code**

The OpenWeatherMap API key is hardcoded in `WeatherRepository.java`:

```java
// WeatherRepository.java — DO NOT commit this to a public repository
private static final String API_KEY = "365bcfffa76a3cd900729adfe5ee60f1";
```

**This key must never be committed to a public repository.** Anyone who finds it can exhaust your API quota or incur charges.

**Recommended fix:** Store the key in `local.properties` (already in `.gitignore`) and expose it at build time via `BuildConfig`:

```kotlin
// app/build.gradle.kts
android {
    defaultConfig {
        buildConfigField("String", "OWM_API_KEY", "\"${properties["OWM_API_KEY"]}\"")
    }
}
```

```java
// WeatherRepository.java
private static final String API_KEY = BuildConfig.OWM_API_KEY;
```

---

## Setup & Configuration

### Prerequisites
- Android Studio Hedgehog (2023.1.1) or later
- JDK 17
- An [OpenWeatherMap](https://openweathermap.org/api) account (free tier is sufficient)

### Steps

1. **Clone the repository**
   ```bash
   git clone https://github.com/<your-username>/weather-app-android.git
   cd weather-app-android
   ```

2. **Add your API key** in `local.properties` (create the file if it does not exist):
   ```
   OWM_API_KEY=your_api_key_here
   ```

3. **Sync Gradle** in Android Studio (`File > Sync Project with Gradle Files`)

4. **Run** on a physical device or emulator with Google Play Services (required for `FusedLocationProviderClient`).

> **GPS on emulator:** Use the Extended Controls panel in Android Studio (`... > Location`) to send mock GPS coordinates.

---

## API Reference

| Endpoint | Method | Description |
|---|---|---|
| `/data/2.5/weather` | GET | Current weather by coordinates |
| `/data/2.5/forecast` | GET | 5-day forecast in 3-hour steps |

**Common query parameters:**

| Parameter | Value |
|---|---|
| `lat` | Device latitude |
| `lon` | Device longitude |
| `appid` | Your API key |
| `units` | `metric` (°C) |

---

## Improvements Roadmap

The following improvements are recommended for a production-grade release:

- **Migrate to Kotlin** — Replace Java with idiomatic Kotlin to leverage coroutines, `data class`, `sealed class`, and extension functions.
- **Kotlin Coroutines + Flow** — Replace `Call<T>` + `Callback` with `suspend fun` and `StateFlow`/`SharedFlow` for cleaner async handling and lifecycle-safe collection.
- **Hilt (Dependency Injection)** — Remove manual `new WeatherRepository(context)` construction; inject dependencies via `@HiltViewModel` and `@Inject`.
- **WorkManager for background refresh** — Schedule periodic cache refresh in the background without keeping the app alive.
- **Unit Tests** — Add JUnit + Mockito tests for `WeatherRepository` (mocking `WeatherDao` and `WeatherApiService`) and `WeatherViewModel` (using `InstantTaskExecutorRule`).
- **Secure API key** — Move to `BuildConfig` field sourced from `local.properties` (see [Security Warning](#security-warning)).
- **Error state UI** — Replace `Toast` with a dedicated error `TextView` or `Snackbar` with a retry action.
- **Migrate to OWM API v3.0** — The current `/data/2.5/` endpoints are deprecated in favour of the One Call API 3.0.

---

## License

This project is provided for educational purposes. See `LICENSE` for details.
