package com.tuempresa.pathsecure;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    private FusedLocationProviderClient fusedLocationClient;
    private static final int PERMISSION_REQUEST_CODE = 1001;

    private Button btnSaveManual;
    private List<Route.CustomLatLng> manualPoints = new ArrayList<>();
    private Polyline currentPolyline;
    private Route activeRouteToFollow = null;
    private LocationCallback locationCallback;
    private boolean isMonitoringDeviation = false;
    private static final float MAX_DEVIATION_METERS = 50.0f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        checkAndRequestPermissions();

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.mapFragment);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        Button btnContacts = findViewById(R.id.btnContacts);
        Button btnRoutes = findViewById(R.id.btnRoutes);
        Button btnSettings = findViewById(R.id.btnSettings);
        FloatingActionButton fabLocation = findViewById(R.id.fabCurrentLocation);

        btnSaveManual = findViewById(R.id.btnSaveManualRoute);

        btnContacts.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, ContactsActivity.class)));
        btnRoutes.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, RoutesActivity.class)));
        btnSettings.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, SettingsActivity.class)));

        fabLocation.setOnClickListener(v -> getCurrentLocation());

        if (btnSaveManual != null) {
            btnSaveManual.setOnClickListener(v -> saveManualRouteDialog());
        }

        setupLocationCallback();
    }

    private void setupLocationCallback() {
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                if (locationResult == null) return;
                for (Location location : locationResult.getLocations()) {
                    if (isMonitoringDeviation && activeRouteToFollow != null) {
                        checkDeviation(location);
                    }
                }
            }
        };
    }

    private void checkDeviation(Location currentLocation) {
        if (activeRouteToFollow.getPoints() == null || activeRouteToFollow.getPoints().isEmpty()) return;

        float minDistance = Float.MAX_VALUE;

        for (Route.CustomLatLng point : activeRouteToFollow.getPoints()) {
            float[] results = new float[1];
            Location.distanceBetween(currentLocation.getLatitude(), currentLocation.getLongitude(),
                    point.latitude, point.longitude, results);
            if (results[0] < minDistance) {
                minDistance = results[0];
            }
        }

        if (minDistance > MAX_DEVIATION_METERS) {
            Toast.makeText(this, "¡Desvío Detectado! Activando Alerta", Toast.LENGTH_LONG).show();
            triggerEmergencyAlert();
            isMonitoringDeviation = false;
        }
    }

    private void triggerEmergencyAlert() {
        Intent intent = new Intent(MainActivity.this, AlertActivity.class);
        startActivity(intent);

        Intent serviceIntent = new Intent(this, SensorService.class);
        serviceIntent.setAction("ACTION_TRIGGER_EMERGENCY");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }

    private void startDeviationMonitoring() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return;

        isMonitoringDeviation = true;
        Toast.makeText(this, "Protección de Ruta ACTIVADA. Sigue el camino azul.", Toast.LENGTH_LONG).show();

        LocationRequest locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
                .setMinUpdateDistanceMeters(5)
                .build();

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
    }

    private void checkAndRequestPermissions() {
        String[] permissions;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions = new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.SEND_SMS,
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.POST_NOTIFICATIONS
            };
        } else {
            permissions = new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.SEND_SMS,
                    Manifest.permission.RECORD_AUDIO
            };
        }

        List<String> listPermissionsNeeded = new ArrayList<>();
        for (String p : permissions) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                listPermissionsNeeded.add(p);
            }
        }

        if (!listPermissionsNeeded.isEmpty()) {
            ActivityCompat.requestPermissions(this, listPermissionsNeeded.toArray(new String[0]), PERMISSION_REQUEST_CODE);
        } else {
            startSensorService();
            if (mMap != null) enableUserLocation();
        }
    }

    private void startSensorService() {
        Intent sensorService = new Intent(this, SensorService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(sensorService);
        } else {
            startService(sensorService);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            if (grantResults.length > 0) {
                for (int result : grantResults) {
                    if (result != PackageManager.PERMISSION_GRANTED) {
                        allGranted = false;
                        break;
                    }
                }
            }

            if (allGranted) {
                Toast.makeText(this, "Seguridad Activada", Toast.LENGTH_SHORT).show();
                startSensorService();
                enableUserLocation();
            } else {
                Toast.makeText(this, "Se necesitan permisos para las alertas", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            enableUserLocation();
        }
        checkIntentForRoutes();
    }

    private void checkIntentForRoutes() {
        if (getIntent().getBooleanExtra("MODE_MANUAL_DRAW", false)) {
            if(btnSaveManual != null) btnSaveManual.setVisibility(View.VISIBLE);
            Toast.makeText(this, "MODO DIBUJO: Toca el mapa para trazar", Toast.LENGTH_LONG).show();

            mMap.setOnMapClickListener(latLng -> {
                manualPoints.add(new Route.CustomLatLng(latLng.latitude, latLng.longitude));

                mMap.addMarker(new MarkerOptions().position(latLng));

                if (currentPolyline != null) currentPolyline.remove();
                PolylineOptions polyOptions = new PolylineOptions().color(Color.MAGENTA).width(12);
                for (Route.CustomLatLng p : manualPoints) {
                    polyOptions.add(new LatLng(p.latitude, p.longitude));
                }
                currentPolyline = mMap.addPolyline(polyOptions);
            });
        }

        String viewRouteId = getIntent().getStringExtra("VIEW_ROUTE_ID");
        if (viewRouteId != null) {
            FirebaseFirestore.getInstance().collection("users")
                    .document(FirebaseAuth.getInstance().getUid())
                    .collection("routes").document(viewRouteId).get()
                    .addOnSuccessListener(doc -> {
                        Route route = doc.toObject(Route.class);
                        if (route != null && route.getPoints() != null) {
                            activeRouteToFollow = route;

                            PolylineOptions polylineOptions = new PolylineOptions().color(Color.BLUE).width(15);
                            for (Route.CustomLatLng point : route.getPoints()) {
                                polylineOptions.add(new LatLng(point.latitude, point.longitude));
                            }
                            mMap.addPolyline(polylineOptions);

                            if(!route.getPoints().isEmpty()){
                                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(
                                        new LatLng(route.getPoints().get(0).latitude, route.getPoints().get(0).longitude), 16f));
                            }

                            new AlertDialog.Builder(MainActivity.this)
                                    .setTitle("Seguir Ruta Segura")
                                    .setMessage("¿Deseas activar la protección por desvío? Si te alejas 50m de esta ruta, se activará la alerta.")
                                    .setPositiveButton("SÍ, ACTIVAR", (dialog, which) -> startDeviationMonitoring())
                                    .setNegativeButton("Solo ver", null)
                                    .show();
                        }
                    });
        }
    }

    private void saveManualRouteDialog() {
        if (manualPoints.size() < 2) {
            Toast.makeText(this, "Dibuja al menos 2 puntos", Toast.LENGTH_SHORT).show();
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Guardar Ruta Manual");
        final EditText input = new EditText(this);
        input.setHint("Nombre de la ruta");
        builder.setView(input);

        builder.setPositiveButton("Guardar", (dialog, which) -> {
            String name = input.getText().toString();
            if (name.isEmpty()) name = "Ruta Manual";

            String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
            String routeId = UUID.randomUUID().toString();
            String date = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(new Date());

            Route newRoute = new Route(routeId, name, date, manualPoints);

            FirebaseFirestore.getInstance().collection("users").document(uid).collection("routes").document(routeId)
                    .set(newRoute)
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(this, "Ruta guardada", Toast.LENGTH_SHORT).show();
                        if(btnSaveManual != null) btnSaveManual.setVisibility(View.GONE);
                        mMap.clear();
                        manualPoints.clear();
                        mMap.setOnMapClickListener(null);
                    });
        });
        builder.setNegativeButton("Cancelar", null);
        builder.show();
    }

    private void enableUserLocation() {
        if (mMap == null) return;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            mMap.setMyLocationEnabled(true);
            getCurrentLocation();
        }
    }

    private void getCurrentLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                    .addOnSuccessListener(this, location -> {
                        if (location != null && mMap != null) {
                            LatLng currentLatLng = new LatLng(location.getLatitude(), location.getLongitude());
                            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 15f));
                        } else {
                            Toast.makeText(this, "Buscando señal GPS...", Toast.LENGTH_SHORT).show();
                        }
                    })
                    .addOnFailureListener(e -> Toast.makeText(this, "Error GPS: " + e.getMessage(), Toast.LENGTH_SHORT).show());
        }
    }
}