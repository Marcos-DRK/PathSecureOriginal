package com.tuempresa.pathsecure;

import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class AlertActivity extends AppCompatActivity {

    private TextView tvCount;
    private Button btnSafe;
    private CountDownTimer timer;

    private static final long START_TIME_IN_MILLIS = 60000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_alert);

        tvCount = findViewById(R.id.tvCountdown);
        btnSafe = findViewById(R.id.btnSafe);

        startTimer();

        btnSafe.setOnClickListener(v -> {
            stopTimer();
            Intent stopIntent = new Intent(this, SensorService.class);
            stopIntent.setAction("ACTION_CANCEL_ALERT");
            startService(stopIntent);

            finish();
        });
    }

    private void startTimer() {
        timer = new CountDownTimer(START_TIME_IN_MILLIS, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                tvCount.setText(String.valueOf(millisUntilFinished / 1000));
            }

            @Override
            public void onFinish() {
                tvCount.setText("0");
                triggerEmergency();
            }
        }.start();
    }

    private void stopTimer() {
        if (timer != null) {
            timer.cancel();
        }
    }

    private void triggerEmergency() {
        Intent intent = new Intent(this, SensorService.class);
        intent.setAction("ACTION_TRIGGER_EMERGENCY");
        startService(intent);
        finish();
    }

    @Override
    public void onBackPressed() {
    }
}