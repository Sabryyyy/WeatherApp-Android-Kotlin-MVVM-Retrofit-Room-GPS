package com.abdelhamid.examentpv2.data.repository;

import android.content.Context;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.abdelhamid.examentpv2.BuildConfig;
import com.abdelhamid.examentpv2.data.local.AppDatabase;
import com.abdelhamid.examentpv2.data.local.WeatherDao;
import com.abdelhamid.examentpv2.data.local.WeatherEntity;
import com.abdelhamid.examentpv2.data.remote.RetrofitClient;
import com.abdelhamid.examentpv2.data.remote.WeatherApiService;
import com.abdelhamid.examentpv2.data.remote.model.ForecastResponse;
import com.abdelhamid.examentpv2.data.remote.model.WeatherResponse;
import com.abdelhamid.examentpv2.utils.Resource;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Single source of truth for weather data.
 *
 * <p>Strategy: cache-first with a 30-minute staleness window.
 * <ul>
 *   <li>Room is queried on a background thread via {@code databaseWriteExecutor}.</li>
 *   <li>If cached data is fresh, it is returned immediately without a network call.</li>
 *   <li>If data is missing or stale, the OpenWeatherMap API is called and the
 *       result is persisted to Room before being posted to the UI.</li>
 * </ul>
 *
 * <p><b>Security:</b> The API key is read from {@code BuildConfig.OWM_API_KEY},
 * which is sourced from {@code local.properties} at build time and never
 * committed to version control.
 */
public class WeatherRepository {

    private static final String TAG = "WeatherRepository";
    private static final long STALE_THRESHOLD_MS = 30 * 60 * 1_000L; // 30 minutes
    // API key injected from local.properties via BuildConfig — never hardcode here.
    private static final String API_KEY = BuildConfig.OWM_API_KEY;

    private final WeatherDao weatherDao;
    private final WeatherApiService apiService;

    public WeatherRepository(Context context) {
        AppDatabase db = AppDatabase.getDatabase(context);
        this.weatherDao = db.weatherDao();
        this.apiService = RetrofitClient.getApiService();
    }

    // -------------------------------------------------------------------------
    // Current Weather
    // -------------------------------------------------------------------------

    /**
     * Returns a {@link LiveData} stream of the current weather for the given coordinates.
     *
     * <p>Emits {@link Resource#loading(Object)} immediately, then either
     * a cached {@link Resource#success(Object)} (if fresh) or triggers a network
     * request and emits the result.
     */
    public LiveData<Resource<WeatherEntity>> getWeather(double lat, double lon) {
        MutableLiveData<Resource<WeatherEntity>> result = new MutableLiveData<>();
        result.setValue(Resource.loading(null));

        // Room must never be queried on the main thread.
        AppDatabase.databaseWriteExecutor.execute(() -> {
            WeatherEntity cached = weatherDao.getLatestWeatherSync();
            boolean isFresh = cached != null
                    && (System.currentTimeMillis() - cached.getTimestamp() < STALE_THRESHOLD_MS);

            if (isFresh) {
                Log.d(TAG, "Cache hit — returning fresh data for " + cached.getCityName());
                result.postValue(Resource.success(cached));
            } else {
                Log.d(TAG, "Cache miss or stale — fetching from network");
                fetchCurrentWeatherFromApi(lat, lon, result);
            }
        });

        return result;
    }

    private void fetchCurrentWeatherFromApi(double lat, double lon,
                                             MutableLiveData<Resource<WeatherEntity>> result) {
        apiService.getCurrentWeather(lat, lon, API_KEY, "metric")
                .enqueue(new Callback<WeatherResponse>() {
                    @Override
                    public void onResponse(Call<WeatherResponse> call,
                                           Response<WeatherResponse> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            WeatherResponse body = response.body();

                            // Guard against empty weather list to avoid IndexOutOfBoundsException
                            List<?> weatherList = body.getWeather();
                            if (weatherList == null || weatherList.isEmpty()) {
                                result.postValue(Resource.error("Malformed API response", null));
                                return;
                            }

                            WeatherEntity entity = new WeatherEntity(
                                    body.getName(),
                                    body.getMain().getTemp(),
                                    body.getMain().getHumidity(),
                                    body.getWeather().get(0).getDescription(),
                                    body.getWeather().get(0).getIcon(),
                                    System.currentTimeMillis(),
                                    lat,
                                    lon
                            );

                            // Persist on background thread before posting to UI
                            AppDatabase.databaseWriteExecutor.execute(
                                    () -> weatherDao.insertWeather(entity)
                            );

                            result.postValue(Resource.success(entity));
                        } else {
                            String errorMsg = "API error " + response.code()
                                    + ": " + response.message();
                            Log.e(TAG, errorMsg);
                            result.postValue(Resource.error(errorMsg, null));
                        }
                    }

                    @Override
                    public void onFailure(Call<WeatherResponse> call, Throwable t) {
                        Log.e(TAG, "Network failure: " + t.getMessage());
                        result.postValue(Resource.error("Network failure: " + t.getMessage(), null));
                    }
                });
    }

    // -------------------------------------------------------------------------
    // 5-Day Forecast
    // -------------------------------------------------------------------------

    /**
     * Returns a {@link LiveData} stream of the 5-day / 3-hour forecast.
     *
     * <p>Forecast data is not cached in Room (it changes frequently and is
     * large). It is fetched directly from the network on every call.
     */
    public LiveData<Resource<ForecastResponse>> getForecast(double lat, double lon) {
        MutableLiveData<Resource<ForecastResponse>> result = new MutableLiveData<>();
        result.setValue(Resource.loading(null));

        apiService.getForecast(lat, lon, API_KEY, "metric")
                .enqueue(new Callback<ForecastResponse>() {
                    @Override
                    public void onResponse(Call<ForecastResponse> call,
                                           Response<ForecastResponse> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            result.postValue(Resource.success(response.body()));
                        } else {
                            result.postValue(Resource.error(
                                    "Forecast API error " + response.code(), null));
                        }
                    }

                    @Override
                    public void onFailure(Call<ForecastResponse> call, Throwable t) {
                        Log.e(TAG, "Forecast network failure: " + t.getMessage());
                        result.postValue(Resource.error(t.getMessage(), null));
                    }
                });

        return result;
    }
}
