package com.abdelhamid.examentpv2.data.remote;

import com.abdelhamid.examentpv2.BuildConfig;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Thread-safe Retrofit singleton.
 *
 * <p>Uses double-checked locking to ensure a single {@link Retrofit} instance
 * across the application lifetime.
 *
 * <p><b>Logging:</b> Full BODY logging is enabled only in debug builds
 * ({@code BuildConfig.DEBUG}). In release builds the logging level is set to
 * NONE to avoid leaking sensitive data (API keys, location) into logcat.
 *
 * <p><b>Timeouts:</b> Explicit connect / read / write timeouts are configured
 * to prevent the app from hanging indefinitely on slow networks.
 */
public final class RetrofitClient {

    private static final String BASE_URL = "https://api.openweathermap.org/data/2.5/";

    /** Connect timeout in seconds. */
    private static final long TIMEOUT_CONNECT_S = 10;
    /** Read / write timeout in seconds. */
    private static final long TIMEOUT_RW_S = 15;

    private static volatile Retrofit retrofit;

    // Private constructor — static utility class, not instantiable
    private RetrofitClient() {}

    /**
     * Returns the singleton {@link WeatherApiService} instance, creating it
     * lazily on first call.
     */
    public static WeatherApiService getApiService() {
        if (retrofit == null) {
            synchronized (RetrofitClient.class) {
                if (retrofit == null) {
                    retrofit = buildRetrofit();
                }
            }
        }
        return retrofit.create(WeatherApiService.class);
    }

    private static Retrofit buildRetrofit() {
        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        // Log full body only in debug — never in production builds
        logging.setLevel(BuildConfig.DEBUG
                ? HttpLoggingInterceptor.Level.BODY
                : HttpLoggingInterceptor.Level.NONE);

        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(TIMEOUT_CONNECT_S, TimeUnit.SECONDS)
                .readTimeout(TIMEOUT_RW_S, TimeUnit.SECONDS)
                .writeTimeout(TIMEOUT_RW_S, TimeUnit.SECONDS)
                .addInterceptor(logging)
                .build();

        return new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .client(client)
                .build();
    }
}
