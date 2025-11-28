package com.tuempresa.pathsecure;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.telephony.SmsManager;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.File;
import java.io.IOException;

public class SensorService extends Service implements SensorEventListener {

    private SensorManager sensorManager;
    private Sensor accelerometer;
    private FusedLocationProviderClient fusedLocationClient;
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private StorageReference storageRef;
    private float mAccel;
    private float mAccelCurrent;
    private float mAccelLast;
    private int volumeClickCount = 0;
    private long lastVolumeClickTime = 0;
    private VolumeReceiver volumeReceiver;
    private MediaRecorder mediaRecorder;
    private String audioFileName = null;
    private Handler locationHandler;
    private Runnable locationRunnable;
    private boolean isTracking = false;
    private static final long TRACKING_INTERVAL = 15000;

    @Override
    public void onCreate() {
        super.onCreate();

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();
        storageRef = FirebaseStorage.getInstance().getReference();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        locationHandler = new Handler(Looper.getMainLooper());

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI);
        }

        mAccel = 0.00f;
        mAccelCurrent = SensorManager.GRAVITY_EARTH;
        mAccelLast = SensorManager.GRAVITY_EARTH;

        volumeReceiver = new VolumeReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction("android.media.VOLUME_CHANGED_ACTION");
        registerReceiver(volumeReceiver, filter);

        startForegroundServiceNotification();
    }

    private void startForegroundServiceNotification() {
        String CHANNEL_ID = "PathSecure_Channel";
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Protección Activa", NotificationManager.IMPORTANCE_HIGH);
            ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).createNotificationChannel(channel);
        }

        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("PathSecure")
                .setContentText("Seguridad activada. Toca para abrir.")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION | ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
        } else {
            startForeground(1, notification);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            if (intent.getAction().equals("ACTION_TRIGGER_EMERGENCY")) {
                executeEmergencyProtocol();
            } else if (intent.getAction().equals("ACTION_CANCEL_ALERT")) {
                stopTracking();
                Toast.makeText(this, "Alerta Cancelada", Toast.LENGTH_SHORT).show();
            }
        }
        return START_STICKY;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            float x = event.values[0];
            float y = event.values[1];
            float z = event.values[2];
            mAccelLast = mAccelCurrent;
            mAccelCurrent = (float) Math.sqrt((double) (x * x + y * y + z * z));
            float delta = mAccelCurrent - mAccelLast;
            mAccel = mAccel * 0.9f + delta;

            if (mAccel > 9) {
                openAlertScreen();
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    private class VolumeReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ("android.media.VOLUME_CHANGED_ACTION".equals(intent.getAction())) {
                long currentTime = System.currentTimeMillis();
                if (currentTime - lastVolumeClickTime < 800) {
                    volumeClickCount++;
                } else {
                    volumeClickCount = 1;
                }
                lastVolumeClickTime = currentTime;

                if (volumeClickCount >= 2) {
                    volumeClickCount = 0;
                    openAlertScreen();
                }
            }
        }
    }

    private void openAlertScreen() {
        Intent intent = new Intent(this, AlertActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    private void executeEmergencyProtocol() {
        if (isTracking) return;
        isTracking = true;

        Toast.makeText(this, "¡ALERTA INICIADA! Grabando y Rastreando...", Toast.LENGTH_LONG).show();

        startRecordingAudio();
        startLocationLoop();

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            stopRecordingAudio();
            uploadAudioToFirebaseAndSendLink();
        }, 15000);
    }

    private void startLocationLoop() {
        locationRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isTracking) return;

                if (ActivityCompat.checkSelfPermission(SensorService.this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                            .addOnSuccessListener(location -> {
                                sendSMSToContacts(location, null);
                            });
                } else {
                    sendSMSToContacts(null, null);
                }

                locationHandler.postDelayed(this, TRACKING_INTERVAL);
            }
        };
        locationHandler.post(locationRunnable);
    }

    private void stopTracking() {
        isTracking = false;
        if (locationHandler != null && locationRunnable != null) {
            locationHandler.removeCallbacks(locationRunnable);
        }
        stopRecordingAudio();
    }

    private void sendSMSToContacts(@Nullable Location location, @Nullable String audioLink) {
        if (mAuth.getCurrentUser() == null) return;
        String userId = mAuth.getCurrentUser().getUid();

        StringBuilder msg = new StringBuilder("¡ALERTA! ");

        if (location != null) {
            msg.append("Ubicación: http://maps.google.com/?q=").append(location.getLatitude()).append(",").append(location.getLongitude());
        } else {
            msg.append("Necesito ayuda urgente.");
        }

        if (audioLink != null) {
            msg.append(" | EVIDENCIA (AUDIO): ").append(audioLink);
        }

        final String finalMsg = msg.toString();

        db.collection("users").document(userId).collection("contacts").get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    SmsManager smsManager;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        smsManager = getSystemService(SmsManager.class);
                    } else {
                        smsManager = SmsManager.getDefault();
                    }

                    for (DocumentSnapshot doc : queryDocumentSnapshots) {
                        String phone = doc.getString("phone");
                        if (phone != null && !phone.isEmpty()) {
                            try {
                                smsManager.sendTextMessage(phone, null, finalMsg, null, null);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }
                });
    }

    private void startRecordingAudio() {
        try {
            audioFileName = getExternalCacheDir().getAbsolutePath() + "/emergency_audio.3gp";
            mediaRecorder = new MediaRecorder();
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
            mediaRecorder.setOutputFile(audioFileName);
            mediaRecorder.prepare();
            mediaRecorder.start();
        } catch (IOException | RuntimeException e) {
            e.printStackTrace();
        }
    }

    private void stopRecordingAudio() {
        if (mediaRecorder != null) {
            try {
                mediaRecorder.stop();
                mediaRecorder.release();
                mediaRecorder = null;
            } catch (RuntimeException stopException) {
            }
        }
    }

    private void uploadAudioToFirebaseAndSendLink() {
        if (audioFileName == null) return;
        File fileObj = new File(audioFileName);
        if (!fileObj.exists()) return;

        Uri file = Uri.fromFile(fileObj);
        StorageReference audioRef = storageRef.child("audios/" + mAuth.getCurrentUser().getUid() + "/" + System.currentTimeMillis() + ".3gp");

        audioRef.putFile(file)
                .addOnSuccessListener(taskSnapshot -> {
                    audioRef.getDownloadUrl().addOnSuccessListener(uri -> {
                        String downloadUrl = uri.toString();

                        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                                    .addOnSuccessListener(location -> {
                                        sendSMSToContacts(location, downloadUrl);
                                    });
                        } else {
                            // Fallback si no hay GPS
                            sendSMSToContacts(null, downloadUrl);
                        }
                    });
                })
                .addOnFailureListener(e -> Log.e("PathSecure", "Error subiendo audio", e));
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopTracking();
        if (sensorManager != null) sensorManager.unregisterListener(this);
        if (volumeReceiver != null) unregisterReceiver(volumeReceiver);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}