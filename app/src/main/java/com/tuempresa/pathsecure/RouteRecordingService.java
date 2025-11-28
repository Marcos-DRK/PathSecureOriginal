package com.tuempresa.pathsecure;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.IBinder;
import android.os.Looper;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class RouteRecordingService extends Service {

    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private List<Route.CustomLatLng> recordedPoints;
    private boolean isRecording = false;

    @Override
    public void onCreate() {
        super.onCreate();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        recordedPoints = new ArrayList<>();

        startForegroundNotification();

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null) return;
                for (android.location.Location location : locationResult.getLocations()) {
                    recordedPoints.add(new Route.CustomLatLng(location.getLatitude(), location.getLongitude()));
                }
            }
        };
    }

    private void startForegroundNotification() {
        String CHANNEL_ID = "Route_Channel";
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Grabación de Ruta", NotificationManager.IMPORTANCE_LOW);
            ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).createNotificationChannel(channel);
        }

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("PathSecure")
                .setContentText("Grabando tu ruta...")
                .setSmallIcon(R.mipmap.ic_launcher)
                .build();

        startForeground(2, notification);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            if ("START_RECORDING".equals(action)) {
                startRecording();
            } else if ("STOP_RECORDING".equals(action)) {
                String routeName = intent.getStringExtra("ROUTE_NAME");
                stopRecordingAndSave(routeName);
            }
        }
        return START_STICKY;
    }

    private void startRecording() {
        if (isRecording) return;
        isRecording = true;
        recordedPoints.clear();

        LocationRequest locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
                .setMinUpdateDistanceMeters(5)
                .build();

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
            Toast.makeText(this, "Grabación Iniciada", Toast.LENGTH_SHORT).show();
        }
    }

    private void stopRecordingAndSave(String routeName) {
        isRecording = false;
        fusedLocationClient.removeLocationUpdates(locationCallback);

        if (recordedPoints.isEmpty()) {
            Toast.makeText(this, "Ruta vacía, no se guardó", Toast.LENGTH_SHORT).show();
            stopSelf();
            return;
        }

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        String routeId = UUID.randomUUID().toString();
        String date = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(new Date());

        Route newRoute = new Route(routeId, routeName, date, recordedPoints);

        db.collection("users").document(uid).collection("routes").document(routeId)
                .set(newRoute)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Ruta guardada exitosamente", Toast.LENGTH_SHORT).show();
                    stopSelf();
                });
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return null; }
}