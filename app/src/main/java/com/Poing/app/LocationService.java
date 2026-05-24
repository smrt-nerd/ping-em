package com.Poing.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

public class LocationService extends Service {

    // ---------------------------------------------------------------
    // CAMPUS CONFIG — change these to test anywhere
    // For BRACU launch: 23.7748, 90.4043, radius 300
    // ---------------------------------------------------------------
    private static final double CAMPUS_LAT    = 23.7748;
    private static final double CAMPUS_LNG    = 90.4043;
    private static final float  CAMPUS_RADIUS = 300f;
    // ---------------------------------------------------------------

    private static final String CHANNEL_ID = "poing_location_channel";
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private boolean currentlyOnCampus = false;
    private boolean hasInitialized = false;

    @Override
    public void onCreate() {
        super.onCreate();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        createNotificationChannel();
        startForeground(1, buildNotification());
        startLocationUpdates();
    }

    private void startLocationUpdates() {
        LocationRequest request = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 30000)
                .setMinUpdateIntervalMillis(15000)
                .build();

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                android.util.Log.d("LocationService", "onLocationResult fired");
                if (locationResult == null) {
                    android.util.Log.d("LocationService", "locationResult is null");
                    return;
                }
                android.location.Location location = locationResult.getLastLocation();
                if (location == null) {
                    android.util.Log.d("LocationService", "location is null");
                    return;
                }

                android.util.Log.d("LocationService", "Got GPS: " + location.getLatitude() + ", " + location.getLongitude());

                float[] results = new float[1];
                android.location.Location.distanceBetween(
                        location.getLatitude(), location.getLongitude(),
                        CAMPUS_LAT, CAMPUS_LNG,
                        results
                );

                float distanceFromCampus = results[0];
                android.util.Log.d("LocationService", "Distance from campus: " + distanceFromCampus + "m");
                boolean nowOnCampus = distanceFromCampus <= CAMPUS_RADIUS;

                if (nowOnCampus != currentlyOnCampus || !hasInitialized) {
                    hasInitialized = true;
                    currentlyOnCampus = nowOnCampus;
                    android.util.Log.d("LocationService", "Status changed: onCampus = " + nowOnCampus);
                    updateCampusStatus(nowOnCampus);
                }
            }
        };

        try {
            fusedLocationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper());
        } catch (SecurityException e) {
            e.printStackTrace();
        }
    }

    private void updateCampusStatus(boolean onCampus) {
        String uid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid()
                : null;

        if (uid == null) return;

        FirebaseFirestore.getInstance()
                .collection("users")
                .document(uid)
                .update("onCampus", onCampus);
    }

    private Notification buildNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Poing is active")
                .setContentText("Watching for campus entry...")
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Poing Location Service",
                NotificationManager.IMPORTANCE_LOW
        );
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(channel);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}