package com.abdelhamid.examentpv2.ui;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.Transformations;

import com.abdelhamid.examentpv2.data.local.WeatherEntity;
import com.abdelhamid.examentpv2.data.remote.model.ForecastResponse;
import com.abdelhamid.examentpv2.data.repository.WeatherRepository;
import com.abdelhamid.examentpv2.utils.Resource;

/**
 * ViewModel for the Weather screen.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Hold and expose {@link #weatherResource} and {@link #forecastResource}
 *       as observable {@link LiveData} streams.</li>
 *   <li>Survive configuration changes (screen rotations).</li>
 *   <li>Delegate all data access to {@link WeatherRepository}.</li>
 * </ul>
 *
 * <p><b>Memory-leak fix:</b> The forecast {@link LiveData} returned by the
 * repository is observed with {@code observeForever}. The corresponding
 * {@link Observer} reference is stored so it can be removed in
 * {@link #onCleared()}, preventing a leak when the ViewModel is destroyed.
 */
public class WeatherViewModel extends AndroidViewModel {

    private final WeatherRepository repository;

    /** Trigger: posting a new value re-executes the repository query via switchMap. */
    private final MutableLiveData<LocationParams> locationTrigger = new MutableLiveData<>();

    /** Current weather — driven by switchMap on locationTrigger. */
    public final LiveData<Resource<WeatherEntity>> weatherResource;

    /** 5-day forecast — manually forwarded from the repository LiveData. */
    public final MutableLiveData<Resource<ForecastResponse>> forecastResource =
            new MutableLiveData<>();

    /**
     * Tracks the active forecast LiveData so we can remove its observer in onCleared().
     * Without this, observeForever leaks the ViewModel after it is destroyed.
     */
    private LiveData<Resource<ForecastResponse>> activeForecastSource;
    private final Observer<Resource<ForecastResponse>> forecastObserver =
            resource -> forecastResource.postValue(resource);

    public WeatherViewModel(@NonNull Application application) {
        super(application);
        repository = new WeatherRepository(application);

        // switchMap automatically unsubscribes from the previous LiveData source
        // whenever locationTrigger posts a new value.
        weatherResource = Transformations.switchMap(
                locationTrigger,
                params -> repository.getWeather(params.lat, params.lon)
        );
    }

    /**
     * Triggers a fresh data fetch for the given GPS coordinates.
     *
     * <p>Safe to call multiple times (e.g. on permission grant or manual refresh).
     * Each call cancels the previous forecast observer before attaching a new one.
     *
     * @param lat WGS-84 latitude
     * @param lon WGS-84 longitude
     */
    public void setLocation(double lat, double lon) {
        // Remove the previous forecast observer to avoid duplicate deliveries
        if (activeForecastSource != null) {
            activeForecastSource.removeObserver(forecastObserver);
        }

        // Post new location — this drives weatherResource via switchMap
        locationTrigger.setValue(new LocationParams(lat, lon));

        // Attach observer to the new forecast source
        activeForecastSource = repository.getForecast(lat, lon);
        activeForecastSource.observeForever(forecastObserver);
    }

    /**
     * Called when the ViewModel is about to be destroyed.
     * Removes the forever-observer to prevent a memory leak.
     */
    @Override
    protected void onCleared() {
        super.onCleared();
        if (activeForecastSource != null) {
            activeForecastSource.removeObserver(forecastObserver);
        }
    }

    // -------------------------------------------------------------------------
    // Internal value object — no need to expose outside this class
    // -------------------------------------------------------------------------

    private static final class LocationParams {
        final double lat;
        final double lon;

        LocationParams(double lat, double lon) {
            this.lat = lat;
            this.lon = lon;
        }
    }
}
