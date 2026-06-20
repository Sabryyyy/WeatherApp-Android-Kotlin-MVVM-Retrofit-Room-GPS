package com.abdelhamid.examentpv2.utils;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;

/**
 * Stateless helper for GPS location retrieval using {@link FusedLocationProviderClient}.
 *
 * <p>Improvements over the original implementation:
 * <ul>
 *   <li>Uses {@code getCurrentLocation()} with {@link Priority#PRIORITY_BALANCED_POWER_ACCURACY}
 *       instead of {@code getLastLocation()}, which can return {@code null} when the device has
 *       just rebooted or location has never been requested.</li>
 *   <li>The {@link FusedLocationProviderClient} is created inside each method call to avoid
 *       holding a reference to a potentially-destroyed Context.</li>
 *   <li>An {@code OnFailureListener} is attached to handle task failures explicitly.</li>
 * </ul>
 */
public final class LocationHelper {

    private static final String TAG = "LocationHelper";

    /** Callback interface delivered on the calling thread (main thread). */
    public interface LocationCallback {
        void onLocationReceived(double lat, double lon);
        void onLocationFailed();
    }

    // Private constructor — static utility class
    private LocationHelper() {}

    /**
     * Requests the current device location.
     *
     * <p>Permissions must be checked with {@link #hasPermissions(Context)} before calling this.
     *
     * @param context  must be an {@link Activity} (required by FusedLocationProviderClient)
     * @param callback delivers the result on the main thread
     */
    public static void getCurrentLocation(Context context, LocationCallback callback) {
        if (!hasPermissions(context)) {
            Log.w(TAG, "getCurrentLocation called without permissions — aborting");
            callback.onLocationFailed();
            return;
        }

        FusedLocationProviderClient client =
                LocationServices.getFusedLocationProviderClient(context);
        CancellationTokenSource cts = new CancellationTokenSource();

        //noinspection MissingPermission — guarded by hasPermissions() above
        client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cts.getToken())
                .addOnSuccessListener(location -> {
                    if (location != null) {
                        Log.d(TAG, "Location acquired: "
                                + location.getLatitude() + ", " + location.getLongitude());
                        callback.onLocationReceived(location.getLatitude(), location.getLongitude());
                    } else {
                        Log.w(TAG, "Location task succeeded but returned null");
                        callback.onLocationFailed();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Location task failed: " + e.getMessage());
                    callback.onLocationFailed();
                });
    }

    /**
     * Returns {@code true} if either FINE or COARSE location permission is granted.
     */
    public static boolean hasPermissions(Context context) {
        return ActivityCompat.checkSelfPermission(context,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ActivityCompat.checkSelfPermission(context,
                Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Requests FINE and COARSE location permissions from the user.
     *
     * @param activity    the host activity
     * @param requestCode used to identify the result in {@code onRequestPermissionsResult}
     */
    public static void requestPermissions(Activity activity, int requestCode) {
        ActivityCompat.requestPermissions(activity,
                new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                },
                requestCode);
    }
}
