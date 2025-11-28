package com.tuempresa.pathsecure;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.HashMap;
import java.util.Map;

public class SettingsActivity extends AppCompatActivity {

    private EditText etName, etEmail;
    private SeekBar seekBarSensitivity;
    private CheckBox cbShake, cbVolume;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        prefs = getSharedPreferences("PathSecurePrefs", Context.MODE_PRIVATE);

        etName = findViewById(R.id.etEditName);
        etEmail = findViewById(R.id.etEditEmail);
        seekBarSensitivity = findViewById(R.id.seekBarSensitivity);
        cbShake = findViewById(R.id.cbTriggerShake);
        cbVolume = findViewById(R.id.cbTriggerVolume);

        Button btnUpdate = findViewById(R.id.btnUpdateProfile);
        Button btnLogout = findViewById(R.id.btnLogout);
        Button btnDelete = findViewById(R.id.btnDeleteAccount);
        Button btnBack = findViewById(R.id.btnBackMainSettings);

        loadCurrentSettings();

        btnUpdate.setOnClickListener(v -> saveSettings());

        btnLogout.setOnClickListener(v -> {
            mAuth.signOut();
            Intent intent = new Intent(SettingsActivity.this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        });

        btnDelete.setOnClickListener(v -> confirmDeleteAccount());
        btnBack.setOnClickListener(v -> finish());
    }

    private void loadCurrentSettings() {
        int sensitivity = prefs.getInt("sensitivity", 12);
        boolean shakeEnabled = prefs.getBoolean("trigger_shake", true);
        boolean volumeEnabled = prefs.getBoolean("trigger_volume", true);

        seekBarSensitivity.setProgress(sensitivity);
        cbShake.setChecked(shakeEnabled);
        cbVolume.setChecked(volumeEnabled);

        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            etEmail.setText(user.getEmail());
            db.collection("users").document(user.getUid()).get()
                    .addOnSuccessListener(documentSnapshot -> {
                        if (documentSnapshot.exists()) {
                            etName.setText(documentSnapshot.getString("username"));
                        }
                    });
        }
    }

    private void saveSettings() {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt("sensitivity", seekBarSensitivity.getProgress());
        editor.putBoolean("trigger_shake", cbShake.isChecked());
        editor.putBoolean("trigger_volume", cbVolume.isChecked());
        editor.apply();

        FirebaseUser user = mAuth.getCurrentUser();
        String newName = etName.getText().toString();
        String newEmail = etEmail.getText().toString();

        if (user != null) {
            if (!newEmail.equals(user.getEmail())) {
                user.updateEmail(newEmail);
            }

            Map<String, Object> updates = new HashMap<>();
            updates.put("username", newName);
            updates.put("email", newEmail);

            db.collection("users").document(user.getUid()).update(updates)
                    .addOnSuccessListener(aVoid -> Toast.makeText(this, "Ajustes actualizados", Toast.LENGTH_SHORT).show());
        }

        Intent intent = new Intent(this, SensorService.class);
        stopService(intent);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    private void confirmDeleteAccount() {
        new AlertDialog.Builder(this)
                .setTitle("Eliminar Cuenta")
                .setMessage("¿Estás seguro? Esto borrará tus datos y contactos permanentemente.")
                .setPositiveButton("ELIMINAR", (dialog, which) -> {
                    FirebaseUser user = mAuth.getCurrentUser();
                    if (user != null) {
                        db.collection("users").document(user.getUid()).delete();
                        user.delete().addOnCompleteListener(task -> {
                            if (task.isSuccessful()) {
                                Toast.makeText(SettingsActivity.this, "Cuenta eliminada", Toast.LENGTH_SHORT).show();
                                startActivity(new Intent(SettingsActivity.this, LoginActivity.class));
                                finishAffinity();
                            }
                        });
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }
}